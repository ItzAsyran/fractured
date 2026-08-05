package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class SlimeBallConsumptionMixin {
    private static final int SLIME_BALL_USE_DURATION = 32;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void slimeform$startSlimeBallConsumption(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.SLIME_BALL)
                || !SlimeFormState.isActive(player)
                || SlimeFormState.getSize(player) >= SlimeFormState.getMaxSize()
                || stack.getCount() < SlimeFormConfig.get().effectiveSlimeBallsRequired()) {
            return;
        }

        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeBallEatingAnimation(
            ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (stack.is(Items.SLIME_BALL)) {
            cir.setReturnValue(ItemUseAnimation.EAT);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void slimeform$useSlimeBallEatingDuration(
            ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (stack.is(Items.SLIME_BALL)) {
            cir.setReturnValue(SLIME_BALL_USE_DURATION);
        }
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void slimeform$finishSlimeBallConsumption(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!(entity instanceof Player player)
                || !stack.is(Items.SLIME_BALL)
                || !SlimeFormState.isActive(player)
                || SlimeFormState.getSize(player) >= SlimeFormState.getMaxSize()
                || stack.getCount() < SlimeFormConfig.get().effectiveSlimeBallsRequired()) {
            return;
        }

        if (!level.isClientSide()) {
            SlimeFormState.setSize(player, SlimeFormState.getSize(player) + 1);
            SlimeFormState.applyHealth(player, true);
            stack.consume(SlimeFormConfig.get().effectiveSlimeBallsRequired(), player);
            player.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 1.0F);
        }
        cir.setReturnValue(stack);
    }
}
