package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.base.HusbandryActionKind;

/** Feeding/collection are available by default; culling requires explicit request authorization. */
public final class HusbandryActionAuthorization {

    private HusbandryActionAuthorization() {}

    public static boolean isAuthorized(HusbandryActionKind kind) {
        return isAuthorized(kind, false);
    }

    public static boolean isAuthorized(HusbandryActionKind kind, boolean allowCulling) {
        if (kind == null) throw new IllegalArgumentException("husbandry action kind is required");
        return kind == HusbandryActionKind.FEED_ADULT || kind == HusbandryActionKind.COLLECT_DROPS
            || kind == HusbandryActionKind.CULL_EXCESS_ADULT && allowCulling;
    }

    public static String diagnostic(HusbandryActionKind kind) {
        if (isAuthorized(kind)) return kind + " is enabled by the non-destructive husbandry policy";
        return "Automatic livestock culling is disabled until the operator explicitly authorizes animal attacks";
    }
}
