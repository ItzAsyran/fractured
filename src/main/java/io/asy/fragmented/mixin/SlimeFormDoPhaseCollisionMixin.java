package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.SlimeFormMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
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
        boolean playerPhase = entity instanceof Player player
                && (SlimeFormState.isActive(player)
                    || (player.level().isClientSide()
                        && SlimeFormState.isClientVisualSlimeForm(player)))
                && SlimeFormState.isPhaseEnabled(player);
        boolean slimePhase = entity instanceof Slime slime
                && SlimeFormMod.isPlayerOriginSlime(slime)
                && SlimeFormState.isPhaseEnabled(slime);
        if (playerPhase || slimePhase) {
            boolean phaseable = isPhaseable(state);
            if (phaseable) {
                VoxelShape vanillaShape = state.getCollisionShape(level, pos, context);
                boolean crouching = entity instanceof Player player && player.isCrouching();
                boolean descending = context.isDescending();

                // Stairs remain solid during ordinary movement so their normal
                // step-up collision is preserved. Sneaking enables phasing.
                if (state.getBlock() instanceof StairBlock) {
                    return entity instanceof Player && !crouching ? vanillaShape : Shapes.empty();
                }

                boolean standingAbove = isStandingAbove(vanillaShape, context, pos);

                if (crouching) {
                    return Shapes.empty();
                }
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

    private static boolean isPhaseable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StairBlock
                || block instanceof SlabBlock
                || block instanceof IronBarsBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof WallBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof AbstractChestBlock
                || block instanceof ChainBlock
                || block instanceof SignBlock
                || block instanceof ButtonBlock
                || block instanceof PressurePlateBlock
                || block instanceof LanternBlock
                || block instanceof LeavesBlock
                || block instanceof LeverBlock
                || block instanceof SlimeBlock;
    }
}
