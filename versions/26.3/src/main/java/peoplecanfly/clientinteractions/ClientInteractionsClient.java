package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLScancode;
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

    private ModConfig config;
    private ConfigStore configStore;
    private AutomationEngine engine;
    private GameInput input;
    private KeyMapping attackToggleKey;
    private KeyMapping useToggleKey;
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

        // 26.x moved the key binding API and replaced the string category with KeyMapping.Category.
        this.configStore = new ConfigStore(FabricLoader.getInstance().getConfigDir());
        this.config = this.configStore.load();
        this.input = new GameInput(Minecraft.getInstance());
        this.engine = new AutomationEngine(new EngineHost());
        this.syncEngine();

        this.attackToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.clientinteractions.toggle_attack",
                InputConstants.Type.KEYBOARD,
                SDLScancode.SDL_SCANCODE_R,
                KeyMapping.Category.MISC));
        this.useToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.clientinteractions.toggle_use",
                InputConstants.Type.KEYBOARD,
                SDLScancode.SDL_SCANCODE_V,
                KeyMapping.Category.MISC));
        this.openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.clientinteractions.open_config",
                InputConstants.Type.KEYBOARD,
                SDLScancode.SDL_SCANCODE_RIGHTBRACKET,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTick());
        HudOverlay.register();

        LOGGER.info("{} initialised; config at {}", StatusText.MOD_NAME, this.configStore.path());
    }

    private void onClientTick() {
        Minecraft client = Minecraft.getInstance();

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
            client.setScreenAndShow(new ConfigScreen(client.gui.screen(), this));
        }

        this.engine.tick();
    }

    private void toggle(ActionChannel channel) {
        boolean on = this.engine.toggle(channel);

        if (this.config.showMessages()) {
            Minecraft client = Minecraft.getInstance();

            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal(StatusText.toggleMessage(channel, on)));
            }
        }
    }

    /** Lines for the HUD, rebuilt from the live engine state. */
    public List<String> hudLines() {
        List<String> lines = new ArrayList<>(2);

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
