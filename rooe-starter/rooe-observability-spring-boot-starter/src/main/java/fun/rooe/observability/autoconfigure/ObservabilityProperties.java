package fun.rooe.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("rooe.observability")
public class ObservabilityProperties {

    private final TraceHeader traceHeader = new TraceHeader();

    public TraceHeader getTraceHeader() {
        return traceHeader;
    }

    public static class TraceHeader {

        /**
         * Whether to return the current trace ID in HTTP responses.
         */
        private boolean enabled = true;

        /**
         * HTTP response header containing the current trace ID.
         */
        private String name = "traceId";

        /**
         * Whether to expose the trace header to browser JavaScript over CORS.
         */
        private boolean expose = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            Assert.isTrue(name != null && name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"),
                    "Trace header name must be a valid HTTP header token");
            this.name = name;
        }

        public boolean isExpose() {
            return expose;
        }

        public void setExpose(boolean expose) {
            this.expose = expose;
        }
    }
}
