package io.asy.fragmented.gui;

import io.asy.fragmented.SlimeFormConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

public class SlimeStageWidget extends AbstractWidget {
    public enum PreviewMode {
        AVATAR_SHADERS("Avatar Shaders"),
        SLIME_SCALE("Slime Growth Scale"),
        MOUNTED_RIDER("Mounted Rider Offset"),
        FLOATING_ITEMS("Floating Items Belly"),
        RECOVERY_RING("Lineage Recovery Ring");

        private final String displayName;

        PreviewMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final int STAGE_BG = 0x20000000;
    private static final int STAGE_BORDER = 0x38FFFFFF;

    private PreviewMode mode = PreviewMode.AVATAR_SHADERS;
    private float rotY = 0.0F;
    private float rotX = 0.0F;
    private float zoom = 1.0F;
    private Slime previewSlime;
    private Level lastLevel;

    public SlimeStageWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Slime Stage"));
    }

    public PreviewMode getPreviewMode() {
        return mode;
    }

    public void setPreviewMode(PreviewMode mode) {
        this.mode = mode;
    }

    public void resetView() {
        this.rotY = 0.0F;
        this.rotX = 0.0F;
        this.zoom = 1.0F;
    }

    public void adjustZoom(float delta) {
        this.zoom = Mth.clamp(this.zoom + delta, 0.5F, 2.0F);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        this.rotY += (float) dragX * 1.5F;
        this.rotX -= (float) dragY * 1.5F;
        this.rotX = Mth.clamp(this.rotX, -60.0F, 60.0F);
    }

    private Slime getOrCreateSlime(Level level) {
        if (previewSlime == null || lastLevel != level) {
            previewSlime = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
            if (previewSlime != null) {
                previewSlime.setId(-1000);
            }
            lastLevel = level;
        }
        return previewSlime;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        Font font = Minecraft.getInstance().font;
        Minecraft client = Minecraft.getInstance();

        // 1. Stage container background & border
        graphics.fill(x + 2, y, x + width - 2, y + height, STAGE_BG);
        graphics.fill(x, y + 2, x + width, y + height - 2, STAGE_BG);

        graphics.fill(x + 2, y, x + width - 2, y + 1, STAGE_BORDER);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, STAGE_BORDER);
        graphics.fill(x, y + 2, x + 1, y + height - 2, STAGE_BORDER);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, STAGE_BORDER);

        // 2. Mode pill badge at top left
        String modeText = "✦ " + mode.getDisplayName();
        int badgeWidth = font.width(modeText) + 12;
        graphics.fill(x + 8, y + 8, x + 8 + badgeWidth, y + 22, 0x30FFFFFF);
        graphics.text(font, Component.literal(modeText), x + 14, y + 11, 0xFF47D147, false);

        // 3. Interaction hint at bottom
        graphics.text(font, Component.literal("⟳ Drag to Orbit | Mode: " + (int)(zoom * 100) + "%"),
                x + 10, y + height - 14, 0x80FFFFFF, false);

        // 4. Render 3D Entity stage
        if (client.level != null && client.player != null) {
            int x1 = x + 10;
            int y1 = y + 26;
            int x2 = x + width - 10;
            int y2 = y + height - 20;

            SlimeFormConfig config = SlimeFormConfig.get();
            try {
                switch (mode) {
                    case AVATAR_SHADERS -> {
                        int scale = (int) (48 * zoom);
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, x1, y1, x2, y2, scale, 0.0F, rotY, rotX, client.player);
                    }
                    case SLIME_SCALE -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            int size = Math.max(1, config.effectiveMaxSlimeSize());
                            slime.setSize(size, false);
                            int scale = (int) ((24 + size * 4) * zoom);
                            InventoryScreen.extractEntityInInventoryFollowsMouse(
                                    graphics, x1, y1, x2, y2, scale, 0.0F, rotY, rotX, slime);
                        }
                    }
                    case MOUNTED_RIDER -> {
                        int scale = (int) (42 * zoom);
                        float yOffset = (float) (config.effectiveRiderOffsetYPerSize() * 10.0D);
                        InventoryScreen.extractEntityInInventoryFollowsMouse(
                                graphics, x1, y1, x2, y2, scale, yOffset, rotY, rotX, client.player);
                    }
                    case FLOATING_ITEMS -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            slime.setSize(2, false);
                            int scale = (int) (45 * zoom);
                            InventoryScreen.extractEntityInInventoryFollowsMouse(
                                    graphics, x1, y1, x2, y2, scale, 0.0F, rotY, rotX, slime);
                        }
                    }
                    case RECOVERY_RING -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            slime.setSize(1, false);
                            int scale = (int) (55 * zoom);
                            InventoryScreen.extractEntityInInventoryFollowsMouse(
                                    graphics, x1, y1, x2, y2, scale, 0.0F, rotY, rotX, slime);
                        }
                    }
                }
            } catch (Throwable t) {
                // Safe fallback if entity rendering encounters an issue
                graphics.centeredText(font, Component.literal("Stage Preview Active"), x + width / 2, y + height / 2, 0xFF47D147);
            }
        } else {
            // Placeholder when not in-world
            graphics.centeredText(font, Component.literal("✦ Slime Stage ✦"), x + width / 2, y + height / 2 - 8, 0xFF47D147);
            graphics.centeredText(font, Component.literal("In-World Live Preview"), x + width / 2, y + height / 2 + 6, 0x80FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
