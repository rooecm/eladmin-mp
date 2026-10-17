package fun.rooe.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.framework.advice.ApiResultResponseAdvice;
import fun.rooe.admin.framework.advice.GlobalExceptionAdvice;
import fun.rooe.admin.framework.config.webConfig.ConfigurerAdapter;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.observability.logging.InfoHttpLogWriter;
import fun.rooe.starter.support.env.YamlDefaultsEnvironmentPostProcessor;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryEventPublisherBeansApplicationListener;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LogbookWebLoggingTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(context -> new YamlDefaultsEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), null))
            .withInitializer(context -> {
                // Context runners need the listener normally registered by SpringApplication.
                OpenTelemetryEventPublisherBeansApplicationListener.addWrapper();
                context.addApplicationListener(new OpenTelemetryEventPublisherBeansApplicationListener());
            })
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    ObservationAutoConfiguration.class, OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class, MicrometerTracingAutoConfiguration.class,
                    WebMvcObservationAutoConfiguration.class, LogbookAutoConfiguration.class))
            .withConfiguration(AutoConfigurations.of(starterAutoConfigurations()));

    private static Class<?>[] starterAutoConfigurations() {
        return StreamSupport.stream(ImportCandidates.load(AutoConfiguration.class,
                        LogbookWebLoggingTest.class.getClassLoader()).spliterator(), false)
                .filter(name -> name.startsWith("fun.rooe.observability."))
                .map(name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException exception) {
                        throw new IllegalStateException(exception);
                    }
                }).toArray(Class<?>[]::new);
    }

    @Test
    void logsRequestsAndResponsesWithTraceIdsAndRedactsCredentials() {
        withLogging((mvc, events) -> {
            var response = mvc.perform(post("/api/logbook")
                            .queryParam("password", "query-secret")
                            .header("Authorization", "Bearer header-secret")
                            .header("Cookie", "session=cookie-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"body-secret\",\"nested\":{\"oldPass\":\"old-secret\","
                                    + "\"newPass\":\"new-secret\"},\"name\":\"visible\"}"))
                    .andReturn().getResponse();

            assertEquals(200, response.getStatus());
            var body = new ObjectMapper().readTree(response.getContentAsByteArray()).path("data");
            assertEquals("body-secret", body.path("password").asText());
            assertEquals("response-secret", body.path("token").asText());
            assertEquals("session=response-cookie-secret", response.getHeader("Set-Cookie"));
            String traceId = response.getHeader("traceId");
            assertNotNull(traceId);
            assertTrue(traceId.matches("[0-9a-f]{32}"));
            assertEquals(2, events.list.size());
            for (ILoggingEvent event : events.list) {
                assertEquals(Level.INFO, event.getLevel());
                assertEquals(traceId, event.getMDCPropertyMap().get("traceId"));
                String message = event.getFormattedMessage();
                assertEquals(traceId, new ObjectMapper().readTree(message).path("traceId").asText());
                assertTrue(message.contains("visible"));
                assertFalse(message.contains("secret"));
                assertTrue(message.contains("XXX"));
            }
            assertEquals("request", new ObjectMapper().readTree(events.list.get(0).getFormattedMessage())
                    .path("type").asText());
            var responseLog = new ObjectMapper().readTree(events.list.get(1).getFormattedMessage());
            assertEquals("response", responseLog.path("type").asText());
            assertEquals(200, responseLog.path("status").asInt());
            assertTrue(responseLog.has("duration"));
        });
    }

    @Test
    void logsControllerFailuresWithTheirStatusAndTraceId() {
        withLogging((mvc, events) -> {
            var response = mvc.perform(get("/api/logbook/failure")).andReturn().getResponse();
            assertEquals(500, response.getStatus());
            assertEquals(2, events.list.size());
            var responseEvent = events.list.get(1);
            assertEquals(response.getHeader("traceId"), responseEvent.getMDCPropertyMap().get("traceId"));
            assertEquals(500, new ObjectMapper().readTree(responseEvent.getFormattedMessage()).path("status").asInt());
        });
    }

    @Test
    void excludesMonitoringDocumentationAndStaticFiles() {
        withLogging((mvc, events) -> {
            for (String path : new String[]{"/actuator/health", "/swagger-ui/index.html", "/file/example.txt"}) {
                assertEquals(200, mvc.perform(get(path)).andReturn().getResponse().getStatus());
            }
            assertTrue(events.list.isEmpty());
        });
    }

    @Test
    void exposesTraceHeaderThroughTheApplicationsCorsFilter() {
        withLogging((mvc, events) -> {
            var response = mvc.perform(get("/actuator/health")
                            .header(HttpHeaders.ORIGIN, "https://example.com")
                            .header("traceId", "client-value"))
                    .andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertEquals("traceId", response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
            assertTrue(response.getHeader("traceId").matches("[0-9a-f]{32}"));
        });
    }

    @Test
    void propagatesTraceIdsForEmptyErrorAndCommittedDownloadResponses() {
        withLogging((mvc, events) -> {
            String traceId = "0123456789abcdef0123456789abcdef";
            for (String path : new String[]{"/api/logbook/empty", "/api/logbook/invalid", "/api/logbook/download"}) {
                var response = mvc.perform(get(path).header("traceparent",
                                "00-" + traceId + "-0123456789abcdef-01"))
                        .andReturn().getResponse();
                assertEquals(200, response.getStatus());
                assertEquals(traceId, response.getHeader("traceId"));
                if (path.endsWith("invalid")) {
                    assertEquals("COMMON_1005", new ObjectMapper().readTree(response.getContentAsByteArray())
                            .path("code").asText());
                } else if (path.endsWith("download")) {
                    assertEquals("file", response.getContentAsString());
                }
            }
            var next = mvc.perform(get("/api/logbook/empty").header("traceId", "client-value"))
                    .andReturn().getResponse();
            assertTrue(next.getHeader("traceId").matches("[0-9a-f]{32}"));
            assertNotEquals(traceId, next.getHeader("traceId"));
        });
    }

    @Test
    void applicationCanDisableWebLogsAndTraceHeaders() {
        contextRunner.withPropertyValues("WEB_LOG_ENABLED=false",
                        "rooe.observability.trace-header.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("logbookFilter"));
                    assertFalse(context.containsBean("secureLogbookFilter"));
                    assertFalse(context.containsBean("rooeTraceIdFilter"));
                    assertNotNull(context.getBean(Tracer.class));
                });
    }

    private void withLogging(LoggingAssertion assertion) {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(Tracer.class));
            assertFalse(context.getBean(ObservationRegistry.class).isNoop());
            assertTrue(context.containsBean("webMvcObservationFilter"));
            assertTrue(context.containsBean("propagatingReceiverTracingObservationHandler"));
            assertTrue(context.containsBean("rooeTraceIdFilter"));
            assertInstanceOf(InfoHttpLogWriter.class, context.getBean(HttpLogWriter.class));
            Logger logger = (Logger) LoggerFactory.getLogger("org.zalando.logbook.Logbook");
            var events = new ListAppender<ILoggingEvent>() {
                @Override
                protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    super.append(event);
                }
            };
            events.start();
            logger.addAppender(events);
            try {
                var filters = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                        .sorted(Comparator.comparingInt(FilterRegistrationBean::getOrder))
                        .map(FilterRegistrationBean::getFilter).toArray(jakarta.servlet.Filter[]::new);
                var mvc = MockMvcBuilders.standaloneSetup(new TestController())
                        .addFilters(filters)
                        .addFilters(new ConfigurerAdapter(new FileProperties(), new ObjectMapper()).corsFilter())
                        .setControllerAdvice(new ApiResultResponseAdvice(), new GlobalExceptionAdvice()).build();
                assertion.verify(mvc, events);
            } finally {
                logger.detachAppender(events);
                events.stop();
            }
        });
    }

    @FunctionalInterface
    private interface LoggingAssertion {
        void verify(MockMvc mvc, ListAppender<ILoggingEvent> events) throws Exception;
    }

    @RestController
    static class TestController {

        @PostMapping("/api/logbook")
        ResponseEntity<Map<String, Object>> echo(@RequestBody Map<String, Object> body) {
            body.put("token", "response-secret");
            return ResponseEntity.ok().header("Set-Cookie", "session=response-cookie-secret").body(body);
        }

        @GetMapping("/api/logbook/failure")
        void failure() {
            throw new IllegalStateException("test failure");
        }

        @GetMapping("/api/logbook/empty")
        void empty() {
        }

        @GetMapping("/api/logbook/invalid")
        void invalid() {
            throw new BadRequestException("invalid request");
        }

        @GetMapping("/api/logbook/download")
        void download(HttpServletResponse response) throws IOException {
            response.getWriter().write("file");
            response.flushBuffer();
        }

        @GetMapping({"/actuator/health", "/swagger-ui/index.html", "/file/example.txt"})
        Map<String, String> excluded() {
            return Map.of("status", "ok");
        }
    }
}
