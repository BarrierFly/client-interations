package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureSchedulerTest {
    private static FeatureSettings settings(int interval, int duration, int count) {
        return new FeatureSettings(interval, duration, count);
    }

    private static List<TickAction> run(FeatureScheduler scheduler, Feature feature,
                                        FeatureSettings settings, int ticks) {
        List<TickAction> out = new ArrayList<>(ticks);

        for (int i = 0; i < ticks; i++) {
            out.add(scheduler.tick(feature, settings));
        }

        return out;
    }

    private static FeatureScheduler newActive() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();
        return scheduler;
    }

    /** {@code H} hold, {@code F} one click, digit multi-click, {@code r} release, {@code c} release+click. */
    private static String pattern(List<TickAction> actions) {
        StringBuilder builder = new StringBuilder(actions.size());

        for (TickAction action : actions) {
            if (action.releases()) {
                if (action.clicks() == 0) {
                    builder.append('r');
                } else if (action.clicks() == 1) {
                    builder.append('c');
                } else {
                    builder.append(action.clicks() > 9 ? '#' : (char) ('a' + action.clicks()));
                }
            } else if (action.hold()) {
                builder.append('H');
            } else if (action.clicks() > 1) {
                builder.append(action.clicks() > 9 ? '*' : (char) ('0' + action.clicks()));
            } else if (action.clicks() == 1) {
                builder.append('F');
            } else {
                builder.append('.');
            }
        }

        return builder.toString();
    }

    @Test
    @DisplayName("an inactive scheduler produces nothing")
    void inactiveProducesNothing() {
        FeatureScheduler scheduler = new FeatureScheduler();

        assertEquals("....", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 1), 4)));
        assertFalse(scheduler.isActive());
    }

    @Test
    @DisplayName("hold keeps the button held on every tick")
    void holdHoldsEveryTick() {
        FeatureScheduler scheduler = newActive();

        assertEquals("HHHHHH", pattern(run(scheduler, Feature.ATTACK_HOLD, settings(20, 10, 3), 6)));
    }

    @Test
    @DisplayName("fast click never acts on its own")
    void fastClickDoesNothingByItself() {
        FeatureScheduler scheduler = newActive();

        // The whole point of the fast-click semantics: no input at all unless the player clicks.
        assertEquals("........", pattern(run(scheduler, Feature.USE_FAST_CLICK, settings(20, 10, 5), 8)));
        assertEquals("..........", pattern(run(scheduler, Feature.ATTACK_FAST_CLICK, settings(20, 10, 9), 10)));
    }

    @Test
    @DisplayName("fast click reports its multiplier only while active")
    void fastClickMultiplier() {
        FeatureScheduler scheduler = new FeatureScheduler();

        assertEquals(0, scheduler.fastClickCount(Feature.ATTACK_FAST_CLICK, settings(20, 10, 5)),
                "off means the player's click is left alone");

        scheduler.activate();

        assertEquals(5, scheduler.fastClickCount(Feature.ATTACK_FAST_CLICK, settings(20, 10, 5)));
        assertEquals(1, scheduler.fastClickCount(Feature.ATTACK_FAST_CLICK, settings(20, 10, 1)));

        // Only the fast-click shape answers; a different feature must not be treated as one.
        assertEquals(0, scheduler.fastClickCount(Feature.ATTACK_HOLD, settings(20, 10, 5)));

        scheduler.deactivate();
        assertEquals(0, scheduler.fastClickCount(Feature.ATTACK_FAST_CLICK, settings(20, 10, 5)));
    }

    @Test
    @DisplayName("periodic click fires immediately then once per interval")
    void periodicClickRespectsInterval() {
        FeatureScheduler scheduler = newActive();

        assertEquals("F..F..F..F", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 1), 10)));
    }

    @Test
    @DisplayName("periodic click spreads its clicks over consecutive ticks")
    void periodicClickSpreadsClicks() {
        // Three clicks every six ticks must be three separate ticks, not three calls in one tick: vanilla
        // resets the attack strength on the first click, so a same-tick burst would be swallowed.
        assertEquals("FFF...FFF...",
                pattern(run(newActive(), Feature.ATTACK_PERIODIC_CLICK, settings(6, 1, 3), 12)));
    }

    @Test
    @DisplayName("a click count larger than the interval fires every tick")
    void periodicClickCountLongerThanInterval() {
        assertEquals("FFF", pattern(run(newActive(), Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 99), 3)));
    }

    @Test
    @DisplayName("periodic hold holds for the duration, then releases once")
    void periodicHoldRespectsDuration() {
        assertEquals("HHHHr.....HHHHr.....",
                pattern(run(newActive(), Feature.ATTACK_PERIODIC_HOLD, settings(10, 4, 1), 20)));
    }

    @Test
    @DisplayName("a duration at least as long as the interval never releases")
    void periodicHoldFullDurationNeverReleases() {
        assertEquals("HHHHHHHH", pattern(run(newActive(), Feature.ATTACK_PERIODIC_HOLD, settings(4, 4, 1), 8)));
        assertEquals("HHHH", pattern(run(newActive(), Feature.ATTACK_PERIODIC_HOLD, settings(4, 99, 1), 4)));
    }

    @Test
    @DisplayName("interval is clamped to 1..200 and count to 1..200")
    void valuesAreClamped() {
        assertEquals(1, settings(0, 5, 1).interval());
        assertEquals(FeatureSettings.MAX_INTERVAL, settings(9999, 5, 1).interval());
        assertEquals(1, settings(20, 10, 0).count());
        assertEquals(FeatureSettings.MAX_COUNT, settings(20, 10, 9999).count());
    }

    @Test
    @DisplayName("deactivate stops output and resets the phase")
    void deactivateResetsPhase() {
        FeatureScheduler scheduler = newActive();
        run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1), 3);
        scheduler.deactivate();

        assertEquals(".", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1), 1)));

        scheduler.activate();
        assertEquals(0, scheduler.elapsedTicks());
        assertEquals("F", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1), 1)));
    }

    @Test
    @DisplayName("leaving hold releases once")
    void holdToClickReleasesOnce() {
        FeatureScheduler scheduler = newActive();
        run(scheduler, Feature.ATTACK_HOLD, settings(5, 1, 1), 2);
        assertEquals("r..", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1), 3)));
    }

    @Test
    @DisplayName("long runs stay in phase and never drift")
    void noDriftOverLongRun() {
        FeatureSettings value = settings(7, 3, 1);
        FeatureScheduler scheduler = newActive();

        List<TickAction> actions = run(scheduler, Feature.ATTACK_PERIODIC_HOLD, value, 7000);
        int held = 0;

        for (int i = 0; i < actions.size(); i++) {
            boolean expectedHold = i % 7 < 3;
            assertEquals(expectedHold, actions.get(i).hold(), "tick " + i);
            assertEquals(0, actions.get(i).clicks(), "tick " + i);

            if (expectedHold) {
                held++;
            }
        }

        assertEquals(3000, held);
    }

    @Test
    @DisplayName("activeFlag tracks activate and deactivate")
    void activeFlag() {
        FeatureScheduler scheduler = new FeatureScheduler();
        assertFalse(scheduler.isActive());

        scheduler.activate();
        assertTrue(scheduler.isActive());

        scheduler.deactivate();
        assertFalse(scheduler.isActive());
    }

    @Test
    @DisplayName("the old four-value config shape is read as one click count")
    void legacySettings() {
        FeatureSettings legacy = FeatureSettings.legacy(20, 10, 1, 5);

        // perTick and burst both meant "clicks per interval"; the larger one is kept.
        assertEquals(5, legacy.count());
        assertEquals(20, legacy.interval());
    }
}
