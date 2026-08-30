package io.github.kaseyawolf2.horizonwright.core.safety.death;

/** Redundant signals which can latch a death. */
public enum DeathSignal {
    LETHAL_HEALTH_PACKET,
    PLAYER_IS_DEAD,
    POSITIVE_DEATH_TIME,
    LOCAL_DEATH_CALLBACK,
    GAME_OVER_SCREEN
}
