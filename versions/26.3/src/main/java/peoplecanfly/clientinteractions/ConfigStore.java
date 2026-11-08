package peoplecanfly.clientinteractions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.FeatureSettings;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.ModConfig;

/**
 * Reads and writes {@code config/clientinteractions.json}.
 *
 * <p>Every feature has its own block, so features are configured independently. A hotkey is stored as
 * the vanilla key name plus a modifier string such as {@code "ctrl+shift"}, which keeps the file
 * readable and portable between versions.</p>
 */
public final class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientInteractions");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "clientinteractions.json";

    private final Path path;

    public ConfigStore(Path configDir) {
        this.path = configDir.resolve(FILE_NAME);
    }

    public Path path() {
        return this.path;
    }

    /** Loads the configuration, falling back to defaults for anything missing or invalid. */
    public ModConfig load() {
        ModConfig config = new ModConfig();

        if (!Files.isRegularFile(this.path)) {
            return config;
        }

        try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            config.setEnabled(getBoolean(root, "enabled", config.isEnabled()));
            config.setHudEnabled(getBoolean(root, "hud", config.isHudEnabled()));
            config.setShowMessages(getBoolean(root, "messages", config.showMessages()));

            for (Feature feature : Feature.values()) {
                if (!root.has(feature.id()) || !root.get(feature.id()).isJsonObject()) {
                    continue;
                }

                JsonObject node = root.getAsJsonObject(feature.id());
                FeatureSettings defaults = config.settings(feature);

                int interval = getInt(node, "interval", defaults.interval());
                int duration = getInt(node, "duration", defaults.duration());
                int count = getInt(node, "count", defaults.count());

                // Older configs stored perTick and burst separately; both meant "clicks per interval", so
                // the larger of the two is carried over as the single count.
                int perTick = getInt(node, "perTick", 0);
                int burst = getInt(node, "burst", 0);

                config.setSettings(feature, perTick > 0 || burst > 0
                        ? FeatureSettings.legacy(interval, duration, perTick, burst)
                        : new FeatureSettings(interval, duration, count));

                String key = getString(node, "key", null);

                if (key != null && !key.isEmpty()) {
                    config.setKey(feature, KeyCombo.parse(key, getString(node, "modifiers", "")));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read {}, using defaults", this.path, e);
        }

        return config;
    }

    /** Writes the configuration, creating the directory if needed. */
    public void save(ModConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", config.isEnabled());
        root.addProperty("hud", config.isHudEnabled());
        root.addProperty("messages", config.showMessages());

        for (Feature feature : Feature.values()) {
            FeatureSettings settings = config.settings(feature);
            JsonObject node = new JsonObject();
            node.addProperty("interval", settings.interval());
            node.addProperty("duration", settings.duration());
            node.addProperty("count", settings.count());

            KeyCombo combo = config.key(feature);

            if (combo.isBound()) {
                node.addProperty("key", combo.keyName());

                if (combo.hasModifiers()) {
                    node.addProperty("modifiers", combo.modifierString());
                }
            }

            root.add(feature.id(), node);
        }

        try {
            Files.createDirectories(this.path.getParent());

            try (Writer writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write {}", this.path, e);
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
