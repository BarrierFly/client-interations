package peoplecanfly.clientinteractions.common;

import java.util.EnumMap;
import java.util.Map;

/**
 * Persisted configuration: one settings block per feature, plus three global switches.
 *
 * <p>Features default to off and unbound, and the feature's on/off state is runtime only - it is not
 * saved, so nothing starts clicking when the game launches.</p>
 */
public final class ModConfig {
    private final Map<Feature, FeatureSettings> settings = new EnumMap<>(Feature.class);
    private boolean enabled = true;
    private boolean hudEnabled = true;
    private boolean showMessages = true;

    public ModConfig() {
        for (Feature feature : Feature.values()) {
            this.settings.put(feature, FeatureSettings.defaults());
        }
    }

    public FeatureSettings settings(Feature feature) {
        return this.settings.get(feature);
    }

    public void setSettings(Feature feature, FeatureSettings value) {
        this.settings.put(feature, value == null ? FeatureSettings.defaults() : value);
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
