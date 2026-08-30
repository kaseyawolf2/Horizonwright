package io.github.kaseyawolf2.horizonwright.core.task;

/** A time source whose value never moves backwards. */
public interface MonotonicClock {

    long nowMillis();
}
