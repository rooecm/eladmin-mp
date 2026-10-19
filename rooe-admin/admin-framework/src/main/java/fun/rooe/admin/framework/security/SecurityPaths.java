package fun.rooe.admin.framework.security;

import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;

public final class SecurityPaths {
    public static final String[] PUBLIC_PATHS = {"/api/auth/login", "/actuator/health", "/actuator/health/**", "/error", "/", "/avatar/**"};
    public static final List<String> PASSWORD_CHANGE_ALLOWED_PATHS = List.of(
            "/api/auth/me", "/api/auth/password", "/api/auth/logout", "/api/auth/touch");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private SecurityPaths() {
    }

    public static boolean isPublic(String path) {
        return matchesAny(path, Arrays.asList(PUBLIC_PATHS));
    }

    public static boolean matchesAny(String path, Iterable<String> patterns) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
