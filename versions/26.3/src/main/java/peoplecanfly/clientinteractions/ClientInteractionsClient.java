package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
                client.player.sendOverlayMessage(Component.literal(StatusText.toggleMessage(feature, on)));
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
            this.featureKeys.put(feature, KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    feature.keyBindingName(),
                    InputConstants.Type.KEYBOARD,
                    UNBOUND.getValue(),
                    KeyMapping.Category.MISC)));
        }

        // Unbound by default: the mod adds no global hotkey of its own. Open the config from Mod Menu, or
        // bind this key in the controls screen if a hotkey is wanted.
        this.openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.clientinteractions.open_config",
                InputConstants.Type.KEYBOARD,
                UNBOUND.getValue(),
                KeyMapping.Category.MISC));

        for (Feature feature : Feature.values()) {
            this.applyKeyBinding(feature);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> this.onClientTick());
        HudOverlay.register();

        LOGGER.info("{} initialised; config at {}", StatusText.MOD_NAME, this.configStore.path());
    }

    private void onClientTick() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            this.engine.stopAll();
            this.playerKeyHeld[ActionChannel.ATTACK.ordinal()] = false;
            this.playerKeyHeld[ActionChannel.USE.ordinal()] = false;
            return;
        }

        Set<String> heldKeys = HeldKeys.collect(client);

        // A combination can be completed by any of its keys, but vanilla only reports the press of the one
        // key a mapping owns. Watching the held set catches the moment a combination becomes complete,
        // whatever order the keys went down in, and is also where a longer binding takes priority over a
        // shorter one that it contains.
        this.handleHotkeys(client, heldKeys);

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
    private void handleHotkeys(Minecraft client, Set<String> heldKeys) {
        // Drain click counts so vanilla's keyboard repeats never accumulate, but do not use them to
        // decide anything: the held set is what the decision is based on.
        for (Feature feature : Feature.values()) {
            KeyMapping key = this.featureKeys.get(feature);

            while (key != null && key.consumeClick()) {
                // Clicks are drained so vanilla's keyboard repeats never accumulate. The decision below
                // uses the held-key set instead, because vanilla only reports the one key a mapping owns.
            }
        }

        while (this.openConfigKey.consumeClick()) {
            client.gui.setScreen(new ConfigScreen(client.gui.screen(), this));
        }

        this.updateFiredFeatures(heldKeys);
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
    private void updateFiredFeatures(Set<String> heldKeys) {
        // With a screen open vanilla clears every key flag, so the held set would look empty and every
        // combination would count as released. That made a hold re-fire - and therefore switch off - the
        // moment the screen closed. Key state is not meaningful while a UI is up, so leave it alone.
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }

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
