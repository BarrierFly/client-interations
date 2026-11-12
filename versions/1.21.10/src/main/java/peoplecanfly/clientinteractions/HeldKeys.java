package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

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
 * and mouse buttons with GLFW. Only the keys actually used by some binding (plus the modifiers and the
 * attack/use buttons) are checked, so the cost is a handful of lookups per tick.</p>
 *

 * <h2>Mouse buttons and the mod's own holds</h2>
 * <p>The mapping table is consulted as a fallback, because the in-game tests drive input through it. That
 * fallback is skipped for a button the <em>mod</em> is holding, so a running hold cannot make its own
 * combination look complete. The device query is never skipped, so a real press is always seen.</p>
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

        return InputConstants.isKeyDown(client.getWindow(), key.getValue());
    }


    /**
     * Whether a mouse button is down: physically, or through a mapping bound to it.
     *
     * @param allowMappingFallback false for a button the mod is holding, so a hold cannot feed back into
     *                             the combination that switched it on
     */
    private static boolean isMouseDown(InputConstants.Key key, Minecraft client, boolean allowMappingFallback) {
        int button = key.getValue();

        if (button >= 0 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST
                && GLFW.glfwGetMouseButton(client.getWindow().handle(), button) == GLFW.GLFW_PRESS) {
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
