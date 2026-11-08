package peoplecanfly.clientinteractions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.Feature;
import peoplecanfly.clientinteractions.common.FeatureSettings;
import peoplecanfly.clientinteractions.common.StatusText;

/**
 * The configuration screen: one row per feature.
 *
 * <p>Every feature has its own on/off button, its own key binding button and its own value buttons, so
 * nothing is shared and nothing can overlap. Rows are paged by available height, and each value is a
 * click-to-cycle button rather than a slider: that keeps every row exactly one line tall and avoids the
 * zero-height render regions a slider could produce on 26.x.</p>
 */
public final class ConfigScreen extends Screen {
    private static final int ROW = 22;
    private static final int LEFT_WIDTH = 154;
    private static final int TOP = 30;

    private final Screen parent;
    private final ClientInteractionsClient mod;

    /** Index of the first feature shown on this page. */
    private int page;

    public ConfigScreen(Screen parent, ClientInteractionsClient mod) {
        super(Component.literal(StatusText.MOD_NAME));
        this.parent = parent;
        this.mod = mod;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int center = this.width / 2;
        int left = center - 160;
        int right = center + 2;
        int y = TOP;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isEnabled())
                .create(left, y, LEFT_WIDTH, 20, Component.literal("Automation"),
                        (b, v) -> {
                            this.mod.config().setEnabled(v);
                            this.mod.saveConfig();
                        }));

        int pages = this.pageCount();
        int pageIndex = Math.max(0, Math.min(this.page, pages - 1));

        this.addRenderableWidget(Button.builder(
                        Component.literal("Page " + (pageIndex + 1) + "/" + pages),
                        b -> {
                            this.page = (pageIndex + 1) % pages;
                            this.rebuildWidgets();
                        })
                .bounds(right, y, LEFT_WIDTH, 20)
                .build());
        y += ROW;

        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().isHudEnabled())
                .create(left, y, LEFT_WIDTH / 2 - 2, 20, Component.literal("HUD"),
                        (b, v) -> {
                            this.mod.config().setHudEnabled(v);
                            this.mod.saveConfig();
                        }));
        this.addRenderableWidget(CycleButton.onOffBuilder(this.mod.config().showMessages())
                .create(left + LEFT_WIDTH / 2 + 2, y, LEFT_WIDTH / 2 - 2, 20, Component.literal("Messages"),
                        (b, v) -> {
                            this.mod.config().setShowMessages(v);
                            this.mod.saveConfig();
                        }));
        y += ROW + 4;

        this.page = pageIndex;

        for (Feature feature : this.pageFeatures()) {
            y = this.addFeature(feature, left, right, y);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(center - 60, Math.max(TOP, this.height - 26), 120, 20)
                .build());
    }

    /** Features per page, leaving room for the header and the Done button. */
    private int perPage() {
        int available = this.height - TOP - ROW * 2 - 34;
        return Math.max(1, available / ROW);
    }

    private int pageCount() {
        int perPage = this.perPage();
        return Math.max(1, (Feature.values().length + perPage - 1) / perPage);
    }

    private Feature[] pageFeatures() {
        Feature[] all = Feature.values();
        int perPage = this.perPage();
        int index = Math.max(0, Math.min(this.page, this.pageCount() - 1));
        int from = Math.min(all.length, index * perPage);
        int to = Math.min(all.length, from + perPage);
        Feature[] slice = new Feature[Math.max(0, to - from)];

        System.arraycopy(all, from, slice, 0, slice.length);

        return slice;
    }

    private int addFeature(Feature feature, int left, int right, int y) {
        boolean active = this.mod.engine().isActive(feature);

        this.addRenderableWidget(Button.builder(
                        Component.literal((active ? "[ON] " : "[  ] ") + feature.label()),
                        b -> {
                            this.mod.toggleFeature(feature);
                            this.rebuildWidgets();
                        })
                .bounds(left, y, LEFT_WIDTH, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.literal(this.boundKeyName(feature)),
                        b -> this.openKeyBind(new KeyBindScreen(this, this.mod, feature)))
                .bounds(right, y, 84, 20)
                .build());

        int valueX = right + 88;

        for (ValueField field : ValueField.forShape(feature.shape())) {
            FeatureSettings settings = this.mod.config().settings(feature);
            FeatureSettings current = settings;

            this.addRenderableWidget(Button.builder(
                            Component.literal(field.label + ": " + field.get(current)),
                            b -> {
                                this.mod.config().setSettings(feature,
                                        field.advance(this.mod.config().settings(feature)));
                                this.mod.saveConfig();
                                this.rebuildWidgets();
                            })
                    .bounds(valueX, y, 92, 20)
                    .build());
            valueX += 96;
        }

        return y + ROW;
    }

    private String boundKeyName(Feature feature) {
        KeyMapping key = this.mod.keyFor(feature);

        if (key == null || key.isUnbound()) {
            return "Unbound";
        }

        return key.getTranslatedKeyMessage().getString();
    }

    /** Opens a screen through this version's entry point. */
    private void openKeyBind(Screen screen) {
        this.minecraft.setScreen(screen);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Screen#render draws the background on this version.
        super.render(graphics, mouseX, mouseY, delta);
        
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawString(this.font, "Toggle left, rebind right", 8, 18, 0x909090);
    }

    @Override
    public void onClose() {
        this.mod.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    /** The number a value button edits, and the steps it cycles through. */
    private enum ValueField {
        INTERVAL("Interval", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60, 100, 200}),
        DURATION("Hold", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60, 100, 200}),
        PER_TICK("Per tick", new int[] {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 30, 50, 75, 100}),
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
