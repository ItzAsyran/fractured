package io.asy.fragmented;

/** Client-only appearance settings copied onto an avatar render state. */
public interface SlimeAppearanceRenderStateAccess {
    boolean slimeform$isAppearanceActive();

    int slimeform$getTransparencyPercent();

    boolean slimeform$hasSlimeTint();

    boolean slimeform$hasSlimeShell();

    void slimeform$setAppearance(boolean active, int transparencyPercent, boolean slimeTint, boolean slimeShell);
}
