package fun.rooe.observability.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.zalando.logbook.*;
import org.zalando.logbook.json.JsonHttpLogFormatter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceJsonHttpLogFormatter implements HttpLogFormatter {

    private final JsonHttpLogFormatter delegate;
    private final ObjectProvider<Tracer> tracers;

    public TraceJsonHttpLogFormatter(ObjectMapper mapper, ObjectProvider<Tracer> tracers) {
        this.delegate = new JsonHttpLogFormatter(mapper);
        this.tracers = tracers;
    }

    @Override
    public String format(Precorrelation precorrelation, HttpRequest request) throws IOException {
        return format(delegate.prepare(precorrelation, request));
    }

    @Override
    public String format(Correlation correlation, HttpResponse response) throws IOException {
        return format(delegate.prepare(correlation, response));
    }

    private String format(Map<String, Object> content) throws IOException {
        var message = new LinkedHashMap<>(content);
        Tracer tracer = tracers.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span != null) {
            message.put("traceId", span.context().traceId());
        }
        return delegate.format(message);
    }
}
