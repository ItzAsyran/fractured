package io.asy.fragmented.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

public class BedrockTabButton extends AbstractWidget {
    private static final int BG_SELECTED = 0x25FFFFFF;
    private static final int BG_HOVER = 0x18FFFFFF;
    private static final int ACCENT_GREEN = 0xFF47D147;

    private final int tabIndex;
    private boolean selected;
    private final Consumer<Integer> onSelect;

    public BedrockTabButton(int x, int y, int width, int height, Component title, int tabIndex,
                            boolean selected, Consumer<Integer> onSelect) {
        super(x, y, width, height, title);
        this.tabIndex = tabIndex;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (!selected && onSelect != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.SLIME_SQUISH_SMALL, 1.35F));
            onSelect.accept(tabIndex);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        Font font = Minecraft.getInstance().font;

        // Background highlight
        if (selected) {
            graphics.fill(x + 2, y, x + width - 2, y + height - 2, BG_SELECTED);
            graphics.fill(x, y + 2, x + width, y + height - 4, BG_SELECTED);
            // Emerald accent line at bottom
            graphics.fill(x + 4, y + height - 2, x + width - 4, y + height, ACCENT_GREEN);
        } else if (isHovered()) {
            graphics.fill(x + 2, y, x + width - 2, y + height - 2, BG_HOVER);
        }

        // Text
        int textColor = selected ? 0xFFFFFFFF : (isHovered() ? 0xFFE0E0E0 : 0xFFAAAAAA);
        int textWidth = font.width(getMessage());
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2 - 1;
        graphics.text(font, getMessage(), textX, textY, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
