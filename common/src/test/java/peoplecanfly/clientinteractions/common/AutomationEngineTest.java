package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {
    /** Records what the engine asks the game to do. */
    private static final class RecordingHost implements AutomationEngine.Host {
        final List<String> events = new ArrayList<>();
        final List<ActionChannel> released = new ArrayList<>();

        @Override
        public void apply(ActionChannel channel, int clicks, boolean hold, boolean release) {
            StringBuilder builder = new StringBuilder(channel.id());

            if (release) {
                builder.append(":release");
            }

            if (clicks > 0) {
                builder.append(":click").append(clicks);
            }

            if (hold) {
                builder.append(":hold");
            }

            this.events.add(builder.toString());
        }

        @Override
        public void release(ActionChannel channel) {
            this.released.add(channel);
            this.events.add(channel.id() + ":stop");
        }

        List<String> forChannel(ActionChannel channel) {
            return this.events.stream()
                    .filter(event -> event.startsWith(channel.id()))
                    .collect(Collectors.toList());
        }
    }

    private static ChannelSettings settings(int interval, int holdDuration, int perTick, int burst) {
        return new ChannelSettings(interval, holdDuration, perTick, burst);
    }

    private static AutomationEngine engineWith(RecordingHost host, ActionChannel channel,
                                               AutomationMode mode, ChannelSettings settings) {
        AutomationEngine engine = new AutomationEngine(host);
        engine.setConfig(channel, new AutomationEngine.ChannelConfig(mode, settings));
        return engine;
    }

    @Test
    @DisplayName("toggling a channel flips its state")
    void toggleFlipsState() {
        AutomationEngine engine = new AutomationEngine(new RecordingHost());

        assertFalse(engine.isActive(ActionChannel.ATTACK));
        assertTrue(engine.toggle(ActionChannel.ATTACK));
        assertTrue(engine.isActive(ActionChannel.ATTACK));
        assertFalse(engine.toggle(ActionChannel.ATTACK));
        assertFalse(engine.isActive(ActionChannel.ATTACK));
    }

    @Test
    @DisplayName("stopping a channel releases the key exactly once")
    void stopReleasesOnce() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = new AutomationEngine(host);
        engine.toggle(ActionChannel.ATTACK);
        engine.stop(ActionChannel.ATTACK);
        engine.stop(ActionChannel.ATTACK);

        assertEquals(1, host.released.size());
    }

    @Test
    @DisplayName("each tick advances only active channels")
    void onlyActiveChannelsTick() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engineWith(host, ActionChannel.ATTACK, AutomationMode.FAST_CLICK,
                settings(20, 10, 1, 1));
        engine.toggle(ActionChannel.ATTACK);
        host.events.clear();

        engine.tick();
        engine.tick();

        assertEquals(2, host.events.size());
        assertEquals(List.of("attack:click1", "attack:click1"), host.forChannel(ActionChannel.ATTACK));
        assertEquals(List.of(), host.forChannel(ActionChannel.USE));
    }

    @Test
    @DisplayName("both channels can run at the same time")
    void bothChannelsRun() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = new AutomationEngine(host);
        engine.setConfig(ActionChannel.ATTACK, new AutomationEngine.ChannelConfig(
                AutomationMode.HOLD, settings(20, 10, 1, 1)));
        engine.setConfig(ActionChannel.USE, new AutomationEngine.ChannelConfig(
                AutomationMode.FAST_CLICK, settings(20, 10, 2, 1)));
        engine.toggle(ActionChannel.ATTACK);
        engine.toggle(ActionChannel.USE);
        host.events.clear();

        engine.tick();

        assertEquals(List.of("attack:hold"), host.forChannel(ActionChannel.ATTACK));
        assertEquals(List.of("use:click2"), host.forChannel(ActionChannel.USE));
    }

    @Test
    @DisplayName("the master switch suppresses input but keeps the phase running")
    void disabledKeepsPhase() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engineWith(host, ActionChannel.ATTACK, AutomationMode.PERIODIC_CLICK,
                settings(4, 1, 1, 1));
        engine.toggle(ActionChannel.ATTACK);
        engine.setEnabled(false);
        host.events.clear();

        engine.tick();
        engine.tick();

        assertFalse(host.events.stream().anyMatch(event -> event.contains("click")));
        assertEquals(2, engine.scheduler(ActionChannel.ATTACK).elapsedTicks());

        engine.setEnabled(true);
        engine.tick();
        engine.tick();
        engine.tick();
        // the phase kept running while disabled, so the interval boundary lands on the 4th tick overall
        assertEquals(1, host.forChannel(ActionChannel.ATTACK).stream()
                .filter(event -> event.contains("click"))
                .count());
    }

    @Test
    @DisplayName("a stale hold is released when automation is switched off")
    void disablingReleases() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engineWith(host, ActionChannel.ATTACK, AutomationMode.HOLD,
                settings(20, 10, 1, 1));
        engine.toggle(ActionChannel.ATTACK);
        engine.tick();
        host.events.clear();

        engine.setEnabled(false);

        assertEquals(List.of("attack:stop"), host.forChannel(ActionChannel.ATTACK));
    }

    @Test
    @DisplayName("stopAll stops every channel")
    void stopAllStopsEverything() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = new AutomationEngine(host);
        engine.toggle(ActionChannel.ATTACK);
        engine.toggle(ActionChannel.USE);

        engine.stopAll();

        assertFalse(engine.isActive(ActionChannel.ATTACK));
        assertFalse(engine.isActive(ActionChannel.USE));
        assertEquals(2, host.released.size());
    }

    @Test
    @DisplayName("changed settings are used on the very next tick")
    void settingsAreLive() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engineWith(host, ActionChannel.ATTACK, AutomationMode.PERIODIC_CLICK,
                settings(10, 1, 1, 1));
        engine.toggle(ActionChannel.ATTACK);
        host.events.clear();

        engine.tick();
        engine.setConfig(ActionChannel.ATTACK, new AutomationEngine.ChannelConfig(
                AutomationMode.PERIODIC_CLICK, settings(1, 1, 1, 1)));
        engine.tick();

        assertEquals(List.of("attack:click1", "attack:click1"), host.forChannel(ActionChannel.ATTACK));
    }

    @Test
    @DisplayName("mode changes take effect immediately")
    void modeChangesAreLive() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engineWith(host, ActionChannel.ATTACK, AutomationMode.HOLD,
                settings(10, 1, 1, 1));
        engine.toggle(ActionChannel.ATTACK);
        host.events.clear();

        engine.tick();
        engine.setConfig(ActionChannel.ATTACK, new AutomationEngine.ChannelConfig(
                AutomationMode.FAST_CLICK, settings(10, 1, 3, 1)));
        engine.tick();

        assertEquals(List.of("attack:hold", "attack:release:click3"), host.forChannel(ActionChannel.ATTACK));
    }
}
