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
 * raises {@code missTime} to 10000 while a screen is open. Attacks follow that rule by default: clicks
 * are dropped and a hold resumes when the screen closes. With the continue-on-screen option on, the
 * attack channel keeps acting instead: clicks run after dropping the sentinel, and a hold pumps
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

    /**
     * Channels whose button is down only because it completed a combination, not because the player is
     * operating it. The mark is dropped when the button comes back up, so the next press counts again.
     */
    private final boolean[] enablingHold = new boolean[ActionChannel.values().length];

    public GameInput(Minecraft client, BooleanSupplier continueOnScreen) {
        this.client = client;
        this.continueOnScreen = continueOnScreen;
    }

    /** Whether the mod, rather than the player, is currently holding a channel down. */
    public boolean owns(ActionChannel channel) {
        return this.owned[channel.ordinal()];
    }

    /**
     * Marks a channel whose button was just pressed to complete a combination, such as the left button
     * of a {@code Shift+Left Click} binding.
     *
     * <p>The press itself is claimed and never reaches vanilla, but the hold that follows it would still
     * look like an operation in effect to the fast-click multiplier, which would fire its count on the
     * very tick the feature was switched on. The mark makes the multiplier ignore that hold until the
     * button is released once, so switching a feature on by pressing its own button never performs the
     * action; the next press does.</p>
     */
    public void markEnablingHold(ActionChannel channel) {
        this.enablingHold[channel.ordinal()] = true;
    }

    /**
     * Whether this button counts as held down right now, for the fast-click multiplier.
     *
     * <p>The physical device is polled first, so a real press is always seen. The mapping fallback is
     * deliberately left open: when the mod holds the button for a feature, that hold is itself an
     * operation in effect and the multiplier should scale it, so a mod-driven hold counting here is
     * correct rather than a leak. It is {@link HeldKeys}, with the mod-held keys excluded, that must not
     * see the mod's own holds - there a hold would make its own combination look complete.</p>
     *
     * <p>A hold that only completed a combination is the one exception: it is not the player operating
     * the button, it is the player switching a feature on, so it does not count until the button is
     * released once.</p>
     */
    public boolean isPlayerHolding(ActionChannel channel) {
        KeyMapping mapping = this.channelMapping(channel);

        if (mapping == null || this.client.options == null) {
            return false;
        }

        boolean down = HeldKeys.isHeld(mapping.saveString(), this.client, java.util.Set.of());

        if (this.enablingHold[channel.ordinal()]) {
            // The mark is dropped once the button comes back up - the next press is an operation of its
            // own - or when the mod drives the hold itself: that hold is an operation in effect and the
            // multiplier is meant to scale it.
            if (!down || this.owned[channel.ordinal()]) {
                this.enablingHold[channel.ordinal()] = false;
            } else {
                return false;
            }
        }

        return down;
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
        if (this.client.player == null || this.client.level == null) {
            return;
        }

        if (release) {
            this.release(channel);
        }

        boolean wasOwned = this.owned[channel.ordinal()];

        if (hold != wasOwned) {
            this.owned[channel.ordinal()] = hold;
            this.setKeyDown(channel, hold);

            // A real press is two things: the button goes down, and one click is queued. Only the second
            // one produces the arm swing and starts the block being broken, so a hold that merely sets the
            // flag does nothing visible whenever the crosshair is on air or a mob - the very common case.
            // Emitting the press here makes a forced hold behave exactly like a real press-and-hold.
            if (hold && clicks == 0) {
                this.click(channel, 1);
            }
        } else if (hold) {
            // The mod believes it is holding, but vanilla clears isDown whenever a screen opens
            // (KeyMapping.releaseAll) and on a mouse grab. Without re-asserting it here the hold would
            // stay dead for the rest of the activation, with the HUD still reporting it as active.
            this.reassertHold(channel);
        }

        if (hold) {
            this.keepHoldAlive(channel);
        }

        if (clicks > 0) {
            this.click(channel, clicks);
        }
    }

    /**
     * Re-writes {@code isDown} while the mod owns a hold.
     *
     * <p>Vanilla resets every key's down flag when a screen opens, and toggles it on a mouse re-grab.
     * Writing it again each tick keeps a long hold working across those events, and is harmless when the
     * flag is already correct.</p>
     *
     * <p>Only done with no screen open: writing the flag while a GUI is up would inject attacks into the
     * inventory. With a screen open the hold simply resumes once it closes, which is the same behaviour as
     * a real held mouse button.</p>
     */
    private void reassertHold(ActionChannel channel) {
        // Vanilla's releaseAll() clears every key flag when a screen opens, and its input loop does not
        // run in that state to set them again. With the option on the flag is kept asserted for the
        // channel the mod is driving, so a hold really is held while the GUI is up; the vanilla loop is
        // not what acts on it there - the destroy loop and startUseItem are pumped directly.
        if (!this.vanillaInputActive() && !this.continueOnScreen.getAsBoolean()) {
            return;
        }

        KeyMapping mapping = this.channelMapping(channel);

        if (mapping != null && !mapping.isDown()) {
            this.setKeyDown(channel, true);
        }
    }

    /**
     * Vanilla sets {@code missTime} to 10000 while a screen is open and only clears it through
     * {@code continueAttack(false)}, which does not run with a screen open. A forced hold must not inherit
     * that sentinel, so it is dropped here. The normal ten tick missed-swing penalty is far below this
     * value and is never touched.
     */
    private void keepHoldAlive(ActionChannel channel) {
        if (channel == ActionChannel.ATTACK) {
            if (this.client.missTime >= SCREEN_MISS_TIME) {
                this.client.missTime = 0;
            }

            // With the option on, a held attack keeps acting while a screen is open: this pumps
            // continueAttack(true), the loop the vanilla client runs per tick for a held attack button
            // and skips in that state. It continues destroying the block under the crosshair, stops
            // destroying when the crosshair moves off, and honours the "missed swing" timer and item
            // use itself. Like a real held button it does not re-press every tick, so a mob still takes
            // the one hit the press produced.
            if (this.continueOnScreen.getAsBoolean() && !this.vanillaInputActive()) {
                this.client.continueAttack(true);
            }

            return;
        }

        // The vanilla loop does not run with a screen open, so keep the item use going ourselves while
        // still honouring the vanilla four tick delay and the "already using an item" rule.
        if (!this.vanillaInputActive()) {
            LocalPlayer player = this.client.player;

            if (player != null && this.client.rightClickDelay == 0 && !player.isUsingItem()) {
                this.client.startUseItem();
                this.injected++;
            }
        }
    }

    private void click(ActionChannel channel, int clicks) {
        // Attack follows the vanilla rule and is dropped while a screen is open, unless the option
        // says otherwise; use is deliberately allowed to keep working, as configured by the user.
        if (channel == ActionChannel.ATTACK && !this.vanillaInputActive()) {
            if (!this.continueOnScreen.getAsBoolean()) {
                return;
            }

            // Vanilla marks a screen by raising missTime to the 10000 sentinel, and startAttack refuses
            // while it is set. Drop the sentinel so the requested click can act; the normal ten tick
            // "missed swing" penalty is far below the sentinel and is left untouched.
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
     * <p>The key flag is only cleared while the mod actually holds it. Two things depend on that: a
     * release must never clear a physically held mouse button, and it must not touch the client
     * options before they exist - the client entrypoint runs from inside the {@code Minecraft}
     * constructor, which creates {@code options} only afterwards.</p>
     */
    public void release(ActionChannel channel) {
        boolean held = this.owned[channel.ordinal()];
        this.owned[channel.ordinal()] = false;

        if (held) {
            this.setKeyDown(channel, false);
        }

        if (channel == ActionChannel.USE) {
            LocalPlayer player = this.client.player;

            if (player != null && player.isUsingItem() && !this.vanillaInputActive()) {
                // With a screen open the vanilla loop will not release the item for us.
                this.client.gameMode.releaseUsingItem(player);
            }
        }
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
        return this.client.screen == null && this.client.getOverlay() == null;
    }
}
