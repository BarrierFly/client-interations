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
 *       press therefore still applies - attack cooldown, the "missed swing" timer, the four tick use
 *       delay, "hands busy", "already using an item", spectator mode and disabled items.</li>
 *   <li><b>Holds</b> set the {@code isDown} flag of the vanilla attack/use key mapping, which makes the
 *       vanilla input loop do the work itself. Holding attack therefore keeps mining a block, and holding
 *       use keeps eating, drinking or drawing a bow exactly like a physically held mouse button.</li>
 * </ul>
 *
 * <p>The {@code isDown} field is written directly instead of going through {@code KeyMapping#set} because
 * on 1.21.10 and newer the attack and use keys are {@code ToggleKeyMapping}s, whose {@code setDown}
 * flips the state instead of assigning it when the "toggle" accessibility option is enabled.</p>
 *
 * <p>The mod only touches {@code isDown} while it owns the key, i.e. between the first held tick and the
 * release. Click-only modes never write the flag, so a physically held mouse button is never disturbed.</p>
 *
 * <h2>What happens while a screen is open</h2>
 * <p>The vanilla client stops processing key bindings as soon as a screen (inventory, chat, ...) or an
 * overlay is open, and it raises {@code missTime} to 10000 while a screen is open. Attacks follow that
 * rule exactly, so opening a GUI pauses attack automation just like it pauses a real mouse button.
 * The use channel keeps working while a screen is open, as requested, by pumping {@code startUseItem}
 * itself and honouring the vanilla four tick delay. Timers always keep running.</p>
 */
public final class GameInput {
    /** The value vanilla uses for {@code missTime} while a screen is open. */
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
        if (!this.vanillaInputActive()) {
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

            return;
        }

        // The vanilla loop does not run with a screen open, so keep the item use going ourselves while
        // still honouring the vanilla four tick delay and the "already using an item" rule.
        if (!this.vanillaInputActive()) {
            LocalPlayer player = this.client.player;

            if (player != null && this.client.rightClickDelay == 0 && !player.isUsingItem()) {
                this.client.startUseItem();
            }
        }
    }

    private void click(ActionChannel channel, int clicks) {
        // A click is an explicit action. Attack follows vanilla and is dropped while a screen is open;
        // use is deliberately allowed to keep working, as configured by the user.
        if (channel == ActionChannel.ATTACK && !this.vanillaInputActive()) {
            return;
        }

        for (int i = 0; i < clicks; i++) {
            if (channel == ActionChannel.ATTACK) {
                this.client.startAttack();
            } else {
                this.client.startUseItem();
            }
        }

        this.injected += clicks;
    }

    /**
     * Performs one of the player's own clicks {@code times} times, for fast click.
     *
     * <p>This is the only entry point fast click uses: the feature multiplies a real click instead of
     * clicking on a timer, so it does nothing unless the player acts.</p>
     *
     * <p>Vanilla has already processed the physical press by the time this runs, so the extra clicks are
     * the ones added here. A count of {@code 1} is the physical click itself and needs no extra work.</p>
     */
    public void playerClick(ActionChannel channel, int times) {
        if (times <= 1) {
            return;
        }

        this.click(channel, times - 1);
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

    /** Sets or clears the {@code isDown} flag of the vanilla key mapping driven by a channel. */
    /** The vanilla mapping this channel drives. */
    private KeyMapping channelMapping(ActionChannel channel) {
        return channel == ActionChannel.ATTACK ? this.client.options.keyAttack : this.client.options.keyUse;
    }

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
        return this.client.screen == null && this.client.getOverlay() == null;
    }
}
