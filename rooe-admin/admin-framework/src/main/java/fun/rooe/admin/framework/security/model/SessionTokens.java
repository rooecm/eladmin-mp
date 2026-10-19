package fun.rooe.admin.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 轮换后的会话快照；accessTtl 为本次签发的 Access 剩余有效秒数。
 */
@Getter
@AllArgsConstructor
public class SessionTokens {
    /**
     * Session identifier shared by both credentials.
     */
    private final String sessionId;
    /**
     * User ID.
     */
    private final long userId;
    /**
     * Creation timestamp in seconds.
     */
    private final long createdAt;
    /**
     * Absolute expiry timestamp in seconds.
     */
    private final long absoluteExpireAt;
    /**
     * Authentication version bound to the session.
     */
    private final long authVersion;
    /**
     * Access token TTL in seconds.
     */
    private final long accessTtlSeconds;
}
