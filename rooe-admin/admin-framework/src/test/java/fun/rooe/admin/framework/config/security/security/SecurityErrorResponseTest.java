package fun.rooe.admin.framework.config.security.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.framework.config.security.config.SecurityProperties;
import fun.rooe.admin.framework.config.security.service.OnlineSessionService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Exercises real Spring Security filters and method authorization with MVC advice.
 */
class SecurityErrorResponseTest {
    private final ObjectMapper json = new ObjectMapper();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void missingTokenReturnsLoginRequired() throws Exception {
        assertError(get("/secured"), 401, "AUTH_1005");
    }

    @Test
    void invalidTokenIsTranslatedBeforeController() throws Exception {
        when(context.getBean(TokenProvider.class).loginKey("invalid"))
                .thenThrow(new MalformedJwtException("secret parser detail"));
        assertError(get("/secured").header("Authorization", "Bearer invalid"), 401, "AUTH_1005");
    }

    @Test
    void removedRedisSessionReturnsSessionExpired() throws Exception {
        when(context.getBean(TokenProvider.class).loginKey("removed")).thenReturn("online:removed");
        assertError(get("/secured").header("Authorization", "Bearer removed"), 401, "AUTH_1005");
    }

    @Test
    void dependencyFailureReturnsServiceUnavailable() throws Exception {
        when(context.getBean(TokenProvider.class).loginKey("valid")).thenReturn("online:valid");
        when(context.getBean(OnlineSessionService.class).isOnline("online:valid"))
                .thenThrow(new IllegalStateException("redis password=secret"));
        assertError(get("/secured").header("Authorization", "Bearer valid"), 500, "SYS_1001");
    }

    @Test
    void methodSecurityReturnsForbiddenInsteadOfInternalError() throws Exception {
        TokenProvider tokens = context.getBean(TokenProvider.class);
        when(tokens.loginKey("valid")).thenReturn("online:valid");
        when(context.getBean(OnlineSessionService.class).isOnline("online:valid")).thenReturn(true);
        when(tokens.getAuthentication("valid")).thenReturn(
                new UsernamePasswordAuthenticationToken("admin", "valid", List.of()));
        assertError(get("/secured").header("Authorization", "Bearer valid"), 403, "PERMISSION_1001");
    }

    @Test
    void invalidOldTokenDoesNotBlockPublicEndpoints() throws Exception {
        when(context.getBean(TokenProvider.class).loginKey("invalid"))
                .thenThrow(new MalformedJwtException("invalid token"));
        var response = mvc.perform(get("/public").header("Authorization", "Bearer invalid"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("0", json.readTree(response.getContentAsByteArray()).path("code").asText());
    }

    private void assertError(MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertEquals(status, response.getStatus());
        assertEquals(code, body.path("code").asText());
        assertTrue(body.get("data").isNull());
        assertFalse(body.path("message").asText().contains("secret"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({SecurityErrorResponseWriter.class, JwtAuthenticationEntryPoint.class,
            JwtAccessDeniedHandler.class, GlobalExceptionAdvice.class, ApiResultResponseAdvice.class,
            TestController.class})
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        TokenProvider tokenProvider() {
            return mock(TokenProvider.class);
        }

        @Bean
        OnlineSessionService sessions() {
            return mock(OnlineSessionService.class);
        }

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http, TokenProvider tokens, OnlineSessionService sessions,
                                        SecurityErrorResponseWriter writer, JwtAuthenticationEntryPoint entryPoint,
                                        JwtAccessDeniedHandler deniedHandler) throws Exception {
            SecurityProperties properties = new SecurityProperties();
            properties.setHeader("Authorization");
            properties.setTokenStartWith("Bearer");
            return http.csrf(csrf -> csrf.disable())
                    .addFilterBefore(new TokenFilter(tokens, properties, sessions, writer), UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/public").permitAll().anyRequest().authenticated())
                    .build();
        }
    }

    @RestController
    public static class TestController {
        @GetMapping("/secured")
        @PreAuthorize("denyAll()")
        public Map<String, String> secured() {
            return Map.of("name", "admin");
        }

        @GetMapping("/public")
        public Map<String, String> publicEndpoint() {
            return Map.of("name", "public");
        }
    }
}
