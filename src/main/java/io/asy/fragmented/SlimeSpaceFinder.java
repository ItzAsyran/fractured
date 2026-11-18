package io.asy.fragmented;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Finds a spot where an entity's bounding box fits. Used when the player turns back from a morph,
 * when they reform, and during absorb ejection, so entities are never placed inside blocks or over
 * the bottomless void.
 */
final class SlimeSpaceFinder {
    private static final double[] VERTICAL_OFFSETS = {0.0D, 0.5D, 1.0D, 1.5D, 2.0D, 3.0D, -1.0D, -2.0D};
    /** Search a small area first, then widen. */
    private static final int[] SEARCH_RADII = {2, 4, 7};

    private SlimeSpaceFinder() {
    }

    /**
     * Checks if solid ground exists somewhere beneath pos within checkDepth blocks,
     * ensuring entities are not dropped into the bottomless void.
     */
    static boolean hasGroundBeneath(ServerLevel level, Vec3 pos, int checkDepth) {
        if (pos.y < level.getMinY()) {
            return false;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.x, pos.y, pos.z);
        int minY = Math.max(level.getMinY(), cursor.getY() - checkDepth);
        while (cursor.getY() >= minY) {
            cursor.move(Direction.DOWN);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && state.isSolid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns where the entity should be so its box fits around its current position.
     */
    static Vec3 findFittingPosition(ServerLevel level, Entity entity) {
        return findFittingPosition(level, entity, entity.position());
    }

    /**
     * Finds a spot where the entity fits around the specified center position.
     */
    static Vec3 findFittingPosition(ServerLevel level, Entity entity, Vec3 center) {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(center);
        if (level.noCollision(entity, box)) {
            return center;
        }
        for (int radius : SEARCH_RADII) {
            Vec3 best = null;
            double bestCost = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (double dy : VERTICAL_OFFSETS) {
                        AABB moved = box.move(dx, dy, dz);
                        if (!level.noCollision(entity, moved)) {
                            continue;
                        }
                        boolean supported = !level.noCollision(entity, moved.move(0.0D, -0.5D, 0.0D));
                        double cost = dx * dx + dz * dz + dy * dy * 1.5D + (supported ? 0.0D : 9.0D);
                        if (cost < bestCost) {
                            bestCost = cost;
                            best = center.add(dx, dy, dz);
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * Finds a safe ejection position outside the slime's body where the entity does not collide with blocks
     * and is not placed over the void.
     */
    static Vec3 findSafeEjectPosition(ServerLevel level, Slime slime, LivingEntity entity) {
        double forwardDist = (slime.getBbWidth() * 0.5D) + (entity.getBbWidth() * 0.5D) + 0.35D;
        Vec3 look = slime.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        if (forward.lengthSqr() > 1.0E-4D) {
            forward = forward.normalize().scale(forwardDist);
        } else {
            forward = new Vec3(0.0D, 0.0D, forwardDist);
        }

        // Test directions in priority order: forward, left, right, backward, above
        Vec3[] offsets = new Vec3[]{
                forward,
                new Vec3(-forward.z, 0.0D, forward.x),
                new Vec3(forward.z, 0.0D, -forward.x),
                forward.scale(-1.0D),
                new Vec3(0.0D, slime.getBbHeight() + 0.1D, 0.0D)
        };

        Vec3 fallbackSpot = null;
        for (Vec3 offset : offsets) {
            Vec3 candidate = slime.position().add(offset);
            Vec3 fitted = findFittingPosition(level, entity, candidate);
            if (fitted != null) {
                if (hasGroundBeneath(level, fitted, 20)) {
                    return fitted;
                }
                if (fallbackSpot == null) {
                    fallbackSpot = fitted;
                }
            }
        }

        if (fallbackSpot != null) {
            return fallbackSpot;
        }

        Vec3 slimeFitted = findFittingPosition(level, entity, slime.position());
        return slimeFitted != null ? slimeFitted : slime.position();
    }
}
