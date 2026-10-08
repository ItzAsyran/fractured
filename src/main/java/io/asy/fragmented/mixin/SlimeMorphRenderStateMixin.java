package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeMorphRenderStateAccess;
import io.asy.fragmented.SlimeAppearanceRenderStateAccess;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class SlimeMorphRenderStateMixin
        implements SlimeMorphRenderStateAccess, SlimeAppearanceRenderStateAccess {
    @Unique
    private boolean slimeform$morphed;
    @Unique
    private int slimeform$size = 1;
    @Unique
    private boolean slimeform$appearanceActive;
    @Unique
    private int slimeform$transparencyPercent;
    @Unique
    private boolean slimeform$slimeTint;
    @Unique
    private boolean slimeform$slimeShell;

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

    @Override
    public boolean slimeform$isAppearanceActive() {
        return slimeform$appearanceActive;
    }

    @Override
    public int slimeform$getTransparencyPercent() {
        return slimeform$transparencyPercent;
    }

    @Override
    public boolean slimeform$hasSlimeTint() {
        return slimeform$slimeTint;
    }

    @Override
    public boolean slimeform$hasSlimeShell() {
        return slimeform$slimeShell;
    }

    @Override
    public void slimeform$setAppearance(
            boolean active, int transparencyPercent, boolean slimeTint, boolean slimeShell) {
        slimeform$appearanceActive = active;
        slimeform$transparencyPercent = Math.max(0, Math.min(100, transparencyPercent));
        slimeform$slimeTint = slimeTint;
        slimeform$slimeShell = slimeShell;
    }
}
