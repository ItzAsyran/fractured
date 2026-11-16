package io.asy.fragmented.gui;

import io.asy.fragmented.SlimeFormConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    private Integer customSlimeSize = null;
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

    public void setPreviewSlimeSize(int size) {
        this.customSlimeSize = size;
    }

    public void clearCustomSlimeSize() {
        this.customSlimeSize = null;
    }

    public void resetView() {
        this.rotY = 0.0F;
        this.rotX = 0.0F;
        this.zoom = 1.0F;
    }

    public void adjustZoom(float delta) {
        this.zoom = Mth.clamp(this.zoom + delta, 0.5F, 2.5F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isMouseOver(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        this.rotY += (float) dragX * 1.5F;
        this.rotX -= (float) dragY * 1.5F;
        this.rotX = Mth.clamp(this.rotX, -60.0F, 60.0F);
        return true;
    }

    private Slime getOrCreateSlime(Level level) {
        if (previewSlime == null || lastLevel != level) {
            previewSlime = EntityTypes.SLIME.create(level, EntitySpawnReason.TRIGGERED);
            if (previewSlime != null) {
                previewSlime.setId(-1000);
                previewSlime.setPos(0, 0, 0);
            }
            lastLevel = level;
        }
        return previewSlime;
    }

    @SuppressWarnings("unchecked")
    private <T extends LivingEntity, S extends EntityRenderState> void renderEntityOnStage(
            GuiGraphicsExtractor graphics,
            int x1, int y1, int x2, int y2,
            float scale,
            float xOffset, float yOffset, float zOffset,
            float bodyYaw,
            boolean isPassenger,
            T entity) {
        if (entity == null) {
            return;
        }

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<T, S> renderer = (EntityRenderer<T, S>) dispatcher.getRenderer(entity);
        S renderState = renderer.createRenderState(entity, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (renderState instanceof HumanoidRenderState humanoidState) {
            humanoidState.isPassenger = isPassenger;
        }

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = bodyYaw;
            livingState.yRot = bodyYaw;
            livingState.xRot = 0.0F;
            if (isPassenger) {
                livingState.pose = Pose.SITTING;
            }
            if (livingState.scale > 0.0F) {
                livingState.boundingBoxWidth /= livingState.scale;
                livingState.boundingBoxHeight /= livingState.scale;
                livingState.scale = 1.0F;
            }
        }

        // 3D camera orientation: full 360-degree orbit yaw and pitch tilt
        Quaternionf cameraRot = new Quaternionf().rotateZ((float) Math.PI);
        cameraRot.rotateX(rotX * ((float) Math.PI / 180.0F));
        cameraRot.rotateY(rotY * ((float) Math.PI / 180.0F));

        // Transform local (xOffset, zOffset) by camera yaw so the rider orbits with the slime in 3D
        float radY = (float) Math.toRadians(rotY);
        float cosY = (float) Math.cos(radY);
        float sinY = (float) Math.sin(radY);
        float transformedX = xOffset * cosY - zOffset * sinY;
        float transformedZ = xOffset * sinY + zOffset * cosY;

        Vector3f pos = new Vector3f(
                transformedX,
                renderState.boundingBoxHeight / 2.0F + yOffset,
                transformedZ);

        graphics.entity(renderState, scale, pos, cameraRot, null, x1, y1, x2, y2);
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
        graphics.text(font, Component.literal("⟳ Drag to Orbit | Zoom: " + (int)(zoom * 100) + "%"),
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
                        float scale = 48.0F * zoom;
                        renderEntityOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, 180.0F, false, client.player);
                    }
                    case SLIME_SCALE -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            int size = customSlimeSize != null ? Math.max(1, customSlimeSize) : Math.max(1, config.effectiveMaxSlimeSize());
                            slime.setSize(size, false);
                            float scale = (22.0F + size * 5.0F) * zoom;
                            renderEntityOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, 180.0F, false, slime);
                        }
                    }
                    case MOUNTED_RIDER -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            int slimeSize = 2;
                            slime.setSize(slimeSize, false);
                            float baseScale = 40.0F * zoom;

                            // Slime centered slightly lower in the viewport
                            float slimeYShift = -0.32F;
                            renderEntityOnStage(graphics, x1, y1, x2, y2, baseScale, 0.0F, slimeYShift, 0.0F, 180.0F, false, slime);

                            // Player sitting atop the slime matching SlimeRiderScale (0.40 scale for size 2)
                            float riderScale = 0.40F;
                            float playerScale = baseScale * riderScale;

                            // Offset positioning so the sitting legs and hips rest on top of the size 2 slime cube
                            float riderOffsetX = (float) (config.effectiveRiderOffsetX() * 0.25D);
                            float riderOffsetY = slimeYShift + 0.50F + (float) (config.effectiveRiderOffsetYPerSize() * 0.20D);
                            float riderOffsetZ = (float) (config.effectiveRiderOffsetZ() * 0.25D);

                            renderEntityOnStage(graphics, x1, y1, x2, y2, playerScale, riderOffsetX, riderOffsetY, riderOffsetZ, 180.0F, true, client.player);
                        }
                    }
                    case FLOATING_ITEMS -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            slime.setSize(2, false);
                            float scale = 42.0F * zoom;
                            renderEntityOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, 180.0F, false, slime);
                        }
                    }
                    case RECOVERY_RING -> {
                        Slime slime = getOrCreateSlime(client.level);
                        if (slime != null) {
                            slime.setSize(1, false);
                            float scale = 50.0F * zoom;
                            renderEntityOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, 180.0F, false, slime);
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
