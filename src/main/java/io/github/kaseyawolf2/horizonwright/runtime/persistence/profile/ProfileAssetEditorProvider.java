package io.github.kaseyawolf2.horizonwright.runtime.persistence.profile;

import java.util.Optional;

/** Late-bound editor lookup so a GUI cannot retain authority across profile/world replacement. */
public interface ProfileAssetEditorProvider {

    Optional<ProfileAssetEditor> getCurrentProfileAssetEditor();
}
