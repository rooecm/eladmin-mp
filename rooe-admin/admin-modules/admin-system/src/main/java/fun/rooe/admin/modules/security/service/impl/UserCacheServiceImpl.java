package fun.rooe.admin.modules.security.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.security.AuthTelemetry;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.model.UserSnapshot;
import fun.rooe.admin.infrastructure.security.RedisAuthCache;
import fun.rooe.admin.modules.security.service.AuthReadService;
import fun.rooe.admin.modules.security.service.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.IntStream;

/**
 * 缓存不含密码的用户快照；缓存缺失、损坏或版本落后时通过独立事务回源。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserCacheServiceImpl implements UserCacheService {
    private final RedisAuthCache cache;
    private final AuthReadService reads;
    private final AuthProperties properties;
    private final ObjectMapper json;
    private final AuthTelemetry telemetry;
    // 固定数量的进程内分段锁减少同一用户并发回源，避免为每个用户永久保存锁对象。
    private final Object[] locks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    @Override
    public UserSnapshot getUserForSession(long uid, long version) {
        String key = RedisAuthCache.USER + uid;
        String observed = read(key);
        UserSnapshot cached = parse(observed);
        // 更新的用户版本也直接返回，由过滤器判断旧会话是否应失效。
        if (cached != null && cached.getAuthVersion() >= version) {
            return cached;
        }
        synchronized (locks[Math.floorMod(Long.hashCode(uid), locks.length)]) {
            // 只有回源路径二次读取，正常命中只执行一次 GET。
            observed = read(key);
            cached = parse(observed);
            if (cached != null && cached.getAuthVersion() >= version) {
                return cached;
            }
            boolean behind = cached != null && cached.getAuthVersion() < version;
            if (behind) {
                telemetry.count("auth_user_cache_version_refresh_total");
            }
            // 查询前计算绝对过期时间，慢查询不会额外延长旧快照的存活窗口。
            long deadline = cache.deadline(properties.getCache().getUserTtl());
            UserSnapshot user = reads.user(uid);
            if (user != null) {
                try {
                    String value = json.writeValueAsString(user);
                    // 刷新须匹配此前读到的值；首次填充仅写入空键，减少覆盖并发更新的风险。
                    if (observed != null) {
                        cache.refresh(key, observed, value, deadline);
                    } else cache.fill(key, value, deadline);
                } catch (Exception e) {
                    cache.failure();
                    log.warn("User cache fill unavailable");
                }
            }
            return user;
        }
    }

    private String read(String key) {
        try {
            return cache.get(key);
        } catch (RuntimeException e) {
            cache.failure();
            return null;
        }
    }

    private UserSnapshot parse(String value) {
        try {
            return value == null ? null : json.readValue(value, UserSnapshot.class);
        } catch (Exception e) {
            cache.failure();
            return null;
        }
    }
}
