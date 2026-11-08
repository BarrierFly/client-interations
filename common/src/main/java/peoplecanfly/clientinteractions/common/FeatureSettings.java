package peoplecanfly.clientinteractions.common;

/**
 * The numbers one feature uses.
 *
 * <p>Every feature carries the full set so the config file has a single shape per feature, but a
 * given {@link Feature.Shape} only reads the values it needs:</p>
 *
 * <ul>
 *   <li>{@link Feature.Shape#HOLD} - none.</li>
 *   <li>{@link Feature.Shape#FAST_CLICK} - {@code perTick}.</li>
 *   <li>{@link Feature.Shape#PERIODIC_CLICK} - {@code interval}, {@code perTick}.</li>
 *   <li>{@link Feature.Shape#PERIODIC_HOLD} - {@code interval}, {@code duration}.</li>
 *   <li>{@link Feature.Shape#PERIODIC_BURST} - {@code interval}, {@code burst}.</li>
 * </ul>
 */
public final class FeatureSettings {
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 200;
    public static final int MIN_DURATION = 1;
    public static final int MAX_DURATION = 200;
    public static final int MIN_PER_TICK = 1;
    public static final int MAX_PER_TICK = 100;
    public static final int MIN_BURST = 1;
    public static final int MAX_BURST = 200;

    private final int interval;
    private int duration;
    private final int perTick;
    private int burst;

    public FeatureSettings(int interval, int duration, int perTick, int burst) {
        this.interval = clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        this.duration = clamp(duration, MIN_DURATION, MAX_DURATION);
        this.perTick = clamp(perTick, MIN_PER_TICK, MAX_PER_TICK);
        this.burst = clamp(burst, MIN_BURST, MAX_BURST);

        // A hold cannot outlast the interval it lives in, otherwise it would never release. Clamping
        // here (and not only where the value is read) keeps the stored value consistent with what the
        // scheduler does.
        if (this.duration > this.interval) {
            this.duration = this.interval;
        }

        if (this.burst > this.interval) {
            this.burst = this.interval;
        }
    }

    public static FeatureSettings defaults() {
        return new FeatureSettings(20, 10, 1, 3);
    }

    /** Ticks between two activations. */
    public int interval() {
        return this.interval;
    }

    /** How long a periodic hold keeps the button down. */
    public int duration() {
        return this.duration;
    }

    /** How many clicks a fast or periodic click performs per activation. */
    public int perTick() {
        return this.perTick;
    }

    /** How many clicks a burst fires. */
    public int burst() {
        return this.burst;
    }

    public FeatureSettings withInterval(int value) {
        return new FeatureSettings(value, this.duration, this.perTick, this.burst);
    }

    public FeatureSettings withDuration(int value) {
        return new FeatureSettings(this.interval, value, this.perTick, this.burst);
    }

    public FeatureSettings withPerTick(int value) {
        return new FeatureSettings(this.interval, this.duration, value, this.burst);
    }

    public FeatureSettings withBurst(int value) {
        return new FeatureSettings(this.interval, this.duration, this.perTick, value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** A hold duration can never exceed the interval it lives in. */
    public static int clampDuration(int value, int interval) {
        return clamp(value, MIN_DURATION, Math.max(MIN_DURATION, interval));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof FeatureSettings)) {
            return false;
        }

        FeatureSettings settings = (FeatureSettings) other;

        return this.interval == settings.interval
                && this.duration == settings.duration
                && this.perTick == settings.perTick
                && this.burst == settings.burst;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.interval, this.duration, this.perTick, this.burst);
    }

    @Override
    public String toString() {
        return "FeatureSettings[interval=" + this.interval
                + ", duration=" + this.duration
                + ", perTick=" + this.perTick
                + ", burst=" + this.burst + ']';
    }
}
