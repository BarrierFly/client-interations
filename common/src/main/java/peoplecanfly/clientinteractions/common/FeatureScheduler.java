package peoplecanfly.clientinteractions.common;

/**
 * Timing engine for one feature.
 *
 * <p>Pure arithmetic on a tick counter, advanced exactly once per client tick by
 * {@link #tick(Feature, FeatureSettings)}. No Minecraft types, so every timing rule is unit tested.</p>
 *
 * <p>Intervals and durations are in client ticks (1 tick = 50 ms at 20 TPS).</p>
 */
public final class FeatureScheduler {
    /** Ticks elapsed since activation. */
    private int elapsed;

    private boolean active;

    /** Whether the previous tick ended with the button held, so the release is reported exactly once. */
    private boolean heldLastTick;

    public void activate() {
        this.active = true;
        this.elapsed = 0;
        this.heldLastTick = false;
    }

    public void deactivate() {
        this.active = false;
        this.elapsed = 0;
        this.heldLastTick = false;
    }

    public boolean isActive() {
        return this.active;
    }

    public int elapsedTicks() {
        return this.elapsed;
    }

    /** Advances one client tick and returns what the feature should do. */
    public TickAction tick(Feature feature, FeatureSettings settings) {
        if (!this.active) {
            return TickAction.none();
        }

        TickAction action = this.compute(feature.shape(), settings);

        boolean releases = this.heldLastTick && !action.hold();
        this.heldLastTick = action.hold();
        this.elapsed++;

        return releases ? TickAction.of(action.clicks(), action.hold(), true) : action;
    }

    private TickAction compute(Feature.Shape shape, FeatureSettings settings) {
        switch (shape) {
            case HOLD:
                return TickAction.hold(true);

            case FAST_CLICK:
                return TickAction.clicks(settings.perTick());

            case PERIODIC_CLICK: {
                int interval = settings.interval();
                return this.elapsed % interval == 0 ? TickAction.clicks(settings.perTick()) : TickAction.none();
            }

            case PERIODIC_HOLD: {
                int interval = settings.interval();
                int duration = FeatureSettings.clampDuration(settings.duration(), interval);
                return TickAction.hold(this.elapsed % interval < duration);
            }

            case PERIODIC_BURST: {
                int interval = settings.interval();
                int burst = Math.min(settings.burst(), interval);
                return this.elapsed % interval < burst ? TickAction.clicks(1) : TickAction.none();
            }

            default:
                return TickAction.none();
        }
    }
}
