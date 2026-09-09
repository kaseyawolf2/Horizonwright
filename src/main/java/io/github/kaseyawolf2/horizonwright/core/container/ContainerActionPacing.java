package io.github.kaseyawolf2.horizonwright.core.container;

/** Shared client-tick spacing for container clicks and the actions surrounding them. */
public final class ContainerActionPacing {

    private final int intervalTicks;
    private long tick;
    private long nextActionTick;

    public ContainerActionPacing(int intervalTicks) {
        if (intervalTicks < 0) throw new IllegalArgumentException("tick interval must not be negative");
        this.intervalTicks = intervalTicks;
    }

    public void tick() {
        tick++;
    }

    public boolean isReady() {
        return tick >= nextActionTick;
    }

    public void actionPerformed() {
        nextActionTick = tick + intervalTicks;
    }
}
