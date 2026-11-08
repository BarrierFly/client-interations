package peoplecanfly.clientinteractions.common;

import java.util.Objects;

/**
 * One feature's hotkey: a main key plus any combination of Shift, Ctrl, Alt and Win.
 *
 * <h2>Why the combination is not just a {@code KeyMapping}</h2>
 * <p>Vanilla {@code KeyMapping} holds a single key and cannot express {@code Ctrl+Shift+R}, and it can
 * only be registered with the keyboard or the mouse, not both. The mod therefore gives vanilla only the
 * <em>main</em> key (that is what makes vanilla report the press at all) and keeps the modifiers here.
 * The modifier state is then compared on every reported press.</p>
 *
 * <h2>The mouse is a key too</h2>
 * <p>Vanilla already routes mouse buttons through the same {@code KeyMapping} machinery
 * ({@code InputConstants.Type.MOUSE}), so a mouse button is stored as an ordinary key name such as
 * {@code key.mouse.4}. Nothing else is needed to bind one.</p>
 *
 * <h2>Matching is exact</h2>
 * <p>A binding of plain {@code R} must not fire while Shift is held, and {@code Shift+R} must not fire
 * without it, otherwise one press could trigger two features. Every modifier that is <em>not</em> part of
 * the binding must be up, and every one that is part of it must be down.</p>
 */
public final class KeyCombo {
    /** The vanilla prefix for keyboard keys. */
    public static final String KEYBOARD_PREFIX = "key.keyboard.";
    /** The vanilla prefix for mouse buttons. */
    public static final String MOUSE_PREFIX = "key.mouse.";

    private final String keyName;
    private final boolean shift;
    private final boolean control;
    private final boolean alt;
    private final boolean win;

    private KeyCombo(String keyName, boolean shift, boolean control, boolean alt, boolean win) {
        this.keyName = keyName == null ? "" : keyName;
        this.shift = shift;
        this.control = control;
        this.alt = alt;
        this.win = win;
    }

    public static KeyCombo unbound() {
        return new KeyCombo("", false, false, false, false);
    }

    public static KeyCombo of(String keyName, boolean shift, boolean control, boolean alt, boolean win) {
        if (keyName == null || keyName.isEmpty()) {
            return unbound();
        }

        return new KeyCombo(keyName, shift, control, alt, win);
    }

    /** A combination with the three modifiers the older config format could store. */
    public static KeyCombo of(String keyName, boolean shift, boolean control, boolean alt) {
        return of(keyName, shift, control, alt, false);
    }

    /** Just the key, no modifiers. */
    public static KeyCombo of(String keyName) {
        return of(keyName, false, false, false, false);
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

    public boolean hasWin() {
        return this.win;
    }

    public boolean isBound() {
        return !this.keyName.isEmpty();
    }

    /** True when the main key is a mouse button rather than a keyboard key. */
    public boolean isMouse() {
        return this.keyName.startsWith(MOUSE_PREFIX);
    }

    public boolean hasModifiers() {
        return this.shift || this.control || this.alt || this.win;
    }

    /**
     * Whether a press of this combination should fire, given the modifiers held at that moment.
     *
     * <p>Exact on both sides: a modifier that is part of the binding must be held, and one that is not
     * must be up.</p>
     */
    public boolean matches(boolean shiftDown, boolean controlDown, boolean altDown, boolean winDown) {
        return this.isBound()
                && this.shift == shiftDown
                && this.control == controlDown
                && this.alt == altDown
                && this.win == winDown;
    }

    /** Matching when Win is treated as not held, for callers that cannot observe it. */
    public boolean matches(boolean shiftDown, boolean controlDown, boolean altDown) {
        return this.matches(shiftDown, controlDown, altDown, false);
    }

    /**
     * How the combination is shown, e.g. {@code Ctrl+Shift+R}.
     *
     * <p>The order is fixed so the same binding always reads the same way: Ctrl, Shift, Alt, Win, key.</p>
     *
     * @param keyDisplayName the already translated name of the main key
     */
    public String display(String keyDisplayName) {
        if (!this.isBound()) {
            return "Unbound";
        }

        return this.modifierPrefix() + keyDisplayName;
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

        if (this.win) {
            builder.append("Win+");
        }

        return builder.toString();
    }

    /** The modifiers as a storage string, e.g. {@code ctrl+shift}; empty when there are none. */
    public String modifierString() {
        StringBuilder builder = new StringBuilder();

        if (this.control) {
            builder.append("ctrl+");
        }

        if (this.shift) {
            builder.append("shift+");
        }

        if (this.alt) {
            builder.append("alt+");
        }

        if (this.win) {
            builder.append("win+");
        }

        if (builder.length() == 0) {
            return "";
        }

        return builder.substring(0, builder.length() - 1);
    }

    /** Reads the storage string written by {@link #modifierString()}. */
    public static KeyCombo parse(String keyName, String modifiers) {
        String value = modifiers == null ? "" : modifiers.toLowerCase(java.util.Locale.ROOT);

        return of(keyName,
                value.contains("shift"),
                value.contains("ctrl"),
                value.contains("alt"),
                value.contains("win"));
    }

    public KeyCombo withKey(String keyName) {
        return KeyCombo.of(keyName, this.shift, this.control, this.alt, this.win);
    }

    /** A copy with the given modifiers replaced; the key is kept. */
    public KeyCombo withModifiers(boolean shift, boolean control, boolean alt, boolean win) {
        return KeyCombo.of(this.keyName, shift, control, alt, win);
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
                && this.win == combo.win
                && this.keyName.equals(combo.keyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.keyName, this.shift, this.control, this.alt, this.win);
    }

    @Override
    public String toString() {
        return this.isBound()
                ? this.modifierPrefix() + this.keyName
                : "unbound";
    }
}
