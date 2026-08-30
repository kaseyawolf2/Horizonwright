package io.github.kaseyawolf2.horizonwright.forge.client;

/** Pure key-code policy which keeps non-gameplay UI bindings out of automation preemption. */
final class PhysicalInputPreemptionPolicy {

    private PhysicalInputPreemptionPolicy() {}

    static boolean shouldPreempt(int pressedKeyCode, int inventoryKeyCode, int... gameplayKeyCodes) {
        if (pressedKeyCode == inventoryKeyCode || gameplayKeyCodes == null) {
            return false;
        }
        for (int gameplayKeyCode : gameplayKeyCodes) {
            if (pressedKeyCode == gameplayKeyCode) {
                return true;
            }
        }
        return false;
    }
}
