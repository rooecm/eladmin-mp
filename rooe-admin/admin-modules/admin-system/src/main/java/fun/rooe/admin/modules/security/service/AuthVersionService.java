package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.modules.security.mapper.AuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 敏感变更时递增用户认证版本，使旧版本会话在后续认证校验中失效。
 */
@Service
@RequiredArgsConstructor
public class AuthVersionService {
    private final AuthMapper mapper;
    private final AuthTelemetry telemetry;

    public void bumpAuthVersion(long uid, String reason) {
        // 版本与业务数据一起提交或回滚；指标和审计只在提交成功后记录。
        AuthCacheInvalidator.requireTransaction();
        if (mapper.bumpVersion(uid) != 1) {
            throw new IllegalStateException("Authentication user missing");
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        telemetry.count("auth_version_bump_total", "reason", reason);
                        telemetry.audit("AUTH_VERSION_BUMPED", uid, reason);
                    }
                });
    }
}
