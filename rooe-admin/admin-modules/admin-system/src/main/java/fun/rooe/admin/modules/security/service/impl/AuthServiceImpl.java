package fun.rooe.admin.modules.security.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import fun.rooe.admin.framework.security.AuthFailure;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.RefreshReplayException;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import fun.rooe.admin.framework.utils.SecurityUtils;
import fun.rooe.admin.modules.security.domain.dto.LoginCredentialSnapshot;
import fun.rooe.admin.modules.security.domain.dto.LoginResponse;
import fun.rooe.admin.modules.security.domain.dto.RefreshResponse;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.domain.request.PasswordChangeRequest;
import fun.rooe.admin.modules.security.domain.request.RefreshRequest;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import fun.rooe.admin.modules.security.service.*;
import fun.rooe.admin.modules.system.domain.entity.UserDO;
import fun.rooe.admin.modules.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 编排登录、续期、会话与改密流程；Redis 会话细节由框架接口及基础设施实现承接。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;
    private final SessionService sessions;
    private final AuthIdentityService identities;
    private final AuthMapper authMapper;
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final AuthVersionService versions;
    private final AuthCacheInvalidator invalidator;
    private final AdminPrivilegeGuard guard;
    private final AuthTelemetry telemetry;
    private final Clock clock;

    @Override
    public LoginCredentialSnapshot verifyCredentials(LoginRequest request) {
        String username = LoginService.canonical(request.getUsername());
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.getPassword()));
            var verified = (LoginCredentialSnapshot) authentication.getPrincipal();
            // 密码验证通过后才区分禁用账号，登录快照来自主库而非用户缓存。
            if (!Boolean.TRUE.equals(verified.snapshot().getEnabled())) {
                telemetry.count("auth_login_total", "result", "disabled");
                telemetry.audit("LOGIN_FAILURE", verified.snapshot().getId(), "disabled");
                throw new DisabledException("Account disabled");
            }
            return verified;
        } catch (BadCredentialsException e) {
            telemetry.count("auth_login_total", "result", "bad_credentials");
            telemetry.audit("LOGIN_FAILURE", null, "bad_credentials");
            throw e;
        } catch (AuthenticationServiceException e) {
            throw new AuthFailure(503, AuthErrorCode.UNAVAILABLE);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AuthFailure(503, AuthErrorCode.UNAVAILABLE);
        }
    }

    @Override
    public LoginResponse establishSession(LoginCredentialSnapshot verified, String clientType, String ip, String agent) {
        var user = verified.snapshot();
        try {
            String access = tokens.generateToken();
            String refresh = tokens.generateToken();
            // 原始凭证仅返回客户端，Redis 使用摘要作为键。
            var session = sessions.create(tokens.digest(access), tokens.digest(refresh), user.getId(),
                    user.getAuthVersion(), clientType, ip, agent);
            try {
                // 登录信息属于辅助记录，更新失败不撤销已创建的会话。
                authMapper.recordLogin(user.getId(), LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")), ip);
            } catch (RuntimeException e) {
                log.warn("Last login update failed");
            }
            telemetry.count("auth_login_total", "result", "success");
            telemetry.audit("LOGIN_SUCCESS", user.getId(), "success");
            return new LoginResponse("SUCCESS", access, refresh, "Bearer", session.getTtlSeconds(),
                    session.getAbsoluteExpireAt(), Boolean.TRUE.equals(user.getMustChangePassword()),
                    user.getLastLoginAt(), user.getLastLoginIp());
        } catch (RuntimeException e) {
            telemetry.count("auth_redis_error_total", "op", "session");
            throw new AuthFailure(503, AuthErrorCode.UNAVAILABLE);
        } finally {
            // 签发成功或失败都擦除登录快照中的密码哈希。
            verified.eraseCredentials();
        }
    }

    @Override
    public RefreshResponse refresh(RefreshRequest request) {
        try {
            String access = tokens.generateToken();
            String refresh = tokens.generateToken();
            var rotated = sessions.refresh(tokens.digest(request.getRefreshToken()), tokens.digest(access),
                    tokens.digest(refresh));
            if (rotated == null) {
                // 凭证不存在或已过期：对外只表达会话无效，避免泄漏凭证具体状态。
                telemetry.count("auth_refresh_total", "result", "invalid");
                throw new AuthFailure(401, AuthErrorCode.SESSION_INVALID);
            }
            // 轮换后重新校验用户状态与版本，禁用、改密或踢出对续期立即生效。
            var user = identities.getUserForSession(rotated.getUserId(), rotated.getAuthVersion());
            if (user == null || !user.isEnabled() || rotated.getAuthVersion() != user.getAuthVersion()) {
                sessions.revokeBySessionId(rotated.getSessionId());
                telemetry.count("auth_refresh_total", "result", "rejected");
                telemetry.audit("SESSION_REVOKED", rotated.getUserId(), user == null ? "user_missing" : "session_stale");
                throw new AuthFailure(401, AuthErrorCode.SESSION_INVALID);
            }
            telemetry.count("auth_refresh_total", "result", "success");
            return new RefreshResponse(access, refresh, "Bearer", rotated.getAccessTtlSeconds(),
                    rotated.getAbsoluteExpireAt());
        } catch (RefreshReplayException e) {
            // 重放已由存储层吊销整条会话，这里只对客户端表达会话失效。
            throw new AuthFailure(401, AuthErrorCode.SESSION_INVALID);
        } catch (AuthFailure e) {
            throw e;
        } catch (RuntimeException e) {
            telemetry.count("auth_redis_error_total", "op", "session");
            throw new AuthFailure(503, AuthErrorCode.UNAVAILABLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeRequest request) {
        long uid = SecurityUtils.getCurrentUserId();
        guard.lockAdministration();
        // 用户行锁使并发改密串行验证，避免两个请求都基于同一旧密码完成修改。
        var user = users.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getId, uid).last("FOR UPDATE"));
        if (user == null) {
            throw new AuthFailure(401, AuthErrorCode.SESSION_INVALID);
        }
        guard.changeUser(uid, false);
        if (!passwords.matches(request.getOldPassword(), user.getPassword())) {
            telemetry.audit("PASSWORD_CHANGE_FAILURE", uid, "old_password");
            telemetry.count("auth_password_change_total", "result", "old_password_incorrect");
            throw new AuthFailure(400, AuthErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwords.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "新密码不能与旧密码相同");
        }
        users.update(Wrappers.<UserDO>lambdaUpdate().eq(UserDO::getId, uid)
                .set(UserDO::getPassword, passwords.encode(request.getNewPassword()))
                .set(UserDO::getMustChangePassword, false)
                .set(UserDO::getPwdResetTime, LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"))));
        // 密码和版本原子提交，提交后清缓存，使包括当前会话在内的旧会话重新接受版本校验。
        versions.bumpAuthVersion(uid, "password_changed");
        invalidator.users(List.of(uid));
        telemetry.audit("PASSWORD_CHANGED", uid, "self");
    }

    @Override
    public void logout(String token) {
        try {
            if (tokens.isValid(token)) {
                sessions.revokeByAccessDigest(tokens.digest(token));
            }
            telemetry.audit("LOGOUT", SecurityUtils.getCurrentUserId(), "logout");
        } catch (org.springframework.dao.DataAccessException e) {
            telemetry.count("auth_redis_error_total", "op", "session");
            throw new AuthFailure(503, AuthErrorCode.UNAVAILABLE);
        }
    }
}
