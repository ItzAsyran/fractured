package io.asy.fragmented;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shared client-side rendering helper that draws trapped entities inside a slime body or morph body.
 * <p>
 * Positions trapped entities completely in local holder space, guaranteeing 100% rigid lockstep
 * with the slime matrix regardless of server lag, packet delay, or network latency.
 * Implements reactive visuals:
 * 1. Digestive Jelly Acid Glaze (caustic acid tint that flashes red on damage)
 * 2. Paralyzed / Helpless Suspension Pose (zeroed walk animations with fluid drag tilt)
 * 3. Digestive Core Churn / Orbital Drift (orbital churn accelerating on hop/squish)
 */
public final class SlimeAbsorbRendererHelper {
    private static final ThreadLocal<Boolean> IS_RENDERING_TRAPPED = ThreadLocal.withInitial(() -> false);

    private SlimeAbsorbRendererHelper() {
    }

    public static boolean isRenderingTrapped() {
        return IS_RENDERING_TRAPPED.get();
    }

    public static int getTrappedEntityTint(LivingEntityRenderState state, int originalTint) {
        if (state.hasRedOverlay) {
            // Flashing caustic acid-burn / hurt red-amber
            return ARGB.color(245, 255, 90, 90);
        }
        // Subtle caustic acid sheen - toned down to preserve original colors and textures
        int acidWash = ARGB.color(255, 225, 255, 225);
        return ARGB.multiply(originalTint, acidWash);
    }

