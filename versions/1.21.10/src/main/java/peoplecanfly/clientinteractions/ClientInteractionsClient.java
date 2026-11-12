package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationEngine;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.HotkeyRouter;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.ModConfig;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Client entry point: the engine, one hotkey per feature, and the per-tick driver.
 *
 * <h2>Hotkeys</h2>
 * <p>Vanilla {@code KeyMapping} holds a single key and cannot express {@code Shift+R}, so the mod uses it
 * only to detect that the main key went down. The full combination is matched against the keys actually
 * held, which {@link HotkeyRouter} turns into three decisions that always agree: which binding wins,
 * whether it just fired, and which keys vanilla must not act on.</p>
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

    /** Whether each channel's vanilla button was down on the previous tick, to detect a fresh press. */
    private final boolean[] playerKeyHeld = new boolean[ActionChannel.values().length];

    /** The single arbiter for hotkey priority, firing and key claiming. */
    private final HotkeyRouter hotkeys = new HotkeyRouter();

    /**
     * The physical key state sampled at the start of this tick, before any suppression.
     *
     * <p>Suppressing a bound combination clears the attack and use flags, and those are exactly the keys a
     * combination may contain. Reading the live state later in the same tick would see them released and undo
     * the feature that had just been switched on.</p>
     */
    private Set<String> sampledHeldKeys;

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

    public ConfigStore configStore() {
        return this.configStore;
    }

    public KeyMapping openConfigKey() {
        return this.openConfigKey;
    }

    public KeyMapping keyFor(Feature feature) {
        return this.featureKeys.get(feature);
    }

    public void saveConfig() {
        this.syncEngine();
        this.configStore.save(this.config);
    }

    /**
     * Keeps a feature's vanilla key mapping unbound, because the combination is polled instead.
     *
     * <p>Vanilla allows one mapping per key, so writing a feature's key into its mapping would take that
     * key away from whichever vanilla binding already owns it: a hotkey on {@code W} would stop the player
     * walking, and one on {@code 1} would stop the hotbar. The mod does not need the mapping - presses are
     * detected by polling the keys the bindings mention - so every mapping created for this mod stays
     * unbound and vanilla keeps all of its own keys.</p>
     */
    public void applyKeyBinding(Feature feature) {
        KeyMapping mapping = this.featureKeys.get(feature);

        if (mapping != null) {
            mapping.setKey(UNBOUND);
        }
    }

    /** Rebinds every feature, e.g. after the config was reloaded. */
    public void applyAllKeyBindings() {
        for (Feature feature : Feature.values()) {
            this.applyKeyBinding(feature);
        }
    }

    /**
     * Leaves the config-opening mapping unbound, for the same reason as a feature's: the combination is
     * polled, and the key stays available to whatever vanilla binding owns it.
     */
    public void applyConfigKeyBinding() {
        if (this.openConfigKey != null) {
            this.openConfigKey.setKey(UNBOUND);
        }
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
        // Turning a feature off always works. Turning one on while the master switch is off is refused:
        // the engine would show it as on - message, HUD and config row - while suppressing every action,
        // which is exactly "it says on but nothing happens". Explaining is better than pretending.
        if (!this.engine.isActive(feature) && !this.engine.isEnabled()) {
            this.actionBar(StatusText.masterOffMessage());
            return false;
        }

        boolean on = this.engine.toggle(feature);
        this.actionBar(StatusText.toggleMessage(feature, on));

        return on;
    }

    /** Shows a message in the action bar, if messages are enabled. */
    private void actionBar(String text) {
        if (!this.config.showMessages()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(text), true);
        }
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

        // Unbound by default: the mod adds no global hotkey of its own. Open the config from Mod Menu, or
        // bind this key in the controls screen if a hotkey is wanted.
        this.openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clientinteractions.open_config",
                InputConstants.Type.KEYSYM,
                UNBOUND.getValue(),
                KeyMapping.Category.MISC));

        for (Feature feature : Feature.values()) {
            this.applyKeyBinding(feature);
        }

        // The saved config-opening key has to reach its mapping too, or it would stay inert until the
        // player opened the capture screen once.
        this.applyConfigKeyBinding();

        ClientTickEvents.START_CLIENT_TICK.register(client -> this.claimBoundKeys(client));
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTick());
        HudRenderCallback.EVENT.register(new HudOverlay());

        LOGGER.info("{} initialised; config at {}", StatusText.MOD_NAME, this.configStore.path());
    }

    /**
     * Stops a pressed combination from also performing its ordinary game action.
     *
     * <h2>Why this is needed</h2>
     * <p>Vanilla acts on a key the moment it goes down, before any mod sees it: {@code 1} selects hotbar
     * slot 1, {@code Q} drops an item, {@code E} opens the inventory, and the left button attacks. A mod
     * cannot cancel that from a tick handler, so binding {@code Shift+1} both ran the feature and switched
     * the hotbar slot, and binding {@code Shift+Left Click} both ran the feature and punched.</p>
     *
     * <h2>How</h2>
     * <p>Two things are suppressed, in different ways, because the two kinds of action work differently:</p>
     *
     * <ul>
     *   <li><b>One-shot actions</b> (hotbar slots, drop, inventory, chat) run from the queued
     *       <em>press</em>, so only {@code consumeClick} is drained.</li>
     *   <li><b>Attack and use</b> keep acting for as long as their {@code isDown} flag is set -
     *       {@code continueAttack} reads it every tick - so their flag is cleared too. Otherwise a
     *       combination containing the left button would keep fighting for as long as it was held.</li>
     * </ul>
     *
     * <p>The flag of movement, sneak and sprint is never touched, since those are continuous states read
     * every tick: clearing them would stop the player walking. The attack and use flags are only cleared
     * while the mod is <em>not</em> driving that channel itself, so a feature bound to
     * {@code Shift+Left Click} can still hold the button - which is the whole point of binding it.</p>
     *
     * <p>This runs at the start of a client tick, which is before vanilla's own key-binding pass for that
     * tick. Keyboard events are polled between ticks rather than during one, so the cleared state cannot be
     * refilled before vanilla reads it.</p>
     *
     * <p>Only keys of a <em>complete</em> combination are claimed. Pressing {@code 1} alone when only
     * {@code Shift+1} is bound must still select the hotbar slot, and clicking alone when only
     * {@code Shift+Left Click} is bound must still attack.</p>
     */
    private void claimBoundKeys(Minecraft client) {
        this.sampledHeldKeys = null;

        if (client.options == null || client.player == null) {
            return;
        }

        // While a screen is open the keys belong to the GUI.
        if (client.screen != null || client.getOverlay() != null) {
            return;
        }

        // Sample the physical state once, before anything is cleared. Everything else this tick works from
        // this snapshot, which is what allows the buttons to be cleared below without the combination
        // appearing to be released - the combination is decided from the state as it was, not as it is
        // after suppression.
        Set<String> held = HeldKeys.heldAmong(this.collectCandidateKeys(client), client,
                this.modHeldKeyNames(client));
        this.sampledHeldKeys = held;

        Set<String> claimed = HotkeyRouter.claimedKeys(held, this.config);

        if (claimed.isEmpty()) {
            return;
        }

        for (KeyMapping mapping : client.options.keyMappings) {
            if (!claimed.contains(mapping.saveString()) || this.ownsMapping(mapping)) {
                continue;
            }

            // A queued press drives every one-shot binding: hotbar slots, drop, inventory, chat, and the
            // single attack a click performs.
            while (mapping.consumeClick()) {
                // Drain the press vanilla queued for this key.
            }

            // Attack and use also keep acting for as long as their flag is set - {@code continueAttack}
            // reads it every tick - so a combination containing the left button would keep fighting. The
            // flag is cleared only while the mod is not driving that channel itself, so a feature bound to
            // {@code Shift+Left Click} can still hold the button, which is why it was bound there.
            //
            // Movement, sneak and sprint are never touched: those are continuous states the player owns.
            if (this.isChannelMapping(mapping, client)
                    && !this.input.owns(this.channelOf(mapping, client))) {
                mapping.isDown = false;
            }
        }
    }

    /** True for the vanilla attack or use button, which vanilla acts on while it is held. */
    private boolean isChannelMapping(KeyMapping mapping, Minecraft client) {
        return mapping == client.options.keyAttack || mapping == client.options.keyUse;
    }

    /** The channel a vanilla button belongs to. */
    private ActionChannel channelOf(KeyMapping mapping, Minecraft client) {
        return mapping == client.options.keyAttack ? ActionChannel.ATTACK : ActionChannel.USE;
    }

    /**
     * The held-key set sampled at the start of this tick, before anything was suppressed.
     *
     * <p>Suppression clears the attack and use buttons, and those are exactly the keys a combination may
     * contain. Reading the live state later in the same tick would therefore see the button as released and
     * undo the feature that had just been switched on. Deciding from this snapshot keeps matching and
     * suppression independent of each other.</p>
     */
    private Set<String> heldSnapshot(Minecraft client) {
        if (this.sampledHeldKeys != null) {
            return this.sampledHeldKeys;
        }

        return HeldKeys.heldAmong(this.collectCandidateKeys(client), client,
                this.modHeldKeyNames(client));
    }

    /** True for a mapping this mod created and drives. */
    private boolean ownsMapping(KeyMapping mapping) {
        if (mapping == this.openConfigKey) {
            return true;
        }

        for (KeyMapping own : this.featureKeys.values()) {
            if (own == mapping) {
                return true;
            }
        }

        return false;
    }

    /**
     * Key names the mod is holding down itself for a running feature.
     *
     * <p>A hold writes the vanilla attack/use flag. Without this, a feature bound to a mouse button would
     * see its own hold as the player pressing that button and keep toggling itself. The player pressing the
     * button physically still counts, because the flag alone is not evidence of a press.</p>
     */
    private Set<String> modHeldKeyNames(Minecraft client) {
        Set<String> names = new LinkedHashSet<>();

        for (ActionChannel channel : ActionChannel.values()) {
            if (this.input.owns(channel)) {
                KeyMapping mapping = channel == ActionChannel.ATTACK
                        ? client.options.keyAttack
                        : client.options.keyUse;
                names.add(mapping.saveString());
            }
        }

        return names;
    }

    private void onClientTick() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            this.engine.stopAll();
            this.hotkeys.reset();
            this.playerKeyHeld[ActionChannel.ATTACK.ordinal()] = false;
            this.playerKeyHeld[ActionChannel.USE.ordinal()] = false;
            return;
        }

        // Hotkeys first, so a combination pressed this tick takes effect on this tick's automation. The
        // decision uses the state sampled before vanilla decided what a bound key may also do.
        this.handleHotkeys(client);

        this.pollPlayerClicks(client, ActionChannel.ATTACK, client.options.keyAttack);
        this.pollPlayerClicks(client, ActionChannel.USE, client.options.keyUse);

        this.engine.tick();
    }

    /**
     * Applies this tick's hotkey decisions.
     *
     * <p>Firing happens only on the tick a combination becomes complete, so holding the keys does not
     * toggle repeatedly. The vanilla mappings of this mod exist only so the keys show up in the controls
     * screen; their queued presses are drained, because the decision comes from the held-key set (any key
     * of a combination may complete it, and a modifier alone cannot be polled through a mapping).</p>
     */
    private void handleHotkeys(Minecraft client) {
        for (Feature feature : Feature.values()) {
            KeyMapping key = this.featureKeys.get(feature);

            while (key != null && key.consumeClick()) {
                // Drained so vanilla's key repeats never accumulate.
            }
        }

        while (this.openConfigKey.consumeClick()) {
            // Drained; opening is decided from the complete combination below.
        }

        // With a screen open vanilla releases every key, and the player's keys belong to the GUI. Leaving
        // the router untouched stops a hold from re-firing - and therefore switching itself off - the
        // moment the screen closes.
        if (client.screen != null || client.getOverlay() != null) {
            return;
        }

        HotkeyRouter.Result result = this.hotkeys.update(this.heldSnapshot(client), this.config);

        for (Feature feature : result.toggles()) {
            this.toggleFeature(feature);
        }

        if (result.openConfig()) {
            client.setScreen(new ConfigScreen(client.screen, this));
        }
    }

    /**
     * Every key the held-state poll has to look at.
     *
     * <p>That is the keys of every binding, the config key, the attack and use buttons (a combination may
     * contain a mouse button), and every modifier. The modifiers matter even when no binding mentions them:
     * a combination blocks an extra Shift, so Shift has to be sampled or {@code R} would fire while Shift
     * is held.</p>
     */
    private Set<String> collectCandidateKeys(Minecraft client) {
        Set<String> candidates = new LinkedHashSet<>();

        for (Feature feature : Feature.values()) {
            candidates.addAll(this.config.key(feature).keys());
        }

        candidates.addAll(this.config.configKey().keys());
        candidates.addAll(KeyCombo.modifierKeys());

        // The attack and use buttons are always sampled, even when no binding mentions them. A combination
        // is decided from a snapshot taken before suppression, and suppression clears exactly these two
        // flags, so they have to be in the snapshot for a mouse-button combination to be seen at all.
        if (client.options != null) {
            candidates.add(client.options.keyAttack.saveString());
            candidates.add(client.options.keyUse.saveString());
        }

        return candidates;
    }

    /**
     * Turns the player's own mouse press into the clicks fast click owes.
     *
     * <p>The physical press is already handled by vanilla, so only the repeats are queued. Detecting the
     * press uses the rising edge of the key's {@code isDown} flag, which is what a real mouse press sets.
     * A press that the mod itself is trying to suppress (a complete combination) is not the player asking
     * for extra clicks, and neither is the button the mod is holding for a feature.</p>
     */
    private void pollPlayerClicks(Minecraft client, ActionChannel channel, KeyMapping key) {
        if (key == null) {
            return;
        }

        boolean down = key.isDown();
        boolean wasDown = this.playerKeyHeld[channel.ordinal()];
        this.playerKeyHeld[channel.ordinal()] = down;

        if (!down || wasDown || this.input.owns(channel)) {
            return;
        }

        this.engine.recordPlayerClick(channel);
    }

    /** HUD lines for whatever is currently running. */
    public List<String> hudLines() {
        List<String> lines = new ArrayList<>(3);

        if (!this.engine.isEnabled()) {
            // Without this line the HUD listed features as running while the master switch suppressed
            // every action, so the display contradicted what the game was doing.
            lines.add(StatusText.pausedLine());
        }

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
