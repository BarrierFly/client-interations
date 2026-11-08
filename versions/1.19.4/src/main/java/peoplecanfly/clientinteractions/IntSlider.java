package peoplecanfly.clientinteractions;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import peoplecanfly.clientinteractions.common.ChannelSettings;

/**
 * A labelled integer slider.
 *
 * <p>Vanilla only ships a slider that exposes a {@code double} in {@code [0, 1]}, so this small subclass
 * maps that onto an integer range and shows the current value in the label.</p>
 */
final class IntSlider extends AbstractSliderButton {
    private final String label;
    private final int min;
    private final int max;
    private final IntConsumer onChange;

    IntSlider(int x, int y, int width, int height, String label, int initialValue,
              int min, int max, IntConsumer onChange) {
        super(x, y, width, height, Component.literal(label), toNormalized(initialValue, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        this.updateMessage();
    }

    private static double toNormalized(int value, int min, int max) {
        if (max <= min) {
            return 0.0D;
        }

        return (ChannelSettings.clampValue(value, min, max) - min) / (double) (max - min);
    }

    int value() {
        if (this.max <= this.min) {
            return this.min;
        }

        return this.min + (int) Math.round(this.value * (this.max - this.min));
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(this.label + ": " + this.value()));
    }

    @Override
    protected void applyValue() {
        this.onChange.accept(this.value());
    }
}
