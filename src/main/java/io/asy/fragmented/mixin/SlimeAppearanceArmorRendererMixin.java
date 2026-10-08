package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeAppearanceRenderStateAccess;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(targets = "net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer")
public abstract class SlimeAppearanceArmorRendererMixin {
    @Unique
    private static final ThreadLocal<SlimeAppearanceRenderStateAccess> slimeform$appearance = new ThreadLocal<>();

    private static final String RENDER_LAYERS_METHOD = "renderLayers("
            + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;"
            + "Lnet/minecraft/client/model/Model;"
            + "Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "ILnet/minecraft/resources/Identifier;II)V";

    @Inject(method = RENDER_LAYERS_METHOD, at = @At("HEAD"))
    private void slimeform$captureAppearance(
            EquipmentClientInfo.LayerType layerType,
            ResourceKey<?> asset,
            Model<?> model,
            Object renderState,
            ItemStack stack,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            Identifier playerTexture,
            int outlineColor,
            int order,
            CallbackInfo ci) {
        if (renderState instanceof AvatarRenderState
                && renderState instanceof SlimeAppearanceRenderStateAccess appearance
                && appearance.slimeform$isAppearanceActive()
                && !appearance.slimeform$hasSlimeShell()
                && appearance.slimeform$getTransparencyPercent() > 0) {
            slimeform$appearance.set(appearance);
        } else {
            slimeform$appearance.remove();
        }
    }

    @Inject(method = RENDER_LAYERS_METHOD, at = @At("RETURN"))
    private void slimeform$clearAppearance(
            EquipmentClientInfo.LayerType layerType,
            ResourceKey<?> asset,
            Model<?> model,
            Object renderState,
            ItemStack stack,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            Identifier playerTexture,
            int outlineColor,
            int order,
            CallbackInfo ci) {
        slimeform$appearance.remove();
    }

    @Redirect(
            method = RENDER_LAYERS_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;"
                            + "armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)"
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType slimeform$makeArmorTranslucent(Identifier texture) {
        return slimeform$appearance.get() == null
                ? RenderTypes.armorCutoutNoCull(texture)
                : RenderTypes.armorTranslucent(texture);
    }

    @ModifyArg(
            method = RENDER_LAYERS_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;"
                            + "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/rendertype/RenderType;IIIL"
                            + "net/minecraft/client/renderer/texture/TextureAtlasSprite;I"
                            + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 6)
    private int slimeform$applyArmorOpacity(int color) {
        SlimeAppearanceRenderStateAccess appearance = slimeform$appearance.get();
        if (appearance == null) {
            return color;
        }

        float opacity = 1.0F - appearance.slimeform$getTransparencyPercent() / 100.0F;
        int tint = ARGB.multiplyAlpha(color, opacity);
        if (appearance.slimeform$hasSlimeTint()) {
            tint = ARGB.multiply(tint, ARGB.color(255, 85, 255, 85));
        }
        return tint;
    }
}
