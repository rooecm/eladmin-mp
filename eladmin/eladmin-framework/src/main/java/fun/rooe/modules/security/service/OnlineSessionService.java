package fun.rooe.modules.security.service;

/**
 * JWT 过滤器使用的在线会话查询接口，由业务模块提供实现。
 */
public interface OnlineSessionService {

    /**
     * 判断登录会话是否仍然有效。
     * @param loginKey 登录会话的 Redis key
     * @return 会话是否存在
     */
    boolean isOnline(String loginKey);
}
