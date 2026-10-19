package fun.rooe.admin.security;

import fun.rooe.admin.framework.security.SecurityPaths;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ControllerAuthorizationCoverageTest {
    // 这些接口只要求登录，由全局过滤链认证；其余非公开接口仍须声明业务权限。
    private static final Set<String> AUTHENTICATED_ENDPOINTS = Set.of(
            "GET /api/auth/me", "POST /api/auth/logout", "POST /api/auth/touch", "POST /api/auth/password",
            "GET /api/dictDetail", "GET /api/dictDetail/map", "GET /api/menus/build", "GET /api/roles/level",
            "GET /api/logs/user", "PUT /api/users/center", "POST /api/users/updateAvatar",
            "POST /api/users/updateEmail/{code}", "POST /api/code/resetEmail", "POST /api/code/email/resetPass",
            "GET /api/code/validated");

    @Test
    void businessHandlersDeclarePermissionsAndPermissionCodesAreSeeded() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Web.class);
            for (var candidate : scanner.findCandidateComponents("fun.rooe.admin")) {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                if (type.getProtectionDomain().getCodeSource().getLocation().toString().contains("test-classes")) {
                    continue;
                }
                context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton(type.getName(), mock(type)));
            }
            context.refresh();
            var mapping = context.getBean(RequestMappingHandlerMapping.class);
            assertTrue(mapping.getHandlerMethods().size() > 60, "Must inspect the real production controller inventory");
            StringBuilder seeds = new StringBuilder();
            try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
                for (Path path : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                    seeds.append(Files.readString(path));
                }
            }
            Pattern permission = Pattern.compile("@perm\\.has\\('([^']+)'\\)");
            Set<String> authenticatedEndpoints = new HashSet<>();
            mapping.getHandlerMethods().forEach((info, handler) -> {
                var annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
                if (annotation == null) {
                    annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
                }
                var endpoints = info.getMethodsCondition().getMethods().stream()
                        .flatMap(method -> info.getPatternValues().stream().map(path -> method.name() + " " + path))
                        .collect(Collectors.toSet());
                boolean authenticationOnly = !endpoints.isEmpty() && AUTHENTICATED_ENDPOINTS.containsAll(endpoints);
                if (authenticationOnly) {
                    assertFalse(info.getPatternValues().stream().anyMatch(SecurityPaths::isPublic),
                            () -> "Authentication-only endpoint must not be public: " + handler);
                    authenticatedEndpoints.addAll(endpoints);
                }
                assertTrue(annotation != null || authenticationOnly || info.getPatternValues().stream().allMatch(SecurityPaths::isPublic),
                        () -> "Missing authorization: " + handler);
                if (annotation != null) {
                    var matcher = permission.matcher(annotation.value());
                    while (matcher.find()) assertTrue(seeds.toString().contains("'" + matcher.group(1) + "'"),
                            "Unseeded permission: " + matcher.group(1));
                    assertFalse(annotation.value().contains("@el."));
                }
            });
            assertEquals(AUTHENTICATED_ENDPOINTS, authenticatedEndpoints, "Authentication-only endpoint inventory is stale");
        }
    }

    @Configuration
    @EnableWebMvc
    static class Web {
    }
}
