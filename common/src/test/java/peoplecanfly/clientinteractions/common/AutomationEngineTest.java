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
            engine.setSettings(feature, new FeatureSettings(20, 10, 3));
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
    @DisplayName("activating a button-driving feature switches off the others, but never the multiplier")
    void featuresOfOneChannelAreMutuallyExclusive() {
        AutomationEngine engine = engine(new RecordingHost());

        // Fast click multiplies the channel's clicks instead of driving the button, so it is not part of
        // the exclusion group: it stays on across every activation in this loop.
        engine.activate(Feature.ATTACK_FAST_CLICK);

        for (Feature feature : Feature.forChannel(ActionChannel.ATTACK)) {
            if (feature.shape() == Feature.Shape.FAST_CLICK) {
                continue;
            }

            engine.activate(feature);

            for (Feature other : Feature.forChannel(ActionChannel.ATTACK)) {
                if (other.shape() == Feature.Shape.FAST_CLICK) {
                    assertTrue(engine.isActive(other), "the multiplier survives " + feature.id());
                    continue;
                }

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
    @DisplayName("a release is sent when switching between button-driving features of one channel")
    void switchingReleasesTheOldButton() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.activate(Feature.ATTACK_HOLD);
        engine.tick();
        host.events.clear();

        engine.activate(Feature.ATTACK_PERIODIC_CLICK);

        assertEquals(1, host.count("attack:stop"), "the hold must be released before the new feature runs");
    }

    @Test
    @DisplayName("only active features tick, and each tick advances by exactly one")
    void tickingOnlyAdvancesActiveFeatures() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        host.events.clear();

        engine.tick();
        engine.tick();
        engine.tick();

        // Three clicks over three ticks, not three in one tick.
        assertEquals(3, host.count("attack:click1"));
        assertEquals(3, engine.scheduler(Feature.ATTACK_PERIODIC_CLICK).elapsedTicks());
        assertEquals(0, engine.scheduler(Feature.ATTACK_HOLD).elapsedTicks());
    }

    @Test
    @DisplayName("fast click produces no input of its own while ticking")
    void fastClickEmitsNothingOnTick() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.activate(Feature.ATTACK_FAST_CLICK);
        host.events.clear();

        for (int i = 0; i < 10; i++) {
            engine.tick();
        }

        assertEquals(0, host.count("click"), "fast click must not click by itself");
        assertEquals(0, host.count("hold"), "fast click must not hold");
    }

    @Test
    @DisplayName("fast click multiplies a channel's own clicks on the same tick")
    void fastClickMultipliesTheChannelsClicks() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(1, 1, 1));
        engine.setSettings(Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 4));

        // Periodic click asks for one click per tick; with fast click on it must ask for four.
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        engine.activate(Feature.ATTACK_FAST_CLICK);
        host.events.clear();

        engine.tick();

        assertEquals(1, host.count("attack:click4"), "one tick must carry all four clicks");
        assertEquals(0, host.count("attack:click1"), "the single-click form must not be used");
        assertEquals(1, host.events.size(), "all of them are one apply call, on one tick");
    }

    @Test
    @DisplayName("fast click runs alongside the channel's own feature instead of excluding it")
    void fastClickDoesNotExcludeTheChannelsFeature() {
        AutomationEngine engine = engine(new RecordingHost());

        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        engine.activate(Feature.ATTACK_FAST_CLICK);

        assertTrue(engine.isActive(Feature.ATTACK_PERIODIC_CLICK),
                "activating fast click must leave periodic click alone");
        assertTrue(engine.isActive(Feature.ATTACK_FAST_CLICK));

        engine.activate(Feature.ATTACK_HOLD);
        assertTrue(engine.isActive(Feature.ATTACK_FAST_CLICK),
                "activating hold must leave fast click alone");
        assertFalse(engine.isActive(Feature.ATTACK_PERIODIC_CLICK),
                "the two button-driving features still exclude each other");
    }

    @Test
    @DisplayName("stopping fast click leaves the channel's own feature running")
    void stoppingFastClickKeepsTheChannelsFeature() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_HOLD, new FeatureSettings(20, 10, 1));
        engine.activate(Feature.ATTACK_HOLD);
        engine.activate(Feature.ATTACK_FAST_CLICK);
        engine.tick();
        host.events.clear();

        engine.stop(Feature.ATTACK_FAST_CLICK);

        assertTrue(engine.isActive(Feature.ATTACK_HOLD), "the hold must survive stopping fast click");
        assertEquals(0, host.count("attack:stop"),
                "stopping a multiplier must not release the button a hold is holding");

        engine.tick();
        assertEquals(1, host.count("attack:hold"), "the hold keeps driving the button");
    }

    @Test
    @DisplayName("the multiplier applies per tick and stops with the feature")
    void multiplierStopsWithTheFeature() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(1, 1, 1));
        engine.setSettings(Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 3));
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        engine.activate(Feature.ATTACK_FAST_CLICK);
        host.events.clear();

        engine.tick();
        engine.tick();
        assertEquals(2, host.count("attack:click3"));
        assertEquals(0, host.count("attack:click1"));

        engine.stop(Feature.ATTACK_FAST_CLICK);
        host.events.clear();
        engine.tick();
        assertEquals(1, host.count("attack:click1"), "off, the channel clicks once per tick again");
    }

    @Test
    @DisplayName("fast click multiplies the player's click and nothing else changes it")
    void fastClickMultiplier() {
        AutomationEngine engine = engine(new RecordingHost());

        // Off: the player's click stays one click.
        assertEquals(1, engine.playerClickCount(ActionChannel.ATTACK));
        assertEquals(1, engine.playerClickCount(ActionChannel.USE));

        engine.setSettings(Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 7));
        engine.activate(Feature.ATTACK_FAST_CLICK);

        assertEquals(7, engine.playerClickCount(ActionChannel.ATTACK));
        // The other channel is unaffected by an attack feature.
        assertEquals(1, engine.playerClickCount(ActionChannel.USE));
    }

    @Test
    @DisplayName("the master switch also disables the fast-click multiplier")
    void fastClickRespectsMasterSwitch() {
        AutomationEngine engine = engine(new RecordingHost());
        engine.setSettings(Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 5));
        engine.activate(Feature.ATTACK_FAST_CLICK);

        assertEquals(5, engine.playerClickCount(ActionChannel.ATTACK));

        engine.setEnabled(false);
        assertEquals(1, engine.playerClickCount(ActionChannel.ATTACK));
    }

    @Test
    @DisplayName("the master switch suppresses input but keeps the phase")
    void masterSwitchKeepsPhase() {
        RecordingHost host = new RecordingHost();
        AutomationEngine engine = engine(host);
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(4, 1, 1));
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
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(10, 1, 1));
        engine.activate(Feature.ATTACK_PERIODIC_CLICK);
        host.events.clear();

        engine.tick();
        engine.setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(1, 1, 2));
        engine.tick();

        assertEquals(2, host.count("click"), "first tick clicked once, second tick clicked once more");
    }

    @Test
    @DisplayName("all eight features exist, four per button, with unique ids")
    void eightFeatures() {
        assertEquals(8, Feature.values().length);

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Feature feature : Feature.values()) {
            assertTrue(ids.add(feature.id()), "duplicate id " + feature.id());
            assertTrue(feature.keyBindingName().startsWith("key.clientinteractions."));
        }

        assertEquals(4, Feature.forChannel(ActionChannel.ATTACK).length);
        assertEquals(4, Feature.forChannel(ActionChannel.USE).length);
    }

    @Test
    @DisplayName("only fast click is a multiplier; every other feature acts by itself")
    void onlyFastClickReactsToThePlayer() {
        for (Feature feature : Feature.values()) {
            if (feature.shape() == Feature.Shape.FAST_CLICK) {
                assertFalse(feature.actsOnItsOwn(), feature.id());
            } else {
                assertTrue(feature.actsOnItsOwn(), feature.id());
            }
        }
    }
}
