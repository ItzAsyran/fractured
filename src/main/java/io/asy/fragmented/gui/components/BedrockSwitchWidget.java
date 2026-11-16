package io.asy.fragmented.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

public class BedrockSwitchWidget extends AbstractWidget {
    private static final int TRACK_ACTIVE = 0xFF47D147;
    private static final int TRACK_INACTIVE = 0x33FFFFFF;
    private static final int TRACK_INACTIVE_HOVER = 0x50FFFFFF;
    private static final int KNOB_COLOR = 0xFFFFFFFF;
    private static final int BORDER_COLOR = 0x44FFFFFF;

    private boolean value;
    private final Consumer<Boolean> onToggle;

    public BedrockSwitchWidget(int x, int y, boolean initial, Consumer<Boolean> onToggle) {
        super(x, y, 30, 16, Component.empty());
        this.value = initial;
        this.onToggle = onToggle;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.value = !this.value;
        if (this.onToggle != null) {
            this.onToggle.accept(this.value);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int trackColor = value ? TRACK_ACTIVE : (isHovered() ? TRACK_INACTIVE_HOVER : TRACK_INACTIVE);

        // Pill shape track (rounded ends)
        graphics.fill(x + 2, y, x + width - 2, y + height, trackColor);
        graphics.fill(x, y + 2, x + width, y + height - 2, trackColor);

        // 1px subtle track outline
        graphics.fill(x + 2, y, x + width - 2, y + 1, BORDER_COLOR);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, BORDER_COLOR);
        graphics.fill(x, y + 2, x + 1, y + height - 2, BORDER_COLOR);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, BORDER_COLOR);

        // Sliding knob
        int knobX = value ? (x + width - 14) : (x + 2);
        int knobY = y + 2;
        int knobSize = height - 4; // 12px

        // Rounded knob
        graphics.fill(knobX + 1, knobY, knobX + knobSize - 1, knobY + knobSize, KNOB_COLOR);
        graphics.fill(knobX, knobY + 1, knobX + knobSize, knobY + knobSize - 1, KNOB_COLOR);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
