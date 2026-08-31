package io.github.kaseyawolf2.horizonwright.forge.client.farm;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;

/** Resolves named farm plots only through the exact active identity-bound profile editor. */
public final class ProfileFarmConfiguration {

    private final ProfileAssetEditorProvider profiles;

    public ProfileFarmConfiguration(ProfileAssetEditorProvider profiles) {
        if (profiles == null) throw new IllegalArgumentException("profile editor provider is required");
        this.profiles = profiles;
    }

    public NamedArea resolve(String plotId) {
        if (plotId == null || plotId.trim()
            .isEmpty()) throw new IllegalArgumentException("plot id is required");
        ProfileAssetEditor editor = profiles.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
        ProfileEnvelope profile = editor.load();
        for (NamedArea area : profile.getNamedAreas()) {
            if (area.getId()
                .equals(plotId.trim())) return area;
        }
        throw new IllegalStateException("active profile has no named area '" + plotId.trim() + "'");
    }
}
