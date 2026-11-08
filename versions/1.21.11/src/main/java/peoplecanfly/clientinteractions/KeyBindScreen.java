package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.KeyNames;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Captures one key <em>or mouse button</em>, with modifiers, and assigns it to one feature.
 *
 * <h2>What can be bound</h2>
 * <p>Any keyboard key, any mouse button vanilla knows (left, right, middle, 4 through 8), and any
 * combination of Ctrl, Shift, Alt and Win around them. Vanilla routes mouse buttons through the same
 * mapping table as keys, so a mouse button is stored as an ordinary key name such as
 * {@code key.mouse.4}; this screen simply listens to both kinds of press.</p>
 *
 * <h2>What the screen shows</h2>
 * <p>Choosing a binding is the moment feedback matters, so the screen always draws: the feature, the
 * binding in effect now, the modifiers held at this very frame, and what Escape and Clear do. Pressing a
 * modifier on its own is reported rather than ignored, which was the old "nothing is shown" case.</p>
 *
 * <h2>Colours</h2>
 * <p>Every text colour carries an explicit alpha of {@code FF}. Vanilla skips drawing text whose colour
 * has zero alpha from 1.21.10 on, so a plain {@code 0xFFFFFF} rendered nothing.</p>
 */
public final class KeyBindScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int ACCENT = 0xFFFFE0B0;
    private static final int WARN = 0xFFFF8080;
    private static final int LINE = 11;

    private final Screen parent;
    private final ClientInteractionsClient mod;
    private final Feature feature;

    /** Set when a press cannot be used as a binding, so the screen can explain instead of ignoring it. */
    private String notice = "";

    public KeyBindScreen(Screen parent, ClientInteractionsClient mod, Feature feature) {
        super(Component.literal("Rebind " + feature.label()));
        this.parent = parent;
        this.mod = mod;
        this.feature = feature;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.buttonTop(), 96, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.apply(KeyCombo.unbound()))
                .bounds(this.width / 2 + 4, this.buttonTop(), 96, 20)
                .build());
    }

    private int buttonTop() {
        return Math.max(40, this.height - 40);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int center = this.width / 2;
        int y = 20;

        graphics.drawCenteredString(this.font, this.title, center, y, TEXT);
        y += LINE + 6;

        graphics.drawCenteredString(this.font, StatusText.keyPrompt(this.feature), center, y, DIM);
        y += LINE + 2;

        // What is bound right now, so the screen is never blank about the current state.
        graphics.drawCenteredString(this.font,
                Component.literal("Now: " + this.keyDisplay()), center, y, ACCENT);
        y += LINE + 8;

        // A live readout of the modifiers held at this instant.
        graphics.drawCenteredString(this.font, Component.literal(this.heldModifiers()), center, y, TEXT);
        y += LINE + 2;

        if (!this.notice.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.notice), center, y, WARN);
            y += LINE;
        }

        graphics.drawCenteredString(this.font, Component.literal(KeyNames.bindingHint()), center, y, DIM);
        y += LINE;
        graphics.drawCenteredString(this.font, Component.literal("Esc or Clear unbinds"), center, y, DIM);
    }

    /** The modifiers currently held, spelled out, including Win. */
    private String heldModifiers() {
        String described = this.modifiers().describe();

        return described.equals("none") ? "No modifier held" : "Holding: " + described;
    }

    private Modifiers modifiers() {
        return new Modifiers(this.minecraft);
    }

    private String keyDisplay() {
        KeyCombo combo = this.mod.config().key(this.feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        return combo.display(KeyNames.readable(combo.keyName()));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.apply(KeyCombo.unbound());
            return true;
        }

        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(event.key());

        return this.capture(key.getName());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // A click on one of this screen's own buttons must still work; only clicks outside them are
        // treated as a binding.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());

        return this.capture(key.getName());
    }

    /** Stores a captured key name, or explains why it cannot be used. */
    private boolean capture(String keyName) {
        if (KeyNames.isModifier(keyName)) {
            // A modifier alone is not a usable binding; say so rather than closing silently.
            this.notice = KeyNames.readable(keyName) + " is a modifier - hold it and press another key";
            this.rebuildWidgets();
            return true;
        }

        Modifiers held = this.modifiers();

        this.apply(KeyCombo.of(keyName, held.shift(), held.control(), held.alt(), held.win()));
        return true;
    }

    /** Stores the combination and returns to the list. */
    private void apply(KeyCombo combo) {
        this.mod.config().setKey(this.feature, combo);
        this.mod.applyKeyBinding(this.feature);
        this.mod.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
