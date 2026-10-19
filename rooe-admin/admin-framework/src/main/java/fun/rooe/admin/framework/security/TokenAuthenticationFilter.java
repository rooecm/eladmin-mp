package fun.rooe.admin.framework.security;

import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.framework.config.security.config.AuthProperties;
import fun.rooe.admin.framework.config.security.security.SecurityErrorResponseWriter;
import fun.rooe.admin.framework.security.model.AuthenticatedUser;
import fun.rooe.admin.framework.security.model.LoginUser;
import fun.rooe.admin.framework.security.model.SessionInfo;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 请求认证入口：验证服务端会话、用户状态与认证版本，再构建本次请求的安全上下文。
 */
@Slf4j
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final SessionService sessions;
    private final AuthIdentityService identities;
    private final AuthProperties properties;
    private final SecurityErrorResponseWriter errors;
    private final AuthTelemetry telemetry;

    private static String rejectionReason(AuthenticatedUser user, SessionInfo session) {
        if (user == null) {
            return "user_missing";
        }
        if (!user.isEnabled()) {
            return "user_disabled";
        }
        // 用户版本升高表示发生敏感变更；会话版本反而更高则视为数据不一致，同样拒绝。
        if (session.getAuthVersion() < user.getAuthVersion()) {
            return "version_stale";
        }
        if (session.getAuthVersion() > user.getAuthVersion()) {
            return "auth_version_inconsistent";
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.isPublic(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        try {
            String header = request.getHeader("Authorization");
            String token = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7) : null;
            if (tokens.isValid(token) && !authenticate(request, response, token)) {
                return;
            }
            // 下游业务异常交给后续处理链，避免被认证基础设施异常处理误转为 503。
            chain.doFilter(request, response);
        } finally {
            // Servlet 线程会复用，所有退出路径均清理上下文，避免身份残留到下一请求。
            SecurityContextHolder.clearContext();
        }
    }

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response, String token) throws IOException {
        try {
            String digest = tokens.digest(token);
            boolean renew = !SecurityPaths.matchesAny(request.getServletPath(), properties.getSession().getRenewExcludePaths());
            // Lua 原子读取会话并按策略续期，注销及配置的排除路径不会续期。
            var session = sessions.authenticateAndRenew(digest, renew);
            if (session == null) {
                errors.write(response, HttpStatus.UNAUTHORIZED, AuthErrorCode.SESSION_INVALID);
                return false;
            }
            var user = identities.getUserForSession(session.getUserId(), session.getAuthVersion());
            String reason = rejectionReason(user, session);
            if (reason != null) {
                sessions.revokeByDigest(digest);
                telemetry.count("auth_session_rejected_total", "reason", reason);
                telemetry.audit("SESSION_REVOKED", session.getUserId(), reason);
                if (reason.equals("auth_version_inconsistent"))
                    log.error("Authentication version is ahead of authoritative user version");
                errors.write(response, HttpStatus.UNAUTHORIZED, AuthErrorCode.SESSION_INVALID);
                return false;
            }
            if (session.isRenewed()) {
                response.setHeader("X-Session-Expires-In", Long.toString(session.getTtlSeconds()));
                telemetry.count("auth_session_renew_total");
            }
            // 强制改密期间清空业务权限，并在下方将访问范围限制到改密相关端点。
            var authorities = user.isMustChangePassword() ? List.<org.springframework.security.core.GrantedAuthority>of()
                    : identities.getAuthorities(user.getRoleIds());
            var principal = new LoginUser(user.getId(), user.getUsername(), user.getNickName(), session.getSessionId(), user.isMustChangePassword(), authorities);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
            SecurityContextHolder.setContext(context);
            if (user.isMustChangePassword() && !SecurityPaths.PASSWORD_CHANGE_ALLOWED_PATHS.contains(request.getServletPath())) {
                errors.write(response, HttpStatus.FORBIDDEN, AuthErrorCode.PASSWORD_CHANGE_REQUIRED);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // 会话或身份查询故障时返回 503；前端可保留 Token，待服务恢复后重试。
            SecurityContextHolder.clearContext();
            telemetry.count("auth_redis_error_total", "op", "session");
            log.warn("Authentication infrastructure unavailable ({})", e.getClass().getSimpleName());
            errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, AuthErrorCode.UNAVAILABLE);
            return false;
        }
    }
}
