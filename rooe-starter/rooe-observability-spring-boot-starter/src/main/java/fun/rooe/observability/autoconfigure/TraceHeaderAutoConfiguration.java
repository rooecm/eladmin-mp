package fun.rooe.observability.autoconfigure;

import fun.rooe.observability.web.TraceIdResponseFilter;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Servlet;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration(after = {MicrometerTracingAutoConfiguration.class, WebMvcObservationAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Servlet.class, Tracer.class})
@ConditionalOnBean(Tracer.class)
@ConditionalOnProperty(prefix = "rooe.observability.trace-header", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class TraceHeaderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "rooeTraceIdFilter")
    public FilterRegistrationBean<TraceIdResponseFilter> rooeTraceIdFilter(
            Tracer tracer, ObservabilityProperties properties) {
        var registration = new FilterRegistrationBean<>(
                new TraceIdResponseFilter(tracer, properties.getTraceHeader()));
        registration.setName("rooeTraceIdFilter");
        // Boot's HTTP observation filter runs at HIGHEST_PRECEDENCE + 1.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setAsyncSupported(true);
        return registration;
    }
}
