package fun.rooe.admin.framework.security.service;

import fun.rooe.admin.framework.security.model.SessionInfo;

/**
 * 服务端会话存储接口；框架认证流程不直接依赖 Redis 实现。
 */
public interface SessionService {
    SessionInfo create(String digest, long userId, long authVersion, String clientType, String ip, String userAgent);

    /**
     * 验证与可选续期须原子完成；无有效会话时返回 null，存储故障抛异常。
     */
    SessionInfo authenticateAndRenew(String digest, boolean renewAllowed);

    void revokeByDigest(String digest);
}
