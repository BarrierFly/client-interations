package peoplecanfly.clientinteractions.common;

/**
 * The automation modes a channel can be switched to.
 *
 * <p>Every mode is expressed purely in terms of "should the channel fire on this client tick?" so that the
 * whole timing behaviour can be unit tested without a running game.</p>
 *
 * <ul>
 *   <li>{@link #HOLD} - press the button and keep it pressed until the mode is turned off.</li>
 *   <li>{@link #FAST_CLICK} - one click every client tick, i.e. the highest rate the vanilla client can consume.</li>
 *   <li>{@link #PERIODIC_CLICK} - one click every {@code interval} ticks.</li>
 *   <li>{@link #PERIODIC_HOLD} - hold for {@code duration} ticks, then release, every {@code interval} ticks.</li>
 *   <li>{@link #PERIODIC_BURST} - {@code burst} clicks spaced one tick apart, repeating every {@code interval} ticks.</li>
 * </ul>
 */
public enum AutomationMode {
    HOLD("hold"),
    FAST_CLICK("fast_click"),
    PERIODIC_CLICK("periodic_click"),
    PERIODIC_HOLD("periodic_hold"),
    PERIODIC_BURST("periodic_burst");

    private final String id;

    AutomationMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static AutomationMode byId(String id) {
        for (AutomationMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }

        return HOLD;
    }
}
