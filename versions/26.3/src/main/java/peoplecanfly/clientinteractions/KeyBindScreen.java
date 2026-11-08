package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.KeyNames;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Captures one key press, with modifiers, and assigns it to one feature.
 *
 * <h2>What the screen shows</h2>
 * <p>Waiting for a key is the moment the user most needs feedback, so the screen draws the feature name,
 * a live list of the modifiers currently held, and what pressing Escape or Backspace will do. The
 * modifier state is redrawn every frame, so pressing Shift visibly changes the hint before the main key
 * is chosen - that is the difference between "it captured my combination" and "nothing happened".</p>
 *
 * <h2>Colours</h2>
 * <p>Every text colour carries an explicit alpha of {@code FF}. Vanilla skips drawing text whose colour
 * has zero alpha from 1.21.10 on, so the plain {@code 0xFFFFFF} used before rendered nothing at all.</p>
 */
public final class KeyBindScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int ACCENT = 0xFFFFE0B0;
    private static final int LINE = 11;

    private final Screen parent;
    private final ClientInteractionsClient mod;
    private final Feature feature;

    /** Set when a modifier key was pressed on its own, so the screen can say so instead of ignoring it. */
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

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
                            this.apply(KeyCombo.unbound());
                        })
                .bounds(this.width / 2 + 4, this.buttonTop(), 96, 20)
                .build());
    }

    private int buttonTop() {
        return Math.max(40, this.height - 40);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int center = this.width / 2;
        int y = 24;

        graphics.centeredText(this.font, this.title, center, y, TEXT);
        y += LINE + 6;

        graphics.centeredText(this.font, StatusText.keyPrompt(this.feature), center, y, DIM);
        y += LINE + 2;

        // What is bound right now, so the screen is never blank about the current state.
        graphics.centeredText(this.font,
                Component.literal("Now: " + this.keyDisplay()), center, y, ACCENT);
        y += LINE + 10;

        // A live readout of the modifiers held at this instant.
        graphics.centeredText(this.font, Component.literal(this.heldModifiers()), center, y, TEXT);
        y += LINE + 2;

        if (!this.notice.isEmpty()) {
            graphics.centeredText(this.font, Component.literal(this.notice), center, y, ACCENT);
            y += LINE;
        }

        graphics.centeredText(this.font,
                Component.literal("Esc or Clear button unbinds"), center, y, DIM);
    }

    /** The modifiers currently held, spelled out. */
    private String heldModifiers() {
        boolean shift = this.minecraft.hasShiftDown();
        boolean control = this.minecraft.hasControlDown();
        boolean alt = this.minecraft.hasAltDown();

        if (!shift && !control && !alt) {
            return "No modifier held";
        }

        StringBuilder builder = new StringBuilder("Holding: ");

        if (control) {
            builder.append("Ctrl ");
        }

        if (shift) {
            builder.append("Shift ");
        }

        if (alt) {
            builder.append("Alt ");
        }

        return builder.toString().trim();
    }

    private String keyDisplay() {
        KeyCombo combo = this.mod.config().key(this.feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        KeyMapping mapping = this.mod.keyFor(this.feature);
        String keyName = mapping != null && !mapping.isUnbound()
                ? mapping.getTranslatedKeyMessage().getString()
                : KeyNames.readable(combo.keyName());

        return combo.display(keyName);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            this.apply(KeyCombo.unbound());
            return true;
        }

        String name = keyNameOf(event);

        if (name == null) {
            return true;
        }

        if (KeyNames.isModifier(name)) {
            // A modifier on its own is not a usable binding, so say that rather than closing silently.
            this.notice = KeyNames.readable(name) + " is a modifier - hold it and press another key";
            this.rebuildWidgets();
            return true;
        }

        this.apply(KeyCombo.of(name,
                this.minecraft.hasShiftDown(),
                this.minecraft.hasControlDown(),
                this.minecraft.hasAltDown()));
        return true;
    }

    /** The vanilla name of the pressed key, or {@code null} when it cannot be represented. */
    private String keyNameOf(KeyEvent event) {
        InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(event.key());

        return key.getName();
    }

    /** Stores the combination and returns to the list. */
    private void apply(KeyCombo combo) {
        this.mod.config().setKey(this.feature, combo);
        this.mod.applyKeyBinding(this.feature);
        this.mod.saveConfig();
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
