package fun.rooe.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ObservabilityStarterTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
class ObservabilityStarterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("org.zalando.logbook.Logbook");
    private final Events events = new Events();
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper mapper;
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
        events.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void tracesRealRequestsAndRedactsLogsWithoutChangingResponse() throws Exception {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("header-secret");
        headers.set(HttpHeaders.COOKIE, "session=cookie-secret");
        var response = rest.postForEntity("/public/echo?password=query-secret", new HttpEntity<>(
                "{\"password\":\"body-secret\",\"nested\":{\"newPass\":\"nested-secret\"},\"name\":\"visible\"}",
                headers), String.class);

        String traceId = response.getHeaders().getFirst("traceId");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("body-secret", "response-secret");
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("response-cookie-secret");
        assertThat(mapper.readTree(response.getBody()).path("traceId").asText()).isEqualTo(traceId);
        assertThat(events.values).hasSize(2);
        for (ILoggingEvent event : events.values) {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).doesNotContain("secret").contains("XXX", "visible");
            assertThat(json(event).path("traceId").asText()).isEqualTo(traceId);
            assertThat(event.getMDCPropertyMap().get("traceId")).isEqualTo(traceId);
        }
        assertThat(json(events.values.get(1)).path("status").asInt()).isEqualTo(200);
        assertThat(json(events.values.get(1)).path("body").path("code").asText()).isEqualTo("OK");
    }

    @Test
    void returnsTraceAndLogsForSecurityRejections() throws Exception {
        for (var response : List.of(rest.getForEntity("/protected", String.class),
                rest.withBasicAuth("reader", "password").getForEntity("/forbidden", String.class))) {
            assertThat(response.getStatusCode().value()).isIn(401, 403);
            assertThat(response.getHeaders().getFirst("traceId")).matches("[0-9a-f]{32}");
            String traceId = response.getHeaders().getFirst("traceId");
            assertThat(events.values.stream().map(this::json)
                    .filter(log -> log.path("type").asText().equals("response"))
                    .anyMatch(log -> log.path("traceId").asText().equals(traceId)
                            && log.path("status").asInt() == response.getStatusCode().value())).isTrue();
        }
        assertThat(events.values.stream().map(this::json).filter(log -> log.has("status"))
                .map(log -> log.path("status").asInt()).toList()).contains(401, 403);
    }

    @Test
    void logsHandledFailuresAndRetainsHeaderOnContainerErrorDispatch() {
        var handled = rest.getForEntity("/public/failure", String.class);
        assertThat(handled.getStatusCode().value()).isEqualTo(500);
        String traceId = handled.getHeaders().getFirst("traceId");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(events.values.stream().map(this::json).anyMatch(log -> log.path("status").asInt() == 500
                && log.path("traceId").asText().equals(traceId))).isTrue();
        var unhandled = rest.getForEntity("/public/crash", String.class);
        assertThat(unhandled.getStatusCode().value()).isEqualTo(500);
        assertThat(unhandled.getHeaders().getFirst("traceId")).matches("[0-9a-f]{32}");
    }

    @Test
    void honorsIncomingW3cContextAndGeneratesDifferentTraceIdsForIndependentRequests() {
        String incomingTrace = "0123456789abcdef0123456789abcdef";
        var headers = new HttpHeaders();
        headers.set("traceparent", "00-" + incomingTrace + "-0123456789abcdef-01");
        var propagated = rest.exchange("/public/echo", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(propagated.getHeaders().getFirst("traceId")).isEqualTo(incomingTrace);
        var independent = rest.getForEntity("/public/echo", String.class);
        var next = rest.getForEntity("/public/echo", String.class);
        assertThat(independent.getHeaders().getFirst("traceId")).matches("[0-9a-f]{32}")
                .isNotEqualTo(incomingTrace).isNotEqualTo(next.getHeaders().getFirst("traceId"));
    }

    @Test
    void preservesCorsRulesAndSupportsDownloadsAndAsyncResponses() {
        var headers = new HttpHeaders();
        headers.setOrigin("https://example.com");
        var download = rest.exchange("/public/download", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(download.getBody()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(download.getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://example.com");
        assertThat(download.getHeaders().getAccessControlExposeHeaders()).contains("Content-Disposition", "traceId");
        assertThat(download.getHeaders().getFirst("traceId")).matches("[0-9a-f]{32}");
        var async = rest.getForEntity("/public/async", String.class);
        assertThat(async.getStatusCode().value()).isEqualTo(200);
        assertThat(async.getHeaders().getFirst("traceId")).matches("[0-9a-f]{32}");
        assertThat(events.values.stream().map(this::json).anyMatch(log ->
                log.path("type").asText().equals("response")
                        && log.path("traceId").asText().equals(async.getHeaders().getFirst("traceId"))))
                .isTrue();
    }

    @Test
    void excludesDocumentationAndMonitoringByDefault() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(rest.getForEntity("/swagger-ui/test", String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(events.values).isEmpty();
    }

    private JsonNode json(ILoggingEvent event) {
        try {
            return mapper.readTree(event.getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("Expected structured JSON web log", exception);
        }
    }

    static class Events extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> values = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            values.add(event);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({Endpoints.class, Errors.class})
    static class Application {

        @Bean
        SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/public/**", "/error", "/actuator/**", "/swagger-ui/**").permitAll()
                            .requestMatchers("/forbidden").denyAll().anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults()).build();
        }

        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager(User.withUsername("reader")
                    .password("{noop}password").roles("READER").build());
        }

        @Bean
        WebMvcConfigurer cors() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/public/**").allowedOrigins("https://example.com")
                            .exposedHeaders("Content-Disposition");
                }
            };
        }
    }

    @RestController
    static class Endpoints {
        private final Tracer tracer;

        Endpoints(Tracer tracer) {
            this.tracer = tracer;
        }

        @PostMapping("/public/echo")
        ResponseEntity<Map<String, Object>> echo(@RequestBody Map<String, Object> body) {
            body.put("traceId", tracer.currentSpan().context().traceId());
            body.put("token", "response-secret");
            body.put("code", "OK");
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, "session=response-cookie-secret").body(body);
        }

        @GetMapping({"/public/echo", "/swagger-ui/test"})
        Map<String, String> echo() {
            return Map.of("traceId", tracer.currentSpan().context().traceId());
        }

        @GetMapping("/public/download")
        ResponseEntity<byte[]> download() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=test.bin")
                    .body(new byte[]{1, 2, 3});
        }

        @GetMapping("/public/async")
        Callable<Map<String, String>> async() {
            return () -> Map.of("result", "ok");
        }

        @GetMapping("/public/failure")
        void failure() {
            throw new IllegalArgumentException("handled failure");
        }

        @GetMapping("/public/crash")
        void crash() {
            throw new IllegalStateException("unhandled failure");
        }
    }

    @RestControllerAdvice
    static class Errors {
        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, String>> handle() {
            return ResponseEntity.internalServerError().body(Map.of("error", "failed"));
        }
    }
}
