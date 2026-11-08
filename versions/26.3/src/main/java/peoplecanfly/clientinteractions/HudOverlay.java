package peoplecanfly.clientinteractions;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws the active channels in the top left corner.
 *
 * <p>26.x replaced {@code HudRenderCallback} with the layered HUD registry, and the renderer receives a
 * {@link GuiGraphicsExtractor} instead of a {@code GuiGraphics}.</p>
 */
public final class HudOverlay implements HudElement {
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ClientInteractionsClient.MOD_ID, "status"),
                new HudOverlay());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
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
            graphics.text(client.font, line, MARGIN, y, 0xFFFFE0B0);
            y += LINE_HEIGHT;
        }
    }
}
