package peoplecanfly.clientinteractions.common;

/**
 * The user facing settings of one {@link ActionChannel}.
 */
public final class ChannelSettings {
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 200;
    public static final int MIN_SPAN = 1;
    public static final int MAX_SPAN = 200;
    public static final int MIN_PER_TICK = 1;
    public static final int MAX_PER_TICK = 100;
    public static final int MIN_BURST = 1;
    public static final int MAX_BURST = 100;

    private final int interval;
    private final int holdDuration;
    private final int perTick;
    private final int burstSize;

    public ChannelSettings(int interval, int holdDuration, int perTick, int burstSize) {
        this.interval = clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        this.holdDuration = clamp(holdDuration, MIN_SPAN, MAX_SPAN);
        this.perTick = clamp(perTick, MIN_PER_TICK, MAX_PER_TICK);
        this.burstSize = clamp(burstSize, MIN_BURST, MAX_BURST);
    }

    /** Default settings: one click per second, held for half a second, one click per tick, 3 click bursts. */
    public static ChannelSettings defaults() {
        return new ChannelSettings(20, 10, 1, 3);
    }

    public int interval() {
        return this.interval;
    }

    public int holdDuration() {
        return this.holdDuration;
    }

    /** How many clicks {@link AutomationMode#FAST_CLICK} injects on every tick. */
    public int perTick() {
        return this.perTick;
    }

    public int burstSize() {
        return this.burstSize;
    }

    public ChannelSettings withInterval(int value) {
        return new ChannelSettings(value, this.holdDuration, this.perTick, this.burstSize);
    }

    public ChannelSettings withHoldDuration(int value) {
        return new ChannelSettings(this.interval, value, this.perTick, this.burstSize);
    }

    public ChannelSettings withPerTick(int value) {
        return new ChannelSettings(this.interval, this.holdDuration, value, this.burstSize);
    }

    public ChannelSettings withBurstSize(int value) {
        return new ChannelSettings(this.interval, this.holdDuration, this.perTick, value);
    }

    public static int clampInterval(int value) {
        return clamp(value, MIN_INTERVAL, MAX_INTERVAL);
    }

    public static int clampPerTick(int value) {
        return clamp(value, MIN_PER_TICK, MAX_PER_TICK);
    }

    public static int clampBurst(int value) {
        return clamp(value, MIN_BURST, MAX_BURST);
    }

    /** A hold duration can never exceed the interval it lives in. */
    public static int clampHoldDuration(int value, int interval) {
        return clamp(value, MIN_SPAN, Math.max(MIN_SPAN, interval));
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clampValue(int value, int min, int max) {
        return clamp(value, min, max);
    }

    @Override
    public String toString() {
        return "ChannelSettings[interval=" + this.interval
                + ", holdDuration=" + this.holdDuration
                + ", perTick=" + this.perTick
                + ", burstSize=" + this.burstSize + ']';
    }
}
