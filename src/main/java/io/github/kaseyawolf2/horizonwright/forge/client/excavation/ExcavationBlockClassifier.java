package io.github.kaseyawolf2.horizonwright.forge.client.excavation;

import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationBlockClassification;
import io.github.kaseyawolf2.horizonwright.core.excavation.ExcavationObservation;

/** Conservative reduction of captured block facts into clean-volume planning semantics. */
final class ExcavationBlockClassifier {

    private ExcavationBlockClassifier() {}

    static ExcavationObservation classify(ExcavationBlockEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("evidence must not be null");
        ExcavationBlockClassification classification;
        if (!evidence.isLoaded()) classification = ExcavationBlockClassification.UNREACHABLE;
        else if (evidence.isAir()) classification = ExcavationBlockClassification.AIR;
        else if (evidence.isProtectedGrave()) classification = ExcavationBlockClassification.PROTECTED_GRAVE;
        else if (evidence.isInfrastructure()) {
            classification = ExcavationBlockClassification.PROTECTED_INFRASTRUCTURE;
        } else if (evidence.isFluid()) {
            // Clean-volume mode has no bucket or containment integration yet. Never attack a fluid packet-blindly.
            classification = evidence.isFluidSource() ? ExcavationBlockClassification.FLUID_SOURCE_UNREACHABLE
                : ExcavationBlockClassification.FLUID_FLOWING;
        } else if (evidence.isBreakable()) classification = ExcavationBlockClassification.BREAKABLE;
        else classification = ExcavationBlockClassification.UNREACHABLE;
        return new ExcavationObservation(evidence.getPosition(), classification, evidence.getFingerprint());
    }
}
