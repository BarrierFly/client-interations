package peoplecanfly.clientinteractions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.ActionChannel;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.FeatureSettings;
import peoplecanfly.clientinteractions.common.KeyCombo;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * The configuration screen: one page per button, one row per feature.
 *
 * <h2>Why two pages</h2>
 * <p>Ten features do not fit a normal gui at once. Paging by {@link ActionChannel} rather than by raw
 * count keeps a page self-consistent: the five features on it drive the same mouse button, so only one
 * of them can be on, which is exactly the mutual exclusion rule.</p>
 *
 * <h2>Layout</h2>
 * <p>Every column comes from one width budget instead of fixed offsets, so the row can never run past
 * the screen edge, and the row spacing follows the available height so the last row never sits under
 * the Done button.</p>
 *
 * <pre>
 *   name ......... key ....... [every][clicks]
 * </pre>
 *
 * <h2>Numbers are typed, not cycled</h2>
 * <p>Each value is a small text field with a label, so an exact number can be entered instead of being
 * stepped through. The field accepts digits only, commits on Enter or when it loses focus, and clamps to
 * the same range the engine enforces.</p>
 */
public final class ConfigScreen extends Screen {
    /** Height of one row, including the gap under it. */
    private static final int ROW = 24;
    /** Side margin, so nothing touches the screen edge. */
    private static final int MARGIN = 8;
    /** Widest the content is allowed to get, so it stays readable on very wide windows. */
    private static final int MAX_CONTENT = 400;
    /** Width of the key button. */
    private static final int KEY_WIDTH = 84;
    /** Width of one number field. */
    private static final int FIELD_WIDTH = 38;
    /** Width reserved to the left of a field for its label. */
    private static final int LABEL_WIDTH = 34;
    /** Width one labelled field block takes: label plus field plus the gap to the next column. */
    private static final int VALUE_BLOCK = LABEL_WIDTH + FIELD_WIDTH + 6;

    private final Screen parent;
    private final ClientInteractionsClient mod;

    /** Which channel is on screen. */
    private ActionChannel page = ActionChannel.ATTACK;

    /** Labels drawn above the number fields; rebuilt with the widgets. */
    private final java.util.List<FieldLabel> fieldLabels = new java.util.ArrayList<>();

