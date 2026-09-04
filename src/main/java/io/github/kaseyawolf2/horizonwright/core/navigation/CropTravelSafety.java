package io.github.kaseyawolf2.horizonwright.core.navigation;

/** Optional navigation capability that protects fragile crops for the lifetime of a farm operation. */
public interface CropTravelSafety {

    void beginCropOperation(String operationId);

    void endCropOperation(String operationId);
}
