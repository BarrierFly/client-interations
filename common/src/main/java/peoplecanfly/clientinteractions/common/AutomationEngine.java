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
    }

    private final Map<Feature, FeatureScheduler> schedulers = new EnumMap<>(Feature.class);
    private final Map<Feature, FeatureSettings> settings = new EnumMap<>(Feature.class);
    private final Host host;
    private boolean enabled = true;


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
     *
     * <p>Only channels with a running feature are released. Releasing an idle channel is meaningless.</p>
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
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
                    // Fast click adds no action of its own; it multiplies what the channel produces. Its
                    // clock still advances, so the feature behaves like every other one while idle.
                    scheduler.tick(feature, this.settings.get(feature));
                    continue;
                }

                TickAction action = scheduler.tick(feature, this.settings.get(feature));
                clicks += action.clicks();
                hold |= action.hold();
                release |= action.releases();
            }

            if (clicks > 0) {
                clicks *= this.playerClickCount(channel);
            }

            if (clicks > 0 || hold || release) {
                this.host.apply(channel, clicks, hold, release);
            }
        }
    }

    /**
     * How many clicks one click of this channel becomes while fast click is running.
     *
     * <p>Returns {@code 1} unless fast click is active, in which case it is that feature's {@code count}.
     * The multiplier applies to every click the channel produces: the player's own press, and the clicks a
     * running feature of the same channel asks for.</p>
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
