package io.asy.fragmented;

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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Explicit block families that the temporary morph slime can phase through. */
public final class SlimeFormPhaseableBlocks {
    private SlimeFormPhaseableBlocks() {
    }

    public static boolean isPhaseable(BlockState state) {
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

    /** Mirrors vanilla's in-wall probe for the morph body's damage path. */
    public static boolean shouldIgnoreInWallDamage(Entity entity) {
        float width = entity.getDimensions(entity.getPose()).width() * 0.8F;
        AABB probe = AABB.ofSize(entity.getEyePosition(), width, 1.0E-6D, width);
        VoxelShape entityShape = Shapes.create(probe);
        CollisionContext context = CollisionContext.of(entity);
        boolean phaseableCollision = false;

        int minX = (int) Math.floor(probe.minX);
        int maxX = (int) Math.floor(probe.maxX);
        int minY = (int) Math.floor(probe.minY);
        int maxY = (int) Math.floor(probe.maxY);
        int minZ = (int) Math.floor(probe.minZ);
        int maxZ = (int) Math.floor(probe.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = entity.level().getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(entity.level(), pos, context);
                    if (shape.isEmpty()
                            || !Shapes.joinIsNotEmpty(
                                    shape.move(x, y, z), entityShape, BooleanOp.AND)) {
                        continue;
                    }

                    if (isPhaseable(state)) {
                        phaseableCollision = true;
                    } else if (state.isSuffocating(entity.level(), pos)) {
                        return false;
                    }
                }
            }
        }
        return phaseableCollision;
    }
}
