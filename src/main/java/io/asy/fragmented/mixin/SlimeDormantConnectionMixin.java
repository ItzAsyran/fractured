package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeFormMod;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SlimeDormantConnectionMixin {
    private boolean slimeform$blockIfDormant(CallbackInfo ci) {
        ServerPlayer player = ((SlimeServerConnectionAccessor) this).slimeform$getPlayer();
        if (SlimeFormMod.isDormant(player)) {
            SlimeFormMod.wakeDormant(player);
            ci.cancel();
            return true;
        }
        return false;
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void slimeform$action(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void slimeform$calibrationChat(ServerboundChatPacket packet, CallbackInfo ci) {
        ServerPlayer player = ((SlimeServerConnectionAccessor) this).slimeform$getPlayer();
        if (SlimeFormMod.handleCalibrationChat(player, packet.message())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void slimeform$useOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void slimeform$use(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void slimeform$interact(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void slimeform$animate(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void slimeform$changeSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!slimeform$blockIfDormant(ci)) {
            SlimeFormMod.recordActivity(((SlimeServerConnectionAccessor) this).slimeform$getPlayer());
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void slimeform$containerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        slimeform$blockIfDormant(ci);
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void slimeform$playerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        slimeform$blockIfDormant(ci);
    }
}
