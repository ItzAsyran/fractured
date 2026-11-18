package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAbsorbClientState;
import io.asy.fragmented.SlimeAbsorbRenderStateAccess;
import net.minecraft.client.renderer.entity.AbstractCubeMobRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractCubeMobRenderer.class)
public abstract class SlimeAbsorbCubeMobRendererMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/monster/cubemob/AbstractCubeMob;Lnet/minecraft/client/renderer/entity/state/SlimeRenderState;F)V",
            at = @At("TAIL"))
    private void slimeform$extractAbsorbState(
            AbstractCubeMob entity, SlimeRenderState state, float partialTick, CallbackInfo ci) {
        int id = entity.getId();
        boolean holding = SlimeAbsorbClientState.isHolding(id);
        SlimeAbsorbRenderStateAccess access = (SlimeAbsorbRenderStateAccess) state;
        access.slimeform$setHolderEntityId(id);
        access.slimeform$setHoldingAbsorbed(holding);
        if (holding) {
            access.slimeform$setTrappedEntityIds(SlimeAbsorbClientState.getTrappedIds(id));
        } else {
            access.slimeform$setTrappedEntityIds(java.util.Collections.emptyList());
        }
    }
}
