package peoplecanfly.clientinteractions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import peoplecanfly.clientinteractions.common.ActionChannel;

/**
 * Bridges the shared automation engine to the vanilla client for one Minecraft version.
 *
 * <h2>How input is injected</h2>
 * <p>Two different things have to be expressed: a <em>click</em> and a <em>hold</em>.</p>
 *
 * <ul>
 *   <li><b>Clicks</b> call the very same methods the vanilla client calls for a real press:
 *       {@code Minecraft#startAttack} and {@code Minecraft#startUseItem}. Everything vanilla does on a
 *       press therefore still applies - attack cooldown, {@code missTime}, the four tick use delay,
 *       "hands busy", "already using an item", spectator mode and disabled items.</li>
 *   <li><b>Holds</b> set the {@code isDown} flag of the vanilla attack/use key mapping, which makes the
 *       vanilla input loop do the work itself. Holding attack therefore keeps mining a block, and holding
 *       use keeps eating, drinking or drawing a bow exactly like a physically held mouse button.</li>
 * </ul>
 *
 * <p>The {@code isDown} field is written directly instead of going through {@code KeyMapping#set} because
 * the attack and use keys are {@code ToggleKeyMapping}s, whose {@code setDown} flips the state instead of
 * assigning it when the "toggle" accessibility option is enabled.</p>
 *
 * <h2>What happens while a screen is open</h2>
 * <p>The vanilla client stops processing key bindings as soon as a screen or an overlay is open, and it also
 * raises {@code missTime} to 10000 while a screen is open. Attacks follow that rule exactly. The use channel
 * keeps working while a screen is open by pumping {@code startUseItem} itself, because a held use button is
 * not processed by the vanilla loop in that state either. Timers always keep running.</p>
 */
public final class GameInput {
    /** The value vanilla uses to mark "a screen is open". */
    private static final int SCREEN_MISS_TIME = 10000;

    private final Minecraft client;

    public GameInput(Minecraft client) {
        this.client = client;
    }

    /**
     * Performs one tick worth of automation for one channel.
     *
     * @param channel the channel being driven
     * @param clicks number of discrete clicks to request on this tick
     * @param hold whether the button should count as held for this tick
     * @param release whether a previous hold has to be released before anything else happens
     */
    public void apply(ActionChannel channel, int clicks, boolean hold, boolean release) {
        LocalPlayer player = this.client.player;

        if (player == null || this.client.level == null) {
            return;
        }

        if (release) {
            this.release(channel);
        }

        if (channel == ActionChannel.ATTACK) {
            this.applyAttack(clicks, hold);
        } else {
            this.applyUse(clicks, hold);
        }
    }

    private void applyAttack(int clicks, boolean hold) {
        this.setKeyDown(ActionChannel.ATTACK, hold);

        if (hold && this.client.missTime >= SCREEN_MISS_TIME) {
            // A forced hold must not inherit the sentinel vanilla sets while a screen is open, otherwise
            // automation stays inert for 500 seconds after closing a GUI. The 10 tick "missed swing"
            // penalty below that value is left untouched.
            this.client.missTime = 0;
        }

        if (!this.vanillaInputActive()) {
            return;
        }

        for (int i = 0; i < clicks; i++) {
            this.client.startAttack();
        }
    }

    private void applyUse(int clicks, boolean hold) {
        this.setKeyDown(ActionChannel.USE, hold);

        if (hold && !this.vanillaInputActive()) {
            LocalPlayer player = this.client.player;

            if (player != null && this.client.rightClickDelay == 0 && !player.isUsingItem()) {
                this.client.startUseItem();
            }
        }

        for (int i = 0; i < clicks; i++) {
            this.client.startUseItem();
        }
    }

    /** Releases a channel: drops the forced hold and stops any item use it started. */
    public void release(ActionChannel channel) {
        this.setKeyDown(channel, false);

        if (channel == ActionChannel.USE) {
            LocalPlayer player = this.client.player;

            if (player != null && player.isUsingItem() && !this.vanillaInputActive()) {
                this.client.gameMode.releaseUsingItem(player);
            }
        }
    }

    /** Sets or clears the {@code isDown} flag of the vanilla key mapping driven by a channel. */
    private void setKeyDown(ActionChannel channel, boolean down) {
        KeyMapping mapping = channel == ActionChannel.ATTACK
                ? this.client.options.keyAttack
                : this.client.options.keyUse;

        if (mapping != null) {
            mapping.isDown = down;
        }
    }

    /** True when the vanilla client would process key bindings on this tick. */
    private boolean vanillaInputActive() {
        return this.client.gui.screen() == null && this.client.gui.overlay() == null;
    }
}
