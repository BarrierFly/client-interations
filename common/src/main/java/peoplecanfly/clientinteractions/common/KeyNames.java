package peoplecanfly.clientinteractions.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates vanilla key names into display names and back.
 *
 * <p>Vanilla already has a translated name for every key it knows, e.g. {@code key.keyboard.r} becomes
 * {@code R}. That translation lives in the game, so this class only deals with the parts the mod needs
 * on its own: knowing which names are modifiers, and having a readable fallback when the game cannot
 * translate a name.</p>
 */
public final class KeyNames {
    /** The modifier keys vanilla knows, by key name, so they can be offered as combination hints. */
    private static final Map<String, String> MODIFIER_LABELS = new LinkedHashMap<>();

    static {
        MODIFIER_LABELS.put("key.keyboard.left.shift", "Left Shift");
        MODIFIER_LABELS.put("key.keyboard.right.shift", "Right Shift");
        MODIFIER_LABELS.put("key.keyboard.left.control", "Left Ctrl");
        MODIFIER_LABELS.put("key.keyboard.right.control", "Right Ctrl");
        MODIFIER_LABELS.put("key.keyboard.left.alt", "Left Alt");
        MODIFIER_LABELS.put("key.keyboard.right.alt", "Right Alt");
    }

    private KeyNames() {
    }

    /** True for the modifier keys themselves, which cannot be a combination's main key. */
    public static boolean isModifier(String keyName) {
        return keyName != null
                && (MODIFIER_LABELS.containsKey(keyName)
                || keyName.equals("key.keyboard.left.win")
                || keyName.equals("key.keyboard.right.win"));
    }

    /**
     * A readable name for a key, used when the game has no translation for it.
     *
     * <p>Vanilla names have the shape {@code key.keyboard.r} or {@code key.mouse.left}, so the part after
     * the last dot is the interesting one.</p>
     */
    public static String readable(String keyName) {
        if (keyName == null || keyName.isEmpty()) {
            return "Unbound";
        }

        if (keyName.equals("key.keyboard.unknown")) {
            return "Unbound";
        }

        String modifier = MODIFIER_LABELS.get(keyName);

        if (modifier != null) {
            return modifier;
        }

        int dot = keyName.lastIndexOf('.');

        if (dot < 0 || dot == keyName.length() - 1) {
            return keyName;
        }

        String tail = keyName.substring(dot + 1);

        if (tail.startsWith("f") && tail.length() <= 3) {
            // Function keys are already written as F1, F12, ...
            return tail.toUpperCase(java.util.Locale.ROOT);
        }

        return tail.toUpperCase(java.util.Locale.ROOT);
    }

    /** A short hint listing the modifiers that can be combined with a key. */
    public static String modifierHint() {
        return "Hold Ctrl, Shift or Alt for a combination";
    }
}
