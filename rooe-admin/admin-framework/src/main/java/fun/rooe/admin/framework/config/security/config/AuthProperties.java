package fun.rooe.admin.framework.config.security.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;

/**
 * 认证参数集中绑定与启动校验；时间和跨域来源配置不合法时阻止启动。
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
        for (Duration value : List.of(session.idleTimeout, session.maxLifetime, cache.userTtl,
                cache.rolePermissionTtl)) {
            if (value.toSeconds() <= 0)
                throw new IllegalArgumentException("auth durations must be at least one second");
        }
        if (session.maxLifetime.compareTo(session.idleTimeout) <= 0)
            throw new IllegalArgumentException("auth max-lifetime must exceed idle-timeout");
        // touch 专用于活动续期，不能配置到禁止续期的路径中。
        if (session.renewExcludePaths.stream().anyMatch(p -> new AntPathMatcher().match(p, "/api/auth/touch")))
            throw new IllegalArgumentException("auth touch must renew the session");
        if (superAdminRoleCode == null || superAdminRoleCode.isBlank() || allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(o -> o.contains("*")))
            throw new IllegalArgumentException("auth requires a role code and explicit CORS origins");
    }

    @Data
    public static class Session {
        // 空闲超时允许活动续期，最长生命周期是不可被续期突破的上限。
        private Duration idleTimeout = Duration.ofMinutes(30);
        private Duration maxLifetime = Duration.ofHours(12);
        private List<String> renewExcludePaths = List.of("/api/auth/logout", "/actuator/**");
    }

    @Data
    public static class Cache {
        private Duration userTtl = Duration.ofSeconds(60);
        private Duration rolePermissionTtl = Duration.ofMinutes(5);
    }
}
