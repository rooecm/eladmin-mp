package fun.rooe.observability.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.Correlation;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.Precorrelation;

public class InfoHttpLogWriter implements HttpLogWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger("org.zalando.logbook.Logbook");

    @Override
    public boolean isActive() {
        return LOGGER.isInfoEnabled();
    }

    @Override
    public void write(Precorrelation precorrelation, String request) {
        LOGGER.info("{}", request);
    }

    @Override
    public void write(Correlation correlation, String response) {
        LOGGER.info("{}", response);
    }
}
