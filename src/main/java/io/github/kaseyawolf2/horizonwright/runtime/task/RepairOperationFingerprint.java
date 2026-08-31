package io.github.kaseyawolf2.horizonwright.runtime.task;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.kaseyawolf2.horizonwright.core.container.ContainerTransactionFingerprint;
import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

/** Stable digest of both the exact click chain and all semantic repair evidence. */
final class RepairOperationFingerprint {

    private RepairOperationFingerprint() {}

    static String fingerprint(RepairObservationResult observation) {
        StringBuilder value = new StringBuilder();
        append(value, ContainerTransactionFingerprint.fingerprint(observation.getTransaction()));
        append(value, observation.getWindowId());
        append(value, observation.getStationSlotCount());
        append(value, observation.getReservedContainerSlot());
        append(value, observation.isRecognizedLayout() ? 1 : 0);
        append(value, observation.getPredictedMaterialConsumed());
        append(value, observation.getInputTool());
        append(value, observation.getPredictedOutput());
        append(
            value,
            observation.getApprovedMaterialContainerSlots()
                .size());
        for (Integer slot : observation.getApprovedMaterialContainerSlots()) append(value, slot);
        return sha256(value.toString());
    }

    private static void append(StringBuilder value, RepairToolSnapshot tool) {
        append(value, tool.getStableToolIdentity());
        append(value, tool.getDamage());
        append(value, tool.getMaximumDamage());
        append(value, tool.getReservedInventorySlot());
    }

    private static void append(StringBuilder value, String text) {
        value.append('S')
            .append(text.length())
            .append(':')
            .append(text)
            .append(';');
    }

    private static void append(StringBuilder value, long number) {
        value.append('L')
            .append(number)
            .append(';');
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) encoded.append(String.format("%02x", current & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
