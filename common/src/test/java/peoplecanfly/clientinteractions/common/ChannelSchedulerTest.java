package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelSchedulerTest {
    private static final AutomationMode HOLD = AutomationMode.HOLD;
    private static final AutomationMode FAST = AutomationMode.FAST_CLICK;
    private static final AutomationMode CLICK = AutomationMode.PERIODIC_CLICK;
    private static final AutomationMode HOLD_CYCLE = AutomationMode.PERIODIC_HOLD;
    private static final AutomationMode BURST = AutomationMode.PERIODIC_BURST;

    private static ChannelSettings settings(int interval, int holdDuration, int perTick, int burstSize) {
        return new ChannelSettings(interval, holdDuration, perTick, burstSize);
    }

    /** Runs {@code ticks} ticks and returns the produced actions. */
    private static List<TickAction> run(ChannelScheduler scheduler, AutomationMode mode,
                                        ChannelSettings settings, int ticks) {
        List<TickAction> out = new ArrayList<>(ticks);

        for (int i = 0; i < ticks; i++) {
            out.add(scheduler.tick(mode, settings));
        }

        return out;
    }

    /**
     * Compacts a run into a readable pattern:
     * {@code H} hold, {@code F} a single click, digits for multi-click ticks,
     * {@code r} a bare release and {@code c} a release followed by one click.
     */
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
        ChannelScheduler scheduler = new ChannelScheduler();

        assertEquals("..........", pattern(run(scheduler, CLICK, settings(3, 1, 1, 1), 10)));
        assertFalse(scheduler.isActive());
    }

    @Test
    @DisplayName("hold keeps the button held on every tick")
    void holdHoldsEveryTick() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("HHHHHH", pattern(run(scheduler, HOLD, settings(20, 10, 1, 3), 6)));
    }

    @Test
    @DisplayName("fast click injects the configured number of clicks on every tick")
    void fastClickUsesPerTick() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("FFFF", pattern(run(scheduler, FAST, settings(20, 10, 1, 3), 4)));

        ChannelScheduler many = new ChannelScheduler();
        many.activate();
        assertEquals("****", pattern(run(many, FAST, settings(20, 10, 12, 3), 4)));
    }

    @Test
    @DisplayName("per-tick count is clamped to 1..100")
    void perTickIsClamped() {
        assertEquals(1, settings(20, 10, 0, 1).perTick());
        assertEquals(1, settings(20, 10, -5, 1).perTick());

        ChannelSettings high = settings(20, 10, 1000, 1);
        assertEquals(ChannelSettings.MAX_PER_TICK, high.perTick());

        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();
        assertEquals(ChannelSettings.MAX_PER_TICK, scheduler.tick(FAST, high).clicks());
    }

    @Test
    @DisplayName("periodic click fires immediately and then once per interval")
    void periodicClickRespectsInterval() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("F..F..F..F", pattern(run(scheduler, CLICK, settings(3, 1, 1, 1), 10)));
    }

    @Test
    @DisplayName("periodic click repeats the per-tick click count on every firing tick")
    void periodicClickUsesPerTick() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("3..3..", pattern(run(scheduler, CLICK, settings(3, 1, 3, 1), 6)));
    }

    @Test
    @DisplayName("periodic click with interval 1 behaves like fast click")
    void periodicClickIntervalOne() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("FFFF", pattern(run(scheduler, CLICK, settings(1, 1, 1, 1), 4)));
    }

    @Test
    @DisplayName("periodic hold holds for the configured duration, then releases")
    void periodicHoldRespectsDuration() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        // first tick is a normal hold, the release is reported on the first tick after the hold ends
        assertEquals("HHHHr.....HHHHr.....", pattern(run(scheduler, HOLD_CYCLE, settings(10, 4, 1, 1), 20)));
    }

    @Test
    @DisplayName("periodic hold with a full length duration never releases")
    void periodicHoldFullDurationNeverReleases() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("HHHHHHHH", pattern(run(scheduler, HOLD_CYCLE, settings(4, 4, 1, 1), 8)));
    }

    @Test
    @DisplayName("a hold duration longer than the interval is clamped when used, not when stored")
    void holdDurationClampedToInterval() {
        ChannelSettings value = settings(5, 99, 1, 1);
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        // clamping happens against the interval while ticking, so a stored 99 still means "hold the whole cycle"
        assertEquals("HHHHH", pattern(run(scheduler, HOLD_CYCLE, value, 5)));
        assertEquals(99, value.holdDuration());
        assertEquals(5, ChannelSettings.clampHoldDuration(value.holdDuration(), value.interval()));
    }

    @Test
    @DisplayName("periodic burst fires the requested number of clicks then idles")
    void periodicBurstRespectsBurstSize() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("FF....FF....", pattern(run(scheduler, BURST, settings(6, 1, 1, 2), 12)));
    }

    @Test
    @DisplayName("a burst longer than the interval fires on every tick of the cycle")
    void burstLongerThanInterval() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        assertEquals("FFF", pattern(run(scheduler, BURST, settings(3, 1, 1, 99), 3)));
    }

    @Test
    @DisplayName("interval is clamped to at least one tick")
    void intervalClampedToOne() {
        assertEquals(1, settings(0, 5, 1, 1).interval());
        assertEquals(1, settings(-7, 5, 1, 1).interval());
    }

    @Test
    @DisplayName("deactivate stops output and resets the phase")
    void deactivateResetsPhase() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();
        run(scheduler, CLICK, settings(5, 1, 1, 1), 3);
        scheduler.deactivate();

        assertEquals(".", pattern(run(scheduler, CLICK, settings(5, 1, 1, 1), 1)));

        scheduler.activate();
        assertEquals(0, scheduler.elapsedTicks());
        assertEquals("F", pattern(run(scheduler, CLICK, settings(5, 1, 1, 1), 1)));
    }

    @Test
    @DisplayName("reactivating restarts the cycle from tick zero")
    void reactivateRestarts() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();
        run(scheduler, CLICK, settings(4, 1, 1, 1), 2);
        scheduler.activate();

        assertEquals(0, scheduler.elapsedTicks());
        assertEquals("F...", pattern(run(scheduler, CLICK, settings(4, 1, 1, 1), 4)));
    }

    @Test
    @DisplayName("switching modes takes effect on the next tick without a reset")
    void switchingModes() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();
        assertEquals("H", pattern(run(scheduler, HOLD, settings(5, 1, 1, 1), 1)));
        // leaving HOLD releases the button once, and the periodic click fires on the first tick of the cycle
        assertEquals("c", pattern(run(scheduler, FAST, settings(5, 1, 1, 1), 1)));
        assertEquals("F", pattern(run(scheduler, FAST, settings(5, 1, 1, 1), 1)));
        assertEquals(".", pattern(run(scheduler, CLICK, settings(5, 1, 1, 1), 1)));
    }

    @Test
    @DisplayName("switching from hold to click releases the button exactly once")
    void holdToClickReleasesOnce() {
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();
        run(scheduler, HOLD, settings(5, 1, 1, 1), 2);
        assertEquals("r..", pattern(run(scheduler, CLICK, settings(5, 1, 1, 1), 3)));
    }

    @Test
    @DisplayName("long runs stay in phase and never drift")
    void noDriftOverLongRun() {
        ChannelSettings value = settings(7, 3, 1, 1);
        ChannelScheduler scheduler = new ChannelScheduler();
        scheduler.activate();

        List<TickAction> actions = run(scheduler, HOLD_CYCLE, value, 7000);
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
    @DisplayName("a state is active only between activate and deactivate")
    void activeFlag() {
        ChannelScheduler scheduler = new ChannelScheduler();
        assertFalse(scheduler.isActive());

        scheduler.activate();
        assertTrue(scheduler.isActive());

        scheduler.deactivate();
        assertFalse(scheduler.isActive());
    }
}