    public static void renderTrappedEntities(
            SlimeRenderState holderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (IS_RENDERING_TRAPPED.get()) {
            return;
        }
        if (!(holderState instanceof SlimeAbsorbRenderStateAccess access) || !access.slimeform$isHoldingAbsorbed()) {
            return;
        }

        int holderId = access.slimeform$getHolderEntityId();
        List<Integer> trappedIds = access.slimeform$getTrappedEntityIds();
        if (trappedIds == null || trappedIds.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // Stage 2 rule: do NOT draw trapped entities for the holder while camera is in first person
        if (cameraState.isFirstPerson) {
            if (mc.getCameraEntity() != null && mc.getCameraEntity().getId() == holderId) {
                return;
            }
            if (mc.player != null && (mc.player.getId() == holderId || SlimeAbsorbClientState.isHolderOf(holderId, mc.player.getId()))) {
                return;
            }
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        float partialTick = cameraState.cameraEntityPartialTicks;

        // Slime inner core bounds (inner cube is ~0.60 of slime dimensions)
        double innerWidth = holderState.boundingBoxWidth * 0.58D;
        double halfWidth = innerWidth * 0.5D;
        double innerHeight = holderState.boundingBoxHeight * 0.58D;
        double centerY = holderState.boundingBoxHeight * 0.35D; // Centered inside the 3D gelatinous cube

        int count = trappedIds.size();
        float time = holderState.ageInTicks;
        float squishFactor = Math.abs(holderState.squish);
        // Dynamic churn that accelerates during hops and squishes
        double churnSpeed = 0.010D + (squishFactor * 0.028D);

        try {
            IS_RENDERING_TRAPPED.set(true);
            for (int i = 0; i < count; i++) {
                int entityId = trappedIds.get(i);
                Entity entity = mc.level.getEntity(entityId);
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                    continue;
                }

                // Deterministic pseudo-random seed based on entityId
                long hash = ((long) entityId * 3123456789L) ^ 0x5DEECE66DL;
                double randX = ((hash & 0xFF) / 255.0D - 0.5D);
                double randZ = (((hash >> 8) & 0xFF) / 255.0D - 0.5D);
                double randY = (((hash >> 16) & 0xFF) / 255.0D - 0.5D);
                float randYaw = ((hash >> 24) & 0xFF) / 255.0F * 360.0F;

                // Option 1 & 3 Hybrid: 3D Stratified Cell Tiering + Double-Helix Vortex Churn
                double localX;
                double localY;
                double localZ;
                float dragPitch;
                float dragRoll;
                float wobbleYaw;

                if (count == 1) {
                    double orbitRadius = halfWidth * 0.28D;
                    double orbitAngle = (time * churnSpeed) + (hash % 100) * 0.1D;
                    localX = Math.cos(orbitAngle) * orbitRadius + (randX * halfWidth * 0.12D);
                    localZ = Math.sin(orbitAngle * 0.85D) * orbitRadius + (randZ * halfWidth * 0.12D);

                    double bob = Math.sin((time * 0.03D) + (entityId % 50)) * (innerHeight * 0.06D);
                    double squishReaction = -holderState.squish * 0.04D;
                    localY = centerY + (randY * innerHeight * 0.15D) + bob + squishReaction;

                    dragPitch = (float) (Math.sin(orbitAngle) * 3.0F + (holderState.squish * 2.5F));
                    dragRoll = (float) (Math.cos(orbitAngle) * 2.5F);
                    wobbleYaw = randYaw + (float) Math.sin((time + entityId % 40) * 0.015D) * 3.0F;
                } else {
                    // Option 1: Vertical stratification across distinct tiers
                    double u_i = ((double) i / (count - 1.0D)) - 0.5D; // -0.5 to +0.5
                    double tierY = centerY + (u_i * innerHeight * 0.48D) + (randY * innerHeight * 0.10D);

                    // Option 3: Double-helix vortex orbital angles and staggered radii
                    double theta = (time * churnSpeed) + ((2.0D * Math.PI * i) / count) + (randX * 0.20D);
                    double radiusMult = (i % 2 == 0) ? 0.62D : 0.38D;
                    double r = halfWidth * radiusMult + (randZ * halfWidth * 0.12D);

                    localX = r * Math.cos(theta);
                    localZ = r * Math.sin(theta);

                    // Vertical helical wave undulation + squish reaction
                    double wave = Math.sin((time * 0.035D) + (theta * 1.5D) + (entityId % 25)) * (innerHeight * 0.09D);
                    double squishReaction = -holderState.squish * 0.04D;
                    localY = tierY + wave + squishReaction;

                    // Fluid vortex drag rotations
                    dragPitch = (float) (Math.sin(theta) * 5.0F + (holderState.squish * 3.5F));
                    dragRoll = (float) (Math.cos(theta) * 4.0F);
                    wobbleYaw = randYaw - (float) Math.toDegrees(theta * 0.4D) + (float) Math.sin((time * 0.02D) + i) * 6.0F;
                }

                // Safety bounding: guarantee entities stay suspended within inner gelatinous core
                localX = Math.max(-halfWidth * 0.85D, Math.min(halfWidth * 0.85D, localX));
                localZ = Math.max(-halfWidth * 0.85D, Math.min(halfWidth * 0.85D, localZ));
                localY = Math.max(holderState.boundingBoxHeight * 0.12D, Math.min(holderState.boundingBoxHeight * 0.68D, localY));

                renderTrappedEntity(
                        dispatcher, living, partialTick,
                        localX, localY, localZ,
                        wobbleYaw, dragPitch, dragRoll,
                        poseStack, collector, cameraState);
            }
        } finally {
            IS_RENDERING_TRAPPED.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity, S extends LivingEntityRenderState> void renderTrappedEntity(
            EntityRenderDispatcher dispatcher,
            T living,
            float partialTick,
            double localX, double localY, double localZ,
            float yaw, float pitch, float roll,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        EntityRenderer<? super T, S> renderer = (EntityRenderer<? super T, S>) dispatcher.getRenderer(living);
        if (renderer == null) {
            return;
        }

        S state = renderer.createRenderState(living, partialTick);
        if (state == null) {
            return;
        }
        state.nameTag = null; // Hide floating name tags inside the gelatinous core

        // Paralyzed suspension pose: zero out walk animations so limbs float limp in the jelly
        state.walkAnimationSpeed = 0.0F;
        state.walkAnimationPos = 0.0F;
        state.shadowRadius = 0.0F;
        state.isInWater = true;

        Vec3 offset = renderer.getRenderOffset(state);

        poseStack.pushPose();
        // Translate entirely in local slime model space
        poseStack.translate(localX + offset.x(), localY + offset.y(), localZ + offset.z());

        // Suspension fluid rotations
        poseStack.rotateDegrees(Axis.YP, yaw);
        poseStack.rotateDegrees(Axis.XP, pitch);
        poseStack.rotateDegrees(Axis.ZP, roll);

        renderer.submit(state, poseStack, collector, cameraState);
        poseStack.popPose();
    }
}
