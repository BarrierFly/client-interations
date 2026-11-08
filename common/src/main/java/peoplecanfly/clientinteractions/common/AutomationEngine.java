package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the runtime state of every feature.
 *
 * <p>Mutual exclusion is the rule that matters here: the five features of one {@link ActionChannel}
 * all drive the same mouse button, so activating one switches the others of that channel off. Features
 * of the other channel are untouched, because attack and use are independent.</p>
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
     * <p>Turning a feature on switches off every other feature of the same channel first, so at most
     * one feature per button is ever running.</p>
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

    /** Activates a feature, switching off the other features of its channel. */
    public void activate(Feature feature) {
        for (Feature other : Feature.forChannel(feature.channel())) {
            if (other != feature) {
                this.stop(other);
            }
        }

        this.schedulers.get(feature).activate();
    }

    public void stop(Feature feature) {
        FeatureScheduler scheduler = this.schedulers.get(feature);

        if (scheduler.isActive()) {
            scheduler.deactivate();
            this.host.release(feature.channel());
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

    /** Advances every active feature by exactly one client tick. */
    public void tick() {
        for (Feature feature : Feature.values()) {
            FeatureScheduler scheduler = this.schedulers.get(feature);

            if (!scheduler.isActive()) {
                continue;
            }

            TickAction action = scheduler.tick(feature, this.settings.get(feature));

            if (this.enabled) {
                this.host.apply(feature.channel(), action.clicks(), action.hold(), action.releases());
            } else {
                // Paused: the phase keeps running but the game is not touched.
                this.host.release(feature.channel());
            }
        }
    }
}
