package peoplecanfly.clientinteractions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.FeatureSettings;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * The configuration screen: one page per button.
 *
 * <h2>Why two pages</h2>
 * <p>Ten features plus their values do not fit a normal gui. Paging by {@link ActionChannel} rather than
 * by raw count keeps a page self-consistent: the five features on a page drive the same mouse button, so
 * only one of them can be on, which is exactly the mutual exclusion rule.</p>
 *
 * <h2>Layout</h2>
 * <p>Every column is derived from one width budget instead of being placed at fixed offsets, so the row
 * can never run past the screen edge:</p>
 *
 * <pre>
 *   name ................ key ....... value .......
 * </pre>
 *
 * <p>Values are click-to-cycle buttons rather than sliders: a row stays exactly one line tall, which
 * keeps the layout predictable and avoids the zero-height render regions a slider produced on 26.x.</p>
 */
public final class ConfigScreen extends Screen {
    /** Height of one row, including the gap under it. */
    private static final int ROW = 24;
    /** Side margin, so nothing touches the screen edge. */
    private static final int MARGIN = 8;
    /** Widest the content is allowed to get, so it stays readable on very wide windows. */
    private static final int MAX_CONTENT = 396;
    /** Width of the key button. */
    private static final int KEY_WIDTH = 84;

    private final Screen parent;
    private final ClientInteractionsClient mod;

    /** Which channel is on screen. */
    private ActionChannel page = ActionChannel.ATTACK;

