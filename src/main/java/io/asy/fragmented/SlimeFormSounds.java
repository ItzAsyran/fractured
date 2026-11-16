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

    /** Individually switchable slime sounds (see the Sounds tab in the config screen). */
    public enum Kind {
        FOOTSTEPS, JUMP, LANDING, FALL, HURT, DEATH, ATTACK, GROWTH
    }

    public static boolean isEnabled(Kind kind) {
        SlimeFormConfig config = SlimeFormConfig.get();
        if (!config.slimeSoundsEnabled) {
            return false;
        }
        return switch (kind) {
            case FOOTSTEPS -> config.slimeSoundFootsteps;
            case JUMP -> config.slimeSoundJump;
            case LANDING -> config.slimeSoundLanding;
            case FALL -> config.slimeSoundFall;
            case HURT -> config.slimeSoundHurt;
            case DEATH -> config.slimeSoundDeath;
            case ATTACK -> config.slimeSoundAttack;
            case GROWTH -> config.slimeSoundGrowth;
        };
    }

    public static boolean isSlime(Player player) {
        return SlimeFormState.isClientVisualSlimeForm(player);
    }

    /** True when this player should use the slime version of the given sound. */
    public static boolean isSlime(Player player, Kind kind) {
        return isEnabled(kind) && isSlime(player);
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
