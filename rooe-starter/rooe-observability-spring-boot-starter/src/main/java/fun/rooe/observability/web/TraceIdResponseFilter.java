package fun.rooe.observability.web;

import fun.rooe.observability.autoconfigure.ObservabilityProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

public class TraceIdResponseFilter extends OncePerRequestFilter {

    private static final String TRACE_ATTRIBUTE = TraceIdResponseFilter.class.getName() + ".traceId";

    private final Tracer tracer;
    private final ObservabilityProperties.TraceHeader properties;

    public TraceIdResponseFilter(Tracer tracer, ObservabilityProperties.TraceHeader properties) {
        this.tracer = tracer;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span span = tracer.currentSpan();
        String traceId = span == null ? (String) request.getAttribute(TRACE_ATTRIBUTE) : span.context().traceId();
        if (traceId == null || traceId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        var wrapped = new TraceResponse(response, properties, traceId);
        wrapped.restoreHeaders();
        chain.doFilter(request, wrapped);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    private static final class TraceResponse extends HttpServletResponseWrapper {

        private final ObservabilityProperties.TraceHeader properties;
        private final String traceId;

        private TraceResponse(HttpServletResponse response, ObservabilityProperties.TraceHeader properties,
                              String traceId) {
            super(response);
            this.properties = properties;
            this.traceId = traceId;
        }

        private void restoreHeaders() {
            if (!isCommitted()) {
                super.setHeader(properties.getName(), traceId);
                if (properties.isExpose()) {
                    String exposed = String.join(", ", getHeaders(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
                    super.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposeTraceHeader(exposed));
                }
            }
        }

        @Override
        public void reset() {
            super.reset();
            restoreHeaders();
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, isExposedHeader(name) ? exposeTraceHeader(value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isExposedHeader(name)) {
                String existing = String.join(", ", getHeaders(name));
                String combined = value == null || value.isBlank() ? existing
                        : existing.isBlank() ? value : existing + ", " + value;
                super.setHeader(name, exposeTraceHeader(combined));
            } else {
                super.addHeader(name, value);
            }
        }

        private boolean isExposedHeader(String name) {
            return properties.isExpose() && HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.equalsIgnoreCase(name);
        }

        private String exposeTraceHeader(String value) {
            if (value == null || value.isBlank()) {
                return properties.getName();
            }
            if (Arrays.stream(value.split(",")).map(String::trim)
                    .anyMatch(properties.getName()::equalsIgnoreCase)) {
                return value;
            }
            return value + ", " + properties.getName();
        }
    }
}
