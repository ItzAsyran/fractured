package io.asy.fragmented.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class BedrockCard {
    private static final int CARD_BG = 0x16FFFFFF;
    private static final int CARD_BORDER = 0x38FFFFFF;
    private static final int HEADER_DIVIDER = 0x22FFFFFF;

    private int x;
    private int y;
    private int width;
    private final Component title;
    private final List<AbstractWidget> children = new ArrayList<>();
    private final List<Component> rowLabels = new ArrayList<>();
    private final List<Integer> labelYPositions = new ArrayList<>();
    private int currentContentY;
    private boolean collapsible = false;
    private boolean collapsed = false;
    private AbstractWidget activeWidget = null;

    public BedrockCard(int x, int y, int width, Component title) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.title = title;
        this.currentContentY = 24; // padding below title
    }

    public void setPosition(int x, int y, int width) {
        int deltaX = x - this.x;
        int deltaY = y - this.y;
        this.x = x;
        this.y = y;
        this.width = width;

        for (AbstractWidget child : children) {
            child.setX(child.getX() + deltaX);
            child.setY(child.getY() + deltaY);
        }
    }

    public void setCollapsible(boolean collapsible, boolean startCollapsed) {
        this.collapsible = collapsible;
        this.collapsed = startCollapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void toggleCollapse() {
        if (collapsible) {
            this.collapsed = !this.collapsed;
        }
    }

    public BedrockSwitchWidget addSwitch(Component label, Component tooltip, boolean initial,
                                         Consumer<Boolean> onToggle, Runnable onFocus) {
        int switchX = x + width - 42;
        int switchY = y + currentContentY;
        BedrockSwitchWidget switchWidget = new BedrockSwitchWidget(switchX, switchY, initial, val -> {
            if (onFocus != null) onFocus.run();
            onToggle.accept(val);
        });
        if (tooltip != null) {
            switchWidget.setTooltip(Tooltip.create(tooltip));
        }

        children.add(switchWidget);
        rowLabels.add(label);
        labelYPositions.add(currentContentY + 4);
        currentContentY += 24;
        return switchWidget;
    }

    public BedrockSliderWidget addSlider(Component label, double current, double min, double max,
                                         boolean isInteger, double step, Function<Double, Component> formatter,
                                         Consumer<Double> onChange, Runnable onFocus) {
        int sliderX = x + 12;
        int sliderY = y + currentContentY;
        int sliderWidth = width - 24;
        int sliderHeight = 26;

        BedrockSliderWidget slider = new BedrockSliderWidget(
                sliderX, sliderY, sliderWidth, sliderHeight, label, current, min, max, isInteger, step,
                formatter,
                val -> {
                    if (onFocus != null) onFocus.run();
                    onChange.accept(val);
                });

        children.add(slider);
        currentContentY += sliderHeight + 6;
        return slider;
    }

    public BedrockSliderWidget addSlider(Component label, double current, double min, double max,
                                         boolean isInteger, Function<Double, Component> formatter,
                                         Consumer<Double> onChange, Runnable onFocus) {
        return addSlider(label, current, min, max, isInteger, isInteger ? 1.0D : 0.05D, formatter, onChange, onFocus);
    }

    public BedrockButton addButton(Component label, BedrockButton.Style style, Runnable onPress) {
        int btnX = x + 12;
        int btnY = y + currentContentY;
        int btnWidth = width - 24;
        int btnHeight = 20;

        BedrockButton button = new BedrockButton(btnX, btnY, btnWidth, btnHeight, label, style, onPress);
        children.add(button);
        currentContentY += btnHeight + 6;
        return button;
    }

    public void addWidget(AbstractWidget widget, int heightOffset) {
        children.add(widget);
        currentContentY += heightOffset;
    }

    public int getHeight() {
        if (collapsible && collapsed) {
            return 22;
        }
        return currentContentY + 6;
    }

    public List<AbstractWidget> getChildren() {
        return children;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int height = getHeight();

        // 1. Card background
        graphics.fill(x + 2, y, x + width - 2, y + height, CARD_BG);
        graphics.fill(x, y + 2, x + width, y + height - 2, CARD_BG);

        // 2. 1px card border
        graphics.fill(x + 2, y, x + width - 2, y + 1, CARD_BORDER);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, CARD_BORDER);
        graphics.fill(x, y + 2, x + 1, y + height - 2, CARD_BORDER);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, CARD_BORDER);

        // 3. Card Title Header
        Font font = Minecraft.getInstance().font;
        String titlePrefix = collapsible ? (collapsed ? "▶ " : "▼ ") : "✦ ";
        graphics.text(font, Component.literal(titlePrefix).append(title), x + 10, y + 7, 0xFF47D147, false);

        if (collapsible && collapsed) {
            return;
        }

        // Header divider
        graphics.fill(x + 8, y + 18, x + width - 8, y + 19, HEADER_DIVIDER);

        // Render Row Labels
        for (int i = 0; i < rowLabels.size(); i++) {
            Component lbl = rowLabels.get(i);
            int lblY = y + labelYPositions.get(i);
            graphics.text(font, lbl, x + 12, lblY, 0xFFE0E0E0, false);
        }

        // Render Children
        for (AbstractWidget child : children) {
            child.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (collapsible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 20) {
            toggleCollapse();
            return true;
        }
        if (collapsible && collapsed) {
            return false;
        }
        for (AbstractWidget child : children) {
            if (child.isMouseOver(mouseX, mouseY) && child.mouseClicked(event, doubleClick)) {
                this.activeWidget = child;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (collapsible && collapsed) {
            return false;
        }
        if (activeWidget != null) {
            boolean result = activeWidget.mouseReleased(event);
            activeWidget = null;
            return result;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (collapsible && collapsed) {
            return false;
        }
        if (activeWidget != null) {
            return activeWidget.mouseDragged(event, dragX, dragY);
        }
        return false;
    }
}
