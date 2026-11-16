package io.asy.fragmented;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Finds a spot where an entity's bounding box fits. Used when the player turns back from a morph and
 * when they reform, so they are never left inside blocks: if their spot is too small they are moved
 * to the nearest place where they fit instead of the action being refused.
 */
final class SlimeSpaceFinder {
    private static final double[] VERTICAL_OFFSETS = {0.0D, 0.5D, 1.0D, 1.5D, 2.0D, 3.0D, -1.0D, -2.0D};
    /** Search a small area first, then widen. */
    private static final int[] SEARCH_RADII = {2, 4, 7};

    private SlimeSpaceFinder() {
    }

    /**
     * Returns where the entity should be so its box fits: its current position if that already
     * fits, otherwise the nearest fitting spot (spots with ground underneath are preferred), or
     * null if nothing fits within range.
     */
    static Vec3 findFittingPosition(ServerLevel level, Entity entity) {
        AABB box = entity.getBoundingBox();
        if (level.noCollision(entity, box)) {
            return entity.position();
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
                            best = entity.position().add(dx, dy, dz);
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
}
