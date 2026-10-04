package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormMod;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inside the slime-chunk aura around active slime-form players, natural slime
 * spawns ignore the vanilla biome, moon phase, light level, height and
 * slime-chunk rules. Only peaceful difficulty and the basic mob placement
 * check (solid block below) still apply. Spawner spawns stay vanilla.
 */
@Mixin(Slime.class)
public final class SlimeChunkSpawnRuleMixin {
    @Inject(method = "checkSlimeSpawnRules", at = @At("HEAD"), cancellable = true)
    private static void slimeform$auraSpawnRules(
            EntityType<Slime> type,
            LevelAccessor level,
            EntitySpawnReason spawnReason,
            BlockPos pos,
            RandomSource random,
            CallbackInfoReturnable<Boolean> cir) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || EntitySpawnReason.isSpawner(spawnReason)
                || !SlimeFormMod.isSlimeChunkAuraActive(level, pos)) {
            return;
        }
        cir.setReturnValue(random.nextInt(100) < SlimeFormConfig.get().effectiveSlimeChunkChance()
                && Mob.checkMobSpawnRules(type, level, spawnReason, pos, random));
    }
}
