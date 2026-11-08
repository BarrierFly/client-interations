package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;

/**
 * Captures one key press and assigns it to one feature's key binding.
 *
 * <p>The vanilla key binds screen lists every binding at once, but each feature here needs its own
 * binding, so this small screen captures a single key. Escape leaves the feature unbound.</p>
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "Press a key, or Escape to unbind",
                this.width / 2, this.height / 2 - 20, 0xA0A0A0);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyMapping mapping = this.mod.keyFor(this.feature);

        if (mapping == null) {
            this.onClose();
            return true;
        }

        if (event.isEscape()) {
            // Escape means "unbound", which is how a feature is left without a hotkey.
            mapping.setKey(InputConstants.UNKNOWN);
        } else {
            mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
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
