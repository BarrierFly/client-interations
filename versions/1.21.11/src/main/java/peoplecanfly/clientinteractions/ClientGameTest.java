package peoplecanfly.clientinteractions;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientInteractions-GameTest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            context.waitTicks(20);

            require(compute(context, () -> ClientInteractionsClient.get() != null), "mod initialised");
            require(compute(context, () -> allFeaturesOff()), "every feature starts off");

            testHold(context, Feature.ATTACK_HOLD);
            testFastClick(context);
            testPeriodicHold(context);
            testPeriodicBurst(context);
            testMutualExclusion(context);
            testMasterSwitch(context);
            testAttackBlockedByScreen(context);
            testConfigScreenRenders(context);

            LOGGER.info("All in-game checks passed");
        }
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
        configure(context, feature, new FeatureSettings(20, 10, 1, 3));
        toggle(context, feature);
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_HOLD)), "hold: active");
        require(compute(context, () -> client().options.keyAttack.isDown()), "hold: vanilla key down");

        stop(context, feature);
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyAttack.isDown()), "hold: vanilla key released");
    }

    private void testFastClick(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 4, 3));
        toggle(context, Feature.ATTACK_FAST_CLICK);
        context.waitTicks(5);

        int elapsed = compute(context, () -> mod().engine().scheduler(Feature.ATTACK_FAST_CLICK).elapsedTicks());
        require(elapsed == 5, "fast click: one scheduler tick per client tick, got " + elapsed);
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "fast click: a click mode leaves the key released");

        stop(context, Feature.ATTACK_FAST_CLICK);
    }

    private void testPeriodicHold(ClientGameTestContext context) {
        configure(context, Feature.USE_PERIODIC_HOLD, new FeatureSettings(6, 3, 1, 3));
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

    private void testPeriodicBurst(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_PERIODIC_BURST, new FeatureSettings(10, 1, 1, 3));
        toggle(context, Feature.ATTACK_PERIODIC_BURST);
        context.waitTicks(9);

        require(compute(context, () -> mod().engine().isActive(Feature.ATTACK_PERIODIC_BURST)),
                "burst: still active after nine ticks");
        require(compute(context, () -> mod().engine().scheduler(Feature.ATTACK_PERIODIC_BURST).elapsedTicks() == 9),
                "burst: phase advanced by nine ticks");

        stop(context, Feature.ATTACK_PERIODIC_BURST);
    }

    private void testMutualExclusion(ClientGameTestContext context) {
        configure(context, Feature.ATTACK_HOLD, new FeatureSettings(20, 10, 1, 3));
        configure(context, Feature.ATTACK_PERIODIC_CLICK, new FeatureSettings(5, 1, 1, 3));

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
        configure(context, Feature.ATTACK_HOLD, new FeatureSettings(20, 10, 1, 3));
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
        configure(context, Feature.ATTACK_FAST_CLICK, new FeatureSettings(20, 10, 1, 3));
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
        require(compute(context, () -> client().screen instanceof ConfigScreen),
                "config screen: opens and survives rendering");

        context.setScreen(() -> null);
        context.waitTicks(2);
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
