package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Reads the four modifier keys in the way this Minecraft version exposes them.
 *
 * <p>Win (the Windows/Super key) is not part of the vanilla modifier API on any version, so it is polled
 * from the window handle directly. That is the only reason this class exists as a per-version file:
 * everything else delegates to vanilla.</p>
 */
final class Modifiers {
    /** SDL scancode of the left Win / Super key. */
    private static final int SDL_LEFT_WIN = org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_LGUI;
    /** SDL scancode of the right Win / Super key. */
    private static final int SDL_RIGHT_WIN = org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_RGUI;

    private final Minecraft client;

    Modifiers(Minecraft client) {
        this.client = client;
    }

    boolean shift() {
        return this.client.hasShiftDown();
    }

    boolean control() {
        return this.client.hasControlDown();
    }

    boolean alt() {
        return this.client.hasAltDown();
    }

    /** True when either Win / Super key is held, read straight from the window. */
    boolean win() {
        return InputConstants.isKeyDown(SDL_LEFT_WIN) || InputConstants.isKeyDown(SDL_RIGHT_WIN);
    }

    /** A short, stable description of what is held, e.g. {@code Ctrl+Shift}. */
    String describe() {
        StringBuilder builder = new StringBuilder();

        if (control()) {
            builder.append("Ctrl+");
        }

        if (shift()) {
            builder.append("Shift+");
        }

        if (alt()) {
            builder.append("Alt+");
        }

        if (win()) {
            builder.append("Win+");
        }

        if (builder.length() == 0) {
            return "none";
        }

        return builder.substring(0, builder.length() - 1);
    }
}
