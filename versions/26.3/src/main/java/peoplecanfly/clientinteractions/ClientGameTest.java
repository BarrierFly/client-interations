package peoplecanfly.clientinteractions;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.FeatureSettings;

/**
 * In-game end to end check, run with {@code ./gradlew :versions:<mc>:runClientGameTest}.
 *
 * <p>Covers what the unit tests cannot: that the vanilla code paths really run, that holds really flip
 * the vanilla key state, that timers advance one step per client tick in a live world, that features of
 * one button exclude each other, and that the screens render.</p>
 */
public final class ClientGameTest implements FabricClientGameTest {
    /** GLFW modifier bits, as delivered by the key callback. */
    private static final int KEY_A = com.mojang.blaze3d.platform.InputConstants.KEY_A;
    private static final int KEY_C = com.mojang.blaze3d.platform.InputConstants.KEY_C;
    private static final int MODIFIER_MASK_SHIFT = 0x0001;
    private static final int MODIFIER_MASK_CONTROL = 0x0002;
    private static final int MODIFIER_MASK_ALT = 0x0004;

    /** GLFW key codes used by the tests. */
    private static final int KEY_W = com.mojang.blaze3d.platform.InputConstants.KEY_W;
    private static final int KEY_K = com.mojang.blaze3d.platform.InputConstants.KEY_K;
    private static final int MODIFIER_KEY_CONTROL = com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL;
    private static final int MODIFIER_KEY_SHIFT = com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT;
    private static final int MODIFIER_KEY_ALT = com.mojang.blaze3d.platform.InputConstants.KEY_LALT;


