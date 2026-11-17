package io.asy.fragmented.gui;

import com.mojang.authlib.GameProfile;
import io.asy.fragmented.EntityPositionRiderAccess;
import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeRiderScale;
import io.asy.fragmented.mixin.EntityRenderDispatcherAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

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
    private SlimePreviewPlayer previewPlayer;
    private ClientLevel activeLevel;

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

    private ClientLevel getEffectiveLevel(Minecraft client) {
        if (client.level != null) {
            return client.level;
        }
        return SlimePreviewLevel.get();
    }

    private void ensureEntities(Minecraft client) {
        ClientLevel targetLevel = getEffectiveLevel(client);
        if (previewSlime == null || previewPlayer == null || activeLevel != targetLevel) {
            activeLevel = targetLevel;
            previewSlime = new Slime(EntityTypes.SLIME, activeLevel);
            previewSlime.setId(-1000);

            GameProfile profile = client.getUser() != null && client.getUser().getProfileId() != null
                    ? new GameProfile(client.getUser().getProfileId(), client.getUser().getName())
                    : new GameProfile(UUID.randomUUID(), "PreviewPlayer");
            previewPlayer = new SlimePreviewPlayer(activeLevel, profile);
            previewPlayer.setId(-1001);

            PlayerSkin skin = null;
            if (client.player != null) {
                skin = client.player.getSkin();
            } else {
                try {
                    skin = client.getSkinManager().createLookup(profile, true).get();
                } catch (Throwable ignored) {}
            }
            if (skin == null && client.getUser() != null && client.getUser().getProfileId() != null) {
                skin = DefaultPlayerSkin.get(client.getUser().getProfileId());
            }
            previewPlayer.setSkin(skin != null ? skin : DefaultPlayerSkin.getDefaultSkin());
            previewPlayer.updateSkinCustomization(client);
        }

        // Keep preview player equipment matching real player when in-game
        if (client.player != null && previewPlayer != null) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                previewPlayer.setItemSlot(slot, client.player.getItemBySlot(slot));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private EntityRenderer<LivingEntity, AvatarRenderState> getAvatarRenderer(
            EntityRenderDispatcher dispatcher, SlimePreviewPlayer player) {
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(player);
        if (renderer == null) {
            var playerRenderers = ((EntityRenderDispatcherAccessor) dispatcher).slimeform$getPlayerRenderers();
            AvatarRenderer<?> avatarRenderer = playerRenderers.get(player.getSkin().model());
            if (avatarRenderer == null) {
                avatarRenderer = playerRenderers.get(PlayerModelType.WIDE);
            }
            renderer = avatarRenderer;
        }
        return (EntityRenderer<LivingEntity, AvatarRenderState>) (EntityRenderer<?, ?>) renderer;
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
        graphics.text(font, Component.literal("⟳ Drag to Orbit | Zoom: " + (int) (zoom * 100) + "%"),
                x + 10, y + height - 14, 0x80FFFFFF, false);

        // 4. Viewport boundaries
        int x1 = x + 10;
        int y1 = y + 26;
        int x2 = x + width - 10;
        int y2 = y + height - 20;

        ensureEntities(client);
        if (previewSlime == null || previewPlayer == null) {
            return;
        }

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        SlimeFormConfig config = SlimeFormConfig.get();

        // Shared 3D camera orientation for all models on stage
        Quaternionf cameraRot = new Quaternionf().rotateZ((float) Math.PI);
        cameraRot.rotateX(rotX * ((float) Math.PI / 180.0F));
        cameraRot.rotateY(rotY * ((float) Math.PI / 180.0F));

        try {
            switch (mode) {
                case AVATAR_SHADERS -> {
                    if (previewPlayer.isPassenger()) {
                        previewPlayer.stopRiding();
                    }
                    previewPlayer.entityTags().clear();
                    previewPlayer.addTag(SlimeFormMod.SLIME_FORM_TAG);
                    SlimeRiderScale.removeRiderScale(previewPlayer);
                    previewPlayer.updateSkinCustomization(client);

                    float scale = 48.0F * zoom;
                    EntityRenderer<LivingEntity, AvatarRenderState> avatarRenderer = getAvatarRenderer(dispatcher, previewPlayer);
                    AvatarRenderState playerState = avatarRenderer.createRenderState(previewPlayer, 1.0F);
                    playerState.shadowPieces.clear();
                    playerState.outlineColor = 0;
                    playerState.bodyRot = 180.0F;
                    playerState.yRot = 0.0F;
                    playerState.xRot = 0.0F;
                    playerState.scale = 1.0F;

                    Vector3f pos = new Vector3f(0.0F, playerState.boundingBoxHeight / 2.0F, 0.0F);
                    graphics.entity(playerState, scale, pos, cameraRot, null, x1, y1, x2, y2);
                }
                case SLIME_SCALE -> {
                    int size = customSlimeSize != null ? Math.max(1, customSlimeSize) : Math.max(1, config.effectiveMaxSlimeSize());
                    previewSlime.setSize(size, false);

                    float scale = (22.0F + size * 5.0F) * zoom;
                    @SuppressWarnings("unchecked")
                    EntityRenderer<LivingEntity, SlimeRenderState> slimeRenderer =
                            (EntityRenderer<LivingEntity, SlimeRenderState>) (EntityRenderer<?, ?>) dispatcher.getRenderer(previewSlime);
                    SlimeRenderState slimeState = slimeRenderer.createRenderState(previewSlime, 1.0F);
                    slimeState.shadowPieces.clear();
                    slimeState.outlineColor = 0;
                    slimeState.bodyRot = 180.0F;
                    slimeState.yRot = 0.0F;
                    slimeState.xRot = 0.0F;

                    Vector3f pos = new Vector3f(0.0F, slimeState.boundingBoxHeight / 2.0F, 0.0F);
                    graphics.entity(slimeState, scale, pos, cameraRot, null, x1, y1, x2, y2);
                }
                case MOUNTED_RIDER -> {
                    int slimeSize = customSlimeSize != null ? Math.max(1, customSlimeSize) : 2;
                    previewSlime.setSize(slimeSize, false);
                    previewSlime.setPos(0, 0, 0);

                    // Configure player state tags
                    previewPlayer.entityTags().clear();
                    previewPlayer.addTag(SlimeFormMod.SLIME_FORM_TAG);
                    previewPlayer.addTag("slimeform.size." + slimeSize);
                    previewPlayer.updateSkinCustomization(client);

                    // Apply rider scale modifier to player's Attributes.SCALE
                    SlimeRiderScale.applyRiderScale(previewPlayer, slimeSize);

                    // Mount programmatically in memory
                    if (previewPlayer.getVehicle() != previewSlime) {
                        previewPlayer.startRiding(previewSlime, true, false);
                    }

                    // Execute real in-game riding positioning (vanilla + SlimePassengerPositionMixin)
                    ((EntityPositionRiderAccess) previewSlime).slimeform$positionRider(previewPlayer, Entity::setPos);

                    float relX = (float) (previewPlayer.getX() - previewSlime.getX());
                    float relY = (float) (previewPlayer.getY() - previewSlime.getY());
                    float relZ = (float) (previewPlayer.getZ() - previewSlime.getZ());

                    float baseScale = 40.0F * zoom;

                    // Extract real slime render state
                    @SuppressWarnings("unchecked")
                    EntityRenderer<LivingEntity, SlimeRenderState> slimeRenderer =
                            (EntityRenderer<LivingEntity, SlimeRenderState>) (EntityRenderer<?, ?>) dispatcher.getRenderer(previewSlime);
                    SlimeRenderState slimeState = slimeRenderer.createRenderState(previewSlime, 1.0F);
                    slimeState.shadowPieces.clear();
                    slimeState.outlineColor = 0;
                    slimeState.bodyRot = 180.0F;
                    slimeState.yRot = 0.0F;
                    slimeState.xRot = 0.0F;

                    // Extract real player render state (triggers SlimeMorphAvatarRendererMixin & SlimeAppearanceShellLayer)
                    EntityRenderer<LivingEntity, AvatarRenderState> avatarRenderer = getAvatarRenderer(dispatcher, previewPlayer);
                    AvatarRenderState playerState = avatarRenderer.createRenderState(previewPlayer, 1.0F);
                    playerState.shadowPieces.clear();
                    playerState.outlineColor = 0;
                    playerState.bodyRot = 180.0F;
                    playerState.yRot = 0.0F;
                    playerState.xRot = 0.0F;

                    // Slime centered vertically in stage
                    float stageCenterY = slimeState.boundingBoxHeight / 2.0F - 0.25F;
                    Vector3f slimePos = new Vector3f(0.0F, stageCenterY, 0.0F);

                    // Transform relative passenger offset by camera rotation so rider follows slime orientation perfectly
                    Vector3f riderOffset = new Vector3f(relX, relY, relZ);
                    cameraRot.transform(riderOffset);
                    Vector3f playerPos = new Vector3f(slimePos).add(riderOffset);

                    // Submit both entities to the native GUI render pipeline
                    graphics.entity(slimeState, baseScale, slimePos, cameraRot, null, x1, y1, x2, y2);
                    graphics.entity(playerState, baseScale, playerPos, cameraRot, null, x1, y1, x2, y2);
                }
                case FLOATING_ITEMS -> {
                    previewSlime.setSize(2, false);
                    float scale = 42.0F * zoom;
                    @SuppressWarnings("unchecked")
                    EntityRenderer<LivingEntity, SlimeRenderState> slimeRenderer =
                            (EntityRenderer<LivingEntity, SlimeRenderState>) (EntityRenderer<?, ?>) dispatcher.getRenderer(previewSlime);
                    SlimeRenderState slimeState = slimeRenderer.createRenderState(previewSlime, 1.0F);
                    slimeState.shadowPieces.clear();
                    slimeState.outlineColor = 0;
                    slimeState.bodyRot = 180.0F;
                    slimeState.yRot = 0.0F;
                    slimeState.xRot = 0.0F;

                    Vector3f pos = new Vector3f(0.0F, slimeState.boundingBoxHeight / 2.0F, 0.0F);
                    graphics.entity(slimeState, scale, pos, cameraRot, null, x1, y1, x2, y2);
                }
                case RECOVERY_RING -> {
                    previewSlime.setSize(1, false);
                    float scale = 50.0F * zoom;
                    @SuppressWarnings("unchecked")
                    EntityRenderer<LivingEntity, SlimeRenderState> slimeRenderer =
                            (EntityRenderer<LivingEntity, SlimeRenderState>) (EntityRenderer<?, ?>) dispatcher.getRenderer(previewSlime);
                    SlimeRenderState slimeState = slimeRenderer.createRenderState(previewSlime, 1.0F);
                    slimeState.shadowPieces.clear();
                    slimeState.outlineColor = 0;
                    slimeState.bodyRot = 180.0F;
                    slimeState.yRot = 0.0F;
                    slimeState.xRot = 0.0F;

                    Vector3f pos = new Vector3f(0.0F, slimeState.boundingBoxHeight / 2.0F, 0.0F);
                    graphics.entity(slimeState, scale, pos, cameraRot, null, x1, y1, x2, y2);
                }
            }
        } catch (Throwable t) {
            SlimeFormMod.LOGGER.error("[slimeform] Error rendering stage preview", t);
            // Safe fallback if unexpected issue occurs
            graphics.centeredText(font, Component.literal("✦ Slime Stage ✦"), x + width / 2, y + height / 2 - 8, 0xFF47D147);
            graphics.centeredText(font, Component.literal("Stage Preview Active"), x + width / 2, y + height / 2 + 6, 0x80FFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
