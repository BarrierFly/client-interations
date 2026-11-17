package peoplecanfly.clientinteractions;

import net.minecraft.client.gui.GuiGraphics;
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
 * <p>Eight features do not fit a normal gui at once. Paging by {@link ActionChannel} rather than by raw
 * count keeps a page self-consistent: the four features on it drive the same mouse button, so only one
 * of them can be on, which is exactly the mutual exclusion rule.</p>
 *
 * <h2>Layout</h2>
 * <p>Every column is computed from one width budget, and the whole page is centred. If the window is too
 * narrow for the preferred widths the columns shrink in a fixed order (name, key, field, label) instead of
 * running off the screen, and the labels are cut to the width they were given. The row spacing follows the
 * available height, and the header moves up if it has to, so the last row never sits under Done and the
 * header never sits on the first row.</p>
 *
 * <pre>
 *   name ......... key ....... [every][clicks]
 * </pre>
 *
 * <h2>Numbers are typed, not cycled</h2>
 * <p>Each value is a small text field with a label. Digits only, and the value is clamped to the same
 * range the engine enforces. A half-typed number is never silently replaced: the field keeps what was
 * typed, and only the stored value is clamped. When the clamp changes another field of the same row
 * (a hold cannot be longer than its interval), that field is updated to what was actually stored, so the
 * screen never shows a value the engine is not using.</p>
 */
public final class ConfigScreen extends Screen {
    /** Preferred height of one row, including the gap under it. */
    private static final int ROW = 24;
    /** Height of one widget. */
    private static final int WIDGET = 20;
    /** Side margin, so nothing touches the screen edge. */
    private static final int MARGIN = 8;
    /** Widest the content is allowed to get, so it stays readable on very wide windows. */
    private static final int MAX_CONTENT = 400;
    /** Rows on one page, used for the vertical budget. */
    private static final int ROWS_PER_PAGE = 4;

    /** Preferred column widths. They are reduced only when the window forces it. */
    private static final int PREFERRED_NAME = 150;
    private static final int MIN_NAME = 84;
    private static final int PREFERRED_KEY = 84;
    private static final int MIN_KEY = 60;
    private static final int PREFERRED_LABEL = 32;
    private static final int MIN_LABEL = 18;
    private static final int PREFERRED_FIELD = 36;
    private static final int MIN_FIELD = 26;
    /** Horizontal gap between columns. */
    private static final int GAP = 4;

    private final Screen parent;
    private final ClientInteractionsClient mod;

    /** Which channel is on screen. */
    private ActionChannel page = ActionChannel.ATTACK;

    /** Labels drawn beside the number fields; rebuilt with the widgets. */
    private final java.util.List<FieldLabel> fieldLabels = new java.util.ArrayList<>();

    /** The number fields of each row, so a clamped value can be written back to its siblings. */
    private final java.util.List<Row> rows = new java.util.ArrayList<>();

    public ConfigScreen(Screen parent, ClientInteractionsClient mod) {
        super(Component.literal(StatusText.MOD_NAME));
        this.parent = parent;
        this.mod = mod;
    }

    /** One feature's editable widgets on the page, so a clamp can update its neighbours. */
    private static final class Row {
        private final java.util.Map<ValueField, EditBox> boxes = new java.util.EnumMap<>(ValueField.class);

        EditBox box(ValueField field) {
            return this.boxes.get(field);
        }
    }

    /** The column x positions and widths for the current page. */
    private static final class Columns {
        private int left;
        private int nameWidth;
        private int keyWidth;
        private int labelWidth;
        private int fieldWidth;
        private int valueX;
        private int rowWidth;

        int fieldX(int index) {
            return this.valueX + index * (this.labelWidth + this.fieldWidth + 6);
        }

        int blockWidth() {
            return this.labelWidth + this.fieldWidth + 6;
        }
    }

    /**
     * The vertical layout, decided once so every part agrees on the same numbers.
     *
     * <p>The preferred layout is the full one: a title block, the three switch rows and the feature rows
     * at full height. If the window is shorter the title block goes first, then the gaps tighten, and only as
     * a last resort do the widgets shrink - in that order, so the layout never overlaps and never pushes a
     * row under Done. The search picks the first combination that fits, which keeps tall windows on the
     * preferred numbers.</p>
     */
    private static final class Layout {
        /** Gap between the switch rows and the first feature row. */
        private static final int GROUP_GAP = 4;
        /** Smallest widget height worth drawing. */
        private static final int MIN_WIDGET = 12;

