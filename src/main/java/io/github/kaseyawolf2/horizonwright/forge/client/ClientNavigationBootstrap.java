package io.github.kaseyawolf2.horizonwright.forge.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.github.kaseyawolf2.horizonwright.HorizonwrightMod;
import io.github.kaseyawolf2.horizonwright.HorizonwrightRuntime;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;
import io.github.kaseyawolf2.horizonwright.core.navigation.NavigationBackend;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationProbe;
import io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneInstallationStatus;

/** Keeps optional Baritone API classes behind a probe and a reflective loading boundary. */
public final class ClientNavigationBootstrap {

    private static final String FACTORY_CLASS = "io.github.kaseyawolf2.horizonwright.navigation.baritone.BaritoneNavigationBackendFactory";

    private ClientNavigationBootstrap() {}

    public static void initialize(HorizonwrightRuntime runtime) {
        BaritoneInstallationStatus status = BaritoneInstallationProbe.inspect();
        runtime.setNavigationDiagnostic(status.getDiagnostic());
        if (!status.isAvailable()) {
            return;
        }

        try {
            Class<?> factory = Class.forName(FACTORY_CLASS, true, ClientNavigationBootstrap.class.getClassLoader());
            Method create = factory.getMethod("create", ActionSessionGuard.class);
            Object candidate = create.invoke(null, runtime.getActionSessionGuard());
            if (!(candidate instanceof NavigationBackend)) {
                throw new IllegalStateException("Baritone adapter factory returned an incompatible backend");
            }
            runtime.installNavigationBackend((NavigationBackend) candidate);
            HorizonwrightMod.LOG.info("Horizonwright Baritone navigation adapter initialized");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Throwable cause = failure instanceof InvocationTargetException
                && ((InvocationTargetException) failure).getCause() != null
                    ? ((InvocationTargetException) failure).getCause()
                    : failure;
            String detail = cause.getMessage() == null ? cause.getClass()
                .getName() : cause.getMessage();
            runtime.setNavigationDiagnostic("Baritone adapter initialization failed: " + detail);
            HorizonwrightMod.LOG
                .error("Baritone passed the installation probe but its adapter failed to initialize", cause);
        }
    }
}
