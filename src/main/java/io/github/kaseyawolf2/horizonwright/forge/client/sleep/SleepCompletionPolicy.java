package io.github.kaseyawolf2.horizonwright.forge.client.sleep;

/** Entering bed alone must never release fallback excavation to move the sleeping player. */
final class SleepCompletionPolicy {

    private SleepCompletionPolicy() {}

    static boolean mayResumeWork(boolean daytime, boolean sleeping) {
        return daytime && !sleeping;
    }
}
