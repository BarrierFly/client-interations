package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.KeyNames;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Builds a hotkey out of any keys the player presses, in order.
 *
 * <h2>How a combination is built</h2>
 * <p>Every press is appended to a list, so {@code Ctrl}, {@code Shift}, {@code Alt} and {@code R} build
 * {@code Ctrl+Shift+Alt+R} exactly as pressed. Any key may take part - an ordinary letter, a modifier, or a
 * mouse button - because the combination is a set of keys held together, not a key plus modifier flags.</p>
 *
 * <p>The capture starts empty and <b>replaces</b> the saved binding: Done stores exactly what was pressed,
 * so a new combination is typed out whole instead of extending the old one. A press does not end the
 * capture; the player keeps adding keys and presses <b>Done</b> to accept the combination, or
 * <b>Cancel</b> to discard it and leave the saved binding alone. That is what makes combinations of
 * ordinary keys possible: with an automatic commit, the first letter would always end the capture.</p>
 *
 * <h2>Keys the screen itself needs</h2>
 * <p>Escape and Enter cannot be captured: they are the screen's own clear and confirm keys. Escape looks
 * at the capture: while keys are captured it empties the list so the next press starts a fresh
 * combination, and with nothing captured it confirms, storing the empty combination - Cancel is the way
 * to leave without saving.</p>
 *
 * <h2>Colours</h2>
 * <p>Every text colour carries an explicit alpha of {@code FF}. Vanilla skips drawing text whose colour has
 * zero alpha from 1.21.10 on, so a plain {@code 0xFFFFFF} rendered nothing.</p>
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

    /** Set when a press cannot be used, so the screen can explain instead of ignoring it. */
    private String notice = "";

    /** The keys captured so far, in press order. */
    private final List<String> captured = new ArrayList<>();

    public KeyBindScreen(Screen parent, ClientInteractionsClient mod, Feature feature) {
        super(Component.literal(feature == null ? "Rebind config key" : "Rebind " + feature.label()));
        this.parent = parent;
        this.mod = mod;
        this.feature = feature;

        // The capture starts empty: Done stores exactly what is pressed, replacing the saved binding
        // rather than extending it.
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int buttonTop = this.buttonTop();
        int spacing = Math.max(56, Math.min(104, this.width / 3 - 4));

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.apply())
                .bounds(this.width / 2 - spacing / 2, buttonTop, spacing, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Undo"), b -> this.undo())
                .bounds(this.width / 2 - spacing * 3 / 2 - 2, buttonTop, spacing, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.clear())
                .bounds(this.width / 2 + spacing / 2 + 2, buttonTop, spacing, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(this.width / 2 - spacing / 2, buttonTop + 24, spacing, 20)
                .build());
    }

    private int buttonTop() {
        return Math.max(52, this.height - 76);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Screen#render draws the background on this version.
        super.render(graphics, mouseX, mouseY, delta);

        int center = this.width / 2;
        int y = 20;

        graphics.drawCenteredString(this.font, this.title, center, y, TEXT);
        y += LINE + 6;

        graphics.drawCenteredString(this.font, StatusText.keyPrompt(this.feature), center, y, DIM);
        y += LINE + 2;

        graphics.drawCenteredString(this.font, Component.literal("Saved: " + this.boundDisplay()),
                center, y, DIM);
        y += LINE + 10;

        // The combination being built, which is the feedback that proves each press was recorded.
        graphics.drawCenteredString(this.font, Component.literal("New: " + this.capturedDisplay()),
                center, y, ACCENT);
        y += LINE + 2;

        if (!this.notice.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.literal(this.notice), center, y, WARN);
            y += LINE;
        }

        graphics.drawCenteredString(this.font, Component.literal(KeyNames.bindingHint()), center, y, DIM);
        y += LINE;
        graphics.drawCenteredString(this.font,
                Component.literal(this.captured.isEmpty()
                        ? "Press any keys, then Done - Esc confirms"
                        : "Press any keys, then Done - Esc clears"), center, y, DIM);
    }

    private String capturedDisplay() {
        if (this.captured.isEmpty()) {
            return "(nothing yet)";
        }

        return KeyCombo.of(this.captured).display(KeyNames::readable);
    }

    /** The binding being edited: a feature's, or the config key's when the feature is {@code null}. */
    private KeyCombo current() {
        return this.feature == null
                ? this.mod.config().configKey()
                : this.mod.config().key(this.feature);
    }

    private String boundDisplay() {
        KeyCombo combo = this.current();

        return combo.isBound() ? combo.display(KeyNames::readable) : "Unbound";
    }

    /**
     * Appends every press to the combination.
     *
     * <p>Nothing is committed here: capture continues until Done. That is what allows ordinary keys to be
     * part of a combination, since an automatic commit would end the capture on the first letter.</p>
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            // Escape works on the capture, not on the saved binding: with keys captured it empties the
            // list so the next press starts a fresh combination, and with nothing captured it confirms
            // the empty combination, like Done. Cancel is the way to leave without saving.
            if (this.captured.isEmpty()) {
                this.apply();
            } else {
                this.clear();
            }
            return true;
        }

        if (event.isConfirmation()) {
            // Enter confirms, like the Done button, instead of silently becoming part of the binding.
            this.apply();
            return true;
        }

        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(event.key());

        this.add(key.getName());
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // A click on one of this screen's own buttons must still work; only clicks outside them are
        // treated as a binding.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.button());

        this.add(key.getName());
        return true;
    }

    /** Records one key, ignoring repeats and unusable names. */
    private void add(String keyName) {
        if (keyName == null || keyName.isEmpty() || keyName.equals("key.keyboard.unknown")) {
            return;
        }

        if (this.captured.contains(keyName)) {
            this.notice = KeyNames.readable(keyName) + " is already part of this combination";
            this.rebuildWidgets();
            return;
        }

        this.captured.add(keyName);
        this.notice = "";
        this.rebuildWidgets();
    }

    private void undo() {
        if (!this.captured.isEmpty()) {
            this.captured.remove(this.captured.size() - 1);
            this.notice = "";
            this.rebuildWidgets();
        }
    }

    private void clear() {
        this.captured.clear();
        this.notice = "";
        this.rebuildWidgets();
    }

    /** Stores the captured combination and returns to the list. */
    private void apply() {
        KeyCombo combo = KeyCombo.of(this.captured);

        if (this.feature == null) {
            this.mod.config().setConfigKey(combo);
            this.mod.applyConfigKeyBinding();
        } else {
            this.mod.config().setKey(this.feature, combo);
            this.mod.applyKeyBinding(this.feature);
        }

        this.mod.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    /** Accepts the current combination; exposed so a test can drive the Done button's action. */
    void accept() {
        this.apply();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
