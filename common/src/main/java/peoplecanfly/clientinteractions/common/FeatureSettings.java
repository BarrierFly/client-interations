package peoplecanfly.clientinteractions.common;

/**
 * The numbers one feature uses.
 *
 * <p>Every feature carries the full set so the config file has a single shape per feature, but a given
 * {@link Feature.Shape} only reads the values it needs:</p>
 *
 * <ul>
 *   <li>{@link Feature.Shape#HOLD} - none.</li>
 *   <li>{@link Feature.Shape#FAST_CLICK} - {@code count}: how many clicks one of the player's own clicks
 *       becomes.</li>
 *   <li>{@link Feature.Shape#PERIODIC_CLICK} - {@code interval}, {@code count}: how many clicks are fired
 *       per interval.</li>
 *   <li>{@link Feature.Shape#PERIODIC_HOLD} - {@code interval}, {@code duration}.</li>
 * </ul>
 */
public final class FeatureSettings {
    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 200;
    public static final int MIN_DURATION = 1;
    public static final int MAX_DURATION = 200;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 200;

    private final int interval;
    private int duration;
    private final int count;

    public FeatureSettings(int interval, int duration, int count) {
        this.interval = clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        this.duration = clamp(duration, MIN_DURATION, MAX_DURATION);
        this.count = clamp(count, MIN_COUNT, MAX_COUNT);

        // A hold cannot outlast the interval it lives in, otherwise it would never release. Clamping
        // here (and not only where the value is read) keeps the stored value consistent with what the
        // scheduler does.
        if (this.duration > this.interval) {
            this.duration = this.interval;
        }
    }

    /**
     * Reads the old four-value shape.
     *
     * <p>Configs written before the two periodic features were merged stored {@code perTick} and
     * {@code burst} separately. Both meant "how many clicks per interval", so the larger one is kept.</p>
     */
    public static FeatureSettings legacy(int interval, int duration, int perTick, int burst) {
        return new FeatureSettings(interval, duration, Math.max(perTick, burst));
    }

    public static FeatureSettings defaults() {
        return new FeatureSettings(20, 10, 3);
    }

    /** Ticks between two activations. */
    public int interval() {
        return this.interval;
    }

    /** How long a periodic hold keeps the button down. */
    public int duration() {
        return this.duration;
    }

    /** How many clicks: per interval for a periodic click, or per player click for fast click. */
    public int count() {
        return this.count;
    }

    public FeatureSettings withInterval(int value) {
        return new FeatureSettings(value, this.duration, this.count);
    }

    public FeatureSettings withDuration(int value) {
        return new FeatureSettings(this.interval, value, this.count);
    }

    public FeatureSettings withCount(int value) {
        return new FeatureSettings(this.interval, this.duration, value);
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
                && this.count == settings.count;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(this.interval, this.duration, this.count);
    }

    @Override
    public String toString() {
        return "FeatureSettings[interval=" + this.interval
                + ", duration=" + this.duration
                + ", count=" + this.count + ']';
    }
}
