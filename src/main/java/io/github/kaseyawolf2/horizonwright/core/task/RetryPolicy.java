package io.github.kaseyawolf2.horizonwright.core.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable retry delays. The default permits retries after 1, 5, and 30 seconds. */
public final class RetryPolicy {

    private static final RetryPolicy DEFAULT = new RetryPolicy(1_000L, 5_000L, 30_000L);

    private final List<Long> backoffMillis;

    public RetryPolicy(long... backoffMillis) {
        if (backoffMillis == null) {
            throw new IllegalArgumentException("backoffMillis must not be null");
        }
        List<Long> copy = new ArrayList<>(backoffMillis.length);
        for (long delay : backoffMillis) {
            if (delay < 0L) {
                throw new IllegalArgumentException("backoff delays must not be negative");
            }
            copy.add(delay);
        }
        this.backoffMillis = Collections.unmodifiableList(copy);
    }

    public static RetryPolicy defaultPolicy() {
        return DEFAULT;
    }

    public int getMaximumRetries() {
        return backoffMillis.size();
    }

    public long getDelayMillis(int retryIndex) {
        if (retryIndex < 0 || retryIndex >= backoffMillis.size()) {
            throw new IllegalArgumentException("retryIndex is outside this policy");
        }
        return backoffMillis.get(retryIndex);
    }

    public List<Long> getBackoffMillis() {
        return backoffMillis;
    }
}
