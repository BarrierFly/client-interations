package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationEngine;
import peoplecanfly.clientinteractions.common.ModConfig;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Client entry point: registers the key bindings, owns the engine and drives it once per client tick.
 */
public final class ClientInteractionsClient implements ClientModInitializer {
    public static final String MOD_ID = "clientinteractions";
    public static final Logger LOGGER = LoggerFactory.getLogger("ClientInteractions");

    private static ClientInteractionsClient instance;

    private final ModConfig config;
    private final ConfigStore configStore;
    private final AutomationEngine engine;
    private final GameInput input;
    private KeyMapping attackToggleKey;
    private KeyMapping useToggleKey;
    private KeyMapping openConfigKey;

    public ClientInteractionsClient() {
        this.configStore = new ConfigStore(
                FabricLoader.getInstance().getConfigDir());
        this.config = this.configStore.load();
        this.input = new GameInput(net.minecraft.client.Minecraft.getInstance());
        this.engine = new AutomationEngine(new EngineHost());
        this.syncEngine();
    }

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

    public void saveConfig() {
        this.syncEngine();
        this.configStore.save(this.config);
    }

    /** Pushes the current settings into the engine, which keeps running while they change. */
    public void syncEngine() {
        this.engine.setEnabled(this.config.isEnabled());

        for (ActionChannel channel : ActionChannel.values()) {
            this.engine.setConfig(channel, new AutomationEngine.ChannelConfig(
                    this.config.mode(channel), this.config.settings(channel)));
        }
    }

    /**
     * Turns the master switch on or off and applies it immediately.
     *
     * <p>Used by the config screen and by the in-game game test, which needs to flip the switch without
     * going through a screen.</p>
     */
    public void setGameEnabled(boolean enabled) {
        this.config.setEnabled(enabled);
        this.syncEngine();
        this.configStore.save(this.config);
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        this.attackToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clientinteractions.toggle_attack",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.MISC));
        this.useToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clientinteractions.toggle_use",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyMapping.Category.MISC));
        this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clientinteractions.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTick());
        HudRenderCallback.EVENT.register(new HudOverlay());


        LOGGER.info("{} initialised; config at {}", StatusText.MOD_NAME, this.configStore.path());
    }

    private void onClientTick() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            this.engine.stopAll();
            return;
        }

        while (this.attackToggleKey.consumeClick()) {
            this.toggle(ActionChannel.ATTACK);
        }

        while (this.useToggleKey.consumeClick()) {
            this.toggle(ActionChannel.USE);
        }

        while (this.openConfigKey.consumeClick()) {
            client.setScreen(new ConfigScreen(client.screen, this));
        }

        this.engine.tick();
    }

    private void toggle(ActionChannel channel) {
        boolean on = this.engine.toggle(channel);

        if (this.config.showMessages()) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();

            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal(StatusText.toggleMessage(channel, on)), true);
            }
        }
    }

    /** Messages collected for the HUD, rebuilt on demand from the live engine state. */
    public List<String> hudLines() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(2);

        for (ActionChannel channel : ActionChannel.values()) {
            if (this.engine.isActive(channel)) {
                lines.add(StatusText.channelLine(channel, this.config.mode(channel), this.config.settings(channel)));
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
