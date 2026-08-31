package io.github.kaseyawolf2.horizonwright.core.safety.death;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable observable inventory used for conservative capacity and content checks.
 *
 * <p>
 * Item keys are stable fingerprints of registry identity, metadata, and all relevant NBT. The manifest never
 * needs to persist raw NBT: {@link #getContentFingerprint()} is a canonical SHA-256 over item keys and counts.
 */
public final class InventoryManifest {

    private final int slotCount;
    private final List<InventoryStack> stacks;
    private final Map<String, Long> counts;
    private final Map<String, Integer> maximumStackSizes;
    private final String contentFingerprint;

    public InventoryManifest(int slotCount, List<InventoryStack> stacks) {
        if (slotCount < 0) {
            throw new IllegalArgumentException("slotCount must not be negative");
        }
        if (stacks == null || stacks.contains(null)) {
            throw new IllegalArgumentException("stacks must not be null or contain null");
        }
        if (stacks.size() > slotCount) {
            throw new IllegalArgumentException("occupied stack count exceeds slotCount");
        }
        this.slotCount = slotCount;
        this.stacks = Collections.unmodifiableList(new ArrayList<>(stacks));

        Map<String, Long> mutableCounts = new TreeMap<>();
        Map<String, Integer> mutableMaximums = new LinkedHashMap<>();
        for (InventoryStack stack : stacks) {
            Integer previousMaximum = mutableMaximums.put(stack.getItemFingerprint(), stack.getMaximumStackSize());
            if (previousMaximum != null && previousMaximum.intValue() != stack.getMaximumStackSize()) {
                throw new IllegalArgumentException("one item fingerprint cannot have conflicting maximum stack sizes");
            }
            long previous = mutableCounts.containsKey(stack.getItemFingerprint())
                ? mutableCounts.get(stack.getItemFingerprint())
                : 0L;
            long combined = previous + stack.getCount();
            if (combined < previous) {
                throw new IllegalArgumentException("item count overflow");
            }
            mutableCounts.put(stack.getItemFingerprint(), combined);
        }
        counts = Collections.unmodifiableMap(mutableCounts);
        maximumStackSizes = Collections.unmodifiableMap(mutableMaximums);
        contentFingerprint = fingerprint(mutableCounts);
    }

    public static InventoryManifest empty(int slotCount) {
        return new InventoryManifest(slotCount, Collections.<InventoryStack>emptyList());
    }

    public int getSlotCount() {
        return slotCount;
    }

    public List<InventoryStack> getStacks() {
        return stacks;
    }

    public int getEmptySlotCount() {
        return slotCount - stacks.size();
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public boolean hasSameContents(InventoryManifest other) {
        return other != null && counts.equals(other.counts);
    }

    public boolean isStrictContentSubsetOf(InventoryManifest other) {
        if (other == null || counts.equals(other.counts)) {
            return false;
        }
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            Long otherCount = other.counts.get(entry.getKey());
            if (otherCount == null || entry.getValue() > otherCount) {
                return false;
            }
        }
        return true;
    }

    /** Conservative vanilla-style merge and empty-slot capacity calculation. */
    public boolean canAcceptAll(InventoryManifest incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("incoming must not be null");
        }
        long requiredNewSlots = 0L;
        for (Map.Entry<String, Long> entry : incoming.counts.entrySet()) {
            String key = entry.getKey();
            long remaining = entry.getValue();
            Integer incomingMaximum = incoming.maximumStackSizes.get(key);
            Integer localMaximum = maximumStackSizes.get(key);
            if (localMaximum != null && !localMaximum.equals(incomingMaximum)) {
                return false;
            }
            if (localMaximum != null) {
                long mergeCapacity = 0L;
                for (InventoryStack stack : stacks) {
                    if (key.equals(stack.getItemFingerprint())) {
                        mergeCapacity += stack.getMaximumStackSize() - stack.getCount();
                    }
                }
                remaining = Math.max(0L, remaining - mergeCapacity);
            }
            if (remaining > 0L) {
                requiredNewSlots += divideRoundingUp(remaining, incomingMaximum.intValue());
            }
            if (requiredNewSlots > getEmptySlotCount()) {
                return false;
            }
        }
        return true;
    }

    public String fingerprintWith(InventoryManifest other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        Map<String, Long> combined = new TreeMap<>(counts);
        for (Map.Entry<String, Long> entry : other.counts.entrySet()) {
            long previous = combined.containsKey(entry.getKey()) ? combined.get(entry.getKey()) : 0L;
            long total = previous + entry.getValue();
            if (total < previous) {
                throw new IllegalArgumentException("combined item count overflow");
            }
            combined.put(entry.getKey(), total);
        }
        return fingerprint(combined);
    }

    /**
     * Conservatively derives contents no longer present after respawn.
     *
     * <p>
     * An empty result means the retained inventory contains an item, count, or stack-size fact that cannot be
     * explained by this pre-death manifest; callers must not guess at grave contents in that case.
     */
    public Optional<InventoryManifest> subtractContents(InventoryManifest retained) {
        if (retained == null) {
            throw new IllegalArgumentException("retained inventory must not be null");
        }
        List<InventoryStack> residual = new ArrayList<>();
        for (Map.Entry<String, Long> retainedEntry : retained.counts.entrySet()) {
            Long originalCount = counts.get(retainedEntry.getKey());
            Integer originalMaximum = maximumStackSizes.get(retainedEntry.getKey());
            Integer retainedMaximum = retained.maximumStackSizes.get(retainedEntry.getKey());
            if (originalCount == null || originalMaximum == null
                || !originalMaximum.equals(retainedMaximum)
                || retainedEntry.getValue() > originalCount) {
                return Optional.empty();
            }
        }
        for (Map.Entry<String, Long> originalEntry : counts.entrySet()) {
            long retainedCount = retained.counts.containsKey(originalEntry.getKey())
                ? retained.counts.get(originalEntry.getKey())
                : 0L;
            long remaining = originalEntry.getValue() - retainedCount;
            int maximum = maximumStackSizes.get(originalEntry.getKey());
            while (remaining > 0L) {
                int count = (int) Math.min(remaining, maximum);
                residual.add(new InventoryStack(originalEntry.getKey(), count, maximum));
                remaining -= count;
            }
        }
        return Optional.of(new InventoryManifest(slotCount, residual));
    }

    private static long divideRoundingUp(long numerator, long denominator) {
        return numerator / denominator + (numerator % denominator == 0L ? 0L : 1L);
    }

    private static String fingerprint(Map<String, Long> itemCounts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, Long> entry : new TreeMap<>(itemCounts).entrySet()) {
                byte[] key = entry.getKey()
                    .getBytes(StandardCharsets.UTF_8);
                digest.update(
                    ByteBuffer.allocate(4)
                        .putInt(key.length)
                        .array());
                digest.update(key);
                digest.update(
                    ByteBuffer.allocate(8)
                        .putLong(entry.getValue())
                        .array());
            }
            byte[] bytes = digest.digest();
            StringBuilder value = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                value.append(String.format("%02x", current & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InventoryManifest)) {
            return false;
        }
        InventoryManifest that = (InventoryManifest) other;
        return slotCount == that.slotCount && stacks.equals(that.stacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotCount, stacks);
    }
}
