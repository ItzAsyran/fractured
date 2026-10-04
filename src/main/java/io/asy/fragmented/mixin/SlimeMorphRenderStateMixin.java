package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphRenderStateAccess;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class SlimeMorphRenderStateMixin implements SlimeMorphRenderStateAccess {
    @Unique
    private boolean slimeform$morphed;
    @Unique
    private int slimeform$size = 1;

    @Override
    public boolean slimeform$isMorphed() {
        return slimeform$morphed;
    }

    @Override
    public void slimeform$setMorphed(boolean morphed) {
        slimeform$morphed = morphed;
    }

    @Override
    public int slimeform$getSize() {
        return slimeform$size;
    }

    @Override
    public void slimeform$setSize(int size) {
        slimeform$size = Math.max(1, size);
    }
}
