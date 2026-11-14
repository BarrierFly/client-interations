package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persisted configuration: one settings block per feature, one hotkey per feature, plus four global
 * switches.
 *
 * <p>Features default to off and unbound, and the feature's on/off state is runtime only - it is not
 * saved, so nothing starts clicking when the game launches.</p>
 */
public final class ModConfig {
    private final Map<Feature, FeatureSettings> settings = new EnumMap<>(Feature.class);
    private final Map<Feature, KeyCombo> keys = new EnumMap<>(Feature.class);
    private boolean enabled = true;
    private boolean hudEnabled = true;
    private boolean showMessages = true;

    /**
     * Whether a running feature keeps acting on the world while a screen (chat, inventory, a container)
     * is open.
     *
     * <p>Off by default, which keeps the vanilla rule for the attack channel: a screen discards attack
     * input, so a hold pauses and resumes when the screen closes. The use channel has always kept
     * working with a screen open and is not affected by this switch. Turning it on makes the mod drive
     * the attack channel through the screen state as well, which means the crosshair target - a mob,
     * the block being mined - keeps receiving the actions while the GUI is up.</p>
     */
    private boolean continueOnScreen = false;

    /** The combination that opens the config screen. */
    private KeyCombo configKey = KeyCombo.unbound();

    public ModConfig() {
        for (Feature feature : Feature.values()) {
            this.settings.put(feature, FeatureSettings.defaults());
            this.keys.put(feature, KeyCombo.unbound());
        }
    }

    public FeatureSettings settings(Feature feature) {
        return this.settings.get(feature);
    }

    public void setSettings(Feature feature, FeatureSettings value) {
        this.settings.put(feature, value == null ? FeatureSettings.defaults() : value);
    }

    public KeyCombo key(Feature feature) {
        KeyCombo combo = this.keys.get(feature);

        return combo == null ? KeyCombo.unbound() : combo;
    }

    public void setKey(Feature feature, KeyCombo value) {
        this.keys.put(feature, value == null ? KeyCombo.unbound() : value);
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

    /** Whether features keep acting on the world while a screen is open; off keeps the vanilla rule. */
    public boolean continueOnScreen() {
        return this.continueOnScreen;
    }

    public void setContinueOnScreen(boolean continueOnScreen) {
        this.continueOnScreen = continueOnScreen;
    }

    /** The combination that opens the config screen; unbound until the user sets one. */
    public KeyCombo configKey() {
        return this.configKey;
    }

    public void setConfigKey(KeyCombo value) {
        this.configKey = value == null ? KeyCombo.unbound() : value;
    }
}
