package io.asy.fragmented.mixin;

import com.mojang.math.Transformation;
import io.asy.fragmented.SlimeDisplayAccess;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public abstract class SlimeDisplayMixin implements SlimeDisplayAccess {
    @Override
    @Invoker("setTransformation")
    public abstract void slimeform$setTransformation(Transformation transformation);
}
