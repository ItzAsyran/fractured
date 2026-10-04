package io.asy.fragmented;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Set;

/** Temporary, bounded diagnostics for locating morph phase suffocation. */
public final class SlimeFormPhaseDebug {
    private static final int MAX_MESSAGES = 40;
    private static final Set<String> REPORTED = new HashSet<>();
    private static int messages;

    private SlimeFormPhaseDebug() {
    }

    public static boolean enabled() {
        return SlimeFormConfig.get().phaseDebugEnabled;
    }

    public static void reset() {
        REPORTED.clear();
        messages = 0;
    }

    public static void recordInWall(Entity entity, boolean result) {
        if (!enabled() || !isAffected(entity)) {
            return;
        }
        String block = firstIntersectingBlock(entity);
        report("isInWall", entity, block,
                "result=" + result + " phase=" + SlimeFormState.isPhaseEnabled(entity));
    }

    public static void recordDamage(Entity entity, String source, boolean ignored) {
        if (!enabled() || !isAffected(entity)) {
            return;
        }
        String block = firstIntersectingBlock(entity);
        report("hurtServer", entity, block,
                "source=" + source
                        + " ignored=" + ignored
                        + " phase=" + SlimeFormState.isPhaseEnabled(entity));
    }

    private static boolean isAffected(Entity entity) {
        return entity instanceof Slime slime && SlimeMorphManager.isMorphBody(slime)
                || entity instanceof Player player && SlimeMorphManager.isMorphedClient(player);
    }

    private static String firstIntersectingBlock(Entity entity) {
        float width = entity.getDimensions(entity.getPose()).width() * 0.8F;
        AABB probe = AABB.ofSize(entity.getEyePosition(), width, 1.0E-6D, width);
        VoxelShape entityShape = Shapes.create(probe);
        CollisionContext context = CollisionContext.of(entity);
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
                    if (!shape.isEmpty() && Shapes.joinIsNotEmpty(
                            shape.move(x, y, z), entityShape, BooleanOp.AND)) {
                        return BuiltInRegistries.BLOCK.getKey(state.getBlock())
                                + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                + " phaseable=" + SlimeFormPhaseableBlocks.isPhaseable(state)
                                + " shapeEmpty=" + shape.isEmpty()
                                + " suffocating=" + state.isSuffocating(entity.level(), pos);
                    }
                }
            }
        }
        return "none";
    }

    private static void report(String event, Entity entity, String block, String details) {
        if (messages >= MAX_MESSAGES) {
            return;
        }
        String key = event + '|' + entity.getClass().getSimpleName() + '|' + block;
        if (!REPORTED.add(key)) {
            return;
        }
        messages++;
        SlimeFormMod.LOGGER.info(
                "[PhaseDebug] {} entity={} morphBody={} block={} {}",
                event,
                entity.getClass().getSimpleName(),
                entity instanceof Slime slime && SlimeMorphManager.isMorphBody(slime),
                block,
                details + " noPhysics=" + entity.noPhysics);
    }
}
