package io.github.kaseyawolf2.horizonwright.forge.client;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;
import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;
import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;

/** Two-corner guided capture which refuses to combine positions from different dimensions. */
final class ProfileAreaCapture {

    private BasePosition first;
    private BasePosition second;

    void recordFirst(BasePosition position) {
        first = required(position);
        DevelopmentTrace.event("work-area", "corner-captured", "corner", 1, "position", first);
    }

    void recordSecond(BasePosition position) {
        second = required(position);
        DevelopmentTrace.event("work-area", "corner-captured", "corner", 2, "position", second);
    }

    boolean isComplete() {
        return first != null && second != null;
    }

    boolean hasFirst() {
        return first != null;
    }

    boolean hasSecond() {
        return second != null;
    }

    NamedArea build(String id) {
        if (!isComplete()) throw new IllegalStateException("capture both area corners first");
        if (first.getDimensionId() != second.getDimensionId()) {
            throw new IllegalStateException("both area corners must be captured in the same dimension");
        }
        String stableId = ProfileAssetInput.stableId(id, "area name");
        NamedArea area = new NamedArea(stableId, displayName(stableId), first, second);
        DevelopmentTrace.event("work-area", "built", "area", area);
        return area;
    }

    String firstSummary() {
        return summary(first);
    }

    String secondSummary() {
        return summary(second);
    }

    private static BasePosition required(BasePosition position) {
        if (position == null) throw new IllegalArgumentException("captured position must not be null");
        return position;
    }

    private static String summary(BasePosition position) {
        return position == null ? "not captured"
            : "dimension " + position
                .getDimensionId() + ", " + position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static String displayName(String id) {
        String[] words = id.split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1));
        }
        return result.toString();
    }
}
