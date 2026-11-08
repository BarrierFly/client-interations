package peoplecanfly.clientinteractions.common;

/**
 * Pure, framework-free timing engine for one {@link ActionChannel}.
 *
 * <p>The scheduler is advanced exactly once per client tick by
 * {@link #tick(AutomationMode, ChannelSettings)}. It never touches Minecraft, which keeps every timing
 * rule of the mod unit testable.</p>
 *
 * <h2>Timing contract</h2>
 * <p>Intervals and durations are counted in client ticks (1 tick = 50 ms at the default 20 TPS).
 * Interval and burst values are clamped to their legal range on construction of {@link ChannelSettings},
 * and a hold duration is additionally clamped to the interval it belongs to.</p>
 *
 * <ul>
 *   <li>{@link AutomationMode#HOLD}: the button is reported as held from the very first tick until the mode
 *       is switched off.</li>
 *   <li>{@link AutomationMode#FAST_CLICK}: injects {@code perTick} clicks on every tick, i.e. the highest
 *       rate the vanilla client can consume (up to {@value ChannelSettings#MAX_PER_TICK} per tick).</li>
 *   <li>{@link AutomationMode#PERIODIC_CLICK}: injects {@code perTick} clicks on the first tick and then on
 *       every {@code interval}-th tick.</li>
 *   <li>{@link AutomationMode#PERIODIC_HOLD}: holds for {@code holdDuration} ticks, then releases for the
 *       rest of the {@code interval}. Both counters start on activation, so the button is held from tick 1.</li>
 *   <li>{@link AutomationMode#PERIODIC_BURST}: one click on each of {@code burstSize} consecutive ticks,
 *       repeating every {@code interval}-th tick, starting immediately.</li>
 * </ul>
 */
public final class ChannelScheduler {
    /** Ticks elapsed since activation; used to derive every mode's phase. */
    private int elapsed;

    /** True while the current mode is active (i.e. after {@link #activate} until {@link #deactivate}). */
    private boolean active;

    /** Whether the previous tick ended with the button held, so releases can be reported exactly once. */
    private boolean heldLastTick;

    /**
     * Starts the mode from a clean phase on the next {@link #tick} call.
     * Calling this while already active restarts the cycle, which is what the user sees as
     * "toggling the key again starts from the beginning".
     */
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

    /** Ticks elapsed inside the current activation; resets to 0 on {@link #activate}. */
    public int elapsedTicks() {
        return this.elapsed;
    }

    /**
     * Advances the scheduler by one client tick and returns what the channel should do on that tick.
     * Returns {@link TickAction#none()} while inactive.
     */
    public TickAction tick(AutomationMode mode, ChannelSettings settings) {
        if (!this.active) {
            return TickAction.none();
        }

        TickAction action = this.compute(mode, settings);

        // Report the transition from "held" to "not held" exactly once, so that the host can release the
        // button (which also ends item usage) at the correct tick.
        boolean releases = this.heldLastTick && !action.hold();
        this.heldLastTick = action.hold();

        this.elapsed++;

        return releases ? TickAction.of(action.clicks(), action.hold(), true) : action;
    }

    private TickAction compute(AutomationMode mode, ChannelSettings settings) {
        switch (mode) {
            case HOLD:
                return TickAction.hold(true);

            case FAST_CLICK:
                return TickAction.clicks(settings.perTick());

            case PERIODIC_CLICK: {
                int interval = ChannelSettings.clampInterval(settings.interval());
                return this.elapsed % interval == 0 ? TickAction.clicks(settings.perTick()) : TickAction.none();
            }

            case PERIODIC_HOLD: {
                int interval = ChannelSettings.clampInterval(settings.interval());
                int duration = ChannelSettings.clampHoldDuration(settings.holdDuration(), interval);
                return TickAction.hold(this.elapsed % interval < duration);
            }

            case PERIODIC_BURST: {
                int interval = ChannelSettings.clampInterval(settings.interval());
                int burst = ChannelSettings.clampBurst(settings.burstSize());
                // A burst longer than the interval simply fires on every tick of the cycle.
                return this.elapsed % interval < Math.min(burst, interval)
                        ? TickAction.clicks(1)
                        : TickAction.none();
            }

            default:
                return TickAction.none();
        }
    }
}
