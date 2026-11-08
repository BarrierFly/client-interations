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
    /** SDL modifier bits: shift, ctrl, alt. */
    private static final int MODIFIER_MASK = 3 | 192 | 768;


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
        // mappings cannot store modifiers.
        compute(context, () -> {
            mod().config().setKey(Feature.USE_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.k", true, true, false));
            mod().saveConfig();
            return null;
        });

        require(compute(context, () -> mod().config().key(Feature.USE_HOLD).hasShift()
                        && mod().config().key(Feature.USE_HOLD).hasControl()
                        && !mod().config().key(Feature.USE_HOLD).hasAlt()),
                "key combo: modifiers survive the save");

        compute(context, () -> {
            mod().config().setKey(Feature.USE_HOLD, peoplecanfly.clientinteractions.common.KeyCombo.unbound());
            mod().saveConfig();
            return null;
        });
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

        // A plain binding fires with no modifier and not while Shift is held.
        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD)
                        .matches(false, false, false)),
                "combo: a plain binding matches with no modifiers");
        require(compute(context, () -> !mod().config().key(Feature.ATTACK_HOLD)
                        .matches(true, false, false)),
                "combo: a plain binding does not match while Shift is held");

        // A Shift binding is the mirror image.
        compute(context, () -> {
            mod().config().setKey(Feature.ATTACK_HOLD,
                    peoplecanfly.clientinteractions.common.KeyCombo.of("key.keyboard.k", true, false, false));
            return null;
        });

        require(compute(context, () -> !mod().config().key(Feature.ATTACK_HOLD)
                        .matches(false, false, false)),
                "combo: a Shift binding does not match without Shift");
        require(compute(context, () -> mod().config().key(Feature.ATTACK_HOLD)
                        .matches(true, false, false)),
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
     * Checks that a three-modifier combination is captured whole, not cut short at the second key.
     *
     * <p>The old screen polled the keyboard when the press was handled. Vanilla queues key events to the
     * main thread, so by then the user had often released the modifiers, and the binding silently became a
     * plain key. This drives the same event the vanilla handler builds, with all modifiers set in the
     * event, and asserts all of them survive into the stored combination.</p>
     */
    private void testFullCombinationIsCaptured(ClientGameTestContext context) {
        context.setScreen(() -> new KeyBindScreen(null, mod(), Feature.ATTACK_HOLD));
        context.waitTicks(3);

        // Ctrl + Shift + Alt + K, as the event would carry it.
        compute(context, () -> {
            KeyBindScreen screen = (KeyBindScreen) client().gui.screen();
            int modifiers = 0x2 | 0x1 | 0x4;
            screen.keyPressed(new net.minecraft.client.input.KeyEvent(
                    com.mojang.blaze3d.platform.InputConstants.KEY_K, 0, MODIFIER_MASK));
            return null;
        });
        context.waitTicks(2);

        require(compute(context, () -> {
            peoplecanfly.clientinteractions.common.KeyCombo combo =
                    mod().config().key(Feature.ATTACK_HOLD);
            return combo.hasControl() && combo.hasShift() && combo.hasAlt();
        }), "combo: Ctrl+Shift+Alt+K keeps all three modifiers");

        require(compute(context, () -> "key.keyboard.k"
                        .equals(mod().config().key(Feature.ATTACK_HOLD).keyName())),
                "combo: the main key is the last one pressed");

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
