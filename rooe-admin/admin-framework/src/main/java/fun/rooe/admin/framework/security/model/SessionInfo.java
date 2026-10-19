package fun.rooe.admin.framework.security.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话存储返回值；时间戳与 TTL 均以秒为单位，renewed 表示本次是否执行续期。
 */
@Getter
@AllArgsConstructor
public class SessionInfo {
    /**
     * Session identifier.
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
     * Whether this authentication performed a renewal.
     */
    private final boolean renewed;
    /**
     * Remaining TTL in seconds.
     */
    private final long ttlSeconds;
}