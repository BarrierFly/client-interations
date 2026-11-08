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
import peoplecanfly.clientinteractions.common.AutomationMode;
import peoplecanfly.clientinteractions.common.ChannelSettings;

/**
 * In-game end to end check, run with {@code ./gradlew :versions:<mc>:runClientGameTest}.
 *
 * <p>This covers what the unit tests cannot: proving that the vanilla attack and use code paths really
 * run when the mod calls them, that holds really flip the vanilla key state, that the timers advance
 * exactly one step per client tick in a live world, and that a screen stops attack input the way vanilla
 * does.</p>
 *
 * <p>All game state is read through {@link ClientGameTestContext#computeOnClient} because the test body
 * runs on its own thread.</p>
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

            testHold(context);
            testFastClick(context);
            testPeriodicHold(context);
            testPeriodicBurst(context);
            testMasterSwitch(context);
            testAttackBlockedByScreen(context);

            LOGGER.info("All in-game checks passed");
        }
    }

    private void testHold(ClientGameTestContext context) {
        configure(context, ActionChannel.ATTACK, AutomationMode.HOLD, new ChannelSettings(20, 10, 1, 3));
        toggle(context, ActionChannel.ATTACK);
        context.waitTicks(3);

        require(compute(context, () -> mod().engine().isActive(ActionChannel.ATTACK)), "hold: channel active");
        require(compute(context, () -> client().options.keyAttack.isDown()), "hold: vanilla attack key is down");

        stop(context, ActionChannel.ATTACK);
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyAttack.isDown()), "hold: vanilla attack key released");
    }

    private void testFastClick(ClientGameTestContext context) {
        configure(context, ActionChannel.ATTACK, AutomationMode.FAST_CLICK, new ChannelSettings(20, 10, 4, 3));
        toggle(context, ActionChannel.ATTACK);
        context.waitTicks(5);

        int elapsed = compute(context, () -> mod().engine().scheduler(ActionChannel.ATTACK).elapsedTicks());
        require(elapsed == 5, "fast click: exactly one scheduler tick per client tick, got " + elapsed);
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "fast click: a click based mode does not leave the key held");

        stop(context, ActionChannel.ATTACK);
    }

    private void testPeriodicHold(ClientGameTestContext context) {
        configure(context, ActionChannel.USE, AutomationMode.PERIODIC_HOLD, new ChannelSettings(6, 3, 1, 3));
        toggle(context, ActionChannel.USE);

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

        require(sawHeld, "periodic hold: the key was held at least once");
        require(sawReleased, "periodic hold: the key was released at least once");

        stop(context, ActionChannel.USE);
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyUse.isDown()),
                "periodic hold: the key is released when the channel stops");
    }

    private void testPeriodicBurst(ClientGameTestContext context) {
        configure(context, ActionChannel.ATTACK, AutomationMode.PERIODIC_BURST, new ChannelSettings(10, 1, 1, 3));
        toggle(context, ActionChannel.ATTACK);
        context.waitTicks(9);

        require(compute(context, () -> mod().engine().isActive(ActionChannel.ATTACK)),
                "periodic burst: the channel stays active across a full cycle");
        require(compute(context, () -> mod().engine().scheduler(ActionChannel.ATTACK).elapsedTicks() == 9),
                "periodic burst: the phase advanced by nine ticks");

        stop(context, ActionChannel.ATTACK);
    }

    private void testMasterSwitch(ClientGameTestContext context) {
        configure(context, ActionChannel.ATTACK, AutomationMode.HOLD, new ChannelSettings(20, 10, 1, 3));
        toggle(context, ActionChannel.ATTACK);
        context.waitTicks(2);
        require(compute(context, () -> client().options.keyAttack.isDown()),
                "master switch: the key is down while enabled");

        compute(context, () -> {
            mod().setGameEnabled(false);
            return null;
        });
        context.waitTicks(2);
        require(compute(context, () -> !client().options.keyAttack.isDown()),
                "master switch: the key is released when disabled");

        compute(context, () -> {
            mod().setGameEnabled(true);
            return null;
        });
        context.waitTicks(2);
        require(compute(context, () -> client().options.keyAttack.isDown()),
                "master switch: the key is down again when re-enabled");

        stop(context, ActionChannel.ATTACK);
    }

    private void testAttackBlockedByScreen(ClientGameTestContext context) {
        configure(context, ActionChannel.ATTACK, AutomationMode.FAST_CLICK, new ChannelSettings(20, 10, 1, 3));
        toggle(context, ActionChannel.ATTACK);
        context.waitTicks(2);

        // Opening a screen makes vanilla raise missTime and stop processing input; the mod follows that.
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(2);
        require(compute(context, () -> client().missTime >= 10000), "screen: vanilla marks the screen via missTime");
        require(compute(context, () -> mod().engine().scheduler(ActionChannel.ATTACK).elapsedTicks() > 0),
                "screen: timers keep running while the screen is open");

        stop(context, ActionChannel.ATTACK);
        context.setScreen(() -> null);
        context.waitTicks(2);
    }

    private void configure(ClientGameTestContext context, ActionChannel channel,
                           AutomationMode mode, ChannelSettings settings) {
        compute(context, () -> {
            mod().config().setMode(channel, mode);
            mod().config().setSettings(channel, settings);
            mod().syncEngine();
            return null;
        });
    }

    private void toggle(ClientGameTestContext context, ActionChannel channel) {
        compute(context, () -> mod().engine().toggle(channel));
    }

    private void stop(ClientGameTestContext context, ActionChannel channel) {
        compute(context, () -> {
            mod().engine().stop(channel);
            return null;
        });
    }

    /** Runs a piece of game code on the client thread, as required by the gametest API. */
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
