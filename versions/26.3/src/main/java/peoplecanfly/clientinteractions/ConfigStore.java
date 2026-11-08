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
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationMode;
import peoplecanfly.clientinteractions.common.ChannelSettings;
import peoplecanfly.clientinteractions.common.ModConfig;

/**
 * Reads and writes the configuration as {@code config/clientinteractions.json}.
 *
 * <p>The JSON format is deliberately flat and version independent so that a config file can be copied
 * between Minecraft versions:</p>
 *
 * <pre>{@code
 * {
 *   "enabled": true,
 *   "hud": true,
 *   "messages": true,
 *   "attack": { "mode": "periodic_hold", "interval": 20, "hold": 10, "perTick": 1, "burst": 3 },
 *   "use":    { "mode": "hold",          "interval": 20, "hold": 10, "perTick": 1, "burst": 3 }
 * }
 * }</pre>
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

    /** Loads the configuration, falling back to the defaults for anything missing or invalid. */
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

            for (ActionChannel channel : ActionChannel.values()) {
                readChannel(root, channel, config);
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

        for (ActionChannel channel : ActionChannel.values()) {
            ChannelSettings settings = config.settings(channel);
            JsonObject node = new JsonObject();
            node.addProperty("mode", config.mode(channel).id());
            node.addProperty("interval", settings.interval());
            node.addProperty("hold", settings.holdDuration());
            node.addProperty("perTick", settings.perTick());
            node.addProperty("burst", settings.burstSize());
            root.add(channel.id(), node);
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

    private static void readChannel(JsonObject root, ActionChannel channel, ModConfig config) {
        if (!root.has(channel.id()) || !root.get(channel.id()).isJsonObject()) {
            return;
        }

        JsonObject node = root.getAsJsonObject(channel.id());
        ChannelSettings defaults = config.settings(channel);

        config.setMode(channel, AutomationMode.byId(getString(node, "mode", config.mode(channel).id())));
        config.setSettings(channel, new ChannelSettings(
                getInt(node, "interval", defaults.interval()),
                getInt(node, "hold", defaults.holdDuration()),
                getInt(node, "perTick", defaults.perTick()),
                getInt(node, "burst", defaults.burstSize())));
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
