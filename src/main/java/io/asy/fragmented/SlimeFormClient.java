package io.asy.fragmented;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class SlimeFormClient implements ClientModInitializer {
    private boolean wakeSent;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        if (client.player == null || !SlimeFormMod.isDormant(client.player)) {
            wakeSent = false;
            return;
        }

        boolean inputDown = false;
        for (var keyMapping : client.options.keyMappings) {
            if (keyMapping.isDown()) {
                inputDown = true;
                break;
            }
        }
        if (inputDown && !wakeSent && ClientPlayNetworking.canSend(SlimeFormPayloads.WAKE_DORMANT_TYPE)) {
            ClientPlayNetworking.send(new SlimeFormPayloads.WakeDormantPayload());
            wakeSent = true;
        } else if (!inputDown) {
            wakeSent = false;
        }
    }
}
