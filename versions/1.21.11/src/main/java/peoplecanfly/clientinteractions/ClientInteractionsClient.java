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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationEngine;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.ModConfig;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Client entry point: the engine, one hotkey per feature, and the per-tick driver.
 *
 * <h2>Hotkeys</h2>
 * <p>Vanilla {@code KeyMapping} holds a single key and cannot express {@code Shift+R}, so the mod uses it
 * only to detect that the main key went down. The modifiers are matched separately against the
 * {@link KeyCombo} stored in the config, which makes {@code R} and {@code Shift+R} distinct bindings and
 * keeps a press from firing a feature whose combination does not match exactly.</p>
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

    /** The keys held on the previous tick, so a combination fires only on the tick it completes. */
    private Set<String> previousHeldKeys = Set.of();

    /** Features whose combination was already satisfied last tick, so holding does not retrigger. */
    private final Set<Feature> firedFeatures = java.util.EnumSet.noneOf(Feature.class);

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
     * Pushes one feature's combination into its vanilla key mapping.
     *
     * <p>Only the main key is written; the modifiers stay in the config, because vanilla has nowhere to
     * put them. A mouse button works the same way, since vanilla reports those through the same mapping
     * table.</p>
     *
     * <p>{@code KeyMapping#setKey} only assigns a field: it does not add the mapping to the
     * key-to-binding table vanilla looks presses up in. That table is rebuilt by
     * {@code KeyMapping#resetMapping}, which vanilla only calls when it loads the options and when the
     * key binds screen opens. Without this call, a key bound at runtime would never fire until the next
     * launch.</p>
     */
    public void applyKeyBinding(Feature feature) {
        KeyMapping mapping = this.featureKeys.get(feature);

        if (mapping == null) {
            return;
        }

        KeyCombo combo = this.config.key(feature);

        if (!combo.isBound()) {
            mapping.setKey(InputConstants.UNKNOWN);
        } else {
            mapping.setKey(InputConstants.getKey(combo.keyName()));
        }

        KeyMapping.resetMapping();
    }

    /** Rebinds every feature, e.g. after the config was reloaded. */
    public void applyAllKeyBindings() {
        for (Feature feature : Feature.values()) {
            this.applyKeyBinding(feature);
        }
    }

    /**
     * Pushes the config-opening combination into its vanilla key mapping.
     *
     * <p>Same rule as a feature: vanilla gets the primary key only, and the table is rebuilt so the change
     * works without a restart.</p>
     */
    public void applyConfigKeyBinding() {
        if (this.openConfigKey == null) {
            return;
        }

        KeyCombo combo = this.config.configKey();

        if (!combo.isBound()) {
            this.openConfigKey.setKey(InputConstants.UNKNOWN);
        } else {
            this.openConfigKey.setKey(InputConstants.getKey(combo.primaryKey()));
        }

        KeyMapping.resetMapping();
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
        Set<String> held = HeldKeys.heldAmong(this.collectCandidateKeys(client), client);
        this.sampledHeldKeys = held;

        Set<String> claimed = this.claimedKeysFrom(held);

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
     * The keys of every combination that is complete in {@code held} and not merely part of a longer one.
     *
     * <p>The shadowing rule is the same as for firing, so a combination that yields to a longer one does
     * not take that longer combination's keys either.</p>
     *
     * @param held the physical key state sampled at the start of this tick, before any suppression
     */
    private Set<String> claimedKeysFrom(Set<String> held) {
        Set<String> claimed = new java.util.HashSet<>();

        for (Feature feature : Feature.values()) {
            KeyCombo combo = this.config.key(feature);

            if (!combo.isBound() || !combo.matches(held)) {
                continue;
            }

            boolean shadowed = false;

            for (Feature other : Feature.values()) {
                KeyCombo otherCombo = this.config.key(other);

                if (other != feature && combo.isShadowedBy(otherCombo) && otherCombo.matches(held)) {
                    shadowed = true;
                    break;
                }
            }

            if (!shadowed) {
                claimed.addAll(combo.keys());
            }
        }

        KeyCombo configCombo = this.config.configKey();

        if (configCombo.isBound() && configCombo.matches(held)) {
            claimed.addAll(configCombo.keys());
        }

        return claimed;
    }

    /**
     * The held-key set sampled at the start of this tick, before anything was suppressed.
     *
     * <p>Suppression clears the attack and use buttons, and those are exactly the keys a combination may
     * contain. Reading the live state later in the same tick would therefore see the button as released and
     * undo the feature that had just been switched on. Deciding from this snapshot keeps matching and
     * suppression independent of each other.</p>
     *
     * @return the snapshot, or {@code null} when it was not taken this tick (no player, or a screen open)
     */
    private Set<String> heldSnapshot(Minecraft client) {
        if (this.sampledHeldKeys != null) {
            return this.sampledHeldKeys;
        }

        return HeldKeys.heldAmong(this.collectCandidateKeys(client), client);
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

    private void onClientTick() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            this.engine.stopAll();
            this.playerKeyHeld[ActionChannel.ATTACK.ordinal()] = false;
            this.playerKeyHeld[ActionChannel.USE.ordinal()] = false;
            return;
        }

        // A combination can be completed by any of its keys, but vanilla only reports the press of the one
        // key a mapping owns. Watching the held set catches the moment a combination becomes complete,
        // whatever order the keys went down in, and is also where a longer binding takes priority over a
        // shorter one that it contains. Only the keys used by some binding are polled, because a key bound
        // to nothing is not tracked by any mapping and would otherwise never appear.
        this.handleHotkeys(client);

        this.pollPlayerClicks(client, ActionChannel.ATTACK, client.options.keyAttack);
        this.pollPlayerClicks(client, ActionChannel.USE, client.options.keyUse);

        this.engine.tick();
    }

    /**
     * Fires the hotkey whose combination just became complete, giving longer combinations priority.
     *
     * <h2>When it fires</h2>
     * <p>Only on the tick the held set changes, so holding the keys does not toggle repeatedly. Releasing
     * and pressing again is a new trigger.</p>
     *
     * <h2>Priority</h2>
     * <p>If one feature is bound to {@code A+C} and another to plain {@code C}, pressing {@code A} then
     * {@code C} must run only the {@code A+C} feature. Without this the plain {@code C} feature would match
     * too, because ordinary keys outside a binding are ignored (the player is always holding something).
     * So only the longest matching combination is triggered, and shorter bindings contained in it are
     * skipped for that press.</p>
     */
    private void handleHotkeys(Minecraft client) {
        // Drain click counts so vanilla's keyboard repeats never accumulate, but do not use them to
        // decide anything: the held-key set is what the decision is based on.
        for (Feature feature : Feature.values()) {
            KeyMapping key = this.featureKeys.get(feature);

            while (key != null && key.consumeClick()) {
                // Clicks are drained so vanilla's keyboard repeats never accumulate. The decision below
                // uses the held-key set instead, because vanilla only reports the one key a mapping owns.
            }
        }

        Set<String> held = this.heldSnapshot(client);

        while (this.openConfigKey.consumeClick()) {
            // Only when the combination actually matches; the mapping owns one key, and a config key that is
            // part of a longer combination must not open the screen on its own.
            KeyCombo configCombo = this.config.configKey();

            if (configCombo.matches(held)) {
                client.setScreen(new ConfigScreen(client.screen, this));
            }
        }

        this.updateFiredFeatures(client);
    }

    /**
     * Every key that appears in some binding, plus the config key.
     *
     * <p>Only these need checking each tick. A key bound to nothing is not tracked by any vanilla mapping,
     * which is precisely why the held state is polled from the keyboard rather than read from the mappings.</p>
     */
    private Set<String> collectCandidateKeys(Minecraft client) {
        Set<String> candidates = new LinkedHashSet<>();

        for (Feature feature : Feature.values()) {
            candidates.addAll(this.config.key(feature).keys());
        }

        if (this.openConfigKey != null && !this.openConfigKey.isUnbound()) {
            candidates.add(this.openConfigKey.saveString());
        }

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
     * Fires each feature whose combination has just become satisfied.
     *
     * <p>Firing is based on the transition from "not satisfied" to "satisfied" for that feature's own
     * combination, not on the whole set of held keys changing. Watching the whole set was wrong: holding a
     * movement key changes it constantly, which retriggered the same hotkey over and over and left the
     * feature toggling on and off while the message appeared each time.</p>
     *
     * <p>A longer combination wins over any shorter one it contains, so binding {@code A+C} blocks a plain
     * {@code C} binding while both keys are down.</p>
     */
    private void updateFiredFeatures(Minecraft client) {
        // With a screen open vanilla releases every key, and the player's keys are going to the GUI, so the
        // held state is meaningless. Leaving it alone also stops a hold from re-firing - and therefore
        // switching itself off - the moment the screen closes.
        if (client.screen != null || client.getOverlay() != null) {
            return;
        }

        // The snapshot taken before this tick's suppression, so claiming the attack button cannot make a
        // combination look released and switch the feature straight back off.
        Set<String> heldKeys = this.heldSnapshot(client);

        // Collect the combinations that are currently satisfied, longest first.
        List<Feature> satisfied = new ArrayList<>();

        for (Feature feature : Feature.values()) {
            if (this.config.key(feature).matches(heldKeys)) {
                satisfied.add(feature);
            }
        }

        satisfied.sort((a, b) -> Integer.compare(
                this.config.key(b).size(), this.config.key(a).size()));

        for (Feature feature : satisfied) {
            KeyCombo combo = this.config.key(feature);

            // Skip a binding that is merely part of a longer satisfied one.
            boolean shadowed = false;

            for (Feature other : satisfied) {
                if (other != feature && combo.isShadowedBy(this.config.key(other))) {
                    shadowed = true;
                    break;
                }
            }

            boolean wasSatisfied = this.firedFeatures.contains(feature);

            if (shadowed) {
                // A longer binding owns this key press; nothing fires for the shorter one, and it is not
                // marked as fired so it can work again once the longer combination is released.
                continue;
            }

            if (!wasSatisfied) {
                this.firedFeatures.add(feature);
                this.toggleFeature(feature);
            }
        }

        // Forget features whose combination is no longer satisfied, so they can fire again next time.
        this.firedFeatures.removeIf(feature -> {
            KeyCombo combo = this.config.key(feature);
            boolean stillSatisfied = false;

            for (Feature other : satisfied) {
                if (other == feature || combo.isShadowedBy(this.config.key(other))) {
                    stillSatisfied = true;
                    break;
                }
            }

            return !stillSatisfied;
        });
    }

    /**
     * Turns the player's own mouse press into extra clicks when fast click is on.
     *
     * <p>The physical press is already handled by vanilla, so only the extra clicks are injected. Detecting
     * the press uses the rising edge of the key's {@code isDown} flag, because that flag is what a real
     * mouse press sets and what the mod never writes for a click-only feature.</p>
     */
    private void pollPlayerClicks(Minecraft client, ActionChannel channel, KeyMapping key) {
        if (key == null) {
            return;
        }

        boolean down = key.isDown();
        boolean wasDown = this.playerKeyHeld[channel.ordinal()];
        this.playerKeyHeld[channel.ordinal()] = down;

        if (!down || wasDown) {
            return;
        }

        int times = this.engine.playerClickCount(channel);

        if (times > 1) {
            this.input.playerClick(channel, times);
        }
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
