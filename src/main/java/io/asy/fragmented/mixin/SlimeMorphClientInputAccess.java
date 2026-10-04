package io.asy.fragmented.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface SlimeMorphClientInputAccess {
    @Accessor("keyPresses")
    Input slimeform$getKeyPresses();

    @Accessor("keyPresses")
    void slimeform$setKeyPresses(Input input);
}
