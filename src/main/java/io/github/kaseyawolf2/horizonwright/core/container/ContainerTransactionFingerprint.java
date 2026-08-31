package io.github.kaseyawolf2.horizonwright.core.container;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable digest used to bind a persisted prepared phase to one exact click chain. */
public final class ContainerTransactionFingerprint {

    private ContainerTransactionFingerprint() {}

    public static String fingerprint(ContainerTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        StringBuilder canonical = new StringBuilder();
        append(canonical, transaction.getTransactionId());
        append(canonical, transaction.getActionEpoch());
        append(
            canonical,
            transaction.getClicks()
                .size());
        for (VerifiedContainerClick click : transaction.getClicks()) {
            append(canonical, click.getClickId());
            append(canonical, click.getSlot());
            append(canonical, click.getMouseButton());
            append(canonical, click.getClickMode());
            append(canonical, click.getExpectedBefore());
            append(canonical, click.getExpectedAfter());
        }
        return sha256(canonical.toString());
    }

    private static void append(StringBuilder target, ContainerSnapshot snapshot) {
        append(target, snapshot.getWindowId());
        append(target, snapshot.getContainerType());
        append(target, snapshot.getSlotLayout());
        append(target, snapshot.getRevision());
        append(
            target,
            snapshot.getSlots()
                .size());
        for (ItemFingerprint item : snapshot.getSlots()) {
            append(target, item);
        }
        append(target, snapshot.getCursor());
    }

    private static void append(StringBuilder target, ItemFingerprint item) {
        if (item == null) {
            target.append("N;");
            return;
        }
        target.append("I;");
        append(target, item.getItemId());
        append(target, item.getMetadata());
        append(target, item.getDataHash());
        append(target, item.getCount());
    }

    private static void append(StringBuilder target, String value) {
        target.append('S')
            .append(value.length())
            .append(':')
            .append(value)
            .append(';');
    }

    private static void append(StringBuilder target, long value) {
        target.append('L')
            .append(value)
            .append(';');
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                encoded.append(String.format("%02x", current & 0xff));
            }
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
