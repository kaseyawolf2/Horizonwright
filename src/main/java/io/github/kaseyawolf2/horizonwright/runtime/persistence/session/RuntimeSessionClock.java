package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

/** Wall-clock boundary used only to timestamp a final durable checkpoint. */
@FunctionalInterface
public interface RuntimeSessionClock {

    long nowEpochMillis();
}
