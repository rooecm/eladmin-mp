package fun.rooe.admin.framework.cache;

/**
 * Counter operations required by the framework's rate-limit aspect.
 */
public interface RateLimitStore {

    Long increment(String key, int maxCount, int periodSeconds);
}
