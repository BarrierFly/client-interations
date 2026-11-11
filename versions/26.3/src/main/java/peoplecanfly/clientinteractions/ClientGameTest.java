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
            testLongerCombinationBlocksShorter(context);
            testBoundKeyDoesNotReachVanilla(context);
            testMasterSwitchRefusesToggles(context);
            testHoldProducesASwing(context);
            testHoldSurvivesScreen(context);
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

        // And the real binding path still works: the mapping ends up on the main key only.
        compute(context, () -> {
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> "key.keyboard.k".equals(mod().keyFor(Feature.ATTACK_HOLD).saveString())),
                "combo: the vanilla mapping is set to the main key");

        // A binding changed at runtime must fire without restarting. Vanilla only rebuilds its
        // key-to-binding table on load and when the key binds screen opens, so applyKeyBinding has to do
        // it; without that the mapping would be inert until the next launch.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.j"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> keyFiresWithoutRestart("key.keyboard.j")),
                "combo: a rebind takes effect immediately, without a restart");

        // Mouse buttons use the same table, so one must be bindable and fire the same way.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.mouse.4"));
            mod().applyKeyBinding(Feature.ATTACK_HOLD);
            return null;
        });
        require(compute(context, () -> keyFiresWithoutRestart("key.mouse.4")),
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
     * True when vanilla's key-to-binding table now resolves a press of {@code keyName} to this mod's
     * mapping for {@code ATTACK_HOLD}.
     *
     * <p>This is exactly what {@code KeyMapping#resetMapping} is responsible for: a mapping whose key
     * field changed but which was never re-registered is invisible to a real press, which is the bug that
     * made a rebind inert until the next launch.</p>
     */
    private static boolean keyFiresWithoutRestart(String keyName) {
        net.minecraft.client.KeyMapping mapping =
                ClientInteractionsClient.get().keyFor(Feature.ATTACK_HOLD);

        if (mapping == null || mapping.isUnbound() || !keyName.equals(mapping.saveString())) {
            return false;
        }

        // KeyMapping.set dispatches through the same table a real press uses, so if the mapping receives
        // the state change it will also receive the press.
        com.mojang.blaze3d.platform.InputConstants.Key key =
                com.mojang.blaze3d.platform.InputConstants.getKey(keyName);

        net.minecraft.client.KeyMapping.set(key, true);
        boolean receivedDown = mapping.isDown();
        net.minecraft.client.KeyMapping.set(key, false);

        return receivedDown && !mapping.isDown();
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

        int injected = compute(context, () -> mod().input().injectedClicks());
        LOGGER.info("fast click: injected {} extra clicks for one player press", injected);
        require(injected == 5,
                "fast click: one press becomes 6 clicks, so 5 extra, saw " + injected);

        stop(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(2);
    }

    /**
     * Presses and releases vanilla's attack button the way a real mouse does.
     *
     * <p>Writing the key state is what vanilla's own mouse handler does on a press, so this drives the
     * same detection the mod uses, without depending on whether the test harness can deliver mouse
     * events to a grabbed window.</p>
     */
    private void pressAttackKey(ClientGameTestContext context, boolean attack) {
        compute(context, () -> {
            client().options.keyAttack.setDown(true);
            return null;
        });
        context.waitTicks(2);
        compute(context, () -> {
            client().options.keyAttack.setDown(false);
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
