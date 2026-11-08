package peoplecanfly.clientinteractions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.AutomationMode;
import peoplecanfly.clientinteractions.common.ChannelSettings;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * The in-game configuration screen.
 *
 * <p>26.x renamed the render entry point to {@code extractRenderState} and hands out a
 * {@link GuiGraphicsExtractor} instead of a {@code GuiGraphics}, so this screen is the 26.x dialect of the
 * same layout used on the older targets.</p>
 */
public final class ConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_WIDTH = 150;

    private final Screen parent;
    private final ClientInteractionsClient mod;

    public ConfigScreen(Screen parent, ClientInteractionsClient mod) {
        super(Component.literal(StatusText.MOD_NAME));
        this.parent = parent;
        this.mod = mod;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int left = center - 160;
        int right = center + 10;
        int y = 40;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isEnabled())
                .create(left, y, WIDGET_WIDTH, 20, Component.literal("Automation"),
                        (button, value) -> {
                            this.mod.config().setEnabled(value);
                            this.mod.saveConfig();
                        }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isHudEnabled())
                .create(right, y, WIDGET_WIDTH, 20, Component.literal("HUD"),
                        (button, value) -> {
                            this.mod.config().setHudEnabled(value);
                            this.mod.saveConfig();
                        }));

        y += ROW_HEIGHT;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().showMessages())
                .create(left, y, WIDGET_WIDTH, 20, Component.literal("Chat messages"),
                        (button, value) -> {
                            this.mod.config().setShowMessages(value);
                            this.mod.saveConfig();
                        }));

        y += ROW_HEIGHT + 8;

        for (ActionChannel channel : ActionChannel.values()) {
            y = this.addChannelWidgets(channel, left, right, y);
            y += 8;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(center - 60, this.height - 28, 120, 20)
                .build());
    }

    private int addChannelWidgets(ActionChannel channel, int left, int right, int y) {
        AutomationMode mode = this.mod.config().mode(channel);
        ChannelSettings settings = this.mod.config().settings(channel);

        this.addRenderableWidget(CycleButton.<AutomationMode>builder(
                        value -> Component.literal(StatusText.modeLabel(value)), mode)
                .withValues(AutomationMode.values())
                .create(left, y, WIDGET_WIDTH, 20,
                        Component.literal(StatusText.channelLabel(channel) + " mode"),
                        (button, value) -> {
                            this.mod.config().setMode(channel, value);
                            this.mod.saveConfig();
                            this.rebuildWidgets();
                        }));

        if (usesInterval(mode)) {
            this.addRenderableWidget(new IntSlider(right, y, WIDGET_WIDTH, 20, "Interval", settings.interval(),
                    ChannelSettings.MIN_INTERVAL, ChannelSettings.MAX_INTERVAL,
                    value -> this.mod.config().setSettings(channel,
                            this.mod.config().settings(channel).withInterval(value))));
            y += ROW_HEIGHT;
        }

        if (mode == AutomationMode.PERIODIC_HOLD) {
            int maxHold = this.mod.config().settings(channel).interval();
            this.addRenderableWidget(new IntSlider(right, y, WIDGET_WIDTH, 20, "Hold", settings.holdDuration(),
                    ChannelSettings.MIN_SPAN, Math.max(ChannelSettings.MIN_SPAN, maxHold),
                    value -> this.mod.config().setSettings(channel,
                            this.mod.config().settings(channel).withHoldDuration(value))));
            y += ROW_HEIGHT;
        }

        if (mode == AutomationMode.PERIODIC_BURST) {
            this.addRenderableWidget(new IntSlider(right, y, WIDGET_WIDTH, 20, "Burst", settings.burstSize(),
                    ChannelSettings.MIN_BURST, ChannelSettings.MAX_BURST,
                    value -> this.mod.config().setSettings(channel,
                            this.mod.config().settings(channel).withBurstSize(value))));
            y += ROW_HEIGHT;
        }

        if (usesPerTick(mode)) {
            this.addRenderableWidget(new IntSlider(right, y, WIDGET_WIDTH, 20, "Per tick", settings.perTick(),
                    ChannelSettings.MIN_PER_TICK, ChannelSettings.MAX_PER_TICK,
                    value -> this.mod.config().setSettings(channel,
                            this.mod.config().settings(channel).withPerTick(value))));
            y += ROW_HEIGHT;
        }

        return y;
    }

    private static boolean usesInterval(AutomationMode mode) {
        return mode == AutomationMode.PERIODIC_CLICK
                || mode == AutomationMode.PERIODIC_HOLD
                || mode == AutomationMode.PERIODIC_BURST;
    }

    private static boolean usesPerTick(AutomationMode mode) {
        return mode == AutomationMode.FAST_CLICK || mode == AutomationMode.PERIODIC_CLICK;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int y = 24;

        for (ActionChannel channel : ActionChannel.values()) {
            graphics.text(this.font,
                    StatusText.channelLine(channel, this.mod.config().mode(channel),
                            this.mod.config().settings(channel)),
                    20, y, 0xA0A0A0);
            y += 10;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.mod.saveConfig();
        this.minecraft.gui.setScreen(this.parent);
    }
}
