package peoplecanfly.clientinteractions.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HotkeyRouterTest {
    private static KeyCombo combo(String... keys) {
        return KeyCombo.of(List.of(keys));
    }

    private static Set<String> held(String... keys) {
        return Set.of(keys);
    }

    private static ModConfig config() {
        return new ModConfig();
    }

    @Test
    @DisplayName("a plain binding fires once and not again while held")
    void firesOncePerPress() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result first = router.update(held("key.keyboard.k"), config);
        assertEquals(List.of(Feature.ATTACK_HOLD), first.toggles());

        HotkeyRouter.Result second = router.update(held("key.keyboard.k"), config);
        assertTrue(second.toggles().isEmpty(), "holding the key must not toggle again");

        router.update(held(), config);
        assertEquals(List.of(Feature.ATTACK_HOLD),
                router.update(held("key.keyboard.k"), config).toggles(),
                "releasing and pressing again is a new press");
    }

    @Test
    @DisplayName("a partial combination does nothing")
    void partialCombinationDoesNothing() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.c"));
        HotkeyRouter router = new HotkeyRouter();

        assertTrue(router.update(held("key.keyboard.a"), config).toggles().isEmpty());
        assertEquals(List.of(Feature.ATTACK_HOLD),
                router.update(held("key.keyboard.a", "key.keyboard.c"), config).toggles());
    }

    @Test
    @DisplayName("a longer combination blocks the shorter one it contains")
    void longerCombinationWins() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.c"));
        config.setKey(Feature.USE_HOLD, combo("key.keyboard.c"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result result = router.update(
                held("key.keyboard.a", "key.keyboard.c"), config);

        assertEquals(List.of(Feature.ATTACK_HOLD), result.toggles(),
                "only the longer binding may fire");
        assertEquals(Set.of("key.keyboard.a", "key.keyboard.c"), result.claimedKeys(),
                "the longer binding owns both keys");
    }

    @Test
    @DisplayName("the shorter binding works again once the longer one is released")
    void shorterWorksAfterLonger() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.c"));
        config.setKey(Feature.USE_HOLD, combo("key.keyboard.c"));
        HotkeyRouter router = new HotkeyRouter();

        assertEquals(List.of(Feature.ATTACK_HOLD),
                router.update(held("key.keyboard.a", "key.keyboard.c"), config).toggles());

        // Letting go of A leaves C held. The plain C binding now owns the key and fires once.
        assertEquals(List.of(Feature.USE_HOLD),
                router.update(held("key.keyboard.c"), config).toggles());
        assertTrue(router.update(held("key.keyboard.c"), config).toggles().isEmpty(),
                "and not again while it is still held");
    }

    @Test
    @DisplayName("the config key wins an exact tie against a feature")
    void configKeyWinsTie() {
        ModConfig config = config();
        config.setConfigKey(combo("key.keyboard.k"));
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result result = router.update(held("key.keyboard.k"), config);

        assertTrue(result.openConfig(), "the screen opens");
        assertTrue(result.toggles().isEmpty(), "and no feature is toggled by the same press");
    }

    @Test
    @DisplayName("a longer config combination shadows a feature's shorter one")
    void longerConfigKeyWins() {
        ModConfig config = config();
        config.setConfigKey(combo("key.keyboard.left.shift", "key.keyboard.k"));
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result result = router.update(
                held("key.keyboard.left.shift", "key.keyboard.k"), config);

        assertTrue(result.openConfig());
        assertTrue(result.toggles().isEmpty());
    }

    @Test
    @DisplayName("a held extra modifier blocks a plain binding")
    void extraModifierBlocks() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        assertTrue(router.update(held("key.keyboard.k", "key.keyboard.left.shift"), config)
                .toggles().isEmpty(), "Shift+K must not fire a plain K binding");
        assertFalse(router.update(held("key.keyboard.k", "key.keyboard.left.shift"), config)
                .claimedKeys().contains("key.keyboard.k"),
                "and K keeps its ordinary game action");
    }

    @Test
    @DisplayName("nothing is claimed while no complete combination is held")
    void noClaimWithoutCombination() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.left.shift", "key.keyboard.1"));

        assertTrue(HotkeyRouter.claimedKeys(held("key.keyboard.1"),
                held("key.keyboard.1"), config).isEmpty(),
                "pressing 1 alone must still select the hotbar slot");
        assertEquals(Set.of("key.keyboard.left.shift", "key.keyboard.1"),
                HotkeyRouter.claimedKeys(held("key.keyboard.left.shift", "key.keyboard.1"),
                        held("key.keyboard.1"), config));
    }

    @Test
    @DisplayName("a key joining a combination being composed is claimed, but the first key is not")
    void chainKeyIsClaimed() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD,
                combo("key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.r"));

        assertTrue(HotkeyRouter.claimedKeys(held("key.keyboard.left.control"),
                        held("key.keyboard.left.control"), config).isEmpty(),
                "the key that starts a chain keeps its ordinary action");

        assertEquals(Set.of("key.keyboard.left.shift"),
                HotkeyRouter.claimedKeys(
                        held("key.keyboard.left.control", "key.keyboard.left.shift"),
                        held("key.keyboard.left.shift"), config),
                "the second key is claimed while the combination is still being composed");
    }

    @Test
    @DisplayName("a chain key stays claimed while its chain holds and is handed back when it breaks")
    void chainKeyFollowsItsChain() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD,
                combo("key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.r"));

        // Second tick of the hold: Shift is no longer a fresh press, but it is still suppressed and the
        // chain is still viable, so the block continues.
        assertEquals(Set.of("key.keyboard.left.shift"),
                HotkeyRouter.claimedKeys(
                        held("key.keyboard.left.control", "key.keyboard.left.shift"),
                        held("key.keyboard.left.shift"), config),
                "a suppressed chain key stays claimed while the chain can still complete");

        // Ctrl released: no other key of the combination is held, so Shift acts again.
        assertTrue(HotkeyRouter.claimedKeys(held("key.keyboard.left.shift"),
                        held("key.keyboard.left.shift"), config).isEmpty(),
                "the chain key is handed back once the chain breaks");
    }

    @Test
    @DisplayName("a chain that can no longer complete does not eat keys")
    void foreignModifierStopsChain() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.left.control", "key.keyboard.r"));

        assertTrue(HotkeyRouter.claimedKeys(
                        held("key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.r"),
                        held("key.keyboard.r"), config).isEmpty(),
                "the extra Shift blocks the combination from ever completing, so R keeps its action");
    }

    @Test
    @DisplayName("a completing key is claimed even though it also sits on a longer chain")
    void completingKeyWinsOverChain() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.left.control", "key.keyboard.x"));
        config.setKey(Feature.USE_HOLD,
                combo("key.keyboard.left.control", "key.keyboard.x", "key.keyboard.z"));

        assertEquals(Set.of("key.keyboard.left.control", "key.keyboard.x"),
                HotkeyRouter.claimedKeys(held("key.keyboard.left.control", "key.keyboard.x"),
                        held("key.keyboard.x"), config),
                "the short binding fires and both of its keys are claimed");
    }

    @Test
    @DisplayName("the attack button is claimed while it composes a combination")
    void attackButtonChain() {
        ModConfig config = config();
        config.setKey(Feature.USE_HOLD,
                combo("key.keyboard.left.shift", "key.mouse.left", "key.keyboard.x"));

        assertEquals(Set.of("key.mouse.left"),
                HotkeyRouter.claimedKeys(held("key.keyboard.left.shift", "key.mouse.left"),
                        held("key.mouse.left"), config),
                "the click is claimed instead of attacking while the combination is incomplete");
    }

    @Test
    @DisplayName("an unbound feature never fires and claims nothing")
    void unboundDoesNothing() {
        ModConfig config = config();

        HotkeyRouter.Result result = new HotkeyRouter().update(held("key.keyboard.a"), config);

        assertTrue(result.toggles().isEmpty());
        assertFalse(result.openConfig());
        assertTrue(result.claimedKeys().isEmpty());
    }

    @Test
    @DisplayName("reset forgets an active press")
    void resetForgetsPress() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        router.update(held("key.keyboard.k"), config);
        router.reset();

        assertEquals(List.of(Feature.ATTACK_HOLD),
                router.update(held("key.keyboard.k"), config).toggles());
    }

    @Test
    @DisplayName("mouse buttons take part like any other key")
    void mouseButtonCombination() {
        ModConfig config = config();
        config.setKey(Feature.USE_PERIODIC_CLICK,
                combo("key.keyboard.left.shift", "key.mouse.left"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result result = router.update(
                held("key.keyboard.left.shift", "key.mouse.left"), config);

        assertEquals(List.of(Feature.USE_PERIODIC_CLICK), result.toggles());
        assertTrue(result.claimedKeys().contains("key.mouse.left"));
        assertTrue(HotkeyRouter.claimedKeys(held("key.mouse.left"), held("key.mouse.left"), config)
                .isEmpty(), "a plain click is not claimed");
    }

    @Test
    @DisplayName("two bindings of equal length do not shadow each other")
    void equalLengthBothFire() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.b"));
        config.setKey(Feature.ATTACK_PERIODIC_CLICK, combo("key.keyboard.c", "key.keyboard.d"));
        HotkeyRouter router = new HotkeyRouter();

        HotkeyRouter.Result result = router.update(
                held("key.keyboard.a", "key.keyboard.b", "key.keyboard.c", "key.keyboard.d"),
                config);

        assertEquals(2, result.toggles().size());
    }

    @Test
    @DisplayName("two bindings with the same keys only fire the first")
    void duplicateBindingsFireOnce() {
        ModConfig config = config();
        config.setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
        config.setKey(Feature.USE_HOLD, combo("key.keyboard.k"));
        HotkeyRouter router = new HotkeyRouter();

        assertEquals(1, router.update(held("key.keyboard.k"), config).toggles().size(),
                "one press must not toggle two features");
    }
}
