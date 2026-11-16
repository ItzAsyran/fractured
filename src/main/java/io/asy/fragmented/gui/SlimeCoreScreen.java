package io.asy.fragmented.gui;

import io.asy.fragmented.SlimeFormClient;
import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.gui.components.BedrockButton;
import io.asy.fragmented.gui.components.BedrockTabButton;
import io.asy.fragmented.gui.tabs.AppearanceAudioTab;
import io.asy.fragmented.gui.tabs.GameplayMechanicsTab;
import io.asy.fragmented.gui.tabs.SlimeCoreTab;
import io.asy.fragmented.gui.tabs.TheLabTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class SlimeCoreScreen extends Screen {
    private static final int HEADER_BG = 0x80101010;
    private static final int FOOTER_BG = 0x80101010;
    private static final int DIVIDER_LINE = 0x38FFFFFF;
    private static final int SCROLLBAR_BG = 0x20FFFFFF;
    private static final int SCROLLBAR_THUMB = 0x60FFFFFF;

    private final Screen parent;
    private final List<SlimeCoreTab> tabs = new ArrayList<>();
    private final List<BedrockTabButton> tabButtons = new ArrayList<>();
    private int activeTab = 0;

    private SlimeStageWidget stageWidget;
    private BedrockButton resetViewBtn;
    private BedrockButton zoomInBtn;
    private BedrockButton zoomOutBtn;
    private BedrockButton closeBtn;
    private BedrockButton doneBtn;
    private BedrockButton revertBtn;

    private int rightX;
    private int contentY;
    private int rightWidth;
    private int contentHeight;
    private int scrollY = 0;

    public SlimeCoreScreen(Screen parent) {
        super(Component.literal("The Slime Core"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.tabs.clear();
        this.tabButtons.clear();

        int stageWidth = (int) (width * 0.38D);
        int stageHeight = height - 76;
        int stageX = 10;
        int stageY = 36;

        this.stageWidget = new SlimeStageWidget(stageX, stageY, stageWidth, stageHeight);
        this.addRenderableWidget(stageWidget);

        // Stage controls below the 3D viewport
        int btnW = (stageWidth - 8) / 3;
        this.resetViewBtn = new BedrockButton(stageX, stageY + stageHeight + 4, btnW, 18,
                Component.literal("↺ Reset"), BedrockButton.Style.NEUTRAL, () -> stageWidget.resetView());
        this.zoomOutBtn = new BedrockButton(stageX + btnW + 4, stageY + stageHeight + 4, btnW, 18,
                Component.literal("🔍 -"), BedrockButton.Style.NEUTRAL, () -> stageWidget.adjustZoom(-0.15F));
        this.zoomInBtn = new BedrockButton(stageX + (btnW + 4) * 2, stageY + stageHeight + 4, btnW, 18,
                Component.literal("🔍 +"), BedrockButton.Style.NEUTRAL, () -> stageWidget.adjustZoom(0.15F));

        this.addRenderableWidget(resetViewBtn);
        this.addRenderableWidget(zoomOutBtn);
        this.addRenderableWidget(zoomInBtn);

        // Right Console geometry
        this.rightX = stageX + stageWidth + 12;
        this.contentY = 36;
        this.rightWidth = width - rightX - 14;
        this.contentHeight = height - 70;

        // Initialize Tabs
        this.tabs.add(new GameplayMechanicsTab());
        this.tabs.add(new AppearanceAudioTab());
        this.tabs.add(new TheLabTab());

        for (SlimeCoreTab tab : tabs) {
            tab.init(rightX, contentY, rightWidth - 8, stageWidget);
        }

        // Top Navigation Tab Buttons
        Component[] tabNames = new Component[]{
                Component.literal("⚙ Gameplay & Powers"),
                Component.literal("🎨 Look & Sounds"),
                Component.literal("🔬 The Lab & Diagnostics")
        };

        int tabBtnWidth = 140;
        int tabsStartX = width / 2 - (tabNames.length * (tabBtnWidth + 4)) / 2;
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            BedrockTabButton tabBtn = new BedrockTabButton(
                    tabsStartX + i * (tabBtnWidth + 4), 6, tabBtnWidth, 24, tabNames[i], i,
                    i == activeTab,
                    tabIndex -> selectTab(tabIndex));
            tabButtons.add(tabBtn);
            this.addRenderableWidget(tabBtn);
        }

        // Header Close (X) button
        this.closeBtn = new BedrockButton(width - 28, 6, 20, 20, Component.literal("✕"),
                BedrockButton.Style.DANGER, this::onClose);
        this.addRenderableWidget(closeBtn);

        // Footer Done & Revert buttons
        this.revertBtn = new BedrockButton(rightX, height - 28, 120, 20,
                Component.literal("↺ Reset Config"), BedrockButton.Style.NEUTRAL, () -> {
            SlimeFormConfig fresh = new SlimeFormConfig();
            // Re-save fresh defaults
            SlimeFormConfig.save();
            this.init();
        });
        this.doneBtn = new BedrockButton(width - 110, height - 28, 96, 20,
                Component.literal("✓ Done / Save"), BedrockButton.Style.PRIMARY, () -> {
            SlimeFormConfig.save();
            SlimeFormClient.syncPlayerAppearance();
            this.onClose();
        });

        this.addRenderableWidget(revertBtn);
        this.addRenderableWidget(doneBtn);

        updateTabPositions();
    }

    private void selectTab(int index) {
        this.activeTab = index;
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).setSelected(i == index);
        }
        this.scrollY = 0;
        updateTabPositions();

        // Default stage mode per tab
        if (index == 0) {
            stageWidget.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE);
        } else if (index == 1) {
            stageWidget.setPreviewMode(SlimeStageWidget.PreviewMode.AVATAR_SHADERS);
        } else {
            stageWidget.resetView();
        }
    }

    private void updateTabPositions() {
        if (activeTab >= 0 && activeTab < tabs.size()) {
            tabs.get(activeTab).updateLayout(scrollY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= rightX && mouseX <= width && mouseY >= contentY && mouseY <= contentY + contentHeight) {
            SlimeCoreTab current = tabs.get(activeTab);
            int maxScroll = Math.max(0, current.getTotalHeight() - contentHeight + 20);
            this.scrollY = Mth.clamp(this.scrollY - (int) (scrollY * 24.0D), 0, maxScroll);
            updateTabPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= rightX && event.x() <= width && event.y() >= contentY && event.y() <= contentY + contentHeight) {
            if (activeTab >= 0 && activeTab < tabs.size()) {
                if (tabs.get(activeTab).mouseClicked(event, doubleClick)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (activeTab >= 0 && activeTab < tabs.size()) {
            if (tabs.get(activeTab).mouseReleased(event)) {
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (activeTab >= 0 && activeTab < tabs.size()) {
            if (tabs.get(activeTab).mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 1. In-game transparent background scrim
        this.extractTransparentBackground(graphics);

        Font font = Minecraft.getInstance().font;

        // 2. Header strip & divider line
        graphics.fill(0, 0, width, 32, HEADER_BG);
        graphics.fill(0, 31, width, 32, DIVIDER_LINE);

        // Header Title Brand
        graphics.text(font, Component.literal("✦ THE SLIME CORE"), 12, 12, 0xFF47D147, false);

        // 3. Footer strip & divider line
        graphics.fill(0, height - 34, width, height, FOOTER_BG);
        graphics.fill(0, height - 34, width, height - 33, DIVIDER_LINE);

        // 4. Scissored rendering of the active Tab inside Right Console
        if (activeTab >= 0 && activeTab < tabs.size()) {
            graphics.enableScissor(rightX, contentY, width - 4, contentY + contentHeight);
            tabs.get(activeTab).render(graphics, mouseX, mouseY, partialTick);
            graphics.disableScissor();

            // Scrollbar
            SlimeCoreTab current = tabs.get(activeTab);
            int totalH = current.getTotalHeight();
            if (totalH > contentHeight) {
                int scrollbarX = width - 8;
                int trackH = contentHeight;
                int thumbH = Math.max(20, (int) ((float) contentHeight / totalH * trackH));
                int maxScroll = totalH - contentHeight + 20;
                int thumbY = contentY + (int) ((float) scrollY / maxScroll * (trackH - thumbH));

                graphics.fill(scrollbarX, contentY, scrollbarX + 4, contentY + trackH, SCROLLBAR_BG);
                graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, SCROLLBAR_THUMB);
            }
        }

        // 5. Render registered widgets (stage, tab buttons, footer buttons)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        SlimeFormConfig.save();
        SlimeFormClient.syncPlayerAppearance();
        if (parent != null) {
            Minecraft.getInstance().setScreenAndShow(parent);
        } else {
            super.onClose();
        }
    }
}
