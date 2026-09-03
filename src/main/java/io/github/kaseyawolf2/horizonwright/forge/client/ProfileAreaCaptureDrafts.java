package io.github.kaseyawolf2.horizonwright.forge.client;

import io.github.kaseyawolf2.horizonwright.DevelopmentTrace;

/** Keeps one two-corner draft bound to the exact profile while the GUI is closed for walking. */
final class ProfileAreaCaptureDrafts {

    private String profileId;
    private ProfileAreaCapture capture;

    synchronized ProfileAreaCapture forProfile(String currentProfileId) {
        String required = required(currentProfileId);
        if (!required.equals(profileId) || capture == null) {
            profileId = required;
            capture = new ProfileAreaCapture();
            DevelopmentTrace.event("work-area", "draft-created", "profile", profileId);
        } else {
            DevelopmentTrace.event(
                "work-area",
                "draft-reused",
                "profile",
                profileId,
                "hasFirst",
                capture.hasFirst(),
                "hasSecond",
                capture.hasSecond());
        }
        return capture;
    }

    synchronized void clear(String currentProfileId) {
        if (required(currentProfileId).equals(profileId)) {
            capture = new ProfileAreaCapture();
            DevelopmentTrace.event("work-area", "draft-cleared", "profile", profileId);
        }
    }

    private static String required(String value) {
        if (value == null || value.trim()
            .isEmpty()) throw new IllegalArgumentException("profile id is required");
        return value.trim();
    }
}
