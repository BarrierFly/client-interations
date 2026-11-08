package peoplecanfly.clientinteractions;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the active channels in the top left corner.
 */
public final class HudOverlay implements HudRenderCallback {
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker tickCounter) {
        ClientInteractionsClient mod = ClientInteractionsClient.get();

        if (mod == null || !mod.config().isHudEnabled()) {
            return;
        }

        List<String> lines = mod.hudLines();

        if (lines.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int y = MARGIN;

        for (String line : lines) {
            graphics.drawString(client.font, line, MARGIN, y, 0xFFFFE0B0);
            y += LINE_HEIGHT;
        }
    }
}
