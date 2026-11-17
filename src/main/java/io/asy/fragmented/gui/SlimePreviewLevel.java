package io.asy.fragmented.gui;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.scores.Scoreboard;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Headless, non-ticking ClientLevel stub used strictly for offline live preview rendering in main menu.
 */
public class SlimePreviewLevel extends ClientLevel {
    private static SlimePreviewLevel instance;
    private static EmptyLevelChunk dummyChunk;
    private static final ClientLevel.ClientLevelData PREVIEW_LEVEL_DATA =
            new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
    private static final Scoreboard SCOREBOARD = new Scoreboard();

    public static SlimePreviewLevel get() {
        if (instance == null) {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Unsafe unsafe = (Unsafe) f.get(null);
                instance = (SlimePreviewLevel) unsafe.allocateInstance(SlimePreviewLevel.class);

                Field isClientSideField = Level.class.getDeclaredField("isClientSide");
                isClientSideField.setAccessible(true);
                isClientSideField.setBoolean(instance, true);

                Field randomField = Level.class.getDeclaredField("random");
                randomField.setAccessible(true);
                randomField.set(instance, RandomSource.create());

                dummyChunk = (EmptyLevelChunk) unsafe.allocateInstance(EmptyLevelChunk.class);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to allocate preview level", t);
            }
        }
        return instance;
    }

    protected SlimePreviewLevel() {
        super(null, PREVIEW_LEVEL_DATA, Level.OVERWORLD, null, 0, 63, null, false, 0L, 0);
    }

    @Override
    public int getNextEntityId() {
        return -1000;
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull) {
        return dummyChunk;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public boolean hasChunkAt(BlockPos pos) {
        return true;
    }

    @Override
    public ClientChunkCache getChunkSource() {
        return null;
    }

    @Override
    public Scoreboard getScoreboard() {
        return SCOREBOARD;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return 15;
    }

    @Override
    public int getMaxLocalRawBrightness(BlockPos pos) {
        return 15;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public ClientLevel.ClientLevelData getLevelData() {
        return PREVIEW_LEVEL_DATA;
    }

    @Override
    public Difficulty getDifficulty() {
        return Difficulty.NORMAL;
    }

    @Override
    public RegistryAccess registryAccess() {
        return RegistryAccess.EMPTY;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return FeatureFlags.DEFAULT_FLAGS;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getHeight() {
        return 384;
    }
}
