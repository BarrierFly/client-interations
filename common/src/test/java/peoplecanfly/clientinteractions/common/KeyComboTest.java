package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyComboTest {
    @Test
    @DisplayName("an unbound combination never matches")
    void unboundNeverMatches() {
        KeyCombo combo = KeyCombo.unbound();

        assertFalse(combo.isBound());
        assertFalse(combo.matches(false, false, false));
        assertFalse(combo.matches(true, true, true));
    }

    @Test
    @DisplayName("a plain key matches only with no modifiers held")
    void plainKeyNeedsNoModifiers() {
        KeyCombo combo = KeyCombo.of("key.keyboard.r");

        assertTrue(combo.matches(false, false, false, false));
        assertFalse(combo.matches(true, false, false, false), "Shift+R must not fire a plain R binding");
        assertFalse(combo.matches(false, true, false, false));
        assertFalse(combo.matches(false, false, true, false));
        assertFalse(combo.matches(false, false, false, true));
    }

    @Test
    @DisplayName("a combination matches only when the modifiers match exactly")
    void combinationNeedsExactModifiers() {
        KeyCombo combo = KeyCombo.of("key.keyboard.r", true, true, false);

        assertTrue(combo.matches(true, true, false));
        assertFalse(combo.matches(true, false, false), "missing Ctrl");
        assertFalse(combo.matches(false, true, false), "missing Shift");
        assertFalse(combo.matches(true, true, true), "unexpected Alt");
    }

    @Test
    @DisplayName("Shift+R and R are different bindings")
    void modifiersMakeTheBindingDistinct() {
        KeyCombo plain = KeyCombo.of("key.keyboard.r");
        KeyCombo shifted = KeyCombo.of("key.keyboard.r", true, false, false);

        assertTrue(plain.matches(false, false, false));
        assertFalse(shifted.matches(false, false, false));

        assertFalse(plain.matches(true, false, false));
        assertTrue(shifted.matches(true, false, false));

        assertFalse(plain.equals(shifted));
    }

    @Test
    @DisplayName("all four modifiers can be combined at once")
    void allFourModifiers() {
        KeyCombo combo = KeyCombo.of("key.keyboard.r", true, true, true, true);

        assertTrue(combo.matches(true, true, true, true));
        assertFalse(combo.matches(true, true, true, false), "Win missing");
        assertFalse(combo.matches(true, true, false, true), "Alt missing");
        assertEquals("Ctrl+Shift+Alt+Win+R", combo.display("R"));
    }

    @Test
    @DisplayName("Win is a real modifier, not ignored")
    void winIsMatched() {
        KeyCombo withWin = KeyCombo.of("key.keyboard.r", false, false, false, true);

        assertTrue(withWin.matches(false, false, false, true));
        assertFalse(withWin.matches(false, false, false, false));

        // A plain binding must not fire while Win is held either.
        assertFalse(KeyCombo.of("key.keyboard.r").matches(false, false, false, true));
    }

    @Test
    @DisplayName("a mouse button is a valid main key")
    void mouseButtonIsABinding() {
        KeyCombo combo = KeyCombo.of("key.mouse.4");

        assertTrue(combo.isBound());
        assertTrue(combo.isMouse());
        assertTrue(combo.matches(false, false, false, false));
        assertEquals("Mouse 4", KeyNames.readable("key.mouse.4"));
        assertEquals("Left Click", KeyNames.readable("key.mouse.left"));
    }

    @Test
    @DisplayName("a mouse button can carry modifiers like any key")
    void mouseButtonWithModifiers() {
        KeyCombo combo = KeyCombo.of("key.mouse.left", false, true, false, false);

        assertTrue(combo.isMouse());
        assertTrue(combo.matches(false, true, false, false));
        assertFalse(combo.matches(false, false, false, false));
        assertEquals("Ctrl+Left Click", combo.display("Left Click"));
    }

    @Test
    @DisplayName("display puts the modifiers in a stable order")
    void displayOrder() {
        assertEquals("R", KeyCombo.of("key.keyboard.r").display("R"));
        assertEquals("Shift+R", KeyCombo.of("key.keyboard.r", true, false, false).display("R"));
        assertEquals("Ctrl+R", KeyCombo.of("key.keyboard.r", false, true, false).display("R"));
        assertEquals("Alt+R", KeyCombo.of("key.keyboard.r", false, false, true).display("R"));
        assertEquals("Win+R", KeyCombo.of("key.keyboard.r", false, false, false, true).display("R"));
        assertEquals("Unbound", KeyCombo.unbound().display("R"));
    }

    @Test
    @DisplayName("the storage string round-trips every modifier combination")
    void storageRoundTrip() {
        for (int mask = 0; mask < 16; mask++) {
            boolean shift = (mask & 1) != 0;
            boolean control = (mask & 2) != 0;
            boolean alt = (mask & 4) != 0;
            boolean win = (mask & 8) != 0;

            KeyCombo combo = KeyCombo.of("key.keyboard.r", shift, control, alt, win);
            KeyCombo restored = KeyCombo.parse(combo.keyName(), combo.modifierString());

            assertEquals(combo, restored, "mask " + mask);
        }
    }

    @Test
    @DisplayName("a missing modifier string parses as no modifiers")
    void emptyModifiersParse() {
        assertEquals(KeyCombo.of("key.keyboard.r"), KeyCombo.parse("key.keyboard.r", ""));
        assertEquals(KeyCombo.of("key.keyboard.r"), KeyCombo.parse("key.keyboard.r", null));
    }

    @Test
    @DisplayName("equality covers the key and every modifier")
    void equality() {
        assertEquals(KeyCombo.of("key.keyboard.r", true, false, false),
                KeyCombo.of("key.keyboard.r", true, false, false));
        assertEquals(KeyCombo.of("key.keyboard.r", true, false, false).hashCode(),
                KeyCombo.of("key.keyboard.r", true, false, false).hashCode());

        assertFalse(KeyCombo.of("key.keyboard.r").equals(KeyCombo.of("key.keyboard.t")));
        assertFalse(KeyCombo.of("key.keyboard.r", true, false, false)
                .equals(KeyCombo.of("key.keyboard.r", false, true, false)));
        assertFalse(KeyCombo.of("key.keyboard.r", false, false, false, true)
                .equals(KeyCombo.of("key.keyboard.r")));
    }

    @Test
    @DisplayName("an empty key name is treated as unbound")
    void emptyIsUnbound() {
        assertFalse(KeyCombo.of("").isBound());
        assertFalse(KeyCombo.of(null).isBound());
        assertFalse(KeyCombo.of("", true, true, true, true).isBound());
    }

    @Test
    @DisplayName("readable names fall back sensibly")
    void readableNames() {
        assertEquals("R", KeyNames.readable("key.keyboard.r"));
        assertEquals("F5", KeyNames.readable("key.keyboard.f5"));
        assertEquals("Unbound", KeyNames.readable(""));
        assertEquals("Unbound", KeyNames.readable("key.keyboard.unknown"));
        assertEquals("Left Shift", KeyNames.readable("key.keyboard.left.shift"));
        assertEquals("Left Win", KeyNames.readable("key.keyboard.left.win"));
    }

    @Test
    @DisplayName("modifier keys are recognised so they cannot become a main key")
    void modifiersAreRecognised() {
        assertTrue(KeyNames.isModifier("key.keyboard.left.shift"));
        assertTrue(KeyNames.isModifier("key.keyboard.right.control"));
        assertTrue(KeyNames.isModifier("key.keyboard.left.alt"));
        assertTrue(KeyNames.isModifier("key.keyboard.left.win"));
        assertFalse(KeyNames.isModifier("key.keyboard.r"));
        assertFalse(KeyNames.isModifier("key.mouse.left"));
        assertFalse(KeyNames.isModifier(""));
        assertFalse(KeyNames.isModifier(null));
    }

    @Test
    @DisplayName("mouse names are recognised and are not modifiers")
    void mouseNames() {
        assertTrue(KeyNames.isMouse("key.mouse.left"));
        assertTrue(KeyNames.isMouse("key.mouse.8"));
        assertFalse(KeyNames.isMouse("key.keyboard.r"));
        assertFalse(KeyNames.isMouse(null));
    }
}
