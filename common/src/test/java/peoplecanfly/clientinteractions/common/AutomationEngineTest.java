package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {
    private static final class RecordingHost implements AutomationEngine.Host {
        final List<String> events = new ArrayList<>();

        @Override
        public void apply(ActionChannel channel, int clicks, boolean hold, boolean release) {
            StringBuilder b = new StringBuilder(channel.id());
            if (release) {
                b.append(":release");
            }
            if (clicks > 0) {
                b.append(":click").append(clicks);
            }
            if (hold) {
                b.append(":hold");
            }
            this.events.add(b.toString());
        }

        @Override
        public void release(ActionChannel channel) {
            this.events.add(channel.id() + ":stop");
        }

        long count(String needle) {
            return this.events.stream().filter(e -> e.contains(needle)).count();
        }
    }

    private static AutomationEngine engine(RecordingHost host) {
        AutomationEngine engine = new AutomationEngine(host);
        for (Feature feature : Feature.values()) {
            engine.setSettings(feature, new FeatureSettings(20, 10, 1, 3));
        }
        return engine;
    }

    @Test
    @DisplayName("every feature starts off")
    void everythingStartsOff() {
        AutomationEngine engine = engine(new RecordingHost());

        for (Feature feature : Feature.values()) {
            assertFalse(engine.isActive(feature), feature.id() + " should start off");
        }
    }

    @Test
    @DisplayName("toggling a feature flips it")
    void toggleFlips() {
        AutomationEngine engine = engine(new RecordingHost());

        assertTrue(engine.toggle(Feature.ATTACK_HOLD));
        assertTrue(engine.isActive(Feature.ATTACK_HOLD));
        assertFalse(engine.toggle(Feature.ATTACK_HOLD));
        assertFalse(engine.isActive(Feature.ATTACK_HOLD));
    }

    @Test
    @DisplayName("activating a feature switches off the other features of the same channel")
    void featuresOfOneChannelAreMutuallyExclusive() {
        AutomationEngine engine = engine(new RecordingHost());

        for (Feature feature : Feature.forChannel(ActionChannel.ATTACK)) {
            engine.activate(feature);

            for (Feature other : Feature.forChannel(ActionChannel.ATTACK)) {
                assertEquals(other == feature, engine.isActive(other),
                        other.id() + " while " + feature.id() + " is active");
            }
        }
    }

    @Test
    @DisplayName("the two channels do not affect each other")
    void channelsAreIndependent() {
        AutomationEngine engine = engine(new RecordingHost());

        engine.activate(Feature.ATTACK_HOLD);
        engine.activate(Feature.USE_PERIODIC_CLICK);

        assertTrue(engine.isActive(Feature.ATTACK_HOLD));
        assertTrue(engine.isActive(Feature.USE_PERIODIC_CLICK));
        assertEquals(Feature.ATTACK_HOLD, engine.activeFeature(ActionChannel.ATTACK));
        assertEquals(Feature.USE_PERIODIC_CLICK, engine.activeFeature(ActionChannel.USE));
    }

    @Test
    @DisplayName("switching a feature off within a channel leaves the channel idle")
    void stoppingLeavesChannelIdle() {
        AutomationEngine engine = engine(new RecordingHost());
        engine.activate(Feature.ATTACK_HOLD);
        engine.stop(Feature.ATTACK_HOLD);

        assertNull(engine.activeFeature(ActionChannel.ATTACK));
    }

    @Test
    @DisplayName("activating the already active feature keeps it active")
    void reactivatingSameFeature() {
        AutomationEngine engine = engine(new RecordingHost());
        engine.activate(Feature.ATTACK_HOLD);
        engine.activate(Feature.ATTACK_HOLD);

        assertTrue(engine.isActive(Feature.ATTACK_HOLD));
    }

    @Test
    @DisplayName("a release is sent when switching features of one channel")
    void switchingReleasesTheOldButton() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.activate(Feature.ATTACK_HOLD);
        engine.tick();
        host.events.clear();

        engine.activate(Feature.ATTACK_FAST_CLICK);

        assertEquals(1, host.count("attack:stop"), "the hold must be released before the new feature runs");
    }

    @Test
    @DisplayName("only active features tick, and each tick advances by exactly one")
    void tickingOnlyAdvancesActiveFeatures() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.activate(Feature.ATTACK_FAST_CLICK);
        host.events.clear();

        engine.tick();
        engine.tick();
        engine.tick();

        assertEquals(3, host.count("attack:click1"));
        assertEquals(3, engine.scheduler(Feature.ATTACK_FAST_CLICK).elapsedTicks());
        assertEquals(0, engine.scheduler(Feature.ATTACK_HOLD).elapsedTicks());
    }

    @Test
    @DisplayName("the master switch suppresses input but keeps the phase")
    void masterSwitchKeepsPhase() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(4, 1, 1, 1));
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        engine.setEnabled(false);
        host.events.clear();

        engine.tick();
        engine.tick();

        assertEquals(0, host.count("click"));
        assertEquals(2, engine.scheduler(Feature.ATTACK_PERIODIC_CLICK).elapsedTicks());

        engine.setEnabled(true);
        engine.tick();
        engine.tick();
        // interval is 4, so with the phase having advanced 2 ticks while disabled the next click lands
        // on the 4th tick overall, which is one more tick away.
        assertEquals(0, host.count("click"), "no click yet on ticks 3 and 4 of the phase");

        engine.tick();
        assertEquals(1, host.count("click"), "the phase kept running while disabled");
    }

    @Test
    @DisplayName("disabling the master switch on startup releases nothing")
    void disablingBeforeAnythingRunsReleasesNothing() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);

        engine.setEnabled(false);

        assertEquals(0, host.events.size(),
                "no feature is running yet, so nothing may be released");

        engine.setEnabled(true);
        assertEquals(0, host.events.size());
    }

    @Test
    @DisplayName("stopAll and stopChannel stop what they should")
    void stoppingGroups() {
        AutomationEngine engine = engine(new RecordingHost());
        engine.activate(Feature.ATTACK_HOLD);
        engine.activate(Feature.USE_HOLD);

        engine.stopChannel(ActionChannel.ATTACK);
        assertFalse(engine.isActive(Feature.ATTACK_HOLD));
        assertTrue(engine.isActive(Feature.USE_HOLD));

        engine.stopAll();
        assertFalse(engine.isActive(Feature.USE_HOLD));
    }

    @Test
    @DisplayName("settings are used live")
    void settingsAreLive() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(10, 1, 1, 1));
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        host.events.clear();

        engine.tick();
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(1, 1, 2, 1));
        engine.tick();

        assertEquals(2, host.count("click"), "first tick clicked once, second tick clicked twice");
    }

    @Test
    @DisplayName("all ten features exist and are named uniquely")
    void tenFeatures() {
        assertEquals(10, Feature.values().length);

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Feature feature : Feature.values()) {
            assertTrue(ids.add(feature.id()), "duplicate id " + feature.id());
            assertTrue(feature.keyBindingName().startsWith("key.clientinteractions."));
        }

        assertEquals(5, Feature.forChannel(ActionChannel.ATTACK).length);
        assertEquals(5, Feature.forChannel(ActionChannel.USE).length);
    }
}
