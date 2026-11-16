package peoplecanfly.clientinteractions;

import java.util.function.BooleanSupplier;
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
 * rewrites {@code missTime} to 10000 on every tick in that state. Attacks follow that rule by default:
 * clicks are dropped and a hold resumes when the screen closes. With the continue-on-screen option on,
 * the attack channel keeps acting instead: clicks run after dropping the sentinel, and a hold pumps
 * {@code continueAttack(true)} - the loop vanilla skips in that state. The use channel keeps working
 * with a screen open either way, by pumping {@code startUseItem} itself, because a held use button is
 * not processed by the vanilla loop in that state either. Timers always keep running.</p>
 *
 * <h2>What this class does not do</h2>
 * <p>It never decides <em>when</em> to click: that is the engine's job. It only turns "N clicks this tick"
 * into N calls, and "hold" into the vanilla flag. In particular the extra clicks of fast click are paced
 * by the engine, one per tick, the rate a player produces separate clicks at.</p>
 */
public final class GameInput {
    /** The value vanilla uses to mark "a screen is open". */
    private static final int SCREEN_MISS_TIME = 10000;

    private final Minecraft client;

    /**
     * Whether a running feature should keep acting on the world while a screen is open. Read live from
     * the config, so a change takes effect on the next tick without any syncing.
     */
    private final BooleanSupplier continueOnScreen;

    /** Whether the mod, rather than the player, is currently holding a channel down. */
    private final boolean[] owned = new boolean[ActionChannel.values().length];

    public GameInput(Minecraft client, BooleanSupplier continueOnScreen) {
        this.client = client;
        this.continueOnScreen = continueOnScreen;
    }

    /** Whether the mod, rather than the player, is currently holding a channel down. */
    public boolean owns(ActionChannel channel) {
        return this.owned[channel.ordinal()];
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
        boolean wasOwned = this.owned[ActionChannel.ATTACK.ordinal()];
        this.setKeyOwned(ActionChannel.ATTACK, hold);

        // A real press is two things: the button goes down, and one click is queued. Only the second one
        // produces the arm swing and starts the block being broken, so a hold that merely sets the flag
        // does nothing visible whenever the crosshair is on air or a mob - the very common case. Emitting
        // the press here makes a forced hold behave exactly like a real press-and-hold.
        if (hold && !wasOwned && clicks == 0) {
            this.click(ActionChannel.ATTACK, 1);
        }

        if (!this.vanillaInputActive()) {
            if (this.continueOnScreen.getAsBoolean()) {
                // With the option on, the attack channel keeps acting while a screen is open. Vanilla
                // rewrites the missTime sentinel on every tick in that state, and startAttack refuses
                // while it is set, so it is dropped here each tick; the 10 tick "missed swing" penalty
                // below that value is left untouched. The hold pumps continueAttack(true) - the loop
                // vanilla runs per tick for a held attack button and skips in this state - and the
                // requested clicks run through the same entry point a real click uses.
                if (this.client.missTime >= SCREEN_MISS_TIME) {
                    this.client.missTime = 0;
                }

                if (hold) {
                    this.client.continueAttack(true);
                }

                if (clicks > 0) {
                    this.click(ActionChannel.ATTACK, clicks);
                }
            }

            return;
        }

        if (hold && this.client.missTime >= SCREEN_MISS_TIME) {
            // A forced hold must not inherit the sentinel vanilla sets while a screen is open, otherwise
            // automation stays inert for 500 seconds after closing a GUI. The 10 tick "missed swing"
            // penalty below that value is left untouched.
            this.client.missTime = 0;
        }

        if (clicks > 0) {
            this.click(ActionChannel.ATTACK, clicks);
        }
    }

    private void applyUse(int clicks, boolean hold) {
        boolean wasOwned = this.owned[ActionChannel.USE.ordinal()];
        this.setKeyOwned(ActionChannel.USE, hold);

        // See applyAttack: a hold needs the queued press as well as the flag to do anything visible.
        if (hold && !wasOwned && clicks == 0 && this.vanillaInputActive()) {
            this.click(ActionChannel.USE, 1);
        }

        if (hold && !this.vanillaInputActive()) {
            LocalPlayer player = this.client.player;

            // The vanilla loop does not run with a screen open, so keep the item use going ourselves while
            // still honouring the vanilla four tick delay and the "already using an item" rule.
            if (player != null && this.client.rightClickDelay == 0 && !player.isUsingItem()) {
                this.click(ActionChannel.USE, 1);
            }
        }

        if (clicks > 0) {
            this.click(ActionChannel.USE, clicks);
        }
    }

    /** Calls the same entry point a real click uses, and counts it. */
    private void click(ActionChannel channel, int clicks) {
        // Attack follows the vanilla rule and is dropped while a screen is open, unless the option says
        // otherwise; use is deliberately allowed to keep working, as configured by the user.
        if (channel == ActionChannel.ATTACK && !this.vanillaInputActive()) {
            if (!this.continueOnScreen.getAsBoolean()) {
                return;
            }

            // startAttack refuses while the 10000 missTime sentinel that vanilla raises for an open
            // screen is set, so it is dropped before the requested clicks run.
            if (this.client.missTime >= SCREEN_MISS_TIME) {
                this.client.missTime = 0;
            }
        }

        for (int i = 0; i < clicks; i++) {
            if (channel == ActionChannel.ATTACK) {
                this.client.startAttack();
            } else {
                this.client.startUseItem();
            }

            this.injected++;
        }
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
            // Already in the wanted state, but vanilla may have cleared the flag behind our back:
            // KeyMapping.releaseAll() runs whenever a screen opens and KeyMapping.setAll() on a mouse
            // re-grab. Re-asserting keeps an active hold from going dead while the HUD still shows it on.
            // With a screen open the vanilla input loop does not run, so the flag is only re-asserted
            // there when the continue-on-screen option asks for it; the acting is then pumped directly.
            if (down && (this.vanillaInputActive() || this.continueOnScreen.getAsBoolean())) {
                KeyMapping mapping = this.channelMapping(channel);

                if (mapping != null && !mapping.isDown()) {
                    this.setKeyDown(channel, true);
                }
            }

            return;
        }

        this.owned[channel.ordinal()] = down;
        this.setKeyDown(channel, down);
    }

    /** The vanilla mapping this channel drives. */
    private KeyMapping channelMapping(ActionChannel channel) {
        return channel == ActionChannel.ATTACK ? this.client.options.keyAttack : this.client.options.keyUse;
    }

    /** Sets or clears the {@code isDown} flag of the vanilla key mapping driven by a channel. */
    private void setKeyDown(ActionChannel channel, boolean down) {
        if (this.client.options == null) {
            // The client entrypoint runs before the Minecraft constructor has created the options, so a
            // key flag cannot be written yet. Writing one is meaningless that early anyway.
            return;
        }

        if (down && !this.vanillaInputActive() && !this.continueOnScreen.getAsBoolean()) {
            // Never force a button down into an open screen unless the option asks for it. Without the
            // option the hold is re-asserted once it closes.
            return;
        }

        KeyMapping mapping = this.channelMapping(channel);

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
