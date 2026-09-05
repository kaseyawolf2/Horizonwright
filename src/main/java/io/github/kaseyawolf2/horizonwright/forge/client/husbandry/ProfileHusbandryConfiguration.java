package io.github.kaseyawolf2.horizonwright.forge.client.husbandry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.kaseyawolf2.horizonwright.core.base.NamedArea;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProfileEnvelope;
import io.github.kaseyawolf2.horizonwright.core.persistence.ProtectedLivestock;
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

    public boolean isProtected(String penId, String entityIdentity) {
        if (penId == null || penId.trim()
            .isEmpty()
            || entityIdentity == null
            || entityIdentity.trim()
                .isEmpty()) {
            throw new IllegalArgumentException("pen id and entity identity are required");
        }
        return protectedIdentities(penId).contains(entityIdentity);
    }

    public Set<String> protectedIdentities(String penId) {
        if (penId == null || penId.trim()
            .isEmpty()) throw new IllegalArgumentException("pen id is required");
        ProfileEnvelope profile = profiles.getCurrentProfileAssetEditor()
            .orElseThrow(() -> new IllegalStateException("active profile assets are unavailable"))
            .load();
        Set<String> identities = new HashSet<>();
        for (ProtectedLivestock protectedAnimal : profile.getProtectedLivestock()) {
            if (penId.trim()
                .equals(protectedAnimal.getPenId())) identities.add(protectedAnimal.getEntityIdentity());
        }
        return Collections.unmodifiableSet(identities);
    }
}
