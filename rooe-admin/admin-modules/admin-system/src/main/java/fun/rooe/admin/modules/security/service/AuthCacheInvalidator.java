package fun.rooe.admin.modules.security.service;

import fun.rooe.admin.constant.CacheKey;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.infrastructure.security.RedisAuthCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 事务内发布缓存失效事件，提交成功后统一清理认证缓存和相关业务缓存。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthCacheInvalidator {
    private final ApplicationEventPublisher events;
    private final RedisAuthCache cache;
    private final AuthTelemetry telemetry;

    public static void requireTransaction() {
        // 禁止脱离事务调用，否则无法保证数据库提交与缓存删除的先后顺序。
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Authentication changes require an active synchronized transaction");
    }

    public void users(Collection<Long> ids) {
        publish(ids, Set.of());
    }

    public void roles(Collection<Long> ids) {
        publish(Set.of(), ids);
    }

    public void keys(Collection<String> keys) {
        requireTransaction();
        events.publishEvent(new Invalidation(Set.copyOf(keys)));
    }

    public void publish(Collection<Long> users, Collection<Long> roles) {
        requireTransaction();
        Set<String> keys = new HashSet<>();
        for (long id : users)
            for (String prefix : List.of(RedisAuthCache.USER, CacheKey.USER_ID,
                    CacheKey.ROLE_USER, CacheKey.ROLE_AUTH, CacheKey.MENU_USER, CacheKey.DATA_USER))
                keys.add(prefix + id);
        for (long id : roles) {
            keys.add(RedisAuthCache.ROLE + id);
            keys.add(CacheKey.ROLE_ID + id);
        }
        events.publishEvent(new Invalidation(Set.copyOf(keys)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void afterCommit(Invalidation event) {
        // 回滚不触发此处；提交后最多尝试三次，仍失败则记录告警，旧缓存最终由 TTL 淘汰。
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                cache.delete(event.getKeys());
                return;
            } catch (RuntimeException e) {
                cache.failure();
                if (attempt < 2) {
                    java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
                }
            }
        }
        telemetry.count("auth_cache_invalidate_failure_total");
        log.error("Authentication cache invalidation failed after three attempts");
    }

    /**
     * Cache invalidation event published inside a transaction.
     */
    @Getter
    @AllArgsConstructor
    public static class Invalidation {
        /**
         * Cache keys to delete after commit.
         */
        private final Set<String> keys;
    }
}
