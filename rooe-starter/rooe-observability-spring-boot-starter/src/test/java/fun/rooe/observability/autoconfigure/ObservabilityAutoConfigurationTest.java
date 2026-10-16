package fun.rooe.observability.autoconfigure;

import fun.rooe.observability.logging.InfoHttpLogWriter;
import fun.rooe.observability.logging.TraceJsonHttpLogFormatter;
import fun.rooe.starter.support.env.YamlDefaultsEnvironmentPostProcessor;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObservabilityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(context -> new YamlDefaultsEnvironmentPostProcessor()
                    .postProcessEnvironment(context.getEnvironment(), null))
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    TraceHeaderAutoConfiguration.class, WebLoggingAutoConfiguration.class,
                    LogbookAutoConfiguration.class));

    @Test
    void configuresHeaderAndJsonInfoLogging() {
        runner.withBean(Tracer.class, () -> mock(Tracer.class)).run(context -> {
            assertThat(context).hasNotFailed().hasBean("rooeTraceIdFilter");
            assertThat(context.getBean(HttpLogWriter.class)).isInstanceOf(InfoHttpLogWriter.class);
            assertThat(context.getBean(HttpLogFormatter.class)).isInstanceOf(TraceJsonHttpLogFormatter.class);
        });
    }

    @Test
    void backsOffWithoutTracerAndWhenHeaderIsDisabled() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("rooeTraceIdFilter"));
        runner.withBean(Tracer.class, () -> mock(Tracer.class))
                .withPropertyValues("rooe.observability.trace-header.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("rooeTraceIdFilter"));
    }

    @Test
    void honorsNativeLoggingSwitchesAndFormat() {
        runner.withPropertyValues("logbook.filter.enabled=false", "logbook.secure-filter.enabled=false",
                        "logbook.format.style=http")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean("logbookFilter")
                            .doesNotHaveBean("secureLogbookFilter");
                    assertThat(context.getBean(HttpLogFormatter.class))
                            .isNotInstanceOf(TraceJsonHttpLogFormatter.class);
                });
    }

    @Test
    void respectsUserSuppliedWriterFormatterAndRegistration() {
        runner.withUserConfiguration(CustomLogging.class)
                .withBean("rooeTraceIdFilter", Object.class, Object::new)
                .withBean(Tracer.class, () -> mock(Tracer.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(HttpLogWriter.class)
                            .hasSingleBean(HttpLogFormatter.class);
                    assertThat(context.getBean(HttpLogWriter.class)).isSameAs(context.getBean("customWriter"));
                    assertThat(context.getBean(HttpLogFormatter.class))
                            .isSameAs(context.getBean("customFormatter"));
                    assertThat(context.getBean("rooeTraceIdFilter")).isExactlyInstanceOf(Object.class);
                });
    }

    @Test
    void doesNotConfigureServletFeaturesInNonWebApplicationOrWithoutServletApi() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TraceHeaderAutoConfiguration.class,
                        WebLoggingAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("rooeTraceIdFilter")
                        .doesNotHaveBean(HttpLogWriter.class));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TraceHeaderAutoConfiguration.class,
                        WebLoggingAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("jakarta.servlet"))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("rooeTraceIdFilter")
                        .doesNotHaveBean("rooeHttpLogWriter"));
    }

    @Test
    void defaultPropertiesHaveLowestPriorityAndAreInstalledOnce() {
        var environment = new MockEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource("hostDefaults",
                Map.of("logbook.write.max-body-size", "128")));
        var processor = new YamlDefaultsEnvironmentPostProcessor();
        processor.postProcessEnvironment(environment, null);
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty("logbook.write.max-body-size")).isEqualTo("128");
        assertThat(environment.getProperty("logbook.format.style")).isEqualTo("json");
        assertThat(environment.getProperty("rooe.observability.trace-header.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("rooe.observability.trace-header.name")).isEqualTo("traceId");
        assertThat(environment.getProperty("management.tracing.enabled")).isEqualTo("true");
        assertThat(environment.getPropertySources().stream()
                .filter(source -> source.getName().startsWith("rooeDefaults [")).count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidHeaderNames() {
        runner.withBean(Tracer.class, () -> mock(Tracer.class))
                .withPropertyValues("rooe.observability.trace-header.name=invalid header")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomLogging {

        @Bean
        HttpLogWriter customWriter() {
            return mock(HttpLogWriter.class);
        }

        @Bean
        HttpLogFormatter customFormatter() {
            return mock(HttpLogFormatter.class);
        }
    }
}
