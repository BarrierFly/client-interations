package peoplecanfly.clientinteractions.common;

/**
 * Human readable strings for the HUD, the config screen and chat messages.
 *
 * <p>Kept here rather than in Minecraft translation keys so every supported version shares one
 * implementation and the wording is unit tested.</p>
 */
public final class StatusText {
    public static final String MOD_NAME = "Client Interactions";

    private StatusText() {
    }

    /** Only the values a feature's shape actually reads. */
    public static String settingsSummary(Feature feature, FeatureSettings settings) {
        switch (feature.shape()) {
            case HOLD:
                return "";
            case FAST_CLICK:
                return "x" + settings.count() + " per your click";
            case PERIODIC_CLICK:
                return settings.count() + " clicks every " + settings.interval() + "t";
            case PERIODIC_HOLD:
                return settings.duration() + "t every " + settings.interval() + "t";
            default:
                return "";
        }
    }

    /** A HUD line, e.g. {@code Attack: Periodic hold  10t every 20t}. */
    public static String featureLine(Feature feature, FeatureSettings settings) {
        String summary = settingsSummary(feature, settings);

        return summary.isEmpty() ? feature.label() : feature.label() + "  " + summary;
    }

    /** An action bar message for a toggle, e.g. {@code Attack: Periodic hold on}. */
    public static String toggleMessage(Feature feature, boolean on) {
        return feature.label() + (on ? " on" : " off");
    }

    /**
     * Shown when a hotkey is pressed while the master switch is off.
     *
     * <p>Without this the feature appeared to switch on - the message, the HUD and the config row all said
     * so - while nothing happened at all, because the master switch suppresses every action. That is a
     * contradiction the player cannot resolve from the screen, so the press is refused and explained.</p>
     */
    public static String masterOffMessage() {
        return "Automation is off - turn it on in the config first";
    }

    /** The HUD line used instead of the feature list while the master switch is off. */
    public static String pausedLine() {
        return "Automation: off";
    }

    /** The page title for one button, e.g. {@code Attack}. */
    public static String channelTitle(ActionChannel channel) {
        return channel == ActionChannel.ATTACK ? "Attack" : "Use";
    }

    /** The hint under a page title explaining what the rows do. */
    public static String screenHint() {
        return "Name toggles - key rebinds - numbers are typed";
    }

    /**
     * The prompt shown while waiting for a key, e.g. {@code Press the key for Attack: Hold}.
     *
     * @param feature the feature being rebound, or {@code null} for the config-opening combination
     */
    public static String keyPrompt(Feature feature) {
        return feature == null
                ? "Press the keys that open this screen"
                : "Press the key for " + feature.label();
    }
}
