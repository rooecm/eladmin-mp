package fun.rooe.admin.framework.cache;

import java.util.concurrent.TimeUnit;

/**
 * Cache operations required by the framework's token renewal flow.
 */
public interface TokenCache {

    long getExpire(String key);

    boolean expire(String key, long timeout, TimeUnit unit);
}
