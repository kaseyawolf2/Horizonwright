package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;

/** Explicit live-action authorization boundary; destructive culling is intentionally off. */
public final class HusbandryActionAuthorization {

    private HusbandryActionAuthorization() {}

    public static boolean isAuthorized(HusbandryActionKind kind) {
        if (kind == null) throw new IllegalArgumentException("husbandry action kind is required");
        return kind == HusbandryActionKind.FEED_ADULT || kind == HusbandryActionKind.COLLECT_DROPS;
    }

    public static String diagnostic(HusbandryActionKind kind) {
        if (isAuthorized(kind)) return kind + " is enabled by the non-destructive husbandry policy";
        return "Automatic livestock culling is disabled until the operator explicitly authorizes animal attacks";
    }
}
