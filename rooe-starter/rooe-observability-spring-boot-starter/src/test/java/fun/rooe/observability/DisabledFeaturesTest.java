package fun.rooe.observability;

import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ObservabilityStarterTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"rooe.observability.trace-header.enabled=false", "logbook.filter.enabled=false",
                "logbook.secure-filter.enabled=false"})
@AutoConfigureObservability
class DisabledFeaturesTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void disablesHeadersAndBothLoggingFiltersInAnActualApplication() {
        var logger = (Logger) LoggerFactory.getLogger("org.zalando.logbook.Logbook");
        var events = new ObservabilityStarterTest.Events();
        events.start();
        logger.addAppender(events);
        try {
            var response = rest.getForEntity("/public/echo", String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getFirst("traceId")).isNull();
            assertThat(events.values).isEmpty();
            assertThat(context.containsBean("rooeTraceIdFilter")).isFalse();
            assertThat(context.containsBean("logbookFilter")).isFalse();
            assertThat(context.containsBean("secureLogbookFilter")).isFalse();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
