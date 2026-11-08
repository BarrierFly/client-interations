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

    /** Whether the mod, rather than the player, is currently holding a channel down. */
    private final boolean[] owned = new boolean[ActionChannel.values().length];

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
        this.setKeyOwned(ActionChannel.ATTACK, hold);

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
        this.setKeyOwned(ActionChannel.USE, hold);

        if (hold && !this.vanillaInputActive()) {
            LocalPlayer player = this.client.player;

            if (player != null && this.client.rightClickDelay == 0 && !player.isUsingItem()) {
                this.client.startUseItem();
            }
        }

        for (int i = 0; i < clicks; i++) {
            this.client.startUseItem();
        }

        this.injected += clicks;
    }

    /**
     * Performs one of the player's own clicks {@code times} times, for fast click.
     *
     * <p>This is the only entry point fast click uses: the feature multiplies a real click instead of
     * clicking on a timer, so it does nothing unless the player acts.</p>
     */
    public void playerClick(ActionChannel channel, int times) {
        if (times <= 1) {
            return;
        }

        for (int i = 0; i < times - 1; i++) {
            if (channel == ActionChannel.ATTACK) {
                this.client.startAttack();
            } else {
                this.client.startUseItem();
            }
        }

        this.injected += times - 1;
    }

    /** How many clicks the mod has injected, so a test can prove the multiplier really fired. */
    private int injected;

    /** The number of clicks injected since the last {@link #resetInjectedClicks()}. */
    public int injectedClicks() {
        return this.injected;
    }

    public void resetInjectedClicks() {
        this.injected = 0;
    }

    /**
     * Releases a channel: drops the forced hold and stops any item use it started.
     *
     * <p>The key flag is only cleared while the mod actually holds it, so a release can never clear a
     * physically held mouse button.</p>
     */
    public void release(ActionChannel channel) {
        this.setKeyOwned(channel, false);

        if (channel == ActionChannel.USE) {
            LocalPlayer player = this.client.player;

            if (player != null && player.isUsingItem() && !this.vanillaInputActive()) {
                this.client.gameMode.releaseUsingItem(player);
            }
        }
    }

    /** Records whether the mod owns a channel and writes the key flag only when that changes. */
    private void setKeyOwned(ActionChannel channel, boolean down) {
        if (this.owned[channel.ordinal()] == down) {
            return;
        }

        this.owned[channel.ordinal()] = down;
        this.setKeyDown(channel, down);
    }

    /** Sets or clears the {@code isDown} flag of the vanilla key mapping driven by a channel. */
    private void setKeyDown(ActionChannel channel, boolean down) {
        if (this.client.options == null) {
            // The client entrypoint runs before the Minecraft constructor has created the options, so a
            // key flag cannot be written yet. Writing one is meaningless that early anyway.
            return;
        }

        KeyMapping mapping = channel == ActionChannel.ATTACK
                ? this.client.options.keyAttack
                : this.client.options.keyUse;

        if (mapping != null) {
            mapping.isDown = down;
        }
    }

    /** True when the vanilla client would process key bindings on this tick. */
    private boolean vanillaInputActive() {
        if (this.client.gui == null) {
            // The client entrypoint runs before the gui exists, so nothing can be open yet.
            return true;
        }

        return this.client.gui.screen() == null && this.client.gui.overlay() == null;
    }
}
