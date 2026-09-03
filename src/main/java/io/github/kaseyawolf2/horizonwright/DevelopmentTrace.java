package io.github.kaseyawolf2.horizonwright;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Structured, high-volume diagnostics for snapshot development builds.
 *
 * <p>
 * Tracing is deliberately enabled by default while Horizonwright is under development. Set the
 * {@value #ENABLED_PROPERTY} system property to {@code false}, or use {@link #setEnabled(boolean)},
 * to silence it without changing normal program behavior.
 */
public final class DevelopmentTrace {

    public static final String ENABLED_PROPERTY = "horizonwright.developmentTrace";
    public static final String PREFIX = "[HWTRACE]";

    private static final Logger LOG = LogManager.getLogger("horizonwright-trace");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static volatile boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));

    private DevelopmentTrace() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        DevelopmentTrace.enabled = enabled;
        LOG.info("{} trace-control enabled={}", PREFIX, enabled);
    }

    /**
     * Writes one parseable event. Fields must be supplied as key/value pairs. Invalid calls are
     * rejected immediately so a malformed diagnostic cannot hide the event being investigated.
     */
    public static void event(String component, String event, Object... fields) {
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("Development trace fields must be key/value pairs");
        }
        if (!enabled) return;

        StringBuilder message = new StringBuilder(192).append(PREFIX)
            .append(" seq=")
            .append(SEQUENCE.incrementAndGet())
            .append(" component=")
            .append(encode(component))
            .append(" event=")
            .append(encode(event));
        for (int index = 0; index < fields.length; index += 2) {
            message.append(' ')
                .append(encode(fields[index]))
                .append('=')
                .append(encode(fields[index + 1]));
        }
        LOG.info(message.toString());
    }

    public static String error(Throwable failure) {
        if (failure == null) return "none";
        return failure.getClass()
            .getSimpleName() + ":"
            + encode(failure.getMessage());
    }

    private static String encode(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value)
            .replace('\\', '/')
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim();
        if (text.isEmpty()) return "empty";
        if (text.length() > 512) text = text.substring(0, 512) + "...";
        return text.indexOf(' ') >= 0 ? '"' + text.replace("\"", "'") + '"' : text;
    }
}
