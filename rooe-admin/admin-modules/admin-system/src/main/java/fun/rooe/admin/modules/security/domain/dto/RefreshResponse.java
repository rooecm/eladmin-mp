package fun.rooe.admin.modules.security.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 凭证轮换结果；与登录响应对齐，仅缺少登录流水字段。
 */
@Getter
@AllArgsConstructor
public class RefreshResponse {
    /**
     * Opaque access token; short-lived and never renewed.
     */
    private final String accessToken;
    /**
     * Opaque refresh token replacing the one presented by the client.
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
     * Absolute expiry timestamp in seconds.
     */
    private final long absoluteExpiresAt;
}
