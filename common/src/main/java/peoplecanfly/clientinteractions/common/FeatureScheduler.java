package peoplecanfly.clientinteractions.common;

/**
 * Timing engine for one feature.
 *
 * <p>Pure arithmetic on a tick counter, advanced exactly once per client tick by
 * {@link #tick(Feature, FeatureSettings)}. No Minecraft types, so every timing rule is unit tested.</p>
 *
 * <p>Intervals and durations are in client ticks (1 tick = 50 ms at 20 TPS).</p>
 *
 * <p>{@link Feature.Shape#FAST_CLICK} is the exception: it never produces a click from here, because it
 * multiplies the player's own clicks rather than acting on its own. See
 * {@link #fastClickCount(Feature, FeatureSettings)}.</p>
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

    /** Advances one client tick and returns what the feature should do by itself. */
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

    /**
     * How many clicks one of the player's own clicks should become, for a fast-click feature.
     *
     * <p>Returns {@code 0} when the feature is off, so the caller can leave the player's click alone, and
     * {@code settings.count()} when it is on. That is the whole behaviour: fast click is a multiplier for
     * the player, not an independent source of clicks.</p>
     */
    public int fastClickCount(Feature feature, FeatureSettings settings) {
        if (!this.active || feature.shape() != Feature.Shape.FAST_CLICK) {
            return 0;
        }

        return settings.count();
    }

    private TickAction compute(Feature.Shape shape, FeatureSettings settings) {
        switch (shape) {
            case HOLD:
                return TickAction.hold(true);

            case FAST_CLICK:
                // Never acts on its own; the player's click is what triggers it.
                return TickAction.none();

            case PERIODIC_CLICK: {
                int interval = settings.interval();

                // The clicks are spread one per tick, the rate a player produces separate clicks at. One
                // tick has one crosshair target and one input pass, so a same-tick burst cannot act as
                // several separate clicks: the use channel would place or restart the same use twice, and
                // the attack channel would swing into an attack cooldown the first click just reset.
                int count = Math.min(settings.count(), interval);

                return this.elapsed % interval < count
                        ? TickAction.clicks(1)
                        : TickAction.none();
            }

            case PERIODIC_HOLD: {
                int interval = settings.interval();
                int duration = FeatureSettings.clampDuration(settings.duration(), interval);
                return TickAction.hold(this.elapsed % interval < duration);
            }

            default:
                return TickAction.none();
        }
    }
}
