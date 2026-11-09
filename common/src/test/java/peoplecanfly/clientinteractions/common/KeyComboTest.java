package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyComboTest {
    /** Convenience: a combo from key names in order. */
    private static KeyCombo combo(String... keys) {
        return KeyCombo.of(List.of(keys));
    }

    /** Convenience: a set of held keys. */
    private static Set<String> held(String... keys) {
        return Set.of(keys);
    }

    @Test
    @DisplayName("an unbound combination never matches")
    void unboundNeverMatches() {
        KeyCombo combo = KeyCombo.unbound();

        assertFalse(combo.isBound());
        assertFalse(combo.matches(held()));
        assertFalse(combo.matches(held("key.keyboard.a")));
    }

    @Test
    @DisplayName("a single key matches only while that key is held")
    void singleKey() {
        KeyCombo combo = combo("key.keyboard.r");

        assertTrue(combo.matches(held("key.keyboard.r")));
        assertFalse(combo.matches(held()));
        assertFalse(combo.matches(held("key.keyboard.t")));
    }

    @Test
    @DisplayName("every key of the combination must be held")
    void allKeysRequired() {
        KeyCombo combo = combo("key.keyboard.a", "key.keyboard.c");

        assertTrue(combo.matches(held("key.keyboard.a", "key.keyboard.c")));
        assertFalse(combo.matches(held("key.keyboard.a")));
        assertFalse(combo.matches(held("key.keyboard.c")));
    }

    @Test
    @DisplayName("order does not matter for matching")
    void orderDoesNotMatter() {
        KeyCombo combo = combo("key.keyboard.a", "key.keyboard.c");

        assertTrue(combo.matches(held("key.keyboard.c", "key.keyboard.a")));
    }

    @Test
    @DisplayName("an unrelated ordinary key does not block the match")
    void unrelatedOrdinaryKeysAreIgnored() {
        KeyCombo combo = combo("key.keyboard.r");

        // The player is always holding something, so W must not cancel a binding.
        assertTrue(combo.matches(held("key.keyboard.r", "key.keyboard.w", "key.keyboard.space")));
    }

    @Test
    @DisplayName("an extra modifier does block the match")
    void extraModifierBlocks() {
        KeyCombo combo = combo("key.keyboard.r");

        assertFalse(combo.matches(held("key.keyboard.r", "key.keyboard.left.shift")),
                "Shift+R must not fire a plain R binding");
        assertEquals("key.keyboard.r", combo.primaryKey());
    }

    @Test
    @DisplayName("ordinary keys plus modifiers work together")
    void ordinaryKeysWithModifiers() {
        KeyCombo combo = combo("key.keyboard.left.control", "key.keyboard.a", "key.keyboard.c");

        assertTrue(combo.matches(held("key.keyboard.left.control",
                "key.keyboard.a", "key.keyboard.c")));
        assertFalse(combo.matches(held("key.keyboard.a", "key.keyboard.c")),
                "Ctrl is part of the binding");
    }

    @Test
    @DisplayName("a longer binding shadows a shorter one it contains")
    void longerBindingShadowsShorter() {
        KeyCombo shortCombo = combo("key.keyboard.c");
        KeyCombo longCombo = combo("key.keyboard.a", "key.keyboard.c");

        assertTrue(shortCombo.isShadowedBy(longCombo),
                "C must yield to A+C");
        assertFalse(longCombo.isShadowedBy(shortCombo),
                "A+C must not yield to C");
    }

    @Test
    @DisplayName("a binding does not shadow an unrelated one")
    void unrelatedBindingsDoNotShadow() {
        KeyCombo c = combo("key.keyboard.c");
        KeyCombo ab = combo("key.keyboard.a", "key.keyboard.b");

        assertFalse(c.isShadowedBy(ab));
        assertFalse(ab.isShadowedBy(c));
    }

    @Test
    @DisplayName("equal-size combos never shadow each other")
    void equalSizeNoShadow() {
        KeyCombo ac = combo("key.keyboard.a", "key.keyboard.c");
        KeyCombo ab = combo("key.keyboard.a", "key.keyboard.b");

        assertFalse(ac.isShadowedBy(ab));
    }

    @Test
    @DisplayName("a mouse button is a valid key, alone and in a combination")
    void mouseButtons() {
        KeyCombo alone = combo("key.mouse.4");

        assertTrue(alone.isBound());
        assertTrue(alone.isMouse());
        assertTrue(alone.matches(held("key.mouse.4")));
        assertEquals("Mouse 4", KeyNames.readable("key.mouse.4"));

        KeyCombo withCtrl = combo("key.keyboard.left.control", "key.mouse.left");
        // The primary is the last key, which is the mouse button here.
        assertTrue(withCtrl.isMouse());
        assertTrue(withCtrl.matches(held("key.keyboard.left.control", "key.mouse.left")));
    }

    @Test
    @DisplayName("the last key pressed is the primary")
    void primaryIsLast() {
        assertEquals("key.keyboard.c", combo("key.keyboard.a", "key.keyboard.c").primaryKey());
        assertEquals("key.keyboard.k", combo("key.keyboard.k").primaryKey());
        assertEquals("", KeyCombo.unbound().primaryKey());
    }

    @Test
    @DisplayName("display reads modifiers first, then the main key")
    void displayOrder() {
        assertEquals("R", combo("key.keyboard.r").display(KeyNames::readable));
        assertEquals("Left Shift+R",
                combo("key.keyboard.left.shift", "key.keyboard.r").display(KeyNames::readable));
        assertEquals("Left Ctrl+Left Shift+R",
                combo("key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.r")
                        .display(KeyNames::readable));
        assertEquals("A+C", combo("key.keyboard.a", "key.keyboard.c").display(KeyNames::readable));
        assertEquals("Unbound", KeyCombo.unbound().display(KeyNames::readable));
    }

    @Test
    @DisplayName("the legacy modifier-flag shape still builds a set")
    void legacyShape() {
        KeyCombo combo = KeyCombo.of("key.keyboard.r", true, true, false, false);

        assertTrue(combo.keys().contains("key.keyboard.left.control"));
        assertTrue(combo.keys().contains("key.keyboard.left.shift"));
        assertFalse(combo.keys().contains("key.keyboard.left.alt"));
        assertEquals("key.keyboard.r", combo.primaryKey());
    }

    @Test
    @DisplayName("equality compares the exact key list")
    void equality() {
        assertEquals(combo("key.keyboard.a", "key.keyboard.c"), combo("key.keyboard.a", "key.keyboard.c"));
        assertEquals(combo("key.keyboard.a", "key.keyboard.c").hashCode(),
                combo("key.keyboard.a", "key.keyboard.c").hashCode());

        // Order is part of identity because the last key is the primary.
        assertFalse(combo("key.keyboard.a", "key.keyboard.c")
                .equals(combo("key.keyboard.c", "key.keyboard.a")));
        assertFalse(combo("key.keyboard.a").equals(combo("key.keyboard.b")));
    }

    @Test
    @DisplayName("duplicate keys are collapsed")
    void duplicatesCollapsed() {
        assertEquals(List.of("key.keyboard.a"), combo("key.keyboard.a", "key.keyboard.a").keys());
    }

    @Test
    @DisplayName("an empty key name is ignored")
    void emptyIgnored() {
        assertFalse(KeyCombo.of("").isBound());
        assertFalse(KeyCombo.of((String) null).isBound());
        assertEquals(List.of("key.keyboard.a"), combo("", "key.keyboard.a").keys());
    }

    @Test
    @DisplayName("modifier keys are recognised")
    void modifiersRecognised() {
        assertTrue(KeyCombo.isModifierKey("key.keyboard.left.shift"));
        assertTrue(KeyCombo.isModifierKey("key.keyboard.right.control"));
        assertTrue(KeyCombo.isModifierKey("key.keyboard.left.win"));
        assertFalse(KeyCombo.isModifierKey("key.keyboard.a"));
        assertFalse(KeyCombo.isModifierKey("key.mouse.left"));
    }

    @Test
    @DisplayName("readable names fall back sensibly")
    void readableNames() {
        assertEquals("A", KeyNames.readable("key.keyboard.a"));
        assertEquals("F5", KeyNames.readable("key.keyboard.f5"));
        assertEquals("Unbound", KeyNames.readable(""));
        assertEquals("Unbound", KeyNames.readable("key.keyboard.unknown"));
        assertEquals("Left Shift", KeyNames.readable("key.keyboard.left.shift"));
        assertEquals("Left Click", KeyNames.readable("key.mouse.left"));
    }
}
