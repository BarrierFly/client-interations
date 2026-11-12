package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the runtime state of every feature.
 *
 * <p>Mutual exclusion is the rule that matters here: the four features of one {@link ActionChannel}
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

    /**
     * Extra clicks still owed to the player for fast click, per channel.
     *
     * <p>Fast click turns one of the player's clicks into {@code count} clicks. The player's own press is
     * already handled by vanilla, so only the remaining {@code count - 1} have to be injected, and they
     * are emitted one per tick. Emitting them all in the same tick does not produce that many clicks:
     * vanilla raises its missed-swing timer, resets the attack strength and throttles item use, so a
     * same-tick burst collapses into a single action. This is the same reason periodic clicks are spread
     * over consecutive ticks.</p>
     */
    private final Map<ActionChannel, Integer> pendingPlayerClicks = new EnumMap<>(ActionChannel.class);

    /**
     * Channels where the player clicked on this tick.
     *
     * <p>The first extra click must not land on the same tick as the physical one: vanilla has just
     * processed that press, so a second {@code startAttack} in the same tick would be swallowed. The
     * emission therefore starts on the following tick.</p>
     */
    private final java.util.Set<ActionChannel> clickedThisTick =
            java.util.EnumSet.noneOf(ActionChannel.class);

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
     * <p>Fast clicks still owed from before the switch was turned off are dropped: a click the player made
     * while automation was allowed must not land after the switch turned it off.</p>
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            this.pendingPlayerClicks.clear();

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
            this.pendingPlayerClicks.remove(feature.channel());
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

    /**
     * Advances every active feature by exactly one client tick.
     *
     * <p>Fast click is the odd one out: it has no timer of its own, but the extra clicks it owes the
     * player are emitted here, one per tick, so they become real separate clicks instead of being
     * swallowed by vanilla. See {@link #recordPlayerClick(ActionChannel)}.</p>
     */
    public void tick() {
        for (Feature feature : Feature.values()) {
            FeatureScheduler scheduler = this.schedulers.get(feature);

            if (!scheduler.isActive()) {
                continue;
            }

            TickAction action = scheduler.tick(feature, this.settings.get(feature));

            if (!this.enabled) {
                // Paused: the phase keeps running but the game is not touched.
                this.host.release(feature.channel());
                continue;
            }

            if (feature.shape() == Feature.Shape.FAST_CLICK) {
                // Fast click has no timer output of its own. It only emits the extras it owes from the
                // player's own press, one per tick.
                this.emitPendingPlayerClick(feature.channel());
                continue;
            }

            this.host.apply(feature.channel(), action.clicks(), action.hold(), action.releases());
        }

        // The player's press has been processed by now; the next tick may start emitting the repeats.
        this.clickedThisTick.clear();
    }

    /**
     * Records one of the player's own clicks and returns the extra clicks now owed for it.
     *
     * <p>The player's physical click is already handled by vanilla; this only schedules the repeats.
     * Returns {@code 0} when fast click is off, so the caller can leave the click alone.</p>
     */
    public int recordPlayerClick(ActionChannel channel) {
        int times = this.playerClickCount(channel);

        if (times <= 1) {
            return 0;
        }

        int extra = times - 1;
        int queued = this.pendingPlayerClicks.getOrDefault(channel, 0);
        this.pendingPlayerClicks.put(channel,
                Math.min(FeatureSettings.MAX_COUNT, queued + extra));
        this.clickedThisTick.add(channel);

        return extra;
    }

    /** Emits at most one owed fast click, returning true when one was emitted. */
    private boolean emitPendingPlayerClick(ActionChannel channel) {
        int pending = this.pendingPlayerClicks.getOrDefault(channel, 0);

        if (pending <= 0) {
            return false;
        }

        if (this.clickedThisTick.contains(channel)) {
            // The player's own press is being processed this tick; the repeats start next tick.
            return false;
        }

        this.pendingPlayerClicks.put(channel, pending - 1);
        this.host.apply(channel, 1, false, false);

        return true;
    }

    /** How many extra fast clicks are still owed on a channel. */
    public int pendingPlayerClicks(ActionChannel channel) {
        return this.pendingPlayerClicks.getOrDefault(channel, 0);
    }

    /**
     * How many clicks one of the player's own clicks should become on this channel.
     *
     * <p>Returns {@code 1} unless fast click is active, in which case it is that feature's {@code count}.
     * The caller reports the physical click and calls {@link #recordPlayerClick(ActionChannel)}, which is
     * why fast click needs no timer of its own: it only ever acts when the player does.</p>
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
