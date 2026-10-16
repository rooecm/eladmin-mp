package fun.rooe.observability.web;

import fun.rooe.observability.autoconfigure.ObservabilityProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdResponseFilterTest {

    private final Tracer tracer = mock(Tracer.class);
    private final ObservabilityProperties.TraceHeader properties = new ObservabilityProperties.TraceHeader();

    @Test
    void writesBeforeCommitAndPreservesHostCorsHeaders() throws Exception {
        currentTrace("0123456789abcdef0123456789abcdef");
        var response = new MockHttpServletResponse();
        new TraceIdResponseFilter(tracer, properties).doFilter(new MockHttpServletRequest(), response,
                (request, outgoing) -> {
                    var http = (HttpServletResponse) outgoing;
                    http.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition");
                    http.addHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "ETag");
                    http.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://example.com");
                    http.flushBuffer();
                });
        assertThat(response.getHeader("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isEqualTo("Content-Disposition, traceId, ETag");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://example.com");
    }

    @Test
    void ignoresIncomingTraceHeaderWithoutActiveSpanAndDoesNotReusePreviousRequest() throws Exception {
        var filter = new TraceIdResponseFilter(tracer, properties);
        currentTrace("0123456789abcdef0123456789abcdef");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> {
        });
        when(tracer.currentSpan()).thenReturn(null);
        var request = new MockHttpServletRequest();
        request.addHeader("traceId", "untrusted");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> {
        });
        assertThat(response.getHeader("traceId")).isNull();
    }

    @Test
    void restoresHeadersAfterResetAndOnErrorDispatchWithoutActiveSpan() throws Exception {
        currentTrace("0123456789abcdef0123456789abcdef");
        var filter = new TraceIdResponseFilter(tracer, properties);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> outgoing.reset());
        assertThat(response.getHeader("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
        when(tracer.currentSpan()).thenReturn(null);
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute("jakarta.servlet.error.request_uri", "/failure");
        response.reset();
        filter.doFilter(request, response, (incoming, outgoing) -> {
        });
        assertThat(response.getHeader("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void supportsCustomHeaderWithoutCorsExposure() throws Exception {
        currentTrace("0123456789abcdef0123456789abcdef");
        properties.setName("X-Trace-Id");
        properties.setExpose(false);
        var response = new MockHttpServletResponse();
        new TraceIdResponseFilter(tracer, properties).doFilter(new MockHttpServletRequest(), response,
                (request, outgoing) -> {
                });
        assertThat(response.getHeader("traceId")).isNull();
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)).isNull();
    }

    private void currentTrace(String traceId) {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn(traceId);
    }
}
