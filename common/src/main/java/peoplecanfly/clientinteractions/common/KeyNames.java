package peoplecanfly.clientinteractions.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates vanilla key names into display names and back.
 *
 * <p>Vanilla already translates the keys it knows, so this class only covers what the mod has to decide
 * on its own: which names are modifiers, which are mouse buttons, and a readable fallback for a name the
 * game cannot translate.</p>
 */
public final class KeyNames {
    /** The modifier keys vanilla knows, by key name. */
    private static final Map<String, String> MODIFIER_LABELS = new LinkedHashMap<>();

    /** Mouse buttons, by the readable number vanilla uses after the last dot. */
    private static final Map<String, String> MOUSE_LABELS = new LinkedHashMap<>();

    static {
        MODIFIER_LABELS.put("key.keyboard.left.shift", "Left Shift");
        MODIFIER_LABELS.put("key.keyboard.right.shift", "Right Shift");
        MODIFIER_LABELS.put("key.keyboard.left.control", "Left Ctrl");
        MODIFIER_LABELS.put("key.keyboard.right.control", "Right Ctrl");
        MODIFIER_LABELS.put("key.keyboard.left.alt", "Left Alt");
        MODIFIER_LABELS.put("key.keyboard.right.alt", "Right Alt");
        MODIFIER_LABELS.put("key.keyboard.left.win", "Left Win");
        MODIFIER_LABELS.put("key.keyboard.right.win", "Right Win");

        MOUSE_LABELS.put("key.mouse.left", "Left Click");
        MOUSE_LABELS.put("key.mouse.right", "Right Click");
        MOUSE_LABELS.put("key.mouse.middle", "Middle Click");
        MOUSE_LABELS.put("key.mouse.4", "Mouse 4");
        MOUSE_LABELS.put("key.mouse.5", "Mouse 5");
        MOUSE_LABELS.put("key.mouse.6", "Mouse 6");
        MOUSE_LABELS.put("key.mouse.7", "Mouse 7");
        MOUSE_LABELS.put("key.mouse.8", "Mouse 8");
    }

    private KeyNames() {
    }

    /** True for the modifier keys themselves, which cannot be a combination's main key. */
    public static boolean isModifier(String keyName) {
        return keyName != null && MODIFIER_LABELS.containsKey(keyName);
    }

    /** True for a vanilla mouse button name. */
    public static boolean isMouse(String keyName) {
        return keyName != null && keyName.startsWith(KeyCombo.MOUSE_PREFIX);
    }

    /**
     * A readable name for a key, used when the game has no translation for it.
     *
     * <p>Vanilla names have the shape {@code key.keyboard.r} or {@code key.mouse.left}, so the part after
     * the last dot is the interesting one.</p>
     */
    public static String readable(String keyName) {
        if (keyName == null || keyName.isEmpty() || keyName.equals("key.keyboard.unknown")) {
            return "Unbound";
        }

        String modifier = MODIFIER_LABELS.get(keyName);

        if (modifier != null) {
            return modifier;
        }

        String mouse = MOUSE_LABELS.get(keyName);

        if (mouse != null) {
            return mouse;
        }

        int dot = keyName.lastIndexOf('.');

        if (dot < 0 || dot == keyName.length() - 1) {
            return keyName;
        }

        return keyName.substring(dot + 1).toUpperCase(java.util.Locale.ROOT);
    }

    /** A hint listing what can be combined and that the mouse is allowed. */
    public static String bindingHint() {
        return "Hold Ctrl, Shift, Alt or Win - or press a mouse button";
    }
}
