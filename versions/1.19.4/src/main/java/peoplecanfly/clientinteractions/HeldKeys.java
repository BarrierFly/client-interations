package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Reads which keys are currently held, so a combination can be matched against the whole keyboard.
 *
 * <h2>Why the whole keyboard</h2>
 * <p>A combination is any set of keys held together, so the four modifiers are not enough. Vanilla exposes
 * Shift/Ctrl/Alt through {@code Minecraft}, Win only through the window, and nothing else, so the state is
 * read from every registered key mapping plus those extras.</p>
 *
 * <p>Key mappings are the right source rather than a raw keyboard poll: vanilla keeps each mapping's
 * {@code isDown} flag in step with the physical keys, and mouse buttons go through the same table, which is
 * what makes a mouse button usable inside a combination.</p>
 */
final class HeldKeys {
    /** GLFW key code of the left Win / Super key. */
    private static final int GLFW_LEFT_WIN = 343;
    /** GLFW key code of the right Win / Super key. */
    private static final int GLFW_RIGHT_WIN = 347;

    private HeldKeys() {
    }

    /** The vanilla names of every key currently held, mouse buttons included. */
    static Set<String> collect(Minecraft client) {
        Set<String> held = new LinkedHashSet<>();

        for (KeyMapping mapping : client.options.keyMappings) {
            if (mapping.isDown() && !mapping.isUnbound()) {
                held.add(mapping.saveString());
            }
        }

        // Win is not a vanilla binding, so it is added from the window directly.
        if (isDown(client, GLFW_LEFT_WIN)) {
            held.add("key.keyboard.left.win");
        }

        if (isDown(client, GLFW_RIGHT_WIN)) {
            held.add("key.keyboard.right.win");
        }

        return held;
    }

    private static boolean isDown(Minecraft client, int keyCode) {
        return InputConstants.isKeyDown(client.getWindow().getWindow(), keyCode);
    }
}
