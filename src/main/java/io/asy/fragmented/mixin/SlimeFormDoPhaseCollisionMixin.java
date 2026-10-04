package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeMorphManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import io.asy.fragmented.SlimeFormPhaseableBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Filters block shapes at the collision-collection call used by vanilla movement. */
@Mixin(BlockCollisions.class)
public abstract class SlimeFormDoPhaseCollisionMixin {
    @Redirect(
            method = "computeNext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/CollisionContext;"
                            + "getCollisionShape(Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/CollisionGetter;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + ")Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape slimeform$filterPhaseableCollision(
            CollisionContext context, BlockState state, CollisionGetter level, BlockPos pos) {
        Entity entity = context instanceof EntityCollisionContext entityContext
                ? entityContext.getEntity()
                : null;
        boolean slimePhase = entity instanceof Slime slime
                && SlimeMorphManager.isMorphBody(slime)
                && SlimeFormState.isPhaseEnabled(slime);
        if (slimePhase) {
            boolean phaseable = SlimeFormPhaseableBlocks.isPhaseable(state);
            if (phaseable) {
                VoxelShape vanillaShape = state.getCollisionShape(level, pos, context);
                boolean descending = context.isDescending();

                boolean standingAbove = isStandingAbove(vanillaShape, context, pos);
                if (descending) {
                    return Shapes.empty();
                }
                if (standingAbove) {
                    return vanillaShape;
                }

                return Shapes.empty();
            }
        }
        return state.getCollisionShape(level, pos, context);
    }

    private static boolean isStandingAbove(
            VoxelShape vanillaShape,
            CollisionContext context,
            BlockPos pos) {
        return !vanillaShape.isEmpty() && context.isAbove(vanillaShape, pos, true);
    }

}
