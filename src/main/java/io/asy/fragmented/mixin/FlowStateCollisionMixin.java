package io.asy.fragmented.mixin;

import io.asy.fragmented.FlowStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class FlowStateCollisionMixin {
    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void slimeform$flowStateCollision(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Entity entity = (Entity) (Object) this;
        if (!FlowStateManager.isPossessed(entity)
                || movement.lengthSqr() == 0.0D) {
            return;
        }

        AABB box = entity.getBoundingBox();
        AABB search = box.expandTowards(movement).inflate(1.0E-7D);
        List<VoxelShape> shapes = new ArrayList<>();
        int minX = (int) Math.floor(search.minX);
        int maxX = (int) Math.floor(search.maxX);
        int minY = (int) Math.floor(search.minY);
        int maxY = (int) Math.floor(search.maxY);
        int minZ = (int) Math.floor(search.minZ);
        int maxZ = (int) Math.floor(search.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = entity.level().getBlockState(pos);
                    if (isFlowStatePassThrough(state)) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(entity.level(), pos);
                    if (!shape.isEmpty()) {
                        shapes.add(shape.move(x, y, z));
                    }
                }
            }
        }
        cir.setReturnValue(Entity.collideBoundingBox(entity, movement, box, entity.level(), shapes));
    }

    private static boolean isFlowStatePassThrough(BlockState state) {
        return state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof ScaffoldingBlock
                || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof LeavesBlock;
    }
}
