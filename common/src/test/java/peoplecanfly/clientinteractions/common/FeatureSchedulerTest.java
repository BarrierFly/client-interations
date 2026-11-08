package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureSchedulerTest {
    private static FeatureSettings settings(int interval, int duration, int perTick, int burst) {
        return new FeatureSettings(interval, duration, perTick, burst);
    }

    private static List<TickAction> run(FeatureScheduler scheduler, Feature feature,
                                        FeatureSettings settings, int ticks) {
        List<TickAction> out = new ArrayList<>(ticks);

        for (int i = 0; i < ticks; i++) {
            out.add(scheduler.tick(feature, settings));
        }

        return out;
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

        assertEquals("....", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 1, 1), 4)));
        assertFalse(scheduler.isActive());
    }

    @Test
    @DisplayName("hold keeps the button held on every tick")
    void holdHoldsEveryTick() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("HHHHHH", pattern(run(scheduler, Feature.ATTACK_HOLD, settings(20, 10, 1, 3), 6)));
    }

    @Test
    @DisplayName("fast click injects perTick clicks on every tick")
    void fastClickUsesPerTick() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("FFFF", pattern(run(scheduler, Feature.USE_FAST_CLICK, settings(20, 10, 1, 3), 4)));

        FeatureScheduler many = new FeatureScheduler();
        many.activate();
        assertEquals("****", pattern(run(many, Feature.USE_FAST_CLICK, settings(20, 10, 12, 3), 4)));
    }

    @Test
    @DisplayName("periodic click fires immediately then once per interval")
    void periodicClickRespectsInterval() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("F..F..F..F", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 1, 1), 10)));
    }

    @Test
    @DisplayName("periodic click repeats the per-tick count")
    void periodicClickUsesPerTick() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("3..3..", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(3, 1, 3, 1), 6)));
    }

    @Test
    @DisplayName("periodic hold holds for the duration, then releases once")
    void periodicHoldRespectsDuration() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("HHHHr.....HHHHr.....",
                pattern(run(scheduler, Feature.ATTACK_PERIODIC_HOLD, settings(10, 4, 1, 1), 20)));
    }

    @Test
    @DisplayName("a duration at least as long as the interval never releases")
    void periodicHoldFullDurationNeverReleases() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("HHHHHHHH", pattern(run(scheduler, Feature.ATTACK_PERIODIC_HOLD, settings(4, 4, 1, 1), 8)));
        assertEquals("HHHH", pattern(run(newActive(), Feature.ATTACK_PERIODIC_HOLD, settings(4, 99, 1, 1), 4)));
    }

    private static FeatureScheduler newActive() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();
        return scheduler;
    }

    @Test
    @DisplayName("periodic burst fires burstSize clicks then idles")
    void periodicBurstRespectsBurstSize() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("FF....FF....", pattern(run(scheduler, Feature.USE_PERIODIC_BURST, settings(6, 1, 1, 2), 12)));
    }

    @Test
    @DisplayName("a burst longer than the interval fires every tick")
    void burstLongerThanInterval() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

        assertEquals("FFF", pattern(run(scheduler, Feature.USE_PERIODIC_BURST, settings(3, 1, 1, 99), 3)));
    }

    @Test
    @DisplayName("interval is clamped to 1..200 and perTick to 1..100")
    void valuesAreClamped() {
        assertEquals(1, settings(0, 5, 1, 1).interval());
        assertEquals(FeatureSettings.MAX_INTERVAL, settings(9999, 5, 1, 1).interval());
        assertEquals(1, settings(20, 10, 0, 1).perTick());
        assertEquals(FeatureSettings.MAX_PER_TICK, settings(20, 10, 9999, 1).perTick());
    }

    @Test
    @DisplayName("deactivate stops output and resets the phase")
    void deactivateResetsPhase() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();
        run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1, 1), 3);
        scheduler.deactivate();

        assertEquals(".", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1, 1), 1)));

        scheduler.activate();
        assertEquals(0, scheduler.elapsedTicks());
        assertEquals("F", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1, 1), 1)));
    }

    @Test
    @DisplayName("leaving hold releases once")
    void holdToClickReleasesOnce() {
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();
        run(scheduler, Feature.ATTACK_HOLD, settings(5, 1, 1, 1), 2);
        assertEquals("r..", pattern(run(scheduler, Feature.ATTACK_PERIODIC_CLICK, settings(5, 1, 1, 1), 3)));
    }

    @Test
    @DisplayName("long runs stay in phase and never drift")
    void noDriftOverLongRun() {
        FeatureSettings value = settings(7, 3, 1, 1);
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.activate();

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
}
