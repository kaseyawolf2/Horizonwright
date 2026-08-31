package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.kaseyawolf2.horizonwright.core.container.ItemFingerprint;

/** Explicit destination policy; no item-type inference occurs here. */
public final class StorageItemFilter {

    private final StorageFilterMode mode;
    private final List<StorageItemRule> rules;

    public StorageItemFilter(StorageFilterMode mode, List<StorageItemRule> rules) {
        if (mode == null || rules == null || rules.contains(null)) {
            throw new IllegalArgumentException("storage filter mode and non-null rules are required");
        }
        if (mode == StorageFilterMode.ACCEPT_ALL && !rules.isEmpty()) {
            throw new IllegalArgumentException("ACCEPT_ALL must not carry unreachable rules");
        }
        if (mode != StorageFilterMode.ACCEPT_ALL && rules.isEmpty()) {
            throw new IllegalArgumentException("selective storage filters require at least one rule");
        }
        this.mode = mode;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public static StorageItemFilter acceptAll() {
        return new StorageItemFilter(StorageFilterMode.ACCEPT_ALL, Collections.<StorageItemRule>emptyList());
    }

    public StorageFilterMode getMode() {
        return mode;
    }

    public List<StorageItemRule> getRules() {
        return rules;
    }

    public boolean accepts(ItemFingerprint item) {
        if (item == null) {
            return false;
        }
        if (mode == StorageFilterMode.ACCEPT_ALL) {
            return true;
        }
        boolean matched = false;
        for (StorageItemRule rule : rules) {
            if (rule.matches(item)) {
                matched = true;
                break;
            }
        }
        return mode == StorageFilterMode.ALLOW_MATCHES ? matched : !matched;
    }
}
