package io.asy.fragmented;

/** Client render-state data used only by the temporary player morph. */
public interface SlimeMorphRenderStateAccess {
    boolean slimeform$isMorphed();

    void slimeform$setMorphed(boolean morphed);

    int slimeform$getSize();

    void slimeform$setSize(int size);
}
