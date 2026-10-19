package fun.rooe.admin.framework.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.error.AuthErrorCode;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.framework.security.config.AuthProperties;
import fun.rooe.admin.framework.security.config.SpringSecurityConfig;
import fun.rooe.admin.framework.security.model.SessionInfo;
import fun.rooe.admin.framework.security.model.UserSnapshot;
import fun.rooe.admin.framework.security.service.AuthIdentityService;
import fun.rooe.admin.framework.security.service.SessionService;
import fun.rooe.admin.framework.security.service.TokenService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

class SecurityHttpIntegrationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private SessionService sessions;
    private AuthIdentityService identities;
    private String token;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
        sessions = context.getBean(SessionService.class);
        identities = context.getBean(AuthIdentityService.class);
        token = context.getBean(TokenService.class).generateToken();
        when(sessions.authenticate(anyString())).thenReturn(new SessionInfo("sid", 1, 1, Long.MAX_VALUE, 2, false, 900));
        when(identities.getUserForSession(1, 2)).thenReturn(new UserSnapshot(1, "user", "User", true, false, 2, List.of(1L)));
        when(identities.getAuthorities(anyList())).thenReturn(List.of(new SimpleGrantedAuthority("user:list")));
    }

    @AfterEach
    void close() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    private org.springframework.mock.web.MockHttpServletResponse call(String path) throws Exception {
        return mvc.perform(get(path).servletPath(path).header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    @Test
    void missingAndMalformedTokensAre401AndNeverConsultInfrastructure() throws Exception {
        var response = mvc.perform(get("/protected").servletPath("/protected")).andReturn().getResponse();
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        token = "malformed";
        assertEquals(401, call("/protected").getStatus());
        verifyNoInteractions(sessions, identities);
    }

    @Test
    void globalAuthenticationProtectsEndpointsWithoutMethodAnnotations() throws Exception {
        String validToken = token;
        for (String path : List.of("/authenticated", "/api/auth/me", "/api/auth/logout", "/failure")) {
            assertEquals(401, mvc.perform(get(path).servletPath(path)).andReturn().getResponse().getStatus(), path);
            token = "malformed";
            assertEquals(401, call(path).getStatus(), path);
        }
        verifyNoInteractions(sessions, identities);
        token = validToken;
        when(identities.getAuthorities(anyList())).thenReturn(List.of());
        assertEquals(200, call("/authenticated").getStatus());
        assertEquals(200, call("/api/auth/me").getStatus());
    }

    @Test
    void methodDenialRemains403ThroughGlobalAdviceAndNeverRenews() throws Exception {
        var response = call("/denied");
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("X-Session-Expires-In"));
        assertTrue(response.getContentAsString().contains("PERMISSION_1001"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, call("/protected").getStatus());
        verify(sessions, times(2)).authenticate(anyString());
    }

    @Test
    void outageIs503AndCannotFallThroughAsAnonymousOrAuthenticated() throws Exception {
        when(sessions.authenticate(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("secret"));
        var response = call("/protected");
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("secret"));
        verifyNoInteractions(identities);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void staleDisabledMissingAndInconsistentVersionsRevokeTheSession() throws Exception {
        for (UserSnapshot user : new UserSnapshot[]{
                new UserSnapshot(1, "u", "U", true, false, 3, List.of()),
                new UserSnapshot(1, "u", "U", false, false, 2, List.of()),
                new UserSnapshot(1, "u", "U", true, false, 1, List.of()), null}) {
            when(identities.getUserForSession(1, 2)).thenReturn(user);
            var response = call("/protected");
            assertEquals(401, response.getStatus());
            assertNull(response.getHeader("X-Session-Expires-In"));
        }
        verify(sessions, times(4)).revokeBySessionId(anyString());
    }

    @Test
    void forcedPasswordChangeBlocksEveryOtherPathEvenWhenOnlyAuthenticationIsRequired() throws Exception {
        when(identities.getUserForSession(1, 2)).thenReturn(new UserSnapshot(1, "u", "U", true, true, 2, List.of(1L)));
        var response = call("/authenticated");
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("AUTH_PASSWORD_CHANGE_REQUIRED"));
        assertEquals(200, call("/api/auth/me").getStatus());
        verify(identities, never()).getAuthorities(anyList());
    }

    @Test
    void publicEndpointsIgnoreBadTokensAndCorsPreflightAllowsAuthorization() throws Exception {
        token = "bad";
        assertEquals(200, call("/error").getStatus());
        verifyNoInteractions(sessions);
        var response = mvc.perform(options("/protected").servletPath("/protected")
                .header("Origin", "http://localhost:8013").header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("http://localhost:8013", response.getHeader("Access-Control-Allow-Origin"));
        token = context.getBean(TokenService.class).generateToken();
        response = mvc.perform(get("/protected").servletPath("/protected").header("Origin", "http://localhost:8013")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals("http://localhost:8013", response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void logoutDoesNotRenewAndSubsequentControllerFailureDoesNotBecome503() throws Exception {

        var response = call("/api/auth/logout");
        assertEquals(200, response.getStatus());
        assertNull(response.getHeader("X-Session-Expires-In"));
        assertEquals(500, call("/failure").getStatus());
    }

    @Test
    void anonymousDownstreamFailureAlwaysClearsContextWithoutConsultingInfrastructure() {
        var filter = filter(context.getBean(SecurityErrorResponseWriter.class));
        var request = new MockHttpServletRequest("GET", "/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer malformed");
        var failure = new ServletException("downstream");
        var thrown = assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("downstream", null, List.of()));
            throw failure;
        }));
        assertSame(failure, thrown);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(sessions, identities);
    }

    @Test
    void rejectionWriterFailureAlsoClearsAuthenticatedContext() throws Exception {
        when(identities.getUserForSession(1, 2)).thenReturn(new UserSnapshot(1, "u", "U", true, true, 2, List.of()));
        var writer = mock(SecurityErrorResponseWriter.class);
        doThrow(new IOException("client disconnected")).when(writer)
                .write(any(), eq(HttpStatus.FORBIDDEN), eq(AuthErrorCode.PASSWORD_CHANGE_REQUIRED));
        var request = new MockHttpServletRequest("GET", "/protected");
        request.setServletPath("/protected");
        request.addHeader("Authorization", "Bearer " + token);
        assertThrows(IOException.class, () -> filter(writer).doFilter(request, new MockHttpServletResponse(), (req, res) -> fail("Rejected request continued")));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private TokenAuthenticationFilter filter(SecurityErrorResponseWriter writer) {
        return new TokenAuthenticationFilter(context.getBean(TokenService.class), sessions, identities,
                context.getBean(AuthProperties.class), writer, context.getBean(AuthTelemetry.class));
    }

    @Configuration
    @EnableWebMvc
    @Import({SpringSecurityConfig.class, PermissionChecker.class, GlobalExceptionAdvice.class})
    static class TestConfig {
        @Bean
        AuthProperties properties() {
            return new AuthProperties();
        }

        @Bean
        ObjectMapper json() {
            return new ObjectMapper();
        }

        @Bean
        AuthTelemetry telemetry() {
            return mock(AuthTelemetry.class);
        }

        @Bean
        SecurityErrorResponseWriter errors(ObjectMapper json, AuthTelemetry telemetry) {
            return new SecurityErrorResponseWriter(json, telemetry);
        }

        @Bean
        SessionService sessions() {
            return mock(SessionService.class);
        }

        @Bean
        AuthIdentityService identities() {
            return mock(AuthIdentityService.class);
        }

        @Bean
        TokenService tokens() {
            return new TokenService();
        }

        @Bean
        UserDetailsService users() {
            return username -> org.springframework.security.core.userdetails.User.withUsername(username).password("unused").authorities("none").build();
        }

        @Bean
        Fixture endpoints() {
            return new Fixture();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            var source = new UrlBasedCorsConfigurationSource();
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of("http://localhost:8013"));
            cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            cors.setExposedHeaders(List.of());
            source.registerCorsConfiguration("/**", cors);
            return source;
        }
    }

    @RestController
    static class Fixture {
        @GetMapping("/protected")
        @PreAuthorize("@perm.has('user:list')")
        public Map<String, String> allowed() {
            return Map.of("ok", "true");
        }

        @GetMapping("/denied")
        @PreAuthorize("@perm.has('user:del')")
        public void denied() {
        }

        @GetMapping({"/authenticated", "/api/auth/me", "/api/auth/logout"})
        public void authenticated() {
        }

        @GetMapping("/error")
        public void error() {
        }

        @GetMapping("/failure")
        public void failure() {
            throw new IllegalStateException("secret");
        }
    }
}


