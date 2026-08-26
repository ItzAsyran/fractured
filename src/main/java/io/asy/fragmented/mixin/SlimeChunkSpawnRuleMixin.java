package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import io.asy.fragmented.SlimeFormConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Slime.class)
public final class SlimeChunkSpawnRuleMixin {
    @Redirect(
            method = "checkSlimeSpawnRules",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z"))
    private static boolean slimeform$ignoreAuraBiome(
            Holder<Biome> biome,
            TagKey<Biome> tag,
            EntityType<Slime> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos) {
        if (SlimeFormMod.isSlimeChunkAuraActive(level, pos)) {
            return false;
        }
        return biome.is(tag);
    }

    @Redirect(
            method = "checkSlimeSpawnRules",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
                    ordinal = 1))
    private static int slimeform$allowNearbySlimeChunk(
            RandomSource random,
            int bound,
            EntityType<Slime> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos) {
        if (bound == 10 && SlimeFormMod.isSlimeChunkAuraActive(level, pos)) {
            return random.nextInt(100) < SlimeFormConfig.get().effectiveSlimeChunkChance() ? 0 : 1;
        }
        return random.nextInt(bound);
    }
}
