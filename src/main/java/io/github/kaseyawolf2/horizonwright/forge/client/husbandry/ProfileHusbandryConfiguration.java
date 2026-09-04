package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditor;
import io.github.kaseyawolf2.horizonwright.runtime.persistence.profile.ProfileAssetEditorProvider;

/** Resolves livestock pens only through the exact active identity-bound profile. */
public final class ProfileHusbandryConfiguration {

    private final ProfileAssetEditorProvider profiles;

    public ProfileHusbandryConfiguration(ProfileAssetEditorProvider profiles) {
        if (profiles == null) throw new IllegalArgumentException("profile editor provider is required");
        this.profiles = profiles;
    }

    public NamedArea resolve(String penId) {
        if (penId == null || penId.trim()
            .isEmpty()) throw new IllegalArgumentException("pen id is required");
        ProfileAssetEditor editor = profiles.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"));
        ProfileEnvelope profile = editor.load();
        for (NamedArea area : profile.getNamedAreas()) {
            if (area.getId()
                .equals(penId.trim())) return area;
        }
        throw new IllegalStateException("active profile has no named area '" + penId.trim() + "'");
    }
}
