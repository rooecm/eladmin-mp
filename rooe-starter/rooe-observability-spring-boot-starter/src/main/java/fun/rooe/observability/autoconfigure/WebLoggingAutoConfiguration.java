package fun.rooe.observability.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.rooe.observability.logging.InfoHttpLogWriter;
import fun.rooe.observability.logging.TraceJsonHttpLogFormatter;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Servlet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.autoconfigure.LogbookAutoConfiguration;
import org.zalando.logbook.servlet.SecureLogbookFilter;

@AutoConfiguration(before = LogbookAutoConfiguration.class,
        after = {JacksonAutoConfiguration.class, MicrometerTracingAutoConfiguration.class})
@ConditionalOnClass({Logbook.class, Servlet.class, Tracer.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HttpLogWriter.class)
    public HttpLogWriter rooeHttpLogWriter() {
        return new InfoHttpLogWriter();
    }

    @Bean
    @ConditionalOnMissingBean(HttpLogFormatter.class)
    @ConditionalOnProperty(prefix = "logbook.format", name = "style", havingValue = "json", matchIfMissing = true)
    public HttpLogFormatter rooeHttpLogFormatter(ObjectProvider<ObjectMapper> mappers,
                                                 ObjectProvider<Tracer> tracers) {
        return new TraceJsonHttpLogFormatter(mappers.getIfAvailable(ObjectMapper::new), tracers);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
    static class SecurityLoggingConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "secureLogbookFilter")
        @ConditionalOnProperty(prefix = "logbook.secure-filter", name = "enabled", matchIfMissing = true)
        FilterRegistrationBean<SecureLogbookFilter> secureLogbookFilter(Logbook logbook) {
            var registration = new FilterRegistrationBean<>(new SecureLogbookFilter(logbook));
            registration.setName("secureLogbookFilter");
            // Logbook's default order ties with observation; run inside the trace scope instead.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
            registration.setAsyncSupported(true);
            return registration;
        }
    }
}
