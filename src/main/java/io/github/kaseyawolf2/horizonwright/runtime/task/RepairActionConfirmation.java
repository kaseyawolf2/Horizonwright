package io.github.kaseyawolf2.horizonwright.runtime.task;

import io.github.kaseyawolf2.horizonwright.core.repair.RepairToolSnapshot;

/** Actual synchronized tool/material evidence after the exact transaction completes. */
public final class RepairActionConfirmation {

    private final String transactionFingerprint;
    private final RepairToolSnapshot outputTool;
    private final int materialConsumed;
    private final boolean recognizedLayout;

    public RepairActionConfirmation(String transactionFingerprint, RepairToolSnapshot outputTool, int materialConsumed,
        boolean recognizedLayout) {
        if (transactionFingerprint == null || transactionFingerprint.trim()
            .isEmpty() || outputTool == null || materialConsumed < 0)
            throw new IllegalArgumentException("complete repair confirmation is required");
        this.transactionFingerprint = transactionFingerprint.trim();
        this.outputTool = outputTool;
        this.materialConsumed = materialConsumed;
        this.recognizedLayout = recognizedLayout;
    }

    public String getTransactionFingerprint() {
        return transactionFingerprint;
    }

    public RepairToolSnapshot getOutputTool() {
        return outputTool;
    }

    public int getMaterialConsumed() {
        return materialConsumed;
    }

    public boolean isRecognizedLayout() {
        return recognizedLayout;
    }
}
