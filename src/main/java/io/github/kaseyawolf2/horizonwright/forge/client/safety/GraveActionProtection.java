package io.github.kaseyawolf2.horizonwright.forge.client.safety;

/** Final generic-action guard independent of recovery's one-shot grave packet gate. */
public interface GraveActionProtection {

    boolean allowsGenericAction(String blockRegistryName, GraveActionKind actionKind);
}
