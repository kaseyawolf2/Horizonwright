package io.github.kaseyawolf2.horizonwright.forge.client.network;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.action.ActionAuthorizationDecision;
import io.github.kaseyawolf2.horizonwright.core.action.ActionCapability;
import io.github.kaseyawolf2.horizonwright.core.action.ActionSessionGuard;

public final class PacketActionRequirement {

    public enum Kind {
        MAINTENANCE,
        SAFE_RELEASE,
        CAPABILITY,
        UNKNOWN_ACTION
    }

    private static final PacketActionRequirement UNRESTRICTED = new PacketActionRequirement(
        Collections.<ActionCapability>emptySet(),
        false,
        "audited protocol maintenance",
        Kind.MAINTENANCE);

    private final Set<ActionCapability> capabilities;
    private final boolean requireAll;
    private final String description;
    private final Kind kind;

    private PacketActionRequirement(Set<ActionCapability> capabilities, boolean requireAll, String description,
        Kind kind) {
        this.capabilities = capabilities.isEmpty() ? Collections.<ActionCapability>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
        this.requireAll = requireAll;
        this.description = description;
        this.kind = kind;
    }

    public static PacketActionRequirement unrestricted() {
        return UNRESTRICTED;
    }

    public static PacketActionRequirement safeRelease(String description) {
        return new PacketActionRequirement(
            Collections.<ActionCapability>emptySet(),
            false,
            requireDescription(description),
            Kind.SAFE_RELEASE);
    }

    public static PacketActionRequirement unknownAction(String description) {
        return new PacketActionRequirement(
            Collections.<ActionCapability>emptySet(),
            false,
            requireDescription(description),
            Kind.UNKNOWN_ACTION);
    }

    public static PacketActionRequirement allOf(String description, ActionCapability first,
        ActionCapability... remaining) {
        return new PacketActionRequirement(
            capabilities(first, remaining),
            true,
            requireDescription(description),
            Kind.CAPABILITY);
    }

    public static PacketActionRequirement anyOf(String description, ActionCapability first,
        ActionCapability... remaining) {
        return new PacketActionRequirement(
            capabilities(first, remaining),
            false,
            requireDescription(description),
            Kind.CAPABILITY);
    }

    public boolean isRestricted() {
        return kind == Kind.CAPABILITY || kind == Kind.UNKNOWN_ACTION;
    }

    public Set<ActionCapability> getCapabilities() {
        return capabilities;
    }

    public String getDescription() {
        return description;
    }

    public Kind getKind() {
        return kind;
    }

    public ActionAuthorizationDecision evaluate(ActionSessionGuard guard) {
        if (kind == Kind.MAINTENANCE || kind == Kind.SAFE_RELEASE) {
            return ActionAuthorizationDecision.PLAYER_PASSTHROUGH;
        }
        if (kind == Kind.UNKNOWN_ACTION) {
            return guard.authorizeUnknownAction();
        }
        return requireAll ? guard.authorizeAll(capabilities) : guard.authorizeAny(capabilities);
    }

    private static Set<ActionCapability> capabilities(ActionCapability first, ActionCapability[] remaining) {
        if (first == null || remaining == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }
        EnumSet<ActionCapability> result = EnumSet.of(first);
        for (ActionCapability capability : remaining) {
            if (capability == null) {
                throw new IllegalArgumentException("capabilities must not contain null");
            }
            result.add(capability);
        }
        return result;
    }

    private static String requireDescription(String description) {
        if (description == null || description.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return description.trim();
    }
}
