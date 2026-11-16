package io.asy.fragmented.gui.tabs;

import io.asy.fragmented.gui.SlimeStageWidget;
import io.asy.fragmented.gui.components.BedrockCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

public abstract class SlimeCoreTab {
    protected final List<BedrockCard> cards = new ArrayList<>();
    protected int x;
    protected int y;
    protected int width;
    protected SlimeStageWidget stage;

    public void init(int x, int y, int width, SlimeStageWidget stage) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.stage = stage;
        this.cards.clear();
        buildCards();
    }

    protected abstract void buildCards();

    public int getTotalHeight() {
        int height = 0;
        for (BedrockCard card : cards) {
            height += card.getHeight() + 10;
        }
        return height;
    }

    public void updateLayout(int scrollY) {
        int curY = y - scrollY;
        for (BedrockCard card : cards) {
            card.setPosition(x, curY, width);
            curY += card.getHeight() + 10;
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (BedrockCard card : cards) {
            card.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (BedrockCard card : cards) {
            if (card.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        for (BedrockCard card : cards) {
            if (card.mouseReleased(event)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        for (BedrockCard card : cards) {
            if (card.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }
}
