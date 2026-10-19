package fun.rooe.admin.framework.security.service;

import fun.rooe.admin.framework.security.model.SessionInfo;
import fun.rooe.admin.framework.security.model.SessionTokens;

/**
 * 服务端会话存储接口；框架认证流程不直接依赖 Redis 实现。
 */
public interface SessionService {
    /**
     * 原子创建 Access 与 Refresh 两把凭证；任一已存在时返回 null。
     */
    SessionInfo create(String accessDigest, String refreshDigest, long userId, long authVersion,
                       String clientType, String ip, String userAgent);

    /**
     * 只读校验 Access 凭证；不存在或已过期返回 null，存储故障抛异常。
     */
    SessionInfo authenticate(String accessDigest);

    /**
     * 轮换凭证：旧 Refresh 换取新的 Access 与 Refresh。
     * 旧凭证不存在时返回 null；检测到重放已吊销整条会话时抛出 {@link RefreshReplayException}。
     */
    SessionTokens refresh(String oldRefreshDigest, String newAccessDigest, String newRefreshDigest);

    /**
     * 按会话标识吊销该会话的全部凭证。
     */
    void revokeBySessionId(String sessionId);

    void revokeByAccessDigest(String accessDigest);
}
