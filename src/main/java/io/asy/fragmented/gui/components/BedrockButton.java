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

public class BedrockButton extends AbstractWidget {
    public enum Style {
        PRIMARY(0x4047D147, 0x6047D147, 0xFF55FF55, 0xFFFFFFFF),
        NEUTRAL(0x20FFFFFF, 0x35FFFFFF, 0x50FFFFFF, 0xFFFFFFFF),
        DANGER(0x40FF5555, 0x60FF5555, 0xFFFF7777, 0xFFFFFFFF);

        final int bgNormal;
        final int bgHover;
        final int border;
        final int textColor;

        Style(int bgNormal, int bgHover, int border, int textColor) {
            this.bgNormal = bgNormal;
            this.bgHover = bgHover;
            this.border = border;
            this.textColor = textColor;
        }
    }

    private final Style style;
    private final Runnable onPress;

    public BedrockButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    public BedrockButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        this(x, y, width, height, message, Style.NEUTRAL, onPress);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (this.onPress != null && active) {
            this.onPress.run();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int bg = isHovered() && active ? style.bgHover : style.bgNormal;
        int border = isHovered() && active ? style.border : (style.border & 0x66FFFFFF);
        int text = active ? style.textColor : 0x80AAAAAA;

        // Rounded button fill
        graphics.fill(x + 1, y, x + width - 1, y + height, bg);
        graphics.fill(x, y + 1, x + width, y + height - 1, bg);

        // 1px border
        graphics.fill(x + 1, y, x + width - 1, y + 1, border);
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, border);
        graphics.fill(x, y + 1, x + 1, y + height - 1, border);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, border);

        // Centered text
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        graphics.text(font, getMessage(), textX, textY, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
