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

        assertTrue(combo.matches(false, false, false));
        assertFalse(combo.matches(true, false, false), "Shift+R must not fire a plain R binding");
        assertFalse(combo.matches(false, true, false));
        assertFalse(combo.matches(false, false, true));
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
    @DisplayName("display puts the modifiers in a stable order")
    void displayOrder() {
        assertEquals("R", KeyCombo.of("key.keyboard.r").display("R"));
        assertEquals("Shift+R", KeyCombo.of("key.keyboard.r", true, false, false).display("R"));
        assertEquals("Ctrl+R", KeyCombo.of("key.keyboard.r", false, true, false).display("R"));
        assertEquals("Alt+R", KeyCombo.of("key.keyboard.r", false, false, true).display("R"));
        assertEquals("Ctrl+Shift+Alt+R",
                KeyCombo.of("key.keyboard.r", true, true, true).display("R"));
        assertEquals("Unbound", KeyCombo.unbound().display("R"));
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
    }

    @Test
    @DisplayName("an empty key name is treated as unbound")
    void emptyIsUnbound() {
        assertFalse(KeyCombo.of("").isBound());
        assertFalse(KeyCombo.of(null).isBound());
        assertFalse(KeyCombo.of("", true, true, true).isBound());
    }

    @Test
    @DisplayName("readable names fall back sensibly")
    void readableNames() {
        assertEquals("R", KeyNames.readable("key.keyboard.r"));
        assertEquals("F5", KeyNames.readable("key.keyboard.f5"));
        assertEquals("LEFT", KeyNames.readable("key.mouse.left"));
        assertEquals("Unbound", KeyNames.readable(""));
        assertEquals("Unbound", KeyNames.readable("key.keyboard.unknown"));
        assertEquals("Left Shift", KeyNames.readable("key.keyboard.left.shift"));
    }

    @Test
    @DisplayName("modifier keys are recognised so they cannot become a main key")
    void modifiersAreRecognised() {
        assertTrue(KeyNames.isModifier("key.keyboard.left.shift"));
        assertTrue(KeyNames.isModifier("key.keyboard.right.control"));
        assertTrue(KeyNames.isModifier("key.keyboard.left.alt"));
        assertFalse(KeyNames.isModifier("key.keyboard.r"));
        assertFalse(KeyNames.isModifier(""));
        assertFalse(KeyNames.isModifier(null));
    }
}
