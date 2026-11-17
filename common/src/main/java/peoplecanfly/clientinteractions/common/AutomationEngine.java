package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the runtime state of every feature.
 *
 * <p>Mutual exclusion applies to the three features that each drive the button on their own: hold,
 * periodic click and periodic hold. Activating one switches the others of that channel off, because
 * they would fight over the same button. Fast click is the exception - it is a multiplier for whatever
 * clicks that channel produces, so it runs alongside them.</p>
 *
 * <p>No Minecraft types; all game access goes through {@link Host}.</p>
 */
public final class AutomationEngine {
    /** Everything the engine needs from the game. */
    public interface Host {
        /**
         * Applies one tick of one feature.
         *
         * @param channel the button being driven
         * @param clicks discrete clicks to perform on this tick
         * @param hold whether the button counts as held for this tick
         * @param release whether a previous hold must be released first
         */
        void apply(ActionChannel channel, int clicks, boolean hold, boolean release);

        /** Releases a button, called when its feature stops. */
        void release(ActionChannel channel);

        /**
         * Whether this button counts as held down right now - by the player, or by a feature the mod
         * drives.
         *
         * <p>Fast click acts for as long as the operation is in effect, and holding a button down is the
         * operation being in effect: the vanilla client stops clicking after the first press and only
         * keeps the button's continuous action going, so the multiplier needs to know the button is still
         * held to keep making its clicks.</p>
         */
        boolean isPlayerHolding(ActionChannel channel);
    }

    private final Map<Feature, FeatureScheduler> schedulers = new EnumMap<>(Feature.class);
    private final Map<Feature, FeatureSettings> settings = new EnumMap<>(Feature.class);
    private final Host host;
    private boolean enabled = true;

    /**
     * Presses the player made on each channel this tick, reported by the caller before {@link #tick()}.
     *
     * <p>Vanilla handles those presses itself, so the multiplier only has to add the rest of their count.
     * They are the reason fast click keeps working while the button is held: the press itself is handled
     * once, and every further tick of the hold is a tick in effect that has to reach the count too.</p>
     */
    private final Map<ActionChannel, Integer> playerPressesThisTick = new EnumMap<>(ActionChannel.class);


    public AutomationEngine(Host host) {
        this.host = host;

        for (Feature feature : Feature.values()) {
            this.schedulers.put(feature, new FeatureScheduler());
            this.settings.put(feature, FeatureSettings.defaults());
        }
    }

    public FeatureSettings settings(Feature feature) {
        return this.settings.get(feature);
    }

