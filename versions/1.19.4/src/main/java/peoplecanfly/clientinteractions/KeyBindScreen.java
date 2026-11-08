package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.KeyNames;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * Captures one key press, with modifiers, and assigns it to one feature.
 *
 * <p>Vanilla key bindings hold a single key, so a combination is captured here and stored in the mod's
 * own config. The screen shows the feature, what is bound now, and which modifiers are held at this
 * instant, so the moment of binding is never silent.</p>
 */
public final class KeyBindScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int ACCENT = 0xFFFFE0B0;
    private static final int LINE = 11;

    private final Screen parent;
    private final ClientInteractionsClient mod;
    private final Feature feature;

    /** Set when a modifier key was pressed on its own, so the screen can say so. */
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
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        // Screen#render does not draw a background on this version.
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, delta);

        int center = this.width / 2;
        int y = 24;

        drawCenteredString(poseStack, this.font, this.title, center, y, TEXT);
        y += LINE + 6;

        drawCenteredString(poseStack, this.font, StatusText.keyPrompt(this.feature), center, y, DIM);
        y += LINE + 2;

        drawCenteredString(poseStack, this.font, Component.literal("Now: " + this.keyDisplay()),
                center, y, ACCENT);
        y += LINE + 10;

        drawCenteredString(poseStack, this.font, Component.literal(this.heldModifiers()), center, y, TEXT);
        y += LINE + 2;

        if (!this.notice.isEmpty()) {
            drawCenteredString(poseStack, this.font, Component.literal(this.notice), center, y, ACCENT);
            y += LINE;
        }

        drawCenteredString(poseStack, this.font, Component.literal("Esc or Clear button unbinds"),
                center, y, DIM);
    }

    private String heldModifiers() {
        boolean shift = Screen.hasShiftDown();
        boolean control = Screen.hasControlDown();
        boolean alt = Screen.hasAltDown();

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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.apply(KeyCombo.unbound());
            return true;
        }

        String name = InputConstants.Type.KEYSYM.getOrCreate(keyCode).getName();

        if (KeyNames.isModifier(name)) {
            this.notice = KeyNames.readable(name) + " is a modifier - hold it and press another key";
            this.rebuildWidgets();
            return true;
        }

        this.apply(KeyCombo.of(name,
                Screen.hasShiftDown(),
                Screen.hasControlDown(),
                Screen.hasAltDown()));
        return true;
    }

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
