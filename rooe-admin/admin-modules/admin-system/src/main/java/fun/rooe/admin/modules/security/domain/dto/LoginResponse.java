package fun.rooe.admin.modules.security.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 登录成功响应载荷；Access 与 Refresh 均为不透明随机串，过期时间以秒为单位。
 */
@Getter
@AllArgsConstructor
public class LoginResponse {
    /**
     * Result status code.
     */
    private final String status;
    /**
     * Opaque access token; short-lived and never renewed.
     */
    private final String accessToken;
    /**
     * Opaque refresh token; exchanged for a new access token when the access token expires.
     */
    private final String refreshToken;
    /**
     * Token type prefix, always Bearer.
     */
    private final String tokenType;
    /**
     * Access token TTL in seconds.
     */
    private final long expiresIn;
    /**
     * Absolute expiry timestamp in seconds; the refresh token never outlives it.
     */
    private final long absoluteExpiresAt;
    /**
     * Whether the user must change password before further access.
     */
    private final boolean mustChangePassword;
    /**
     * Last login time.
     */
    private final LocalDateTime lastLoginAt;
    /**
     * Last login IP.
     */
    private final String lastLoginIp;
}