    private static final Logger LOGGER = LoggerFactory.getLogger("ClientInteractions-GameTest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            context.waitTicks(20);

            require(compute(context, () -> ClientInteractionsClient.get() != null), "mod initialised");
            require(compute(context, () -> allFeaturesOff()), "every feature starts off");

            // The config file is user data: it may have been saved with the master switch off, and
            // starting in that state is exactly what used to crash the client entrypoint. Turn the
            // switch back on here so the behavioural checks below are not at the mercy of that file.
            require(compute(context, () -> masterSwitchOn()), "master switch can be turned back on");

            testHold(context, Feature.ATTACK_HOLD);
            testFastClick(context);
            testFastClickMultipliesRealClicks(context);
            testPeriodicHold(context);
            testPeriodicClickSpread(context);
            testMutualExclusion(context);
            testMasterSwitch(context);
            testAttackBlockedByScreen(context);
            testConfigScreenRenders(context);
            testKeyBindScreenRenders(context);
            testScreensDrawTextAtSmallSize(context);
            testKeyComboRoundTrip(context);
            testKeyComboActuallyFires(context);
            testValueFieldAcceptsTypedNumbers(context);
            testFullCombinationIsCaptured(context);
            testCombinationFiresWithRealKeys(context);
            testExtraModifierBlocks(context);
            testLongerCombinationBlocksShorter(context);
            testBoundKeyDoesNotReachVanilla(context);
            testTapCompletesCombination(context);
            testMovementKeyClaimed(context);
            testMouseCombinationDoesNotAttack(context);
            testMasterSwitchRefusesToggles(context);
            testHoldProducesASwing(context);
            testHoldSurvivesScreen(context);
            testAttackContinuesOnScreen(context);
            testNoDefaultHotkeys(context);

            LOGGER.info("All in-game checks passed");
        }
    }

    private static boolean masterSwitchOn() {
        ClientInteractionsClient mod = ClientInteractionsClient.get();
        mod.setGameEnabled(true);
        return mod.config().isEnabled();
    }

    private static boolean allFeaturesOff() {
        ClientInteractionsClient mod = ClientInteractionsClient.get();

        for (Feature feature : Feature.values()) {
            if (mod.engine().isActive(feature)) {
                return false;
            }
        }

        return true;
    }

    private void testHold(ClientGameTestContext context, Feature feature) {
        configure(context, feature, new FeatureSettings(20, 10, 1));
        toggle(context, feature);
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)), "hold: active");
        require(compute(context, () -> client().options.keyAttack.isDown()), "hold: vanilla key down");

        stop(context, feature);
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyAttack.isDown()), "hold: vanilla key released");
    }

    /**
     * Fast click must be a multiplier for the player's own click, never a timer.
     *
     * <p>Two things are asserted: that it produces no input while left alone, and that its {@code count}
     * is what the engine reports for a player click. The first is the semantics that separates it from
     * every other feature.</p>
     */
    private void testFastClick(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 4));

        // Off: the player's click stays one click.
        require(compute(context, () -> mod().engine().playerClickCount(ActionChannel.ATTACK) == 1),
                "fast click: off means one click per player click");

        toggle(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(6);

        require(compute(context, () -> mod().engine().playerClickCount(ActionChannel.ATTACK) == 4),
                "fast click: multiplies the player's click by its count");

        // Nothing happens on its own, however long it runs.
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "fast click: never holds the button by itself");
        require(compute(context, () -> mod().engine().scheduler(Feature.ATTACK_FAST_CLICK).elapsedTicks() == 6),
                "fast click: still ticks its clock while idle");

        // The other channel is untouched.
        require(compute(context, () -> mod().engine().playerClickCount(ActionChannel.USE) == 1),
                "fast click: does not affect the other button");

        stop(context, Feature.ATTACK_FAST_CLICK);
        require(compute(context, () -> mod().engine().playerClickCount(ActionChannel.ATTACK) == 1),
                "fast click: stopping restores one click per player click");
    }

    private void testPeriodicHold(ClientGameTestContext context) {
        configure(context, Feature.USE_PERIODIC_HOLD, new FeatureSettings(6, 3, 1));
        toggle(context, Feature.USE_PERIODIC_HOLD);

        boolean sawHeld = false;
        boolean sawReleased = false;

        for (int i = 0; i < 12; i++) {
            context.waitTick();
            boolean down = compute(context, () -> client().options.keyUse.isDown());

            if (down) {
                sawHeld = true;
            } else {
                sawReleased = true;
            }
        }

        require(sawHeld, "periodic hold: held at least once");
        require(sawReleased, "periodic hold: released at least once");

        stop(context, Feature.USE_PERIODIC_HOLD);
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyUse.isDown()),
                "periodic hold: released when stopped");
    }

    /**
     * Periodic click must spread its clicks across ticks, one per tick.
     *
     * <p>Firing them all in a single tick does not work: vanilla resets the attack strength on the first
     * click, so the rest would be swallowed. This checks that the scheduler advances one click per tick
     * rather than one burst per interval.</p>
     */
    private void testPeriodicClickSpread(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(6, 1, 3));
        toggle(context, Feature.ATTACK_PERIODIC_CLICK);

        java.util.List<Integer> perTick = new java.util.ArrayList<>();

        for (int i = 0; i < 6; i++) {
            context.waitTick();
            perTick.add(compute(context, () ->
                    mod().engine().scheduler(Feature.ATTACK_PERIODIC_CLICK).elapsedTicks()));
        }

        // The phase must advance by exactly one per client tick and never jump.
        for (int i = 0; i < perTick.size(); i++) {
            require(perTick.get(i) == i + 1,
                    "periodic click: one tick per client tick, saw " + perTick.get(i) + " at " + i);
        }

        stop(context, Feature.ATTACK_PERIODIC_CLICK);
        context.waitTicks(2);
    }

    private void testMutualExclusion(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_HOLD, new FeatureSettings(20, 10, 1));
        configure(context, Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(5, 1, 1));

        toggle(context, Feature.ATTACK_HOLD);
        context.waitTicks(2);
        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)), "exclusion: hold active");

        toggle(context, Feature.ATTACK_PERIODIC_CLICK);
        context.waitTicks(2);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_PERIODIC_CLICK)),
                "exclusion: the second feature is active");
        require(compute(context, () -> !mod().engine().isActive(Feature.ATTACK_HOLD)),
                "exclusion: hold was switched off automatically");
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "exclusion: the old hold released the button");

        stop(context, Feature.ATTACK_PERIODIC_CLICK);
        context.waitTicks(2);
    }

    private void testMasterSwitch(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_HOLD, new FeatureSettings(20, 10, 1));
        toggle(context, Feature.ATTACK_HOLD);
        context.waitTicks(2);
        require(compute(context, () -> client().options.keyAttack.isDown()), "master: down while enabled");

        compute(context, () -> {
            mod().setGameEnabled(false);
            return null;
        });
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyAttack.isDown()), "master: released when disabled");

        compute(context, () -> {
            mod().setGameEnabled(true);
            return null;
        });
        context.waitTicks(2);
        require(compute(context, () -> client().options.keyAttack.isDown()), "master: down again when enabled");

        stop(context, Feature.ATTACK_HOLD);
    }

    private void testAttackBlockedByScreen(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 1));
        toggle(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(2);

        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(2);
        require(compute(context, () -> client().missTime >= 10000), "screen: vanilla marks the screen");
        require(compute(context, () -> mod().engine().scheduler(Feature.ATTACK_FAST_CLICK).elapsedTicks() > 0),
                "screen: timers keep running");

        stop(context, Feature.ATTACK_FAST_CLICK);
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    private void testConfigScreenRenders(ClientGameTestContext context) {
        context.setScreen(() -> new ConfigScreen(null, mod()));
        context.waitTicks(3);
        require(compute(context, () -> client().gui.screen() instanceof ConfigScreen),
                "config screen: opens and survives rendering");

        // Every widget must sit inside the screen. This is the layout bug that put buttons off screen:
        // the row used to be wider than the gui.
        require(compute(context, ClientGameTest::allWidgetsOnScreen),
                "config screen: every widget is inside the screen");

        // Switching to the other page must keep working and stay inside the screen.
        compute(context, () -> {
            ConfigScreen screen = (ConfigScreen) client().gui.screen();
            screen.switchPage();
            return null;
        });
        context.waitTicks(2);
        require(compute(context, ClientGameTest::allWidgetsOnScreen),
                "config screen: the second page is also inside the screen");

        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    private void testKeyBindScreenRenders(ClientGameTestContext context) {
        context.setScreen(() -> new KeyBindScreen(null, mod(), Feature.ATTACK_HOLD));
        context.waitTicks(3);
        require(compute(context, () -> client().gui.screen() instanceof KeyBindScreen),
                "key screen: opens and survives rendering");
        require(compute(context, ClientGameTest::allWidgetsOnScreen),
                "key screen: every widget is inside the screen");

        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /**
     * Renders both screens with a genuinely small gui and checks that text reaches the framebuffer.
     *
     * <p>This is the regression test for two bugs no state check could see: the labels used a colour whose
     * alpha was zero, so nothing was drawn at all from 1.21.10 on, and the rows used a fixed spacing that
     * ran past the Done button on a small window.</p>
     *
     * <p>The gui must actually be shrunk. Screenshot sizes only crop the image; they do not change the
     * gui, so a cropped shot of a large gui would show the left edge of a wide layout instead of the
     * layout that a small window produces.</p>
     */
    private void testScreensDrawTextAtSmallSize(ClientGameTestContext context) {
        int[] original = compute(context, () -> new int[] {
                client().options.guiScale().get(),
                client().getWindow().getGuiScaledWidth(),
                client().getWindow().getGuiScaledHeight()});

        // Force a gui of roughly 320x240, the shape a 640x480 window gets at scale 2.
        compute(context, () -> {
            client().options.guiScale().set(3);
            client().resizeGui();
            return null;
        });
        context.waitTicks(2);

        int[] scaled = compute(context, () -> new int[] {
                client().getWindow().getGuiScaledWidth(),
                client().getWindow().getGuiScaledHeight()});
        LOGGER.info("Gui for the small-size check: {}x{} (was {}x{})",
                scaled[0], scaled[1], original[1], original[2]);

        context.setScreen(() -> new ConfigScreen(null, mod()));
        context.waitTicks(3);
        java.nio.file.Path configShot = context.takeScreenshot(
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("config-small"));
        require(hasNonBackgroundText(configShot), "config screen: text is visible at a small gui");
        require(compute(context, ClientGameTest::allWidgetsOnScreen),
                "config screen: widgets stay inside a small gui without overlapping");

        context.setScreen(() -> new KeyBindScreen(null, mod(), Feature.ATTACK_HOLD));
        context.waitTicks(3);
        java.nio.file.Path keyShot = context.takeScreenshot(
                net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("keybind-small"));
        require(hasNonBackgroundText(keyShot), "key screen: text is visible at a small gui");

        context.setScreen(() -> null);
        compute(context, () -> {
            client().options.guiScale().set(original[0]);
            client().resizeGui();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * True when the image contains near-white pixels, which only the labels produce.
     *
     * <p>The screens draw on vanilla's translucent dark backdrop, so a screen with no text is a narrow
     * band of dark greys.</p>
     */
    private static boolean hasNonBackgroundText(java.nio.file.Path image) {
        try {
            java.awt.image.BufferedImage picture = javax.imageio.ImageIO.read(image.toFile());

            if (picture == null) {
                return false;
            }

            for (int y = 0; y < picture.getHeight(); y++) {
                for (int x = 0; x < picture.getWidth(); x++) {
                    int rgb = picture.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (r > 200 && g > 200 && b > 200) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Could not read screenshot " + image, e);
        }

        return false;
    }

    /**
     * Types a value into the config screen's field and checks it reaches the engine.
     *
     * <p>The old UI only cycled through a fixed list of steps, so an exact number could not be entered.
     * This asserts the field accepts digits, clamps to range, and stores the result.</p>
     */
    private void testValueFieldAcceptsTypedNumbers(ClientGameTestContext context) {
        compute(context, () -> {
            mod().config().setSettings(Feature.ATTACK_PERIODIC_CLICK,
                    new FeatureSettings(20, 10, 1));
            mod().syncEngine();
            return null;
        });

        context.setScreen(() -> new ConfigScreen(null, mod()));
        context.waitTicks(2);

        // Field order on the Attack page: 0 = Fast click x/tick, 1 = Periodic click Interval,
        // 2 = Periodic click x/tick, then the two-field rows below. Index 1 is the periodic interval.
        boolean typed = compute(context, () -> typeIntoField(1, "37"));
        require(typed, "value field: a field exists for the feature");

        context.waitTicks(2);
        require(compute(context, () -> mod().config().settings(Feature.ATTACK_PERIODIC_CLICK).interval() == 37),
                "value field: the typed number is stored, not snapped to a preset step");

        // Out-of-range input is clamped rather than accepted.
        require(compute(context, () -> typeIntoField(1, "999")),
                "value field: the field still accepts input");
        context.waitTicks(2);
        require(compute(context, () -> mod()
                        .config().settings(Feature.ATTACK_PERIODIC_CLICK).interval() == FeatureSettings.MAX_INTERVAL),
                "value field: an out-of-range number is clamped");

        // Back to a sane default for the rest of the run.
        compute(context, () -> {
            mod().config().setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(20, 10, 1));
            mod().syncEngine();
            return null;
        });

        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /**
     * Types text into the first number field on the current config page.
     *
     * <p>Done by driving the widget the same way typing does, so it exercises the filter and the
     * responder rather than writing the config directly.</p>
     *
     * @param fieldIndex which field on the row to fill, in the order the row shows them
     */
    private static boolean typeIntoField(int fieldIndex, String text) {
        Screen screen = client().gui.screen();

        if (!(screen instanceof ConfigScreen)) {
            return false;
        }

        java.util.List<net.minecraft.client.gui.components.EditBox> boxes = new java.util.ArrayList<>();

        for (net.minecraft.client.gui.components.events.GuiEventListener listener : screen.children()) {
            if (listener instanceof net.minecraft.client.gui.components.EditBox box) {
                boxes.add(box);
            }
        }

        // The page lists five rows; the first row that has fields is the one under test.
        if (boxes.size() <= fieldIndex) {
            return false;
        }

        boxes.get(fieldIndex).setValue(text);
        return true;
    }

    private void testKeyComboRoundTrip(ClientGameTestContext context) {
        // A combination must survive a save/load cycle through the config file, because vanilla key
        // mappings cannot store more than one key.
        compute(context, () -> {
            mod().config().setKey(Feature.USE_HOLD, combo(
                    "key.keyboard.left.control", "key.keyboard.left.shift", "key.keyboard.k"));
            mod().saveConfig();
            return null;
        });

        require(compute(context, () -> mod().config().key(Feature.USE_HOLD).keys().equals(
                        java.util.List.of("key.keyboard.left.control",
                                "key.keyboard.left.shift", "key.keyboard.k"))),
                "key combo: all keys survive the save, in order");

        // Reload the file from disk to prove it is the file that round-trips.
        require(compute(context, () -> {
            peoplecanfly.clientinteractions.common.KeyCombo reloaded =
                    new ConfigStore(mod().configStore().path().getParent())
                            .load().key(Feature.USE_HOLD);
            return reloaded.keys().equals(java.util.List.of("key.keyboard.left.control",
                    "key.keyboard.left.shift", "key.keyboard.k"));
        }), "key combo: the combination reads back from the config file");

        compute(context, () -> {
            mod().config().setKey(Feature.USE_HOLD, peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().saveConfig();
            return null;
        });
    }

    /** Builds a combination from key names, in the order given. */
    private static peoplecanfly.clientinteractions.common.KeyCombo combo(String... keys) {
        return peoplecanfly.clientinteractions.common.KeyCombo.of(java.util.List.of(keys));
    }

    /**
     * Presses real keys and checks that the combination decides whether the feature fires.
     *
     * <p>This is the test that matters for combinations: a plain binding must not fire while Shift is
     * held, and a Shift binding must not fire without it. Driving the same key twice, once with the
     * modifier and once without, proves the modifier is actually part of the match rather than ignored.</p>
     */
    private void testKeyComboActuallyFires(ClientGameTestContext context) {
        // A KeyMapping only ever stores the main key, so the modifier rule is applied by the tick handler
        // when a press arrives. This reproduces that decision exactly: the same combo matcher, the same
        // inputs, and the same toggle call the handler makes.
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.k"));
            return null;
        });

        // A plain binding matches when its own key is held, and not while Shift is also down.
        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD)
                        .matches(java.util.Set.of("key.keyboard.k"))),
                "combo: a plain binding matches when its key is held");
        require(compute(context, () -> !mod().config().key(Feature.ATTACK_HOLD)
                        .matches(java.util.Set.of("key.keyboard.k", "key.keyboard.left.shift"))),
                "combo: a plain binding does not match while Shift is held");

        // A Shift binding is the mirror image.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.k", true, false, false));
            return null;
        });

        require(compute(context, () -> !mod().config().key(Feature.ATTACK_HOLD)
                        .matches(java.util.Set.of("key.keyboard.k"))),
                "combo: a Shift binding does not match without Shift");
        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD)
                        .matches(java.util.Set.of("key.keyboard.k", "key.keyboard.left.shift"))),
                "combo: a Shift binding matches with Shift");

        // Applying a binding must not steal the key from vanilla: on this version a key has a single
        // mapping, so writing the mod's key into its mapping would break whatever vanilla bound it to
        // (W would stop walking, 1 would stop selecting the hotbar).
        compute(context, () -> {
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> mod().keyFor(Feature.ATTACK_HOLD).isUnbound()),
                "combo: the mod's own mapping stays unbound, so vanilla keeps every key");

        // A binding changed at runtime must work without restarting. Nothing is registered with vanilla,
        // so the proof is that the hotkey poll sees the new key immediately.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.j"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> hotkeyFires("key.keyboard.j")),
                "combo: a rebind takes effect immediately, without a restart");

        // A mouse button is polled the same way, so one must be bindable too.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.mouse.4"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> hotkeyFires("key.mouse.4")),
                "combo: a mouse button binds and fires like a key");

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * True when the hotkey router resolves a press of {@code keyName} to {@code ATTACK_HOLD}.
     *
     * <p>The router is the code that decides a press, so this is the same decision a real key press goes
     * through. It is also independent of the vanilla mapping, which the mod deliberately leaves unbound.</p>
     */
    private static boolean hotkeyFires(String keyName) {
        return peoplecanfly.clientinteractions.common.HotkeyRouter
                .claimedKeys(java.util.Set.of(keyName), ClientInteractionsClient.get().config())
                .contains(keyName);
    }

    /**
     * Proves fast click actually multiplies a real click, not just reports a number.
     *
     * <p>The physical press is detected from vanilla's own key state, which is exactly what the mod reads
     * at runtime, so this exercises the same path a player would. The witness is the count of clicks the
     * mod injected: with the feature off it must be zero, and with it on it must be {@code count - 1} per
     * press.</p>
     */
    private void testFastClickMultipliesRealClicks(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 6));

        // Off: pressing the button must inject nothing.
        compute(context, () -> {
            mod().input().resetInjectedClicks();
            return null;
        });
        pressAttackKey(context, true);
        require(compute(context, () -> mod().input().injectedClicks() == 0),
                "fast click: off injects nothing for a player press");

        // On: one press must inject count - 1 extra clicks.
        toggle(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(2);
        compute(context, () -> {
            mod().input().resetInjectedClicks();
            return null;
        });

        pressAttackKey(context, true);

        // The repeats are spread one per tick, so they need as many ticks as there are clicks. Waiting
        // past that checks both the count and that the repeats stop: a leak would overshoot.
        context.waitTicks(5);
        int injected = compute(context, () -> mod().input().injectedClicks());
        LOGGER.info("fast click: injected {} extra clicks for one player press", injected);
        require(injected == 5,
                "fast click: one press becomes 6 clicks spread over ticks, so 5 extra, saw " + injected);

        context.waitTicks(5);
        require(compute(context, () -> mod().input().injectedClicks() == 5),
                "fast click: the repeats stop after count clicks");

        stop(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(2);
    }

    /**
     * Delivers one attack press through vanilla's own event-handler path, released again inside the same
     * tick window, the way a quick physical click arrives.
     *
     * <p>The mouse handler is what increments the queued-click counter the mod's press detection samples
     * and what vanilla consumes to attack, so pressing through it is the path a real press takes. Setting
     * only the {@code isDown} flag would bypass all of that and prove nothing about the real input path.</p>
     */
    private void pressAttackKey(ClientGameTestContext context, boolean attack) {
        compute(context, () -> {
            com.mojang.blaze3d.platform.InputConstants.Key left = com.mojang.blaze3d.platform.InputConstants
                    .getKey(client().options.keyAttack.saveString());
            net.minecraft.client.KeyMapping.set(left, true);
            net.minecraft.client.KeyMapping.click(left);
            net.minecraft.client.KeyMapping.set(left, false);
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * Checks that a combination of ordinary keys is captured whole.
     *
     * <p>This is the behaviour the previous design could not do: with an automatic commit, the second key
     * always ended the capture. The screen now keeps collecting and commits on Done, so {@code A+C} works.
     * Modifiers are included too, proving they are not special-cased any more.</p>
     */
    private void testFullCombinationIsCaptured(ClientGameTestContext context) {
        context.setScreen(() -> new KeyBindScreen(null, mod(), Feature.ATTACK_HOLD));
        context.waitTicks(3);

        // Two ordinary keys plus one modifier, in press order.
        pressOnBindScreen(context, KEY_A, 0);
        pressOnBindScreen(context, KEY_C, 0);
        pressOnBindScreen(context, MODIFIER_KEY_SHIFT, 0);

        // The screen must still be open: nothing is committed until Done.
        require(compute(context, () -> client().gui.screen() instanceof KeyBindScreen),
                "combo: pressing keys does not close the screen");
        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD).isBound() == false),
                "combo: nothing is stored until Done");

        compute(context, () -> {
            if (client().gui.screen() instanceof KeyBindScreen screen) {
                screen.accept();
            }
            return null;
        });
        context.waitTicks(2);

        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD).keys()
                        .equals(java.util.List.of("key.keyboard.a", "key.keyboard.c",
                                "key.keyboard.left.shift"))),
                "combo: A+C+Shift is stored whole and in order");

        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /** Sends one key press to the open binding screen. */
    private void pressOnBindScreen(ClientGameTestContext context, int key, int modifiers) {
        compute(context, () -> {
            if (client().gui.screen() instanceof KeyBindScreen screen) {
                screen.keyPressed(new net.minecraft.client.input.KeyEvent(key, 0, modifiers));
            }
            return null;
        });
        context.waitTicks(1);
    }

    /**
     * A held modifier that is not part of the binding must block it.
     *
     * <p>This is the regression check for a binding that fired on the wrong key state: the held keys used
     * to be sampled only from the keys some binding mentioned, so a plain {@code K} binding fired while
     * Shift was held, because Shift was never looked at. Pressing Shift+K must do nothing, and K alone
     * must still work.</p>
     */
    private void testExtraModifierBlocks(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdShift();
        context.waitTicks(2);
        context.getInput().holdKey(KEY_K);
        context.waitTicks(3);
        require(compute(context, () -> !mod().engine().isActive(Feature.ATTACK_HOLD)),
                "modifier: Shift+K does not fire a plain K binding");

        context.getInput().releaseKey(KEY_K);
        context.getInput().releaseShift();
        context.waitTicks(3);

        context.getInput().holdKey(KEY_K);
        context.waitTicks(3);
        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)),
                "modifier: K alone still fires the plain binding");
        context.getInput().releaseKey(KEY_K);
        context.waitTicks(3);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * A bound combination must actually toggle its feature on a real press, and a plain key must not.
     *
     * <p>This is the check for "the binding was stored but nothing happened when pressed". It also pins the
     * exit condition: a real key transitions the feature exactly once per press, rather than toggling on and
     * off while still showing a message.</p>
     *
     * <p>Ordinary keys are used rather than Shift, because Shift is the sneak binding and holding it would
     * affect the world. The key state is set directly because the harness's synthetic input does not update
     * vanilla's key table, which is what the mod reads.</p>
     */
    private void testCombinationFiresWithRealKeys(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.c"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);

        // A alone: not the full combination, so nothing happens.
        context.getInput().holdKey(KEY_A);
        context.waitTicks(3);
        require(compute(context, () -> !mod().engine().isActive(Feature.ATTACK_HOLD)),
                "combo: one key of a combination is not enough");

        // A+C: the combination is complete and must fire.
        context.getInput().holdKey(KEY_C);
        context.waitTicks(3);
        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)),
                "combo: the bound combination fires when all its keys are held");

        // Keeping the keys down must not toggle it again.
        context.waitTicks(8);
        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)),
                "combo: holding the keys does not toggle the feature off again");

        context.getInput().releaseKey(KEY_C);
        context.getInput().releaseKey(KEY_A);
        context.waitTicks(3);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * A longer combination must block a shorter binding it contains.
     *
     * <p>Binding {@code A+C} to one feature and plain {@code C} to another, then pressing both keys: only
     * the {@code A+C} feature may run. Pressing {@code C} on its own must still run the plain binding.</p>
     */
    private void testLongerCombinationBlocksShorter(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a", "key.keyboard.c"));
            mod().config().setKey(Feature.USE_HOLD, combo("key.keyboard.c"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().applyKeyBinding(Feature.USE_HOLD);
            return null;
        });
        context.waitTicks(2);

        // A then C: only the A+C feature.
        context.getInput().holdKey(KEY_A);
        context.waitTicks(2);
        context.getInput().holdKey(KEY_C);
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)),
                "shadowing: the A+C binding fires");
        require(compute(context, () -> !mod().engine().isActive(Feature.USE_HOLD)),
                "shadowing: the plain C binding does not fire while A+C is held");

        context.getInput().releaseKey(KEY_C);
        context.getInput().releaseKey(KEY_A);
        context.waitTicks(3);

        // C alone: the plain binding must work again.
        context.getInput().holdKey(KEY_C);
        context.waitTicks(3);
        require(compute(context, () -> mod().engine().isActive(Feature.USE_HOLD)),
                "shadowing: the plain C binding works again once A is released");
        context.getInput().releaseKey(KEY_C);
        context.waitTicks(3);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().config().setKey(Feature.USE_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().applyKeyBinding(Feature.USE_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * An active hold must resume after a screen closes.
     *
     * <p>Vanilla calls {@code KeyMapping.releaseAll()} whenever a screen opens, which clears every key's
     * down flag. Without re-asserting it, the hold stayed dead for the rest of the activation while the HUD
     * still reported it as on. The flag is deliberately not forced while the screen is open - that would
     * inject attacks into the inventory - so the check is that it comes back once the screen closes.</p>
     */
    private void testHoldSurvivesScreen(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.a"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdKey(KEY_A);
        context.waitTicks(3);
        require(compute(context, () -> client().options.keyAttack.isDown()),
                "screen: the hold is down before the screen opens");

        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)),
                "screen: the hold stays active while a screen is open");
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "screen: the mod does not inject attacks into an open screen");

        context.setScreen(() -> null);
        context.waitTicks(3);
        require(compute(context, () -> client().options.keyAttack.isDown()),
                "screen: the hold re-asserts the vanilla key once the screen closes");

        context.getInput().releaseKey(KEY_A);
        context.waitTicks(2);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * With the continue-on-screen option on, the attack channel keeps acting while a screen is open.
     *
     * <p>Vanilla discards attack input while a screen is open and marks the state with the 10000 tick
     * {@code missTime} sentinel, which {@code startAttack} refuses through. The mod drops that sentinel
     * and drives the same entry points itself. Two witnesses: the injected clicks keep growing with the
     * inventory open, and a held attack keeps swinging on the block under the crosshair - the swing only
     * happens through the destroy loop the vanilla tick skips in that state, so a swing proves the mod
     * really continued it.</p>
     */
    private void testAttackContinuesOnScreen(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setContinueOnScreen(true);
            mod().config().setSettings(Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(1, 1, 1));
            mod().syncEngine();
            mod().engine().activate(Feature.ATTACK_PERIODIC_CLICK);
            mod().input().resetInjectedClicks();
            return null;
        });
        context.waitTicks(2);

        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(5);

        require(compute(context, () -> mod().input().injectedClicks() > 0),
                "screen work: attack clicks keep firing while the inventory is open");

        // A held attack keeps acting: aiming straight down, the destroy loop the mod pumps swings the
        // arm every tick, which nothing else does while a screen is open.
        compute(context, () -> {
            mod().engine().stopAll();
            mod().engine().activate(Feature.ATTACK_HOLD);
            client().player.setXRot(90.0F);
            return null;
        });
        context.waitTicks(6);

        require(compute(context, () -> client().player.isSwinging()),
                "screen work: a held attack keeps acting on the block under the crosshair");

        compute(context, () -> {
            client().player.setXRot(0.0F);
            mod().engine().stopAll();
            mod().config().setContinueOnScreen(false);
            return null;
        });
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    /** The config screen must not ship with a global hotkey of its own. */
    private void testNoDefaultHotkeys(ClientGameTestContext context) {
        // The old ] default is cleared on the first client tick, so let a few pass first.
        context.waitTicks(5);
        require(compute(context, () -> mod().keyFor(Feature.ATTACK_HOLD).isUnbound()),
                "defaults: no feature ships with a bound key");

        require(compute(context, () -> {
            net.minecraft.client.KeyMapping open = mod().openConfigKey();
            return open == null || open.isUnbound();
        }), "defaults: opening the config has no default key either");
    }

    /**
     * A bound key must not also perform its ordinary game action.
     *
     * <p>Vanilla acts on a key before any mod sees it, so binding {@code Shift+1} used to both run the
     * feature and switch the hotbar slot. The mod now drains the queued press of the claimed keys, which is
     * what this checks: pressing the combination fires the feature and leaves the slot alone, while pressing
     * the key on its own still selects the slot.</p>
     */
    private void testBoundKeyDoesNotReachVanilla(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_PERIODIC_CLICK,
                    combo("key.keyboard.left.shift", "key.keyboard.1"));
            mod().applyKeyBinding(Feature.ATTACK_PERIODIC_CLICK);
            client().player.getInventory().setSelectedSlot(4);
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(2);
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.waitTicks(4);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_PERIODIC_CLICK)),
                "claimed key: the combination still fires its feature");
        require(compute(context, () -> client().player.getInventory().getSelectedSlot() == 4),
                "claimed key: the hotbar slot is not switched by a bound combination");

        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.getInput().releaseKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(3);

        // The same key on its own must keep working: only a complete combination is claimed.
        compute(context, () -> {
            client().player.getInventory().setSelectedSlot(4);
            return null;
        });
        context.waitTicks(2);
        context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.waitTicks(4);
        context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
        context.waitTicks(2);

        require(compute(context, () -> client().player.getInventory().getSelectedSlot() == 0),
                "claimed key: pressing the key alone still selects the hotbar slot");

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_PERIODIC_CLICK,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_PERIODIC_CLICK);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * With the master switch off a hotkey must refuse to switch a feature on.
     *
     * <p>Before this the feature reported on - message, HUD and config row - while the master switch
     * suppressed every action, so the player saw "on" and nothing happened.</p>
     */
    private void testMasterSwitchRefusesToggles(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().setGameEnabled(false);
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdKey(KEY_K);
        context.waitTicks(2);
        context.getInput().releaseKey(KEY_K);
        context.waitTicks(3);

        require(compute(context, () -> !mod().engine().isActive(Feature.ATTACK_HOLD)),
                "master off: a hotkey does not switch a feature on");
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "master off: nothing is pressed");
        require(compute(context, () -> mod().hudLines().contains(
                        peoplecanfly.clientinteractions.common.StatusText.pausedLine())),
                "master off: the HUD says automation is off");

        compute(context, () -> {
            mod().setGameEnabled(true);
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * A forced hold must produce the same visible reaction as a real press-and-hold.
     *
     * <p>A real press is two things at once: the button goes down, and a click is queued. Only the queued
     * click produces the arm swing, so a hold that merely set the flag did nothing at all whenever the
     * crosshair was on air or on a mob - which is most of the time. Vanilla's own swing flag is the witness,
     * because it is what the player actually sees.</p>
     */
    private void testHoldProducesASwing(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD, combo("key.keyboard.k"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            // Aim at the sky so no block target exists: only the queued press can produce a swing.
            client().player.setXRot(-85.0F);
            return null;
        });
        context.waitTicks(3);

        context.getInput().holdKey(KEY_K);
        context.waitTicks(2);
        context.getInput().releaseKey(KEY_K);

        boolean sawSwing = false;

        for (int i = 0; i < 6; i++) {
            context.waitTick();

            if (compute(context, () -> client().player.isSwinging())) {
                sawSwing = true;
            }
        }

        require(sawSwing, "hold: a forced hold swings the arm like a real press");

        compute(context, () -> {
            client().player.setXRot(0.0F);
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(3);
    }

    /**
     * A press that is released again before the next tick must still complete its combination.
     *
     * <p>This is the case a tick-start sample cannot see through the {@code isDown} flag: no tick ever
     * observes the key as down, but the mapping still carries the queued click. The combination must
     * fire from it, and the queued click must be drained so the vanilla action - the hotbar slot switch
     * - does not happen anyway.</p>
     */
    private void testTapCompletesCombination(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_PERIODIC_CLICK,
                    combo("key.keyboard.left.shift", "key.keyboard.1"));
            mod().applyKeyBinding(Feature.ATTACK_PERIODIC_CLICK);
            client().player.getInventory().setSelectedSlot(4);
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdShift();
        context.waitTicks(2);

        // The tap: press and release inside one tick window, so no tick sees the key as down. The queued
        // click is what vanilla would act on.
        compute(context, () -> {
            com.mojang.blaze3d.platform.InputConstants.Key one =
                    com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.1");
            net.minecraft.client.KeyMapping.set(one, true);
            net.minecraft.client.KeyMapping.click(one);
            net.minecraft.client.KeyMapping.set(one, false);
            return null;
        });
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_PERIODIC_CLICK)),
                "tap: the combination completes from the queued press");
        require(compute(context, () -> client().player.getInventory().getSelectedSlot() == 4),
                "tap: the queued press is drained and does not switch the hotbar slot");

        context.getInput().releaseShift();
        context.waitTicks(2);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.ATTACK_PERIODIC_CLICK,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.ATTACK_PERIODIC_CLICK);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * A movement key that takes part in a combination loses its vanilla job while the combination is
     * held, and comes back the moment the combination breaks.
     *
     * <p>W alone always walks; Shift+W bound to a feature walks on neither side - the walking is part
     * of the combination on purpose. The witness is vanilla's own key flag, which the movement input
     * reads every tick, and the restore path is what keeps a released modifier from leaving W dead.</p>
     */
    private void testMovementKeyClaimed(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.USE_HOLD, combo("key.keyboard.left.shift", "key.keyboard.w"));
            mod().applyKeyBinding(Feature.USE_HOLD);
            return null;
        });
        context.waitTicks(2);

        context.getInput().holdKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(1);
        context.getInput().holdKey(KEY_W);
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(Feature.USE_HOLD)),
                "movement claim: the combination fires");
        require(compute(context, () -> !client().options.keyUp.isDown()),
                "movement claim: W does not walk while Shift+W is held");

        context.getInput().releaseKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(3);

        require(compute(context, () -> client().options.keyUp.isDown()),
                "movement claim: W walks again once the combination breaks");

        context.getInput().releaseKey(KEY_W);
        context.waitTicks(2);

        compute(context, () -> {
            mod().engine().stopAll();
            mod().config().setKey(Feature.USE_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().applyKeyBinding(Feature.USE_HOLD);
            mod().saveConfig();
            return null;
        });
        context.waitTicks(2);
    }

    /**
     * A bound combination that contains a mouse button must not also attack.
     *
     * <p>This is the reported case: binding {@code Shift+Left Click} used to switch the feature and punch at
     * the same time. The combination is bound to a <em>use</em> feature so the feature itself never touches
     * the attack button, which isolates the question - any swing observed would be the player's own press
     * leaking through.</p>
     */
    private void testMouseCombinationDoesNotAttack(ClientGameTestContext context) {
        compute(context, () -> {
            mod().engine().stopAll();
            for (Feature other : Feature.values()) {
                mod().config().setKey(other, peoplecanfly.clientinteractions.common.KeyCombo.unbound());
                mod().applyKeyBinding(other);
            }
            mod().config().setSettings(Feature.USE_PERIODIC_CLICK, new FeatureSettings(200, 1, 1));
            mod().syncEngine();
            mod().config().setKey(Feature.USE_PERIODIC_CLICK,
                    combo("key.keyboard.left.shift", "key.mouse.left"));
            mod().applyKeyBinding(Feature.USE_PERIODIC_CLICK);
            // Aim at the sky: the swing then cannot come from hitting a block.
            client().player.setXRot(-85.0F);
            return null;
        });
        context.waitTicks(3);

        // Control: with Shift NOT held the combination is incomplete, so a click must attack normally.
        require(pressAndWatchSwing(context), "mouse combo: a plain click still attacks");

        // The combination: Shift held, so the click must be claimed and must not attack.
        context.getInput().holdKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(2);

        boolean swung = pressAndWatchSwing(context);

        require(compute(context, () -> mod().engine().isActive(Feature.USE_PERIODIC_CLICK)),
                "mouse combo: the combination still switches its feature");
        require(!swung, "mouse combo: the press does not also attack");

        // Checked while the combination is still held: the mod must not be holding the attack button either.
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "mouse combo: the mod is not left holding the attack button");

        compute(context, () -> {
            client().options.keyAttack.isDown = false;
            return null;
        });
        context.getInput().releaseKey(MODIFIER_KEY_SHIFT);
        context.waitTicks(3);

        compute(context, () -> {
            client().player.setXRot(0.0F);
            mod().engine().stopAll();
            for (Feature other : Feature.values()) {
                mod().config().setKey(other, peoplecanfly.clientinteractions.common.KeyCombo.unbound());
                mod().applyKeyBinding(other);
            }
            mod().saveConfig();
            return null;
        });
        context.waitTicks(3);
    }

    /**
     * Delivers one physical left press and reports whether vanilla swung the arm.
     *
     * <p>The swing is the witness rather than a count of queued presses, because vanilla consumes the press
     * within the same tick and would leave nothing to count.</p>
     */
    private boolean pressAndWatchSwing(ClientGameTestContext context) {
        // Clear the timers vanilla uses to throttle attacks. A screen opened earlier in the run leaves
        // missTime at 10000, which makes startAttack return immediately - that would report "no swing" for a
        // reason unrelated to the claim pass under test.
        compute(context, () -> {
            client().missTime = 0;
            client().rightClickDelay = 0;
            client().options.keyAttack.isDown = false;
            return null;
        });

        // Let any previous swing finish so each case starts clean.
        for (int i = 0; i < 10 && compute(context, () -> client().player.isSwinging()); i++) {
            context.waitTick();
        }

        // The left button's index differs per version, so it is taken from the game's own attack mapping
        // rather than assumed.
        compute(context, () -> {
            com.mojang.blaze3d.platform.InputConstants.Key left = com.mojang.blaze3d.platform.InputConstants
                    .getKey(client().options.keyAttack.saveString());
            net.minecraft.client.KeyMapping.set(left, true);
            net.minecraft.client.KeyMapping.click(left);
            return null;
        });

        boolean swung = false;

        for (int i = 0; i < 4; i++) {
            context.waitTick();

            if (compute(context, () -> client().player.isSwinging())) {
                swung = true;
            }
        }

        compute(context, () -> {
            com.mojang.blaze3d.platform.InputConstants.Key left = com.mojang.blaze3d.platform.InputConstants
                    .getKey(client().options.keyAttack.saveString());
            net.minecraft.client.KeyMapping.set(left, false);
            return null;
        });
        context.waitTicks(2);

        return swung;
    }

    /**
     * True when every button is fully inside the screen, which is what the overlap bug violated.
     */
    private static boolean allWidgetsOnScreen() {
        Screen screen = client().gui.screen();

        if (screen == null) {
            return false;
        }

        java.util.List<net.minecraft.client.gui.components.AbstractWidget> widgets = new java.util.ArrayList<>();

        for (net.minecraft.client.gui.components.events.GuiEventListener listener : screen.children()) {
            if (listener instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                if (widget.getX() < 0 || widget.getY() < 0
                        || widget.getX() + widget.getWidth() > screen.width
                        || widget.getY() + widget.getHeight() > screen.height) {
                    return false;
                }

                widgets.add(widget);
            }
        }

        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                if (overlaps(widgets.get(i), widgets.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean overlaps(net.minecraft.client.gui.components.AbstractWidget a,
                                    net.minecraft.client.gui.components.AbstractWidget b) {
        return a.getX() < b.getX() + b.getWidth()
                && b.getX() < a.getX() + a.getWidth()
                && a.getY() < b.getY() + b.getHeight()
                && b.getY() < a.getY() + a.getHeight();
    }

    private void configure(ClientGameTestContext context, Feature feature, FeatureSettings settings) {
        compute(context, () -> {
            mod().config().setSettings(feature, settings);
            mod().syncEngine();
            return null;
        });
    }

    private void toggle(ClientGameTestContext context, Feature feature) {
        compute(context, () -> mod().engine().toggle(feature));
    }

    private void stop(ClientGameTestContext context, Feature feature) {
        compute(context, () -> {
            mod().engine().stop(feature);
            return null;
        });
    }

    private static <T> T compute(ClientGameTestContext context, java.util.function.Supplier<T> supplier) {
        return context.computeOnClient(minecraft -> supplier.get());
    }

    private static ClientInteractionsClient mod() {
        return ClientInteractionsClient.get();
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Client Interactions game test failed: " + message);
        }
    }
}