    public ConfigScreen(Screen parent, ClientInteractionsClient mod) {
        super(Component.literal(StatusText.MOD_NAME));
        this.parent = parent;
        this.mod = mod;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.fieldLabels.clear();

        int content = Math.min(MAX_CONTENT, this.width - MARGIN * 2);

        // Below this a row cannot hold a name, a key and one number field without clipping.
        content = Math.max(250, content);

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

        // Three items share this row: Messages, the page switch, and the config-opening key.
        int third = content / 3 - 2;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().showMessages())
                .create(left, y, third, 20, Component.literal("Messages"),
                        (b, v) -> {
                            this.mod.config().setShowMessages(v);
                            this.mod.saveConfig();
                        }));

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.page == ActionChannel.ATTACK ? "Attack >" : "< Use"),
                        b -> this.switchPage())
                .bounds(left + third + 3, y, third, 20)
                .build());

        // The key that opens this screen, editable here so it is not hidden in the vanilla controls list.
        this.addRenderableWidget(Button.builder(
                        Component.literal("Key: " + this.configKeyDisplay()),
                        b -> this.openConfigKeyBind())
                .bounds(left + (third + 3) * 2, y, third, 20)
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
        // Two header rows: the global switches, then messages/page/config key together.
        int natural = this.headerTop() + ROW * 2 + 4;
        int lowestAllowed = this.doneTop() - 4 - 5 * 18;

        return Math.min(natural, Math.max(4, lowestAllowed));
    }

    /**
     * Vertical distance between two feature rows.
     *
     * <p>A gui scale of 2 on a small window leaves about 240 px of height, which is not enough for five
     * rows plus the header and the Done button. The spacing shrinks so the rows fit instead of letting
     * the last row run under Done.</p>
     */
    private int rowHeight() {
        int available = this.doneTop() - 4 - this.rowsTop();

        return Math.min(ROW, Math.max(18, available / 5));
    }

    private int rowGap() {
        return this.rowHeight() - 20;
    }

    /**
     * One feature row: name on the left, key next to it, then one labelled number field per value.
     *
     * <p>Widths are shared out proportionally with a floor per column, so the row is exactly as wide as
     * the content area. The name keeps priority because a truncated name is worse than a tight field.</p>
     *
     * <p>Each field carries its label as a hint inside the box, so a row never needs a second line and the
     * label can never collide with the row above.</p>
     *
     * @return the height the block used
     */
    private int addFeature(Feature feature, int left, int y, int content) {
        boolean active = this.mod.engine().isActive(feature);

        ValueField[] fields = ValueField.forShape(feature.shape());

        int gap = 4;
        int nameWidth = Math.max(110, content - KEY_WIDTH - gap * 2 - VALUE_BLOCK * fields.length);

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
                .bounds(left + nameWidth + gap, y, KEY_WIDTH, 20)
                .build());

        int fieldX = left + nameWidth + gap + KEY_WIDTH + gap;

        for (ValueField field : fields) {
            this.addValueField(feature, field, fieldX, y);
            fieldX += VALUE_BLOCK;
        }

        return 20;
    }

    /**
     * A text field holding one number, preceded by its label.
     *
     * <p>The label is drawn to the left of the box rather than inside it: the box centres its own text, so
     * anything drawn inside would sit on top of the number.</p>
     */
    private void addValueField(Feature feature, ValueField field, int x, int y) {
        this.fieldLabels.add(new FieldLabel(field.shortLabel, x, y));

        int boxX = x + LABEL_WIDTH;
        EditBox box = new EditBox(this.font, boxX, y, FIELD_WIDTH, 20, Component.literal(field.label));
        box.setMaxLength(3);
        box.setValue(Integer.toString(field.get(this.mod.config().settings(feature))));

        // Digits only, so an invalid value cannot be typed in the first place.

        box.setResponder(text -> this.commit(feature, field, text, box));

        this.addRenderableWidget(box);
    }

    /** Where a field's label goes, remembered so the label can be drawn during render. */
    private static final class FieldLabel {
        private final String text;
        private final int x;
        private final int y;

        FieldLabel(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Applies a typed value as soon as it is valid.
     *
     * <p>Applied live rather than on Enter so a partially typed number is never silently kept: the field
     * only calls back with digits, and the value is clamped to the range the engine uses.</p>
     */
    private void commit(Feature feature, ValueField field, String text, EditBox box) {
        if (text.isEmpty()) {
            return;
        }

        int value;

        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            // 26.x has no text filter, so a non-digit has to be rolled back here.
            box.setValue(Integer.toString(field.get(this.mod.config().settings(feature))));
            return;
        }

        FeatureSettings settings = this.mod.config().settings(feature);
        FeatureSettings updated = field.apply(settings, value);

        if (!updated.equals(settings)) {
            this.mod.config().setSettings(feature, updated);
            this.mod.saveConfig();
        }
    }

    /** How the bound combination reads, e.g. {@code Ctrl+Shift+R} or {@code Ctrl+Left Click}. */
    String keyDisplay(Feature feature) {
        KeyCombo combo = this.mod.config().key(feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        // Each key is translated on its own, so a combination shows every key by its own name.
        return combo.display(key -> peoplecanfly.clientinteractions.common.KeyNames.readable(key));
    }

    /** Switches to the other channel's page; used by the button and by the game test. */
    void switchPage() {
        this.page = this.page == ActionChannel.ATTACK ? ActionChannel.USE : ActionChannel.ATTACK;
        this.rebuildWidgets();
    }

    /** How the config-opening key reads. */
    String configKeyDisplay() {
        KeyCombo combo = this.mod.config().configKey();

        return combo.isBound() ? combo.display(peoplecanfly.clientinteractions.common.KeyNames::readable)
                : "Unbound";
    }

    /** Opens the capture screen for the config key. */
    private void openConfigKeyBind() {
        this.openKeyBind(new KeyBindScreen(this, this.mod, null));
    }

    /** Opens a screen through this version's entry point. */
    private void openKeyBind(Screen screen) {
        this.minecraft.gui.setScreen(screen);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The caller draws the background before render on this version.
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        // Every colour needs a full alpha channel: from 1.21.10 on the renderer skips text whose alpha is
        // zero, so a plain 0xFFFFFF would draw nothing at all.
        graphics.text(this.font, StatusText.screenHint(), MARGIN, 6, 0xFF909090);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.literal(StatusText.channelTitle(this.page)),
                this.width / 2, 27, 0xFFFFFF80);

        // The labels sit to the left of their boxes, vertically centred with them.
        for (FieldLabel label : this.fieldLabels) {
            graphics.text(this.font, label.text, label.x, label.y + 6, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        this.mod.saveConfig();
        this.minecraft.gui.setScreen(this.parent);
    }

    /** One number a row can edit: its label, its range, and how to write it back. */
    private enum ValueField {
        INTERVAL("Interval", "every", FeatureSettings.MIN_INTERVAL, FeatureSettings.MAX_INTERVAL),
        DURATION("Hold", "hold", FeatureSettings.MIN_DURATION, FeatureSettings.MAX_DURATION),
        COUNT("Clicks", "clicks", FeatureSettings.MIN_COUNT, FeatureSettings.MAX_COUNT);

        private final String label;
        /** The compact label drawn beside the field, where a full word would not fit. */
        private final String shortLabel;
        private final int min;
        private final int max;

        ValueField(String label, String shortLabel, int min, int max) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.min = min;
            this.max = max;
        }

        static ValueField[] forShape(Feature.Shape shape) {
            switch (shape) {
                case FAST_CLICK:
                    // "Clicks" here means how many clicks one of the player's clicks becomes.
                    return new ValueField[] {COUNT};
                case PERIODIC_CLICK:
                    return new ValueField[] {INTERVAL, COUNT};
                case PERIODIC_HOLD:
                    return new ValueField[] {INTERVAL, DURATION};
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
                case COUNT:
                    return settings.count();
                default:
                    return 0;
            }
        }

        /** Clamps a typed value into range and writes it into the right slot. */
        FeatureSettings apply(FeatureSettings settings, int value) {
            int clamped = FeatureSettings.clamp(value, this.min, this.max);

            switch (this) {
                case INTERVAL:
                    return settings.withInterval(clamped);
                case DURATION:
                    return settings.withDuration(clamped);
                case COUNT:
                    return settings.withCount(clamped);
                default:
                    return settings;
            }
        }
    }
}