        private final int headerTop;
        private final int rowSpacing;
        private final int rowsTop;
        private final int doneTop;
        private final int widgetHeight;

        Layout(int height) {
            this.doneTop = Math.max(WIDGET + 4, height - 26);

            int header = 40;
            int spacing = ROW;
            int widget = WIDGET;
            boolean found = false;

            // Try the full layout first, then without the title block, then with shrinking widgets and
            // gaps. Both loops end at a usable size, so a sane window always finds a fit.
            for (int candidateHeader : new int[] {40, 6}) {
                for (int candidateWidget = WIDGET; candidateWidget >= MIN_WIDGET && !found;
                        candidateWidget -= 2) {
                    for (int candidateSpacing = ROW; candidateSpacing >= candidateWidget;
                            candidateSpacing--) {
                        if (this.fits(candidateHeader, candidateSpacing, candidateWidget)) {
                            header = candidateHeader;
                            spacing = candidateSpacing;
                            widget = candidateWidget;
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    break;
                }
            }

            this.headerTop = header;
            this.rowSpacing = spacing;
            this.widgetHeight = widget;
            this.rowsTop = header + 3 * spacing + GROUP_GAP;
        }

        /** True when the last row still ends above Done at these numbers. */
        private boolean fits(int header, int spacing, int widget) {
            int bottom = header + 3 * spacing + GROUP_GAP
                    + (ROWS_PER_PAGE - 1) * spacing + widget;
            return bottom <= this.doneTop - 2;
        }

        int secondHeaderRow() {
            return this.headerTop + this.rowSpacing;
        }

        int thirdHeaderRow() {
            return this.headerTop + 2 * this.rowSpacing;
        }

        /** True when there is room for the hint above the header. */
        boolean showHint() {
            return this.headerTop >= 20;
        }

        /** Where the title text goes, or a negative number when there is no room above the header. */
        int titleY() {
            return this.headerTop >= 38 ? 16 : -1;
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.fieldLabels.clear();
        this.rows.clear();

        Columns columns = this.columns();
        Layout layout = new Layout(this.height);
        int y = layout.headerTop;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isEnabled())
                .create(columns.left, y, columns.rowWidth / 2 - 2, layout.widgetHeight,
                        Component.literal("Automation"),
                        (b, v) -> this.mod.setGameEnabled(v)));

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isHudEnabled())
                .create(columns.left + columns.rowWidth / 2 + 2, y,
                        columns.rowWidth / 2 - 2, layout.widgetHeight, Component.literal("HUD"),
                        (b, v) -> {
                            this.mod.config().setHudEnabled(v);
                            this.mod.saveConfig();
                        }));

        y = layout.secondHeaderRow();

