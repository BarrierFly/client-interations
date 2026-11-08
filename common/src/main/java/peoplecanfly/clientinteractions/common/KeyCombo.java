package peoplecanfly.clientinteractions.common;

import java.util.Objects;

/**
 * One feature's hotkey: a main key plus optional Shift, Ctrl and Alt.
 *
 * <p>Vanilla {@code KeyMapping} can only hold a single key and cannot express a combination, so the
 * combination is kept here next to the key name. Only the main key is given to the key mapping, which
 * makes vanilla report the press; the modifiers are checked separately and must match exactly.</p>
 *
 * <p>Matching is exact on purpose: a binding of plain {@code R} must not fire while Shift is held, and
 * a binding of {@code Shift+R} must not fire without it. Otherwise two features could trigger from one
 * key press.</p>
 */
public final class KeyCombo {
    private final String keyName;
    private final boolean shift;
    private final boolean control;
    private final boolean alt;

    private KeyCombo(String keyName, boolean shift, boolean control, boolean alt) {
        this.keyName = keyName == null ? "" : keyName;
        this.shift = shift;
        this.control = control;
        this.alt = alt;
    }

    public static KeyCombo unbound() {
        return new KeyCombo("", false, false, false);
    }

    public static KeyCombo of(String keyName, boolean shift, boolean control, boolean alt) {
        if (keyName == null || keyName.isEmpty()) {
            return unbound();
        }

        return new KeyCombo(keyName, shift, control, alt);
    }

    /** Just the key, no modifiers. */
    public static KeyCombo of(String keyName) {
        return of(keyName, false, false, false);
    }

    /** The vanilla key name of the main key, or an empty string when unbound. */
    public String keyName() {
        return this.keyName;
    }

    public boolean hasShift() {
        return this.shift;
    }

    public boolean hasControl() {
        return this.control;
    }

    public boolean hasAlt() {
        return this.alt;
    }

    public boolean isBound() {
        return !this.keyName.isEmpty();
    }

    public boolean hasModifiers() {
        return this.shift || this.control || this.alt;
    }

    /** Whether a press of this combination should fire, given the modifiers held at that moment. */
    public boolean matches(boolean shiftDown, boolean controlDown, boolean altDown) {
        return this.isBound()
                && this.shift == shiftDown
                && this.control == controlDown
                && this.alt == altDown;
    }

    /**
     * How the combination is shown, e.g. {@code Shift+Ctrl+R}.
     *
     * @param keyDisplayName the already translated name of the main key
     */
    public String display(String keyDisplayName) {
        if (!this.isBound()) {
            return "Unbound";
        }

        StringBuilder builder = new StringBuilder();

        if (this.control) {
            builder.append("Ctrl+");
        }

        if (this.shift) {
            builder.append("Shift+");
        }

        if (this.alt) {
            builder.append("Alt+");
        }

        return builder.append(keyDisplayName).toString();
    }

    /** The modifier part on its own, e.g. {@code Ctrl+Shift}; empty when there is none. */
    public String modifierPrefix() {
        StringBuilder builder = new StringBuilder();

        if (this.control) {
            builder.append("Ctrl+");
        }

        if (this.shift) {
            builder.append("Shift+");
        }

        if (this.alt) {
            builder.append("Alt+");
        }

        return builder.toString();
    }

    public KeyCombo withKey(String keyName) {
        return KeyCombo.of(keyName, this.shift, this.control, this.alt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof KeyCombo)) {
            return false;
        }

        KeyCombo combo = (KeyCombo) other;

        return this.shift == combo.shift
                && this.control == combo.control
                && this.alt == combo.alt
                && this.keyName.equals(combo.keyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.keyName, this.shift, this.control, this.alt);
    }

    @Override
    public String toString() {
        return this.isBound()
                ? this.modifierPrefix() + this.keyName
                : "unbound";
    }
}
