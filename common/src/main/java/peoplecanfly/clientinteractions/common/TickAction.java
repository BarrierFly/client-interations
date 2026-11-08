package peoplecanfly.clientinteractions.common;

/**
 * A single output step produced by {@link ChannelScheduler} for one client tick.
 *
 * <p>{@link #clicks()} is the number of discrete clicks the channel should perform on this tick,
 * {@link #hold()} asks for the button to stay pressed for the whole tick, and {@link #releases()}
 * asks for a release at the start of the tick (used between two periodic hold onsets).</p>
 */
public final class TickAction {
    private static final TickAction NONE = new TickAction(0, false, false);

    private final int clicks;
    private final boolean hold;
    private final boolean release;

    private TickAction(int clicks, boolean hold, boolean release) {
        this.clicks = clicks;
        this.hold = hold;
        this.release = release;
    }

    public static TickAction none() {
        return NONE;
    }

    public static TickAction clicks(int count) {
        return count <= 0 ? NONE : new TickAction(count, false, false);
    }

    public static TickAction hold(boolean held) {
        return held ? new TickAction(0, true, false) : NONE;
    }

    public static TickAction release() {
        return new TickAction(0, false, true);
    }

    public static TickAction of(int clicks, boolean hold, boolean release) {
        return clicks <= 0 && !hold && !release ? NONE : new TickAction(Math.max(0, clicks), hold, release);
    }

    /** Number of discrete clicks to inject on this tick. */
    public int clicks() {
        return this.clicks;
    }

    /** Whether any click has to be injected. */
    public boolean fire() {
        return this.clicks > 0;
    }

    /** The button should be reported as held during this tick. */
    public boolean hold() {
        return this.hold;
    }

    /** The button should be explicitly released before any click of this tick. */
    public boolean releases() {
        return this.release;
    }

    public boolean isNone() {
        return this.clicks == 0 && !this.hold && !this.release;
    }

    @Override
    public String toString() {
        return "TickAction[clicks=" + this.clicks + ", hold=" + this.hold + ", release=" + this.release + ']';
    }
}
