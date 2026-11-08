package peoplecanfly.clientinteractions.common;

/**
 * What one feature should do on one client tick.
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

    public static TickAction of(int clicks, boolean hold, boolean release) {
        return clicks <= 0 && !hold && !release ? NONE : new TickAction(Math.max(0, clicks), hold, release);
    }

    /** How many discrete clicks to inject on this tick. */
    public int clicks() {
        return this.clicks;
    }

    /** Whether the button should be reported as held during this tick. */
    public boolean hold() {
        return this.hold;
    }

    /** Whether the button has to be released before this tick's clicks. */
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
