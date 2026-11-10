package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Reads which keys are currently held, straight from the keyboard.
 *
 * <h2>Why not read every key mapping</h2>
 * <p>Vanilla only tracks a key's {@code isDown} flag on mappings that actually exist. A key bound to nothing
 * therefore never appears in the mapping list, so a combination such as {@code A+C} could never match:
 * {@code A} would simply be missing from the held set. A combination is "any keys held together", so the
 * state has to come from the keyboard itself, not from what the game happens to have bound.</p>
 *
 * <h2>How</h2>
 * <p>Every key name the mod stores is a vanilla key name, and {@code InputConstants.getKey(name)} turns it
 * back into the value the platform uses. Polling that value is exact and independent of the bindings. Only
 * the keys actually used by some binding are checked, so the cost is a handful of lookups per tick.</p>
 */
final class HeldKeys {
    /** Prefix vanilla uses for mouse button names. */
    private static final String MOUSE_PREFIX = "key.mouse.";

    private HeldKeys() {
    }

    /** The subset of {@code candidates} that is physically held right now. */
    static Set<String> heldAmong(Set<String> candidates, Minecraft client) {
        Set<String> held = new LinkedHashSet<>();

        for (String name : candidates) {
            if (isHeld(name, client)) {
                held.add(name);
            }
        }

        return held;
    }

    /** Whether one key, named the vanilla way, is physically down. */
    static boolean isHeld(String keyName, Minecraft client) {
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
            return isMouseDown(key, client);
        }

        return InputConstants.isKeyDown(client.getWindow().getWindow(), key.getValue());
    }

    /**
     * Whether a mouse button is down.
     *
     * <p>Vanilla routes mouse buttons through ordinary key mappings and always creates ones for the three
     * main buttons, so the mapping table is reliable for them. Using it also avoids depending on the mouse
     * being grabbed.</p>
     */
    private static boolean isMouseDown(InputConstants.Key key, Minecraft client) {
        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.saveString().equals(key.getName())) {
                return mapping.isDown();
            }
        }

        return false;
    }
}
