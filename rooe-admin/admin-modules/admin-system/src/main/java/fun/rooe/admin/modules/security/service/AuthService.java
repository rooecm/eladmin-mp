package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.modules.security.domain.dto.LoginCredentialSnapshot;
import fun.rooe.admin.modules.security.domain.dto.LoginResponse;
import fun.rooe.admin.modules.security.domain.request.LoginRequest;
import fun.rooe.admin.modules.security.domain.request.PasswordChangeRequest;

/**
 * 认证业务入口：凭证校验、会话签发、改密和注销由业务层编排。
 */
public interface AuthService {
    /**
     * 校验账号密码，返回仅用于本次登录的数据库快照。
     */
    LoginCredentialSnapshot verifyCredentials(LoginRequest request);

    /**
     * 使用已验证快照创建服务端会话，完成后擦除快照中的密码。
     */
    LoginResponse establishSession(LoginCredentialSnapshot verified, String clientType, String ip, String userAgent);

    void changePassword(PasswordChangeRequest request);

    void logout(String token);
}