    public void setSettings(Feature feature, FeatureSettings value) {
        this.settings.put(feature, value == null ? FeatureSettings.defaults() : value);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * Master switch: turning it off releases the buttons but keeps every timer running.
     *
     * <p>Only channels with a running feature are released. Releasing an idle channel is meaningless, and
     * it used to be harmful: this runs during client initialisation, where touching the vanilla key
     * mappings is not possible yet.</p>
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            this.playerPressesThisTick.clear();

            for (ActionChannel channel : ActionChannel.values()) {
                if (this.activeFeature(channel) != null) {
                    this.host.release(channel);
                }
            }
        }
    }

    public boolean isActive(Feature feature) {
        return this.schedulers.get(feature).isActive();
    }

    public FeatureScheduler scheduler(Feature feature) {
        return this.schedulers.get(feature);
    }

    /**
     * Switches a feature on or off.
     *
     * <p>Turning a feature on switches off the other features of the same channel, so at most one feature
     * that drives the button on its own is running; fast click is a multiplier and is left alone.</p>
     *
     * @return the state of the feature after the call
     */
    public boolean toggle(Feature feature) {
        if (this.schedulers.get(feature).isActive()) {
            this.stop(feature);
            return false;
        }

        this.activate(feature);
        return true;
    }

    /**
     * Activates a feature, switching off the other features of its channel.
     *
     * <p>Fast click is never switched off by this and never switches anything off: it multiplies the
     * channel's clicks instead of driving the button, so it can run next to hold, periodic click or
     * periodic hold.</p>
     */
    public void activate(Feature feature) {
        if (feature.shape() != Feature.Shape.FAST_CLICK) {
            for (Feature other : Feature.forChannel(feature.channel())) {
                if (other != feature && other.shape() != Feature.Shape.FAST_CLICK) {
                    this.stop(other);
                }
            }
        }

        this.schedulers.get(feature).activate();
    }

    public void stop(Feature feature) {
        FeatureScheduler scheduler = this.schedulers.get(feature);

        if (scheduler.isActive()) {
            scheduler.deactivate();

            // Only a feature that drives the button has a hold to release. Fast click never touched it,
            // and releasing on its behalf would drop a hold another feature of the channel is running.
            if (feature.shape() != Feature.Shape.FAST_CLICK) {
                this.host.release(feature.channel());
            }
        }
    }

    /** Stops every feature, e.g. when leaving the world. */
    public void stopAll() {
        for (Feature feature : Feature.values()) {
            this.stop(feature);
        }
    }

    /** Stops every feature of one channel. */
    public void stopChannel(ActionChannel channel) {
        for (Feature feature : Feature.forChannel(channel)) {
            this.stop(feature);
        }
    }

    /** The feature currently active for a channel, or {@code null}. */
    public Feature activeFeature(ActionChannel channel) {
        for (Feature feature : Feature.forChannel(channel)) {
            if (this.schedulers.get(feature).isActive()) {
                return feature;
            }
        }

        return null;
    }

    /**
     * Advances every active feature by exactly one client tick.
     *
     * <p>Each feature that drives the button applies its own tick action. Fast click has no action of its
     * own: the clicks a channel produces on a tick are multiplied by it here, so an active fast click makes
     * that channel perform {@code count} clicks in every tick in which anything would have clicked once -
     * the player's own press included. All of them are injected on the same tick.</p>
     */
    public void tick() {
        for (ActionChannel channel : ActionChannel.values()) {
            if (!this.enabled) {
                // Paused: the phases keep running but the game is not touched.
                for (Feature feature : Feature.forChannel(channel)) {
                    FeatureScheduler scheduler = this.schedulers.get(feature);

                    if (scheduler.isActive()) {
                        scheduler.tick(feature, this.settings.get(feature));
                        this.host.release(channel);
                    }
                }

                continue;
            }

            int clicks = 0;
            boolean hold = false;
            boolean release = false;

            for (Feature feature : Feature.forChannel(channel)) {
                FeatureScheduler scheduler = this.schedulers.get(feature);

                if (!scheduler.isActive()) {
                    continue;
                }

                if (feature.shape() == Feature.Shape.FAST_CLICK) {
                    // Fast click adds no action of its own; it scales what the channel does. Its clock
                    // still advances, so the feature behaves like every other one while idle.
                    scheduler.tick(feature, this.settings.get(feature));
                    continue;
                }

                TickAction action = scheduler.tick(feature, this.settings.get(feature));
                clicks += action.clicks();
                hold |= action.hold();
                release |= action.releases();
            }

            clicks = this.applyFastClick(channel, clicks, hold);

            if (clicks > 0 || hold || release) {
                this.host.apply(channel, clicks, hold, release);
            }
        }

        this.playerPressesThisTick.clear();
    }

    /**
     * Scales one tick of a channel by fast click, when it is running.
     *
     * <p>The requirement is "the operation happens {@code count} times in every tick it is in effect",
     * not "once per press": while the player holds the button the vanilla client stops clicking after the
     * first press, so the remaining ticks of the hold are produced here. A tick is in effect when the
     * channel would click anyway (a running feature asked for clicks) or when the button is held - by the
     * player or by a feature's hold. The player's own press is already performed by vanilla, so only the
     * rest of that tick's count is injected.</p>
     *
     * @return how many clicks this tick must inject
     */
    private int applyFastClick(ActionChannel channel, int clicks, boolean hold) {
        Feature fastClick = Feature.fastClickFor(channel);

        if (fastClick == null || !this.schedulers.get(fastClick).isActive()) {
            return clicks;
        }

        int presses = this.playerPressesThisTick.getOrDefault(channel, 0);
        boolean inEffect = clicks > 0 || hold || presses > 0 || this.host.isPlayerHolding(channel);

        if (!inEffect) {
            return clicks;
        }

        int times = this.settings.get(fastClick).count();

        // One operation is what a tick performs by default; a feature that asks for more than one click is
        // scaled with it rather than replaced by the count.
        int target = Math.max(clicks, 1) * times;

        // Vanilla already performed the player's own presses on this tick.
        return Math.max(0, target - presses);
    }

    /**
     * Reports presses the player made on this channel, before this tick's {@link #tick()}.
     *
     * <p>Vanilla performs them itself, so they are recorded to subtract from the tick's count rather than
     * to inject anything: the tick must reach {@code count} clicks in total, and the press is already one
     * of them.</p>
     */
    public void recordPlayerPress(ActionChannel channel, int presses) {
        if (presses > 0) {
            this.playerPressesThisTick.merge(channel, presses, Integer::sum);
        }
    }

    /**
     * How many clicks one click of this channel becomes while fast click is running.
     *
     * <p>Returns {@code 1} unless fast click is active, in which case it is that feature's {@code count}.
     * Used by callers that need the multiplier without running a tick; {@link #tick()} applies it itself.
     * </p>
     */
    public int playerClickCount(ActionChannel channel) {
        Feature fastClick = Feature.fastClickFor(channel);

        if (fastClick == null || !this.enabled) {
            return 1;
        }

        FeatureScheduler scheduler = this.schedulers.get(fastClick);

        if (!scheduler.isActive()) {
            return 1;
        }

        return scheduler.fastClickCount(fastClick, this.settings.get(fastClick));
    }
}
