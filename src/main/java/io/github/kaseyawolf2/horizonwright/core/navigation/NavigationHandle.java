package io.github.kaseyawolf2.horizonwright.core.navigation;

public interface NavigationHandle extends AutoCloseable {

    String getRequestId();

    NavigationProgress progress();

    void cancel();

    @Override
    default void close() {
        cancel();
    }
}
