package io.asy.fragmented;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;

/**
 * Shared state for the Cosmetica compat mixins.
 *
 * Cosmetica draws accessories (tails, wings, halos...) through its own render layer, so the normal
 * body/armor transparency never reaches them. SlimeCosmeticaAccessoryLayerMixin remembers which
 * avatar is currently being drawn, and SlimeCosmeticaModelMixin applies the same opacity and tint
 * to the tint array Cosmetica submits its model with. Nothing here references Cosmetica classes.
 */
public final class SlimeCosmeticaCompat {
    private static final ThreadLocal<SlimeAppearanceRenderStateAccess> CURRENT = new ThreadLocal<>();

    private SlimeCosmeticaCompat() {
    }

    /** Called when Cosmetica starts drawing the accessories of one render state. */
    public static void begin(Object state) {
        if (state instanceof AvatarRenderState
                && state instanceof LivingEntityRenderState living
                && state instanceof SlimeAppearanceRenderStateAccess appearance
                && appearance.slimeform$isAppearanceActive()
                && !appearance.slimeform$hasSlimeShell()
                && appearance.slimeform$getTransparencyPercent() > 0
                && !living.isInvisible) {
            CURRENT.set(appearance);
        } else {
            CURRENT.remove();
        }
    }

    public static void end() {
        CURRENT.remove();
    }

    /** Returns the tint array unchanged unless a transparent slime-form avatar is being drawn. */
    public static int[] applyToTints(int[] tints) {
        SlimeAppearanceRenderStateAccess appearance = CURRENT.get();
        if (appearance == null || tints == null) {
            return tints;
        }

        float opacity = 1.0F - appearance.slimeform$getTransparencyPercent() / 100.0F;
        boolean slimeTint = appearance.slimeform$hasSlimeTint();
        int[] result = new int[tints.length];
        for (int i = 0; i < tints.length; i++) {
            int tint = ARGB.multiplyAlpha(tints[i], opacity);
            if (slimeTint) {
                tint = ARGB.multiply(tint, ARGB.color(255, 85, 255, 85));
            }
            result[i] = tint;
        }
        return result;
    }
}
