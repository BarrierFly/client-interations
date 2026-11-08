package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import peoplecanfly.clientinteractions.common.Feature;

/**
 * Captures one key press and assigns it to one feature's key binding. Escape leaves it unbound.
 */
public final class KeyBindScreen extends Screen {
    private final Screen parent;
    private final ClientInteractionsClient mod;
    private final Feature feature;

    public KeyBindScreen(Screen parent, ClientInteractionsClient mod, Feature feature) {
        super(Component.literal("Rebind " + feature.label()));
        this.parent = parent;
        this.mod = mod;
        this.feature = feature;
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        // Screen#render does not draw a background on this version.
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, delta);

        drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        drawCenteredString(poseStack, this.font, "Press a key, or Escape to unbind",
                this.width / 2, this.height / 2 - 20, 0xA0A0A0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyMapping mapping = this.mod.keyFor(this.feature);

        if (mapping == null) {
            this.onClose();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            mapping.setKey(InputConstants.UNKNOWN);
        } else {
            mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        }

        this.mod.saveConfig();
        this.minecraft.setScreen(this.parent);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
