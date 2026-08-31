package io.github.kaseyawolf2.horizonwright.core.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conservative player-inventory classification before any container transaction is constructed. */
public final class UnloadPlan {

    private final UnloadPlanStatus status;
    private final List<Integer> reservedSlots;
    private final List<Integer> unloadableSlots;
    private final List<Integer> deferredSlots;
    private final Map<String, Integer> missingCounts;

    UnloadPlan(UnloadPlanStatus status, List<Integer> reservedSlots, List<Integer> unloadableSlots,
        List<Integer> deferredSlots, Map<String, Integer> missingCounts) {
        this.status = status;
        this.reservedSlots = Collections.unmodifiableList(new ArrayList<>(reservedSlots));
        this.unloadableSlots = Collections.unmodifiableList(new ArrayList<>(unloadableSlots));
        this.deferredSlots = Collections.unmodifiableList(new ArrayList<>(deferredSlots));
        this.missingCounts = Collections.unmodifiableMap(new LinkedHashMap<>(missingCounts));
    }

    public UnloadPlanStatus getStatus() {
        return status;
    }

    public List<Integer> getReservedSlots() {
        return reservedSlots;
    }

    public List<Integer> getUnloadableSlots() {
        return unloadableSlots;
    }

    public List<Integer> getDeferredSlots() {
        return deferredSlots;
    }

    public Map<String, Integer> getMissingCounts() {
        return missingCounts;
    }

    public boolean mayStartTransaction() {
        return status == UnloadPlanStatus.READY;
    }
}
