package io.github.kaseyawolf2.horizonwright.core.repair;

/** Result of validating one synchronized Tool Station/Forge output. */
public final class RepairVerification {

    private final boolean accepted;
    private final String diagnostic;
    private final int repairedDamage;

    private RepairVerification(boolean accepted, String diagnostic, int repairedDamage) {
        this.accepted = accepted;
        this.diagnostic = diagnostic;
        this.repairedDamage = repairedDamage;
    }

    static RepairVerification accepted(int repairedDamage) {
        return new RepairVerification(true, "stable tool identity retained and damage decreased", repairedDamage);
    }

    static RepairVerification rejected(String diagnostic) {
        return new RepairVerification(false, diagnostic, 0);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public int getRepairedDamage() {
        return repairedDamage;
    }
}
