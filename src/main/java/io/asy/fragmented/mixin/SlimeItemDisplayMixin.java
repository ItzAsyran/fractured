package io.asy.fragmented.mixin;

import io.asy.fragmented.SlimeItemDisplayAccess;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.ItemDisplay.class)
public abstract class SlimeItemDisplayMixin implements SlimeItemDisplayAccess {
    @Override
    @Invoker("setItemTransform")
    public abstract void slimeform$setItemTransform(ItemDisplayContext context);
}
