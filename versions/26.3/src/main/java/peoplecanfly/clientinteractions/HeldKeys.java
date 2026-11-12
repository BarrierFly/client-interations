package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLMouse;

/**
 * Reads which keys are currently held, straight from the keyboard and mouse.
 *
 * <h2>Why not read only the key mappings</h2>
 * <p>Vanilla only tracks a key's {@code isDown} flag on mappings that actually exist. A key that no
 * mapping uses - an ordinary letter nobody bound, a modifier outside every binding, an extra mouse
 * button, or half of a combination whose primary key happens to be the other half - would simply be
 * missing from the held set. A combination is "any keys held together", so the state has to come from
 * the devices themselves.</p>
 *
 * <h2>How</h2>
 * <p>Every key name the mod stores is a vanilla key name, and {@code InputConstants.getKey(name)} turns
 * it back into the value the platform uses. Keyboards are polled with {@code InputConstants.isKeyDown}
 * (SDL keyboard state, already taking a scancode) and mouse buttons with {@code SDL_GetMouseState}. Only
 * the keys actually used by some binding (plus the modifiers and the attack/use buttons) are checked, so
 * the cost is a handful of lookups per tick.</p>
 *
 * <h2>Mouse buttons and the mod's own holds</h2>
 * <p>The mapping table is consulted as a fallback, because the in-game tests drive input through it and
 * because SDL only reports the five buttons with a named mask. That fallback is skipped for a button the
 * <em>mod</em> is holding, so a running hold cannot make its own combination look complete. The device
 * query is never skipped, so a real press is always seen.</p>
 */
final class HeldKeys {
    private HeldKeys() {
    }

    /**
     * The subset of {@code candidates} that is physically held right now.
     *
     * @param modHeldKeyNames keys the mod is holding itself; they may not count through the mapping
     *                        fallback, but a real physical press still does
     */
    static Set<String> heldAmong(Set<String> candidates, Minecraft client, Set<String> modHeldKeyNames) {
        Set<String> held = new LinkedHashSet<>();

        for (String name : candidates) {
            if (isHeld(name, client, modHeldKeyNames)) {
                held.add(name);
            }
        }

        return held;
    }

    /** Whether one key, named the vanilla way, is physically down. */
    static boolean isHeld(String keyName, Minecraft client, Set<String> modHeldKeyNames) {
        if (keyName == null || keyName.isEmpty() || keyName.equals("key.keyboard.unknown")) {
            return false;
        }

        InputConstants.Key key;

        try {
            key = InputConstants.getKey(keyName);
        } catch (RuntimeException e) {
            // A name the game does not know cannot be held.
            return false;
        }

        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }

        if (key.getType() == InputConstants.Type.MOUSE) {
            return isMouseDown(key, client, !modHeldKeyNames.contains(keyName));
        }

        return InputConstants.isKeyDown(key.getValue());
    }

    /**
     * The SDL button mask for a vanilla mouse key value, or {@code 0} for a button SDL does not expose.
     *
     * <p>This version numbers mouse buttons the SDL way: 1 is left, 2 middle, 3 right, 4 and 5 the two
     * side buttons.</p>
     */
    private static int buttonMask(int keyValue) {
        switch (keyValue) {
            case 1:
                return SDLMouse.SDL_BUTTON_LMASK;
            case 2:
                return SDLMouse.SDL_BUTTON_MMASK;
            case 3:
                return SDLMouse.SDL_BUTTON_RMASK;
            case 4:
                return SDLMouse.SDL_BUTTON_X1MASK;
            case 5:
                return SDLMouse.SDL_BUTTON_X2MASK;
            default:
                return 0;
        }
    }

    /**
     * Whether a mouse button is down: physically, or through a mapping bound to it.
     *
     * @param allowMappingFallback false for a button the mod is holding, so a hold cannot feed back into
     *                             the combination that switched it on
     */
    private static boolean isMouseDown(InputConstants.Key key, Minecraft client, boolean allowMappingFallback) {
        int mask = buttonMask(key.getValue());

        if (mask != 0 && (SDLMouse.nSDL_GetMouseState(0L, 0L) & mask) != 0) {
            return true;
        }

        if (!allowMappingFallback) {
            return false;
        }

        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.saveString().equals(key.getName()) && mapping.isDown()) {
                return true;
            }
        }

        return false;
    }
}
