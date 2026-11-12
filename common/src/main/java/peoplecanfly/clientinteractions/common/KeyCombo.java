package peoplecanfly.clientinteractions.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One feature's hotkey: a set of keys that all have to be held at the same time.
 *
 * <h2>Why a set and not a key plus modifier flags</h2>
 * <p>Ctrl, Shift, Alt and Win are not special. Any key can take part, so {@code Ctrl+R}, {@code A+C} and
 * {@code Ctrl+Shift+Alt+Win+R} are the same kind of thing: several keys held together. Treating the
 * modifiers specially would have put an arbitrary limit on which keys may combine.</p>
 *
 * <h2>Matching</h2>
 * <p>Every key in the set must be down. A Shift/Ctrl/Alt/Win that is down but not part of the set blocks
 * the match, which is what keeps {@code R} from firing while Shift is held. Ordinary keys outside the set
 * are ignored by {@link #matches}, because the player is always holding something (W, space, ...).</p>
 *
 * <h2>Priority between overlapping bindings</h2>
 * <p>Because ordinary keys are ignored, {@code C} alone would also match while {@code A+C} is held. That is
 * wrong when one feature owns the longer combination: the longer binding has to win, or pressing {@code A}
 * and {@code C} would fire both. {@link #isShadowedBy} provides that rule, and the caller uses it to skip a
 * binding that is contained in a longer one currently being completed.</p>
 */
public final class KeyCombo {
    /** The vanilla prefix for keyboard keys. */
    public static final String KEYBOARD_PREFIX = "key.keyboard.";
    /** The vanilla prefix for mouse buttons. */
    public static final String MOUSE_PREFIX = "key.mouse.";

    /** The modifier key names vanilla defines. */
    private static final Set<String> MODIFIER_KEYS = Set.of(
            "key.keyboard.left.shift", "key.keyboard.right.shift",
            "key.keyboard.left.control", "key.keyboard.right.control",
            "key.keyboard.left.alt", "key.keyboard.right.alt",
            "key.keyboard.left.win", "key.keyboard.right.win");

    /** Keys in press order; the last one is the primary. Empty when unbound. */
    private final List<String> keys;

    private KeyCombo(List<String> keys) {
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public static KeyCombo unbound() {
        return new KeyCombo(List.of());
    }

    /** A combination of exactly these keys, in the order they were pressed. */
    public static KeyCombo of(List<String> keys) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();

        for (String key : keys) {
            if (key != null && !key.isEmpty()) {
                unique.add(key);
            }
        }

        return new KeyCombo(new ArrayList<>(unique));
    }

    /** A combination with one key plus the given modifier flags; the shape older configs used. */
    public static KeyCombo of(String keyName, boolean shift, boolean control, boolean alt, boolean win) {
        List<String> built = new ArrayList<>();

        if (control) {
            built.add("key.keyboard.left.control");
        }

        if (shift) {
            built.add("key.keyboard.left.shift");
        }

        if (alt) {
            built.add("key.keyboard.left.alt");
        }

        if (win) {
            built.add("key.keyboard.left.win");
        }

        if (keyName != null && !keyName.isEmpty()) {
            built.add(keyName);
        }

        return of(built);
    }

    /** A combo with the three modifiers older configs could store. */
    public static KeyCombo of(String keyName, boolean shift, boolean control, boolean alt) {
        return of(keyName, shift, control, alt, false);
    }

    /** Just the key, no modifiers. */
    public static KeyCombo of(String keyName) {
        return of(keyName, false, false, false, false);
    }

    /** Every key that has to be held, in press order. */
    public List<String> keys() {
        return this.keys;
    }

    /** The last key pressed, which is the one vanilla is told about. */
    public String primaryKey() {
        return this.keys.isEmpty() ? "" : this.keys.get(this.keys.size() - 1);
    }

    /** Kept for callers that only care about the key vanilla is registered on. */
    public String keyName() {
        return this.primaryKey();
    }

    public boolean isBound() {
        return !this.keys.isEmpty();
    }

    /** True when the primary key is a mouse button. */
    public boolean isMouse() {
        return this.primaryKey().startsWith(MOUSE_PREFIX);
    }

    public boolean hasMultipleKeys() {
        return this.keys.size() > 1;
    }

    /** How many keys have to be held. */
    public int size() {
        return this.keys.size();
    }

    /** True when every key of this combo is also part of {@code other}. */
    public boolean isSubsetOf(KeyCombo other) {
        return other != null && other.keys.containsAll(this.keys);
    }

    public static boolean isModifierKey(String keyName) {
        return keyName != null && MODIFIER_KEYS.contains(keyName);
    }

    /**
     * Every modifier key vanilla knows.
     *
     * <p>A combination blocks an extra modifier held outside it, so the caller has to poll these even when
     * no binding mentions them. Otherwise {@code R} would fire while Shift is down simply because Shift
     * was never sampled.</p>
     */
    public static Set<String> modifierKeys() {
        return MODIFIER_KEYS;
    }

    /** True when both combinations consist of exactly the same keys, in any order. */
    public boolean hasSameKeys(KeyCombo other) {
        return other != null
                && this.keys.size() == other.keys.size()
                && this.keys.containsAll(other.keys);
    }

    /**
     * True when {@code other} is a longer combination that this one is part of.
     *
     * <p>Used to give the longer binding priority: while {@code A+C} is held, a feature bound to plain
     * {@code C} must not also fire, because {@code C} is part of a combination that another feature owns.</p>
     */
    public boolean isShadowedBy(KeyCombo other) {
        return this.isBound()
                && other != null
                && other.isBound()
                && other.size() > this.size()
                && this.isSubsetOf(other);
    }

    /**
     * Whether the combination should fire, given every key currently held.
     *
     * <p>All of the combo's keys must be held, and no modifier outside the combo may be held. Ordinary keys
     * outside the combo are ignored; see {@link #isShadowedBy} for how a shorter binding yields to a longer
     * one that contains it.</p>
     */
    public boolean matches(Set<String> heldKeys) {
        if (!this.isBound()) {
            return false;
        }

        if (!heldKeys.containsAll(this.keys)) {
            return false;
        }

        for (String held : heldKeys) {
            if (MODIFIER_KEYS.contains(held) && !this.keys.contains(held)) {
                return false;
            }
        }

        return true;
    }

    /** A copy with one more key appended, keeping the order. */
    public KeyCombo plus(String keyName) {
        List<String> built = new ArrayList<>(this.keys);

        if (keyName != null && !keyName.isEmpty()) {
            built.add(keyName);
        }

        return of(built);
    }

    /** How the combination reads, e.g. {@code Ctrl+Shift+R}. */
    public String display(java.util.function.Function<String, String> keyNameToLabel) {
        if (!this.isBound()) {
            return "Unbound";
        }

        StringBuilder builder = new StringBuilder();

        // Read modifiers first and the main key last, which is how players write a combination.
        List<String> ordered = new ArrayList<>();

        for (String key : this.keys) {
            if (MODIFIER_KEYS.contains(key)) {
                ordered.add(key);
            }
        }

        for (String key : this.keys) {
            if (!MODIFIER_KEYS.contains(key)) {
                ordered.add(key);
            }
        }

        for (String key : ordered) {
            if (builder.length() > 0) {
                builder.append('+');
            }

            builder.append(keyNameToLabel.apply(key));
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof KeyCombo)) {
            return false;
        }

        return this.keys.equals(((KeyCombo) other).keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.keys);
    }

    @Override
    public String toString() {
        return this.isBound() ? String.join("+", this.keys) : "unbound";
    }
}
