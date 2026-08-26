package io.asy.fragmented;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SlimeFormClient implements ClientModInitializer {
    private boolean wakeSent;
    private int morphPhase;
    private int morphRemaining;
    private int morphTotal;
    private boolean dormantDebugVisible;
    private int dormantDebugRemainingTicks;
    private long dormantDebugLastUpdateTick;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.PHASE_STATE_TYPE,
                (payload, context) -> context.client().execute(
                        () -> SlimeFormState.setClientPhaseEnabled(payload.enabled())));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.DORMANT_DEBUG_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    dormantDebugVisible = payload.visible();
                    dormantDebugRemainingTicks = payload.remainingTicks();
                    dormantDebugLastUpdateTick = context.client().level == null
                            ? 0L : context.client().level.getGameTime();
                }));
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.MORPH_STATE_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    morphPhase = payload.phase();
                    morphRemaining = payload.remaining();
                    morphTotal = payload.total();
                }));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "morph_hud"),
                this::renderMorphHud);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(SlimeFormMod.MOD_ID, "dormant_hud"),
                this::renderDormantHud);
    }

    private void tick(Minecraft client) {
        if (client.player == null) {
            wakeSent = false;
            morphPhase = 0;
            morphRemaining = 0;
            morphTotal = 0;
            dormantDebugVisible = false;
            dormantDebugRemainingTicks = 0;
            dormantDebugLastUpdateTick = 0L;
            SlimeFormState.setClientPhaseEnabled(false);
            return;
        }

        SlimeFormConfig config = SlimeFormConfig.get();
        boolean activeTag = SlimeFormState.isActive(client.player);
        boolean active = SlimeFormState.isClientVisualSlimeForm(client.player);
        boolean canSend = ClientPlayNetworking.canSend(SlimeFormPayloads.MORPH_INPUT_TYPE);
        boolean crouch = client.options.keyShift.isDown();
        if (config.slimeMorphEnabled && active && canSend) {
            ClientPlayNetworking.send(new SlimeFormPayloads.MorphInputPayload(
                    crouch,
                    client.options.keyJump.isDown(),
                    client.options.keyUp.isDown(),
                    client.options.keyDown.isDown(),
                    client.options.keyLeft.isDown(),
                    client.options.keyRight.isDown(),
                    client.player.getYRot(),
                    client.player.getXRot()));
        }

        if (!SlimeFormMod.isDormant(client.player)) {
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

    private void renderMorphHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
        if (morphTotal <= 0 || (morphPhase != 1 && morphPhase != 3)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int width = 180;
        int height = 8;
        int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
        int y = client.getWindow().getGuiScaledHeight() - 45;
        int filled = Math.max(0, Math.min(width, (int) ((long) (morphTotal - morphRemaining) * width / morphTotal)));
        graphics.fill(x, y, x + width, y + height, 0xAA202020);
        graphics.fill(x, y, x + filled, y + height, morphPhase == 1 ? 0xFF55D66F : 0xFF55B7D6);
        String label = morphPhase == 1 ? "Morphing into slime" : "Returning to player";
        graphics.drawCenteredString(client.font,
                Component.literal(label + " (" + ((morphRemaining + 19) / 20) + "s)"),
                client.getWindow().getGuiScaledWidth() / 2, y - 12, 0xFFFFFFFF);
    }

    private void renderDormantHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (!dormantDebugVisible || dormantDebugRemainingTicks <= 0 || client.player == null) {
            return;
        }
        int x = 8;
        int y = client.getWindow().getGuiScaledHeight() - 42;
        int width = 180;
        int height = 8;
        float elapsedTicks = client.player.level().getGameTime() - dormantDebugLastUpdateTick
                + tickCounter.getGameTimeDeltaPartialTick(false);
        float remainingTicks = Math.max(0.0F, dormantDebugRemainingTicks - elapsedTicks);
        int filled = Math.max(0, Math.min(width,
                (int) ((200.0F - remainingTicks) * width / 200.0F)));
        graphics.fill(x, y, x + width, y + height, 0xAA202020);
        graphics.fill(x, y, x + filled, y + height, 0xFFE5B84B);
        graphics.drawString(client.font, Component.literal("Entering AFK Mode"), x, y - 12, 0xFFFFFFFF, false);
    }
}
