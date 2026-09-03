package io.github.kaseyawolf2.horizonwright.forge.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutomationInputHoldTest {

    @Test
    public void holdSurvivesExternalClearAndRestoresOriginalReleasedState() {
        FakeBinding binding = new FakeBinding(false);
        AutomationInputHold hold = new AutomationInputHold("excavation", binding);

        hold.hold();
        assertTrue(binding.isPressed());

        binding.setPressed(false);
        hold.hold();
        assertTrue(binding.isPressed());

        hold.release();
        assertFalse(binding.isPressed());
        assertFalse(hold.isHeld());
    }

    @Test
    public void releaseRestoresAnInputThatWasAlreadyPressed() {
        FakeBinding binding = new FakeBinding(true);
        AutomationInputHold hold = new AutomationInputHold("farm", binding);

        hold.hold();
        hold.release();

        assertTrue(binding.isPressed());
    }

    @Test
    public void duplicateReleaseDoesNotOverwriteSubsequentPhysicalState() {
        FakeBinding binding = new FakeBinding(false);
        AutomationInputHold hold = new AutomationInputHold("excavation", binding);
        hold.hold();
        hold.release();
        binding.setPressed(true);

        hold.release();

        assertTrue(binding.isPressed());
    }

    private static final class FakeBinding implements AutomationInputHold.Binding {

        private boolean pressed;

        private FakeBinding(boolean pressed) {
            this.pressed = pressed;
        }

        @Override
        public boolean isPressed() {
            return pressed;
        }

        @Override
        public void setPressed(boolean pressed) {
            this.pressed = pressed;
        }
    }
}
