package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.KeyNames;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Captures one key <em>or mouse button</em>, with modifiers, and assigns it to one feature.
 *
 * <h2>How a combination is captured</h2>
 * <p>Modifiers are collected from the order the keys go down, not from a bit mask and not by polling. The
 * screen watches every key press; each modifier it sees is remembered, and the first non-modifier press
 * ends the combination. Two independent reasons make this the only dependable approach:</p>
 *
 * <ul>
 *   <li>Vanilla queues key events to the main thread, so polling the keyboard while handling an event can
 *       see a modifier the user already released.</li>
 *   <li>The modifier bits inside {@code KeyEvent} come from the platform layer and are not guaranteed to
 *       be populated - some test harnesses inject them as zero. Order of presses has no such dependency.</li>
 * </ul>
 *
 * <p>Because the modifiers are recorded here rather than read from the current state, releasing them
 * before the main key arrives cannot truncate the combination, which is what used to turn {@code Ctrl+Shift+K}
 * into a plain {@code K}.</p>
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

    /** Modifiers seen going down since the screen opened, in press order. */
    private final Set<Modifier> captured = EnumSet.noneOf(Modifier.class);

    /** The four modifiers a combination can carry. */
    private enum Modifier {
        SHIFT, CONTROL, ALT, WIN
    }

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
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        // Screen#render does not draw a background on this version.
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, delta);

        int center = this.width / 2;
        int y = 20;

        drawCenteredString(poseStack, this.font, this.title, center, y, TEXT);
        y += LINE + 6;

        drawCenteredString(poseStack, this.font, StatusText.keyPrompt(this.feature), center, y, DIM);
        y += LINE + 2;

        // What is bound right now, so the screen is never blank about the current state.
        drawCenteredString(poseStack, this.font,
                Component.literal("Now: " + this.keyDisplay()), center, y, ACCENT);
        y += LINE + 8;

        // What has been pressed so far: this is the feedback that proves modifiers are being collected.
        drawCenteredString(poseStack, this.font,
                Component.literal(this.capturedSoFar()), center, y, TEXT);
        y += LINE + 2;

        if (!this.notice.isEmpty()) {
            drawCenteredString(poseStack, this.font, Component.literal(this.notice), center, y, WARN);
            y += LINE;
        }

        drawCenteredString(poseStack, this.font, Component.literal(KeyNames.bindingHint()), center, y, DIM);
        y += LINE;
        drawCenteredString(poseStack, this.font, Component.literal("Esc or Clear unbinds"), center, y, DIM);
    }

    /** What the user has pressed so far, e.g. {@code Ctrl+Shift+...}. */
    private String capturedSoFar() {
        String prefix = this.prefix();

        return prefix.isEmpty() ? "Press a key or click a mouse button" : prefix + "...";
    }

    /** The modifiers captured so far, in the order they read best. */
    private String prefix() {
        StringBuilder builder = new StringBuilder();

        if (this.captured.contains(Modifier.CONTROL)) {
            builder.append("Ctrl+");
        }

        if (this.captured.contains(Modifier.SHIFT)) {
            builder.append("Shift+");
        }

        if (this.captured.contains(Modifier.ALT)) {
            builder.append("Alt+");
        }

        if (this.captured.contains(Modifier.WIN)) {
            builder.append("Win+");
        }

        return builder.toString();
    }

    private String keyDisplay() {
        KeyCombo combo = this.mod.config().key(this.feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        return combo.display(KeyNames.readable(combo.keyName()));
    }

    /**
     * Watches a key press, recording modifiers and finishing the combination on the first real key.
     *
     * <p>A modifier alone never closes the screen: it is remembered and the prompt updates. That is the
     * difference between "my combination was captured" and "the dialog closed after my first key".</p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.apply(KeyCombo.unbound());
            return true;
        }

        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
        String name = key.getName();

        Modifier modifier = modifierOf(name);

        if (modifier != null) {
            this.captured.add(modifier);
            this.notice = "";
            this.rebuildWidgets();
            return true;
        }

        this.apply(this.build(name));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // A click on one of this screen's own buttons must still work; only clicks outside them are
        // treated as a binding.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);

        this.apply(this.build(key.getName()));
        return true;
    }

    /** Builds the combination from the keys captured so far plus the main key just pressed. */
    private KeyCombo build(String mainKey) {
        return KeyCombo.of(mainKey,
                this.captured.contains(Modifier.SHIFT),
                this.captured.contains(Modifier.CONTROL),
                this.captured.contains(Modifier.ALT),
                this.captured.contains(Modifier.WIN));
    }

    /** Which modifier a key name is, or {@code null} when it is an ordinary key. */
    private static Modifier modifierOf(String keyName) {
        switch (keyName) {
            case "key.keyboard.left.shift":
            case "key.keyboard.right.shift":
                return Modifier.SHIFT;
            case "key.keyboard.left.control":
            case "key.keyboard.right.control":
                return Modifier.CONTROL;
            case "key.keyboard.left.alt":
            case "key.keyboard.right.alt":
                return Modifier.ALT;
            case "key.keyboard.left.win":
            case "key.keyboard.right.win":
                return Modifier.WIN;
            default:
                return null;
        }
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
