package io.github.kaseyawolf2.horizonwright.runtime.persistence.session;

import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;

/** Creates fully isolated Horizonwright runtimes for profile session connections. */
public final class HorizonwrightRuntimeSessionFactory implements RuntimeSessionRuntimeFactory {

    private final RuntimeSessionEnvironmentSource environmentSource;
    private final RuntimeSessionDeathStateBoundaryFactory deathStateFactory;

    public HorizonwrightRuntimeSessionFactory(RuntimeSessionEnvironmentSource environmentSource,
        RuntimeSessionDeathStateBoundaryFactory deathStateFactory) {
        if (environmentSource == null || deathStateFactory == null) {
            throw new IllegalArgumentException("environmentSource and deathStateFactory must not be null");
        }
        this.environmentSource = environmentSource;
        this.deathStateFactory = deathStateFactory;
    }

    @Override
    public RuntimeSessionRuntime create(RuntimeSessionConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        RuntimeSessionDeathStateBoundary deathState = deathStateFactory.create(connection);
        if (deathState == null) {
            throw new IllegalStateException("death state factory returned null");
        }
        return new HorizonwrightRuntimeSessionRuntime(
            HorizonwrightRuntime.createSession(),
            connection,
            environmentSource,
            deathState);
    }
}
