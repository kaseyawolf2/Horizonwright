package io.github.kaseyawolf2.horizonwright;

import io.github.kaseyawolf2.horizonwright.core.action.ActionBrokerSnapshot;
import io.github.kaseyawolf2.horizonwright.core.action.InMemoryActionBroker;

public final class HorizonwrightRuntime {

    private static final HorizonwrightRuntime INSTANCE = new HorizonwrightRuntime();

    private final InMemoryActionBroker actionBroker = new InMemoryActionBroker();
    private final long startedAtNanos = System.nanoTime();
    private volatile String navigationDiagnostic = "No navigation backend configured";

    private HorizonwrightRuntime() {}

    public static HorizonwrightRuntime getInstance() {
        return INSTANCE;
    }

    public InMemoryActionBroker getActionBroker() {
        return actionBroker;
    }

    public RuntimeSnapshot snapshot() {
        return new RuntimeSnapshot(actionBroker.snapshot(), navigationDiagnostic, System.nanoTime() - startedAtNanos);
    }

    public void emergencyStop(String reason) {
        actionBroker.enterSafetyLockdown();
        HorizonwrightMod.LOG.warn("Emergency stop latched: {}", reason);
    }

    public void setNavigationDiagnostic(String diagnostic) {
        if (diagnostic == null || diagnostic.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        navigationDiagnostic = diagnostic;
    }

    public static final class RuntimeSnapshot {

        private final ActionBrokerSnapshot actionBroker;
        private final String navigationDiagnostic;
        private final long uptimeNanos;

        private RuntimeSnapshot(ActionBrokerSnapshot actionBroker, String navigationDiagnostic, long uptimeNanos) {
            this.actionBroker = actionBroker;
            this.navigationDiagnostic = navigationDiagnostic;
            this.uptimeNanos = uptimeNanos;
        }

        public ActionBrokerSnapshot getActionBroker() {
            return actionBroker;
        }

        public String getNavigationDiagnostic() {
            return navigationDiagnostic;
        }

        public long getUptimeNanos() {
            return uptimeNanos;
        }
    }
}