    public ConfigScreen(Screen parent, ClientInteractionsClient mod) {
        super(Component.literal(StatusText.MOD_NAME));
        this.parent = parent;
        this.mod = mod;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int content = Math.min(MAX_CONTENT, this.width - MARGIN * 2);

        // Below this the row cannot show name + key + value without clipping, so the columns shrink
        // instead of running off the edge.
        content = Math.max(220, content);

        int left = (this.width - content) / 2;
        int y = this.headerTop();

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isEnabled())
                .create(left, y, content / 2 - 2, 20, Component.literal("Automation"),
                        (b, v) -> {
                            this.mod.config().setEnabled(v);
                            this.mod.saveConfig();
                        }));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isHudEnabled())
                .create(left + content / 2 + 2, y, content / 2 - 2, 20, Component.literal("HUD"),
                        (b, v) -> {
                            this.mod.config().setHudEnabled(v);
                            this.mod.saveConfig();
                        }));
        y += ROW;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().showMessages())
                .create(left, y, content / 2 - 2, 20, Component.literal("Messages"),
                        (b, v) -> {
                            this.mod.config().setShowMessages(v);
                            this.mod.saveConfig();
                        }));

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.page == ActionChannel.ATTACK ? "Attack >" : "< Use"),
                        b -> {
                            this.page = this.page == ActionChannel.ATTACK
                                    ? ActionChannel.USE
                                    : ActionChannel.ATTACK;
                            this.rebuildWidgets();
                        })
                .bounds(left + content / 2 + 2, y, content / 2 - 2, 20)
                .build());
        y = this.rowsTop();

        for (Feature feature : Feature.forChannel(this.page)) {
            y += this.addFeature(feature, left, y, content) + this.rowGap();
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.doneTop(), 120, 20)
                .build());
    }

    /** Where the global switches start; below the title and hint. */
    private int headerTop() {
        return 40;
    }

    /** Where the Done button goes, clamped so it stays on screen. */
    private int doneTop() {
        return Math.max(this.headerTop() + 40, this.height - 26);
    }

    /** The y of the first feature row, pushed up if the header would leave too little room. */
    private int rowsTop() {
        int natural = this.headerTop() + ROW * 2 + 4;
        // Five rows need 5 * 18 px at the tightest spacing; if the header leaves less than that, the
        // rows start higher instead of running under the Done button.
        int lowestAllowed = this.doneTop() - 4 - 5 * 18;

        return Math.min(natural, Math.max(4, lowestAllowed));
    }

    /**
     * Vertical distance between two feature rows.
     *
     * <p>Five rows plus the header and the Done button do not always fit: a gui scale of 2 on a small
     * window leaves about 240 px of height. The spacing shrinks so the rows fit rather than letting the
     * last row run under the Done button, and never grows past {@link #ROW}.</p>
     */
    private int rowHeight() {
        int available = this.doneTop() - 4 - this.rowsTop();

        return Math.min(ROW, Math.max(18, available / 5));
    }

    /** The gap between two feature blocks, derived from the row distance. */
    private int rowGap() {
        return this.rowHeight() - 20;
    }

    /**
     * One feature row: name on the left, key next to it, values filling the rest.
     *
     * <p>Widths are shared out proportionally with a floor per column, so the row never runs past the
     * content width no matter how narrow the gui is. The name keeps priority because it is the only part
     * that must stay recognisable.</p>
     *
     * @return the height the block used, so the caller can advance without guessing
     */
    private int addFeature(Feature feature, int left, int y, int content) {
        boolean active = this.mod.engine().isActive(feature);

        ValueField[] fields = ValueField.forShape(feature.shape());
        int valueCount = fields.length;

        // Content widths measured against the vanilla font:
        //   "[ON] Attack: Periodic burst" = 162 px
        //   "Shift+Ctrl+R"                =  72 px
        //   "x/tick: 100"                 =  66 px
        // Long names must win over wide value buttons, so the name gets its measured share first and the
        // values split what is left, with a floor so "Every: 200" still fits.
        int gap = 4;
        int nameWidth = Math.min(162, Math.max(110, content - KEY_WIDTH - gap - 54 * valueCount));
        int keyWidth = Math.min(KEY_WIDTH, Math.max(56, content - nameWidth - gap - 50 * valueCount));
        int valueWidth = valueCount == 0
                ? 0
                : Math.max(48, (content - nameWidth - keyWidth - gap * 2) / valueCount);

        this.addRenderableWidget(Button.builder(
                        Component.literal((active ? "[ON] " : "[  ] ") + feature.label()),
                        b -> {
                            this.mod.toggleFeature(feature);
                            this.rebuildWidgets();
                        })
                .bounds(left, y, nameWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.keyDisplay(feature)),
                        b -> this.openKeyBind(new KeyBindScreen(this, this.mod, feature)))
                .bounds(left + nameWidth + 4, y, keyWidth, 20)
                .build());

        int valueX = left + nameWidth + 4 + keyWidth + 4;

        for (ValueField field : fields) {
            this.addValueButton(feature, field, valueX, y, valueWidth - 4);
            valueX += valueWidth;
        }

        return 20;
    }

    private void addValueButton(Feature feature, ValueField field, int x, int y, int width) {
        this.addRenderableWidget(Button.builder(
                        Component.literal(field.label + " " + field.get(this.mod.config().settings(feature))),
                        b -> {
                            this.mod.config().setSettings(feature,
                                    field.advance(this.mod.config().settings(feature)));
                            this.mod.saveConfig();
                            this.rebuildWidgets();
                        })
                .bounds(x, y, width, 20)
                .build());
    }


    /** How the bound combination reads, e.g. {@code Shift+Ctrl+R}. */
    String keyDisplay(Feature feature) {
        KeyCombo combo = this.mod.config().key(feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        return combo.display(this.keyLabel(feature, combo));
    }

    /** The translated name of the main key, falling back to a readable form. */
    private String keyLabel(Feature feature, KeyCombo combo) {
        KeyMapping mapping = this.mod.keyFor(feature);

        if (mapping != null && !mapping.isUnbound()) {
            return mapping.getTranslatedKeyMessage().getString();
        }

        return peoplecanfly.clientinteractions.common.KeyNames.readable(combo.keyName());
    }

    /** Switches to the other channel's page; used by the button and by the game test. */
    void switchPage() {
        this.page = this.page == ActionChannel.ATTACK ? ActionChannel.USE : ActionChannel.ATTACK;
        this.rebuildWidgets();
    }

    /** Opens a screen through this version's entry point. */
    private void openKeyBind(Screen screen) {
        this.minecraft.gui.setScreen(screen);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The caller draws the background before render on this version.
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Every colour here needs a full alpha channel: text whose alpha is zero is not
        // drawn at all from 1.21.10 on, so a plain 0xFFFFFF would render nothing.
        graphics.text(this.font, StatusText.screenHint(), MARGIN, 6, 0xFF909090);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.literal(StatusText.channelTitle(this.page)),
                this.width / 2, 27, 0xFFFFFF80);
    }

    @Override
    public void onClose() {
        this.mod.saveConfig();
        this.minecraft.gui.setScreen(this.parent);
    }

    /** The number a value button edits, and the steps it cycles through. */
    private enum ValueField {
        INTERVAL("Every", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60, 100, 200}),
        DURATION("Hold", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60, 100, 200}),
        PER_TICK("x/tick", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 30, 50, 75, 100}),
        BURST("Burst", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 30, 40, 60, 100, 200});

        private final String label;
        private final int[] steps;

        ValueField(String label, int[] steps) {
            this.label = label;
            this.steps = steps;
        }

        static ValueField[] forShape(Feature.Shape shape) {
            switch (shape) {
                case FAST_CLICK:
                    return new ValueField[] {PER_TICK};
                case PERIODIC_CLICK:
                    return new ValueField[] {INTERVAL, PER_TICK};
                case PERIODIC_HOLD:
                    return new ValueField[] {INTERVAL, DURATION};
                case PERIODIC_BURST:
                    return new ValueField[] {INTERVAL, BURST};
                default:
                    return new ValueField[0];
            }
        }

        int get(FeatureSettings settings) {
            switch (this) {
                case INTERVAL:
                    return settings.interval();
                case DURATION:
                    return settings.duration();
                case PER_TICK:
                    return settings.perTick();
                case BURST:
                    return settings.burst();
                default:
                    return 0;
            }
        }

        /** The next step above the current value, wrapping back to the first step at the end. */
        FeatureSettings advance(FeatureSettings settings) {
            int currentValue = this.get(settings);
            int last = this.steps[this.steps.length - 1];
            int next = this.steps[0];

            if (currentValue < last) {
                for (int step : this.steps) {
                    if (step > currentValue) {
                        next = step;
                        break;
                    }
                }
            }

            switch (this) {
                case INTERVAL:
                    return settings.withInterval(next);
                case DURATION:
                    return settings.withDuration(next);
                case PER_TICK:
                    return settings.withPerTick(next);
                case BURST:
                    return settings.withBurst(next);
                default:
                    return settings;
            }
        }
    }
}
