package io.asy.fragmented;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;

public final class SlimeFormClient implements ClientModInitializer {
    private boolean wakeSent;
    private String lastFlowDebugSignature;
    private long lastFlowDebugTick = Long.MIN_VALUE;
    private boolean flowCameraActive;
    private static float flowYaw;
    private static float flowPitch;
    private static double lastFlowMouseX;
    private static double lastFlowMouseY;
    private static boolean haveFlowMousePosition;

    public static boolean isFlowCameraActive() {
        return INSTANCE != null && INSTANCE.flowCameraActive;
    }

    public static void beginFlowLook(float yaw, float pitch) {
        flowYaw = yaw;
        flowPitch = pitch;
        haveFlowMousePosition = false;
    }

    /**
     * MouseHandler.onMove supplies the cursor position, not a movement delta.
     * Convert it here so the normal camera can still be driven while the
     * server-side player remains hidden and stationary.
     */
    public static void recordFlowMouse(double xPos, double yPos) {
        if (!isFlowCameraActive()) {
            return;
        }
        if (!haveFlowMousePosition) {
            lastFlowMouseX = xPos;
            lastFlowMouseY = yPos;
            haveFlowMousePosition = true;
            return;
        }
        double deltaX = xPos - lastFlowMouseX;
        double deltaY = yPos - lastFlowMouseY;
        lastFlowMouseX = xPos;
        lastFlowMouseY = yPos;
        flowYaw -= (float) deltaX * 0.15F;
        flowPitch -= (float) deltaY * 0.15F;
        flowPitch = Math.max(-90.0F, Math.min(90.0F, flowPitch));
    }

    public static float flowYaw() {
        return flowYaw;
    }

    public static float flowPitch() {
        return flowPitch;
    }

    private static SlimeFormClient INSTANCE;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayNetworking.registerGlobalReceiver(
                SlimeFormPayloads.FLOW_STATE_CAMERA_TYPE,
                (payload, context) -> context.client().execute(() -> {
                    flowCameraActive = payload.possessed();
                    Entity target = payload.possessed()
                            ? context.client().level == null ? null
                            : context.client().level.getEntity(payload.entityId())
                            : context.client().player;
                    if (target != null) {
                        if (payload.possessed() && context.client().player != null) {
                            beginFlowLook(context.client().player.getYRot(), context.client().player.getXRot());
                        }
                        context.client().setCameraEntity(target);
                    }
                    SlimeFormMod.LOGGER.info(
                            "[flow-debug] client camera payload: possessed={} entityId={} resolved={} camera={}",
                            payload.possessed(), payload.entityId(), target != null,
                            context.client().getCameraEntity() == null
                                    ? "null" : context.client().getCameraEntity().getType());
                }));
    }

    private void tick(Minecraft client) {
        if (client.player == null) {
            wakeSent = false;
            flowCameraActive = false;
            return;
        }

        if (flowCameraActive
                && client.screen instanceof InventoryScreen) {
            client.setScreen(null);
        }

        boolean flowTagActive = SlimeFormState.isActive(client.player);
        boolean flowActive = SlimeFormState.isClientVisualSlimeForm(client.player);
        boolean flowDebug = SlimeFormConfig.get().flowStateDebug;
        boolean canSendFlowInput = ClientPlayNetworking.canSend(SlimeFormPayloads.FLOW_STATE_INPUT_TYPE);
        if (flowDebug) {
            String signature = flowActive + ":" + canSendFlowInput + ":"
                    + client.options.keyShift.isDown() + ":" + client.options.keyJump.isDown()
                    + ":" + client.options.keyUp.isDown() + ":" + client.options.keyDown.isDown()
                    + ":" + client.options.keyLeft.isDown() + ":" + client.options.keyRight.isDown();
            long now = client.player.level().getGameTime();
            if (!signature.equals(lastFlowDebugSignature)
                    || now - lastFlowDebugTick >= 40L) {
                SlimeFormMod.LOGGER.info(
                        "[flow-debug] client input: tagActive={} inferredActive={} channelAvailable={} crouch={} jump={} yaw={} pitch={} movement={}",
                        flowTagActive, flowActive, canSendFlowInput, client.options.keyShift.isDown(),
                        client.options.keyJump.isDown(), flowCameraActive ? flowYaw() : client.player.getYRot(),
                        flowCameraActive ? flowPitch() : client.player.getXRot(), signature);
                lastFlowDebugSignature = signature;
                lastFlowDebugTick = now;
            }
        } else {
            lastFlowDebugSignature = null;
            lastFlowDebugTick = Long.MIN_VALUE;
        }

        if (flowActive && canSendFlowInput) {
            float yaw = flowCameraActive ? flowYaw() : client.player.getYRot();
            float pitch = flowCameraActive ? flowPitch() : client.player.getXRot();
            ClientPlayNetworking.send(new SlimeFormPayloads.FlowStateInputPayload(
                    client.options.keyShift.isDown(),
                    client.options.keyJump.isDown(),
                    client.options.keyUp.isDown(),
                    client.options.keyDown.isDown(),
                    client.options.keyLeft.isDown(),
                    client.options.keyRight.isDown(),
                    client.options.keyShift.isDown(),
                    yaw,
                    pitch));
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
}
