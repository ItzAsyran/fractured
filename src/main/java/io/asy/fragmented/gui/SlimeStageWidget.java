package io.asy.fragmented.gui;

import com.mojang.authlib.GameProfile;
import io.asy.fragmented.SlimeAppearanceRenderStateAccess;
import io.asy.fragmented.SlimeFormConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.PlayerSkin;
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
        if (level == null) {
            return null;
        }
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
    private AvatarRenderState getOrCreatePlayerRenderState(Minecraft client, float bodyYaw, boolean isPassenger) {
        AvatarRenderState renderState = null;
        if (client.player != null) {
            try {
                EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
                EntityRenderer<?, ?> rawRenderer = dispatcher.getRenderer(client.player);
                if (rawRenderer != null) {
                    EntityRenderer<LivingEntity, AvatarRenderState> avatarRenderer =
                            (EntityRenderer<LivingEntity, AvatarRenderState>) rawRenderer;
                    renderState = avatarRenderer.createRenderState(client.player, 1.0F);
                }
            } catch (Throwable ignored) {}
        }

        if (renderState == null) {
            renderState = new AvatarRenderState();
            PlayerSkin skin = null;
            try {
                if (client.getUser() != null && client.getUser().getProfileId() != null) {
                    GameProfile profile = new GameProfile(client.getUser().getProfileId(), client.getUser().getName());
                    var lookup = client.getSkinManager().createLookup(profile, true);
                    if (lookup != null) {
                        skin = lookup.get();
                    }
                    if (skin == null) {
                        skin = DefaultPlayerSkin.get(client.getUser().getProfileId());
                    }
                }
            } catch (Throwable ignored) {}
            if (skin == null) {
                skin = DefaultPlayerSkin.getDefaultSkin();
            }
            renderState.skin = skin;
        }

        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        renderState.isPassenger = isPassenger;
        renderState.bodyRot = bodyYaw;
        renderState.yRot = bodyYaw;
        renderState.xRot = 0.0F;
        renderState.pose = isPassenger ? Pose.SITTING : Pose.STANDING;
        if (renderState.scale > 0.0F) {
            renderState.boundingBoxWidth /= renderState.scale;
            renderState.boundingBoxHeight /= renderState.scale;
            renderState.scale = 1.0F;
        }
        if (renderState.boundingBoxHeight <= 0.0F) {
            renderState.boundingBoxHeight = 1.8F;
            renderState.boundingBoxWidth = 0.6F;
        }
        renderState.lightCoords = 15728880;

        return renderState;
    }

    @SuppressWarnings("unchecked")
    private SlimeRenderState getOrCreateSlimeRenderState(Minecraft client, int size, float bodyYaw) {
        SlimeRenderState renderState = null;
        if (client.level != null) {
            try {
                Slime slime = getOrCreateSlime(client.level);
                if (slime != null) {
                    slime.setSize(size, false);
                    EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
                    EntityRenderer<?, ?> rawRenderer = dispatcher.getRenderer(slime);
                    if (rawRenderer != null) {
                        EntityRenderer<LivingEntity, SlimeRenderState> slimeRenderer =
                                (EntityRenderer<LivingEntity, SlimeRenderState>) rawRenderer;
                        renderState = slimeRenderer.createRenderState(slime, 1.0F);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (renderState == null) {
            renderState = new SlimeRenderState();
        }

        renderState.entityType = EntityTypes.SLIME;
        renderState.size = size;
        renderState.squish = 0.0F;
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        renderState.bodyRot = bodyYaw;
        renderState.yRot = bodyYaw;
        renderState.xRot = 0.0F;
        renderState.pose = Pose.STANDING;
        renderState.boundingBoxWidth = 0.52F * size;
        renderState.boundingBoxHeight = 0.52F * size;
        renderState.scale = 1.0F;
        renderState.lightCoords = 15728880;

        return renderState;
    }

    private void renderStateOnStage(
            GuiGraphicsExtractor graphics,
            int x1, int y1, int x2, int y2,
            float scale,
            float xOffset, float yOffset, float zOffset,
            EntityRenderState renderState) {
        if (renderState == null) {
            return;
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

        // 4. Render 3D Entity stage (works both in-world and directly from Title/Main Menu)
        int x1 = x + 10;
        int y1 = y + 26;
        int x2 = x + width - 10;
        int y2 = y + height - 20;

        SlimeFormConfig config = SlimeFormConfig.get();
        try {
            switch (mode) {
                case AVATAR_SHADERS -> {
                    float scale = 48.0F * zoom;
                    AvatarRenderState playerState = getOrCreatePlayerRenderState(client, 180.0F, false);
                    if (playerState instanceof SlimeAppearanceRenderStateAccess appearance) {
                        appearance.slimeform$setAppearance(
                                true,
                                config.effectivePlayerTransparencyPercent(),
                                config.playerTransparencySlimeTint,
                                config.playerTransparencySlimeShell
                        );
                    }
                    renderStateOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, playerState);
                }
                case SLIME_SCALE -> {
                    int size = customSlimeSize != null ? Math.max(1, customSlimeSize) : Math.max(1, config.effectiveMaxSlimeSize());
                    SlimeRenderState slimeState = getOrCreateSlimeRenderState(client, size, 180.0F);
                    float scale = (22.0F + size * 5.0F) * zoom;
                    renderStateOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, slimeState);
                }
                case MOUNTED_RIDER -> {
                    int slimeSize = 2;
                    SlimeRenderState slimeState = getOrCreateSlimeRenderState(client, slimeSize, 180.0F);
                    float baseScale = 40.0F * zoom;

                    // Slime centered in the viewport
                    float slimeYShift = -0.32F;
                    renderStateOnStage(graphics, x1, y1, x2, y2, baseScale, 0.0F, slimeYShift, 0.0F, slimeState);

                    // Player sitting atop the slime matching SlimeRiderScale (0.40 scale for size 2)
                    float riderScale = 0.40F;
                    float playerScale = baseScale * riderScale;

                    AvatarRenderState playerState = getOrCreatePlayerRenderState(client, 180.0F, true);

                    // Baseline sitting offset: places player's hips and splayed sitting legs directly atop the size 2 slime
                    float riderOffsetX = (float) (config.effectiveRiderOffsetX() * 0.40D);
                    float riderOffsetY = -2.25F - (float) (config.effectiveRiderOffsetYPerSize() * 0.40D);
                    float riderOffsetZ = (float) (config.effectiveRiderOffsetZ() * 0.40D);

                    renderStateOnStage(graphics, x1, y1, x2, y2, playerScale, riderOffsetX, riderOffsetY, riderOffsetZ, playerState);
                }
                case FLOATING_ITEMS -> {
                    SlimeRenderState slimeState = getOrCreateSlimeRenderState(client, 2, 180.0F);
                    float scale = 42.0F * zoom;
                    renderStateOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, slimeState);
                }
                case RECOVERY_RING -> {
                    SlimeRenderState slimeState = getOrCreateSlimeRenderState(client, 1, 180.0F);
                    float scale = 50.0F * zoom;
                    renderStateOnStage(graphics, x1, y1, x2, y2, scale, 0.0F, 0.0F, 0.0F, slimeState);
                }
            }
        } catch (Throwable t) {
            // Safe fallback if entity rendering encounters an issue
            graphics.centeredText(font, Component.literal("✦ Slime Stage ✦"), x + width / 2, y + height / 2 - 8, 0xFF47D147);
            graphics.centeredText(font, Component.literal("Stage Preview Active"), x + width / 2, y + height / 2 + 6, 0x80FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
