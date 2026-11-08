package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationEngine;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.ModConfig;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Client entry point: one key binding per feature, the engine, and the per-tick driver.
 *
 * <p>All ten features are registered as separate, unbound key bindings. Each feature keeps its own
 * on/off state, and switching one on switches off the other features of the same button.</p>
 */
public final class ClientInteractionsClient implements ClientModInitializer {
    public static final String MOD_ID = "clientinteractions";
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientInteractions");

    /** The unbound key used as the default for every feature binding. */
    private static final InputConstants.Key UNBOUND = InputConstants.UNKNOWN;

    private static ClientInteractionsClient instance;

    private ModConfig config;
    private ConfigStore configStore;
    private AutomationEngine engine;
    private GameInput input;
    private final Map<Feature, KeyMapping> featureKeys = new EnumMap<>(Feature.class);
    private KeyMapping openConfigKey;

    public static ClientInteractionsClient get() {
        return instance;
    }

    public ModConfig config() {
        return this.config;
    }

    public AutomationEngine engine() {
        return this.engine;
    }

    public GameInput input() {
        return this.input;
    }

    public KeyMapping keyFor(Feature feature) {
        return this.featureKeys.get(feature);
    }

    public void saveConfig() {
        // Every save also records the current bindings, so a rebind survives a restart.
        for (Feature feature : Feature.values()) {
            KeyMapping key = this.featureKeys.get(feature);

            if (key != null) {
                this.configStore.setKeyName(feature, key.saveString());
            }
        }

        this.syncEngine();
        this.configStore.save(this.config);
    }

    /** Pushes the current settings into the engine, which keeps running while they change. */
    public void syncEngine() {
        this.engine.setEnabled(this.config.isEnabled());

        for (Feature feature : Feature.values()) {
            this.engine.setSettings(feature, this.config.settings(feature));
        }
    }

    /** Turns the master switch on or off and applies it immediately. */
    public void setGameEnabled(boolean enabled) {
        this.config.setEnabled(enabled);
        this.syncEngine();
        this.configStore.save(this.config);
    }

    /** Toggles one feature and reports it; used by the key bindings and the config screen. */
    public boolean toggleFeature(Feature feature) {
        boolean on = this.engine.toggle(feature);

        if (this.config.showMessages()) {
            Minecraft client = Minecraft.getInstance();

            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal(StatusText.toggleMessage(feature, on)), true);
            }
        }

        return on;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        this.configStore = new ConfigStore(FabricLoader.getInstance().getConfigDir());
        this.config = this.configStore.load();
        this.input = new GameInput(Minecraft.getInstance());
        this.engine = new AutomationEngine(new EngineHost());
        this.syncEngine();

        for (Feature feature : Feature.values()) {
            this.featureKeys.put(feature, KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    feature.keyBindingName(),
                    InputConstants.Type.KEYSYM,
                    UNBOUND.getValue(),
                    KeyMapping.Category.MISC)));
        }

        this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clientinteractions.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                KeyMapping.Category.MISC));

        // Restore the saved per-feature bindings. Vanilla only tracks its own defaults, so the mod
        // keeps the binding strings in its own config file.
        for (Feature feature : Feature.values()) {
            String saved = this.configStore.keyName(feature);

            if (saved != null) {
                this.featureKeys.get(feature).setKey(InputConstants.getKey(saved));
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTick());
        HudRenderCallback.EVENT.register(new HudOverlay());

        LOGGER.info("{} initialised; config at {}", StatusText.MOD_NAME, this.configStore.path());
    }

    private void onClientTick() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            this.engine.stopAll();
            return;
        }

        for (Feature feature : Feature.values()) {
            KeyMapping key = this.featureKeys.get(feature);

            while (key != null && key.consumeClick()) {
                this.toggleFeature(feature);
            }
        }

        while (this.openConfigKey.consumeClick()) {
            client.setScreen(new ConfigScreen(client.screen, this));
        }

        this.engine.tick();
    }

    /** HUD lines for whatever is currently running. */
    public List<String> hudLines() {
        List<String> lines = new ArrayList<>(2);

        for (Feature feature : Feature.values()) {
            if (this.engine.isActive(feature)) {
                lines.add(StatusText.featureLine(feature, this.config.settings(feature)));
            }
        }

        return lines;
    }

    /** Adapts {@link GameInput} to the engine host interface. */
    private final class EngineHost implements AutomationEngine.Host {
        @Override
        public void apply(ActionChannel channel, int clicks, boolean hold, boolean release) {
            ClientInteractionsClient.this.input.apply(channel, clicks, hold, release);
        }

        @Override
        public void release(ActionChannel channel) {
            ClientInteractionsClient.this.input.release(channel);
        }
    }
}
