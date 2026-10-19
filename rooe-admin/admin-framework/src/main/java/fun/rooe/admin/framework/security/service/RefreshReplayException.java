package fun.rooe.admin.framework.security.service;

/**
 * 旧 Refresh 凭证被重复使用时抛出，调用方应将会话视为已泄露并已完成吊销。
 */
public class RefreshReplayException extends RuntimeException {
    public RefreshReplayException(String message) {
        super(message);
    }
}
