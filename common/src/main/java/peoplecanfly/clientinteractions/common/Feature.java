package peoplecanfly.clientinteractions.common;

/**
 * The eight independently switchable features: four behaviours for each of the two buttons.
 *
 * <p>Each feature is configured and toggled on its own. Only one feature per {@link ActionChannel}
 * may be active at a time; see {@link AutomationEngine#activate}.</p>
 */
public enum Feature {
    ATTACK_HOLD(ActionChannel.ATTACK, "attack_hold", "Attack: Hold"),
    ATTACK_FAST_CLICK(ActionChannel.ATTACK, "attack_fast_click", "Attack: Fast click"),
    ATTACK_PERIODIC_CLICK(ActionChannel.ATTACK, "attack_periodic_click", "Attack: Periodic click"),
    ATTACK_PERIODIC_HOLD(ActionChannel.ATTACK, "attack_periodic_hold", "Attack: Periodic hold"),

    USE_HOLD(ActionChannel.USE, "use_hold", "Use: Hold"),
    USE_FAST_CLICK(ActionChannel.USE, "use_fast_click", "Use: Fast click"),
    USE_PERIODIC_CLICK(ActionChannel.USE, "use_periodic_click", "Use: Periodic click"),
    USE_PERIODIC_HOLD(ActionChannel.USE, "use_periodic_hold", "Use: Periodic hold");

    /** The behaviour a feature implements; several features share one shape. */
    public enum Shape {
        /** Keeps the button pressed until switched off. Runs on its own. */
        HOLD,
        /**
         * Multiplies the player's own clicks.
         *
         * <p>This is the one shape that does not act by itself: it only fires when the player makes the
         * corresponding mouse click, and turns one physical click into {@code count} clicks.</p>
         */
        FAST_CLICK,
        /** Clicks {@code count} times every {@code interval} ticks, on its own. */
        PERIODIC_CLICK,
        /** Holds for {@code duration} ticks every {@code interval} ticks, on its own. */
        PERIODIC_HOLD
    }

    private final ActionChannel channel;
    private final String id;
    private final String label;
    private final Shape shape;

    Feature(ActionChannel channel, String id, String label) {
        this.channel = channel;
        this.id = id;
        this.label = label;
        this.shape = shapeFromId(id);
    }

    private static Shape shapeFromId(String id) {
        // Order matters: "attack_periodic_hold" also ends with "_hold", so the longer suffixes have to
        // be tested before the shorter ones.
        if (id.endsWith("_periodic_hold")) {
            return Shape.PERIODIC_HOLD;
        }

        if (id.endsWith("_periodic_click")) {
            return Shape.PERIODIC_CLICK;
        }

        if (id.endsWith("_fast_click")) {
            return Shape.FAST_CLICK;
        }

        if (id.endsWith("_hold")) {
            return Shape.HOLD;
        }

        throw new IllegalArgumentException("Unknown feature id: " + id);
    }

    public ActionChannel channel() {
        return this.channel;
    }

    /** Stable identifier used in the config file and for the key binding name. */
    public String id() {
        return this.id;
    }

    /** Human readable name, e.g. {@code Attack: Periodic hold}. */
    public String label() {
        return this.label;
    }

    public Shape shape() {
        return this.shape;
    }

    /** True when the feature acts by itself, rather than reacting to the player's own clicks. */
    public boolean actsOnItsOwn() {
        return this.shape != Shape.FAST_CLICK;
    }

    /** The key binding name, e.g. {@code key.clientinteractions.attack_hold}. */
    public String keyBindingName() {
        return "key.clientinteractions." + this.id;
    }

    public static Feature[] forChannel(ActionChannel channel) {
        return java.util.Arrays.stream(values())
                .filter(feature -> feature.channel == channel)
                .toArray(Feature[]::new);
    }

    /** The fast-click feature of a channel, or {@code null} if there is none. */
    public static Feature fastClickFor(ActionChannel channel) {
        for (Feature feature : values()) {
            if (feature.channel == channel && feature.shape == Shape.FAST_CLICK) {
                return feature;
            }
        }

        return null;
    }
}
