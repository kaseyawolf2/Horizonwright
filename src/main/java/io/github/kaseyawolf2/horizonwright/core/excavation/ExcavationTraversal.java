package io.github.kaseyawolf2.horizonwright.core.excavation;

/** Persisted traversal identities. Existing checkpoints keep their original order. */
public enum ExcavationTraversal {

    SQUARE_SPIRAL("spiral-v1", "Square spiral"),
    CIRCLE_SPIRAL("circle-spiral-v1", "Circle spiral"),
    CHUNKS("chunk-v1", "Chunk by chunk"),
    ROWS_X("rows-x-v1", "Rows along X"),
    ROWS_Z("rows-z-v1", "Rows along Z");

    private final String id;
    private final String label;

    ExcavationTraversal(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static ExcavationTraversal parse(String id) {
        if (id == null) return CHUNKS;
        for (ExcavationTraversal value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unsupported excavation traversal: " + id);
    }
}
