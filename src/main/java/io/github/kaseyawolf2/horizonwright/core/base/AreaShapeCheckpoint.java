package io.github.kaseyawolf2.horizonwright.core.base;

import java.util.Map;

public final class AreaShapeCheckpoint {

    private AreaShapeCheckpoint() {}

    public static void write(Map<String, String> values, String prefix, NamedArea area) {
        if (area.isCircular()) {
            values.put(prefix + "radius", "" + area.getRadius());
            values.put(
                prefix + "centerY",
                "" + area.getCenter()
                    .getY());
        }
    }

    public static NamedArea read(Map<String, String> values, String prefix, NamedArea area) {
        if (!values.containsKey(prefix + "radius")) return area;
        int r = Integer.parseInt(values.get(prefix + "radius"));
        NamedArea circle = NamedArea.circle(
            area.getId(),
            area.getDisplayName(),
            new BasePosition(
                area.getMinimum()
                    .getDimensionId(),
                area.getMinimum()
                    .getX() + r,
                Integer.parseInt(values.get(prefix + "centerY")),
                area.getMinimum()
                    .getZ() + r),
            r,
            area.getMinimum()
                .getY(),
            area.getMaximum()
                .getY())
            .withSettings(area.getKind(), area.getStorageId());
        if (!circle.getMaximum()
            .equals(area.getMaximum())) throw new IllegalArgumentException("Circle bounds mismatch");
        return circle;
    }
}