        int half = columns.rowWidth / 2 - 2;
        int secondHalf = columns.left + columns.rowWidth / 2 + 2;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().showMessages())
                .create(columns.left, y, half, layout.widgetHeight, Component.literal("Messages"),
                        (b, v) -> {
                            this.mod.config().setShowMessages(v);
                            this.mod.saveConfig();
                        }));

        // Whether a running feature keeps acting on the world while a screen is open - chat, the
        // inventory, a container - instead of the attack channel pausing the way vanilla does. On by
        // default, so a running feature does not stop when a GUI opens; off restores the vanilla rule.
        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().continueOnScreen())
                .create(secondHalf, y, half, layout.widgetHeight, Component.literal("Continue in GUI"),
                        (b, v) -> {
                            this.mod.config().setContinueOnScreen(v);
                            this.mod.saveConfig();
                        }));

        y = layout.thirdHeaderRow();

        // Two items share this row: the page switch, and the config-opening key.
        this.addRenderableWidget(Button.builder(
                        Component.literal(this.fit(this.pageSwitchLabel(), half - 8)),
                        b -> this.switchPage())
                .bounds(columns.left, y, half, layout.widgetHeight)
                .build());

        // The key that opens this screen, editable here so it is not hidden in the vanilla controls list.
        this.addRenderableWidget(Button.builder(
                        Component.literal(this.fit("Key: " + this.configKeyDisplay(), half - 8)),
                        b -> this.openConfigKeyBind())
                .bounds(secondHalf, y, half, layout.widgetHeight)
                .build());

        y = layout.rowsTop;

        for (Feature feature : Feature.forChannel(this.page)) {
            this.addFeature(feature, columns, y, layout.widgetHeight);
            y += layout.rowSpacing;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 60, layout.doneTop, 120, layout.widgetHeight)
                .build());
    }

    /**
     * Divides the available width between the columns.
     *
     * <p>Preferred widths first, then the width is taken back from the name, the key button, the number
     * fields and finally the labels. That order keeps the name readable as long as possible and never
     * lets the row grow past the window.</p>
     */
    private Columns columns() {
        int available = Math.max(MIN_NAME + MIN_KEY + 2 * (MIN_LABEL + MIN_FIELD + 6) + 2 * GAP,
                Math.min(MAX_CONTENT, this.width - MARGIN * 2));

        Columns columns = new Columns();
        columns.nameWidth = PREFERRED_NAME;
        columns.keyWidth = PREFERRED_KEY;
        columns.labelWidth = PREFERRED_LABEL;
        columns.fieldWidth = PREFERRED_FIELD;

        int total = this.rowWidth(columns);

        if (total > available) {
            int deficit = total - available;
            int take = Math.min(deficit, columns.nameWidth - MIN_NAME);
            columns.nameWidth -= take;
            deficit -= take;

            if (deficit > 0) {
                take = Math.min(deficit, columns.keyWidth - MIN_KEY);
                columns.keyWidth -= take;
                deficit -= take;
            }

            if (deficit > 0) {
                take = Math.min(deficit, 2 * (columns.fieldWidth - MIN_FIELD));
                columns.fieldWidth -= (take + 1) / 2;
                deficit -= take;
            }

            if (deficit > 0) {
                take = Math.min(deficit, 2 * (columns.labelWidth - MIN_LABEL));
                columns.labelWidth -= (take + 1) / 2;
                deficit -= take;
            }
        }

        columns.rowWidth = this.rowWidth(columns);
        columns.left = Math.max(MARGIN, (this.width - columns.rowWidth) / 2);
        columns.valueX = columns.left + columns.nameWidth + GAP + columns.keyWidth + GAP;

        return columns;
    }

    /** Two value blocks are always reserved, so the first field of every row lines up. */
    private int rowWidth(Columns columns) {
        return columns.nameWidth + GAP + columns.keyWidth + GAP + 2 * columns.blockWidth();
    }

    /** One feature row: name on the left, key next to it, then one labelled number field per value. */
    private int addFeature(Feature feature, Columns columns, int y, int height) {
        boolean active = this.mod.engine().isActive(feature);
        ValueField[] fields = ValueField.forShape(feature.shape());
        Row row = new Row();
        this.rows.add(row);

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.fit((active ? "[ON] " : "[  ] ") + feature.label(),
                                columns.nameWidth - 8)),
                        b -> {
                            this.mod.toggleFeature(feature);
                            this.rebuildWidgets();
                        })
                .bounds(columns.left, y, columns.nameWidth, height)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.fit(this.keyDisplay(feature), columns.keyWidth - 8)),
                        b -> this.openKeyBind(new KeyBindScreen(this, this.mod, feature)))
                .bounds(columns.left + columns.nameWidth + GAP, y, columns.keyWidth, height)
                .build());

        for (int i = 0; i < fields.length; i++) {
            this.addValueField(feature, fields[i], columns, columns.fieldX(i), y, height, row);
        }

        return height;
    }

    /**
     * A text field holding one number, preceded by its label.
     *
     * <p>The label is drawn to the left of the box rather than inside it: the box centres its own text, so
     * anything drawn inside would sit on top of the number.</p>
     */
    private void addValueField(Feature feature, ValueField field, Columns columns,
                               int x, int y, int height, Row row) {
        this.fieldLabels.add(new FieldLabel(
                this.fit(field.shortLabel, columns.labelWidth - 2), x, y, columns.labelWidth));

        int boxX = x + columns.labelWidth;
        EditBox box = new EditBox(this.font, boxX, y, columns.fieldWidth, height,
                Component.literal(field.label)) {
            @Override
            public void setFocused(boolean focused) {
                super.setFocused(focused);

                if (focused) {
                    // Select the current number, so typing replaces it instead of appending to it.
                    this.setHighlightPos(0);
                    this.setCursorPosition(this.getValue().length());
                }
            }
        };
        box.setMaxLength(3);
        box.setValue(Integer.toString(field.get(this.mod.config().settings(feature))));

        // Digits only, so an invalid value cannot be typed in the first place.
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));

        box.setResponder(text -> this.commit(feature, field, text, box, row));

        row.boxes.put(field, box);
        this.addRenderableWidget(box);
    }

    /** Cuts a label down to the width it is given, so it can never run into the next column. */
    private String fit(String text, int width) {
        if (width <= 0) {
            return "";
        }

        if (this.font.width(text) <= width) {
            return text;
        }

        return this.font.plainSubstrByWidth(text, Math.max(0, width - 4)) + "...";
    }

    /** Where a field's label goes, remembered so the label can be drawn during render. */
    private static final class FieldLabel {
        private final String text;
        private final int x;
        private final int y;
        private final int width;

        FieldLabel(String text, int x, int y, int width) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    /**
     * Applies a typed value as soon as it is valid.
     *
     * <p>The field keeps what was typed; only the stored value is clamped to the engine's range. If the
     * clamp also changed another value of the same row - a hold cannot outlast its interval - that field
     * is updated to the value actually stored, so the screen never shows a number that is not in effect.
     * The value reaches the running engine immediately; the config file is written once, when the screen
     * closes.</p>
     */
    private void commit(Feature feature, ValueField field, String text, EditBox box, Row row) {
        if (text.isEmpty()) {
            // Keep the last good value while the player clears the field to type a new one.
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

        if (updated.equals(settings)) {
            // Nothing changed, but the typed text may still be out of range or have lost its value to
            // another clamp (a hold longer than its interval). Show what is actually stored.
            String stored = Integer.toString(field.get(settings));

            if (!stored.equals(text)) {
                box.setValue(stored);
            }

            return;
        }

        this.mod.config().setSettings(feature, updated);
        // The numbers reach the running engine immediately; the file itself is written once, when the
        // screen closes, instead of on every keystroke.
        this.mod.syncEngine();

        for (ValueField other : ValueField.values()) {
            EditBox sibling = row.box(other);

            if (sibling == null) {
                continue;
            }

            String stored = Integer.toString(other.get(updated));

            if (!stored.equals(sibling.getValue())) {
                sibling.setValue(stored);
            }
        }
    }

    /** How the bound combination reads, e.g. {@code Ctrl+Shift+R} or {@code Ctrl+Left Click}. */
    String keyDisplay(Feature feature) {
        KeyCombo combo = this.mod.config().key(feature);

        if (!combo.isBound()) {
            return "Unbound";
        }

        // Each key is translated on its own, so a combination shows every key by its own name.
        return combo.display(peoplecanfly.clientinteractions.common.KeyNames::readable);
    }

    /** What the page-switch button offers, e.g. {@code Show Use >}. */
    private String pageSwitchLabel() {
        return this.page == ActionChannel.ATTACK ? "Show Use >" : "< Show Attack";
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
        this.minecraft.setScreen(screen);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Screen#render draws the background on this version.
        super.render(graphics, mouseX, mouseY, delta);

        // Every colour needs a full alpha channel: from 1.21.10 on the renderer skips text whose alpha is
        // zero, so a plain 0xFFFFFF would draw nothing at all.
        Layout layout = new Layout(this.height);

        if (layout.showHint()) {
            graphics.drawString(this.font, this.fit(StatusText.screenHint(), this.width - MARGIN * 2),
                    MARGIN, 6, 0xFF909090);
        }

        int titleY = layout.titleY();

        if (titleY >= 0) {
            graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font,
                    Component.literal(StatusText.channelTitle(this.page)),
                    this.width / 2, titleY + 11, 0xFFFFFF80);
        }

        // The labels sit to the left of their boxes, vertically centred with them.
        for (FieldLabel label : this.fieldLabels) {
            graphics.drawString(this.font, label.text, label.x, label.y + 6, 0xFFA0A0A0);
        }
    }

    @Override
    public void onClose() {
        this.mod.saveConfig();
        this.minecraft.setScreen(this.parent);
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
