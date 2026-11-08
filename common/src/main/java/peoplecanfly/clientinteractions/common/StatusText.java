package peoplecanfly.clientinteractions.common;

/**
 * Builds the human readable strings shown by the HUD, the config screen and the chat.
 *
 * <p>Implemented here rather than with Minecraft's translation machinery so that every supported
 * Minecraft version shares exactly one implementation and the wording can be unit tested.</p>
 */
public final class StatusText {
    public static final String MOD_NAME = "Client Interactions";

    private StatusText() {
    }

    /** Short label of a channel, used by the HUD. */
    public static String channelLabel(ActionChannel channel) {
        return channel == ActionChannel.ATTACK ? "Attack" : "Use";
    }

    /** Short label of a mode. */
    public static String modeLabel(AutomationMode mode) {
        switch (mode) {
            case HOLD:
                return "Hold";
            case FAST_CLICK:
                return "Fast click";
            case PERIODIC_CLICK:
                return "Periodic click";
            case PERIODIC_HOLD:
                return "Periodic hold";
            case PERIODIC_BURST:
                return "Periodic burst";
            default:
                return mode.id();
        }
    }

    /** A full line for one channel, e.g. {@code Attack: Periodic hold (20t, held 10t)}. */
    public static String channelLine(ActionChannel channel, AutomationMode mode, ChannelSettings settings) {
        return channelLabel(channel) + ": " + modeLabel(mode) + " " + settingsSummary(mode, settings);
    }

    /** The numbers of a mode, listing only the values the mode actually uses. */
    public static String settingsSummary(AutomationMode mode, ChannelSettings settings) {
        switch (mode) {
            case HOLD:
                return "(continuous)";
            case FAST_CLICK:
                return "(x" + settings.perTick() + " per tick)";
            case PERIODIC_CLICK:
                return "(every " + settings.interval() + "t, x" + settings.perTick() + ")";
            case PERIODIC_HOLD:
                return "(every " + settings.interval() + "t, held " + settings.holdDuration() + "t)";
            case PERIODIC_BURST:
                return "(every " + settings.interval() + "t, " + settings.burstSize() + " clicks)";
            default:
                return "";
        }
    }

    /** One line per channel describing the whole configuration, used by the HUD. */
    public static String hudLine(ModConfig config, ActionChannel channel) {
        return channelLine(channel, config.mode(channel), config.settings(channel));
    }

    /** A chat friendly message for a toggle, e.g. {@code Attack: Periodic click on}. */
    public static String toggleMessage(ActionChannel channel, boolean on) {
        return channelLabel(channel) + ": " + (on ? "on" : "off");
    }

    /** A machine readable description used by the debug log. */
    public static String debugLine(ActionChannel channel, AutomationMode mode, ChannelSettings settings) {
        return channel.id() + '=' + mode.id() + ':' + settings;
    }
}
