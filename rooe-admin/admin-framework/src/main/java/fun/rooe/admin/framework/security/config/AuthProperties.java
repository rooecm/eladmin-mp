package fun.rooe.admin.framework.security.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 认证参数集中绑定与启动校验；时间与跨域来源配置不合法时阻止启动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private Session session = new Session();
    private Cache cache = new Cache();
    private String superAdminRoleCode = "SUPER_ADMIN";
    private List<String> allowedOrigins = List.of("http://localhost:8013", "http://127.0.0.1:8013");
    // 通过环境变量提供的初始化/重置密码，不允许被 Lombok toString 输出到日志。
    @lombok.ToString.Exclude
    private String initialPassword;

    @PostConstruct
    public void validate() {
        for (Duration value : List.of(session.accessTtl, session.refreshTtl, session.replayMarkerTtl,
                cache.userTtl, cache.rolePermissionTtl)) {
            if (value.toSeconds() <= 0)
                throw new IllegalArgumentException("auth durations must be at least one second");
        }
        // Access 必须短于 Refresh，否则续期机制失去意义；绝对生命周期不得超过 Refresh 有效期。
        if (session.accessTtl.compareTo(session.refreshTtl) >= 0)
            throw new IllegalArgumentException("auth access-ttl must be shorter than refresh-ttl");
        if (superAdminRoleCode == null || superAdminRoleCode.isBlank() || allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(o -> o.contains("*")))
            throw new IllegalArgumentException("auth requires a role code and explicit CORS origins");
    }

    @Data
    public static class Session {
        // Access 固定有效期，过期不续期，由前端凭 Refresh 换取新凭证。
        private Duration accessTtl = Duration.ofMinutes(15);
        // Refresh 有效期即会话绝对生命周期，到期必须重新登录。
        private Duration refreshTtl = Duration.ofDays(30);
        // 旧 Refresh 被再次使用后的判定窗口，用于区分重放与普通过期。
        private Duration replayMarkerTtl = Duration.ofMinutes(10);
    }

    @Data
    public static class Cache {
        private Duration userTtl = Duration.ofSeconds(60);
        private Duration rolePermissionTtl = Duration.ofMinutes(5);
    }
}
