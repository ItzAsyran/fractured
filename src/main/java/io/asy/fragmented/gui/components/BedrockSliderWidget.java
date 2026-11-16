package io.asy.fragmented.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Function;

public class BedrockSliderWidget extends AbstractSliderButton {
    private static final int TRACK_BG = 0x33FFFFFF;
    private static final int TRACK_ACTIVE = 0xFF47D147;
    private static final int THUMB_BORDER = 0xFF47D147;

    private final double min;
    private final double max;
    private final Function<Double, Component> valueFormatter;
    private final Consumer<Double> onValueChange;
    private final boolean isInteger;

    public BedrockSliderWidget(int x, int y, int width, int height, Component label,
                               double current, double min, double max, boolean isInteger,
                               Function<Double, Component> valueFormatter,
                               Consumer<Double> onValueChange) {
        super(x, y, width, height, label, Mth.clamp((current - min) / (max - min), 0.0D, 1.0D));
        this.min = min;
        this.max = max;
        this.isInteger = isInteger;
        this.valueFormatter = valueFormatter;
        this.onValueChange = onValueChange;
        updateMessage();
    }

    public double getActualValue() {
        double val = min + this.value * (max - min);
        return isInteger ? Math.round(val) : val;
    }

    @Override
    protected void updateMessage() {
        if (valueFormatter != null) {
            setMessage(valueFormatter.apply(getActualValue()));
        }
    }

    @Override
    protected void applyValue() {
        if (onValueChange != null) {
            onValueChange.accept(getActualValue());
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        Font font = Minecraft.getInstance().font;

        // Draw label & formatted value at the top
        graphics.text(font, getMessage(), x + 2, y + 2, 0xFFE0E0E0, false);

        // Thin track bar (4px height) placed towards the bottom of the widget
        int trackY = y + height - 8;
        int trackHeight = 4;
        int trackWidth = width - 8;
        int trackX = x + 4;

        // Inactive background track
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, TRACK_BG);

        // Active progress track
        int progressWidth = (int) (this.value * trackWidth);
        if (progressWidth > 0) {
            graphics.fill(trackX, trackY, trackX + progressWidth, trackY + trackHeight, TRACK_ACTIVE);
        }

        // Thumb handle (10px wide, 10px tall)
        int thumbX = trackX + progressWidth - 5;
        int thumbY = trackY - 3;
        int thumbSize = 10;

        int thumbBg = isHovered() ? 0xFFFFFFFF : 0xFFEDEDED;
        graphics.fill(thumbX + 1, thumbY, thumbX + thumbSize - 1, thumbY + thumbSize, thumbBg);
        graphics.fill(thumbX, thumbY + 1, thumbX + thumbSize, thumbY + thumbSize - 1, thumbBg);

        // Inner green dot on thumb
        graphics.fill(thumbX + 3, thumbY + 3, thumbX + thumbSize - 3, thumbY + thumbSize - 3, THUMB_BORDER);
    }
}
