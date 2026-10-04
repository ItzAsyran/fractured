package io.asy.fragmented;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

/**
 * Slime sound selection for slime-form players.
 * Many player sounds are chosen client-side (own footsteps, hurt and death
 * events), where the slime-form entity tag is not synchronized, so detection
 * falls back to the synchronized max-health heuristic on the client.
 */
public final class SlimeFormSounds {
    private SlimeFormSounds() {
    }

    public static boolean isSlime(Player player) {
        return SlimeFormState.isClientVisualSlimeForm(player);
    }

    private static boolean isTiny(Player player) {
        return SlimeFormState.getRiderSize(player) <= SlimeFormState.MIN_SIZE;
    }

    public static SoundEvent hurt(Player player) {
        return isTiny(player) ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    public static SoundEvent death(Player player) {
        return isTiny(player) ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    public static SoundEvent squish(Player player) {
        return isTiny(player) ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    public static SoundEvent jump(Player player) {
        return isTiny(player) ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    /** Maps vanilla player attack sounds to their slime equivalents. */
    public static SoundEvent attack(SoundEvent original) {
        if (original == SoundEvents.PLAYER_ATTACK_WEAK
                || original == SoundEvents.PLAYER_ATTACK_NODAMAGE) {
            return SoundEvents.SLIME_SQUISH_SMALL;
        }
        if (original == SoundEvents.PLAYER_ATTACK_STRONG
                || original == SoundEvents.PLAYER_ATTACK_CRIT
                || original == SoundEvents.PLAYER_ATTACK_KNOCKBACK
                || original == SoundEvents.PLAYER_ATTACK_SWEEP) {
            return SoundEvents.SLIME_ATTACK;
        }
        return original;
    }
}
