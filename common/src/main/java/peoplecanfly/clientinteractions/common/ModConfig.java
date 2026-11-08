package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * The persisted user configuration.
 *
 * <p>Pure data with validation only, so that it can be unit tested. The version specific module is
 * responsible for reading and writing it as JSON.</p>
 */
public final class ModConfig {
    public static final String KEY_CATEGORY = "key.categories.clientinteractions";

    private final Map<ActionChannel, AutomationMode> modes = new EnumMap<>(ActionChannel.class);
    private final Map<ActionChannel, ChannelSettings> settings = new EnumMap<>(ActionChannel.class);
    private boolean enabled = true;
    private boolean hudEnabled = true;
    private boolean showMessages = true;

    public ModConfig() {
        for (ActionChannel channel : ActionChannel.values()) {
            this.modes.put(channel, AutomationMode.HOLD);
            this.settings.put(channel, ChannelSettings.defaults());
        }
    }

    public AutomationMode mode(ActionChannel channel) {
        return this.modes.get(channel);
    }

    public void setMode(ActionChannel channel, AutomationMode mode) {
        this.modes.put(channel, mode == null ? AutomationMode.HOLD : mode);
    }

    public ChannelSettings settings(ActionChannel channel) {
        return this.settings.get(channel);
    }

    public void setSettings(ActionChannel channel, ChannelSettings value) {
        this.settings.put(channel, value == null ? ChannelSettings.defaults() : value);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHudEnabled() {
        return this.hudEnabled;
    }

    public void setHudEnabled(boolean hudEnabled) {
        this.hudEnabled = hudEnabled;
    }

    public boolean showMessages() {
        return this.showMessages;
    }

    public void setShowMessages(boolean showMessages) {
        this.showMessages = showMessages;
    }
}
