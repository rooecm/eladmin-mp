package fun.rooe.admin.framework.security;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthTelemetry {
    private static final Logger AUDIT = LoggerFactory.getLogger("AUTH_AUDIT");
    private final ObjectProvider<MeterRegistry> registries;

    public void count(String name, String... tags) {
        registries.orderedStream().findFirst().ifPresent(r -> r.counter(name, tags).increment());
    }

    // Callers supply only server-defined event/reason codes, never credentials, IPs or user agents.
    public void audit(String event, Long userId, String reason) {
        AUDIT.info("event={} userId={} reason={}", event, userId, reason);
    }
}
