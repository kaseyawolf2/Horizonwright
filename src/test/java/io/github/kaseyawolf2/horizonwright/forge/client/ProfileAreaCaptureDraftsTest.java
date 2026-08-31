package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.kaseyawolf2.horizonwright.core.base.BasePosition;

public class ProfileAreaCaptureDraftsTest {

    @Test
    public void retainsCornersAcrossScreensOnlyForTheSameProfile() {
        ProfileAreaCaptureDrafts drafts = new ProfileAreaCaptureDrafts();
        ProfileAreaCapture firstScreen = drafts.forProfile("profile-a");
        firstScreen.recordFirst(new BasePosition(0, 1, 64, 1));

        ProfileAreaCapture reopened = drafts.forProfile("profile-a");

        assertSame(firstScreen, reopened);
        assertTrue(reopened.hasFirst());
        assertFalse(reopened.hasSecond());
        assertFalse(
            drafts.forProfile("profile-b")
                .hasFirst());
    }

    @Test
    public void clearStartsAFreshDraftAfterSuccessfulSave() {
        ProfileAreaCaptureDrafts drafts = new ProfileAreaCaptureDrafts();
        ProfileAreaCapture capture = drafts.forProfile("profile-a");
        capture.recordFirst(new BasePosition(0, 1, 64, 1));
        capture.recordSecond(new BasePosition(0, 2, 64, 2));

        drafts.clear("profile-a");

        assertFalse(
            drafts.forProfile("profile-a")
                .hasFirst());
        assertFalse(
            drafts.forProfile("profile-a")
                .hasSecond());
    }
}
