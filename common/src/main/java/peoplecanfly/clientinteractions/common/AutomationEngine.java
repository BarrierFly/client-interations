package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the per-channel state of one game session.
 *
 * <p>This class is deliberately free of any Minecraft types: all game access happens through
 * {@link Host}. That keeps the whole decision logic testable with a fake host.</p>
 */
public final class AutomationEngine {
    /** Everything the engine needs from the game, and nothing more. */
    public interface Host {
        /**
         * Injects one client-tick worth of input for the given vanilla action.
         *
         * @param channel the button being driven
         * @param clicks how many discrete clicks to perform on this tick
         * @param hold true to report the button as held for this tick
         * @param release true to release the button before the clicks
         */
        void apply(ActionChannel channel, int clicks, boolean hold, boolean release);

        /** Called when a channel stops being active, so the host can release the button. */
        void release(ActionChannel channel);
    }

    /** Immutable snapshot of the configuration for one channel. */
    public static final class ChannelConfig {
        private final AutomationMode mode;
        private final ChannelSettings settings;

        public ChannelConfig(AutomationMode mode, ChannelSettings settings) {
            this.mode = mode;
            this.settings = settings;
        }

        public AutomationMode mode() {
            return this.mode;
        }

        public ChannelSettings settings() {
            return this.settings;
        }
    }

    private final Map<ActionChannel, ChannelScheduler> schedulers = new EnumMap<>(ActionChannel.class);
    private final Map<ActionChannel, ChannelConfig> configs = new EnumMap<>(ActionChannel.class);
    private final Host host;
    private boolean enabled = true;

    public AutomationEngine(Host host) {
        this.host = host;

        for (ActionChannel channel : ActionChannel.values()) {
            this.schedulers.put(channel, new ChannelScheduler());
            this.configs.put(channel, new ChannelConfig(AutomationMode.HOLD, ChannelSettings.defaults()));
        }
    }

    public ChannelScheduler scheduler(ActionChannel channel) {
        return this.schedulers.get(channel);
    }

    public ChannelConfig config(ActionChannel channel) {
        return this.configs.get(channel);
    }

    public void setConfig(ActionChannel channel, ChannelConfig config) {
        this.configs.put(channel, config);
    }

    /** The master switch of the mod; when off no input is injected, but phases keep running. */
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            for (ActionChannel channel : ActionChannel.values()) {
                this.host.release(channel);
            }
        }
    }

    public boolean isActive(ActionChannel channel) {
        return this.schedulers.get(channel).isActive();
    }

    /** Toggles a channel; returns the state after toggling. */
    public boolean toggle(ActionChannel channel) {
        ChannelScheduler scheduler = this.schedulers.get(channel);

        if (scheduler.isActive()) {
            this.stop(channel);
            return false;
        }

        scheduler.activate();
        return true;
    }

    /** Stops a single channel and lets the host release the key. */
    public void stop(ActionChannel channel) {
        ChannelScheduler scheduler = this.schedulers.get(channel);

        if (scheduler.isActive()) {
            scheduler.deactivate();
            this.host.release(channel);
        }
    }

    /** Stops everything, e.g. when leaving the world. */
    public void stopAll() {
        for (ActionChannel channel : ActionChannel.values()) {
            this.stop(channel);
        }
    }

    /**
     * Advances every active channel by exactly one client tick.
     *
     * <p>Called once per client tick, so configured intervals map one-to-one onto game ticks.</p>
     */
    public void tick() {
        for (ActionChannel channel : ActionChannel.values()) {
            ChannelScheduler scheduler = this.schedulers.get(channel);

            if (!scheduler.isActive()) {
                continue;
            }

            ChannelConfig config = this.configs.get(channel);
            TickAction action = scheduler.tick(config.mode(), config.settings());

            if (this.enabled) {
                this.host.apply(channel, action.clicks(), action.hold(), action.releases());
            } else {
                // Automation is paused: the phase keeps running but the game is never touched.
                this.host.release(channel);
            }
        }
    }
}
