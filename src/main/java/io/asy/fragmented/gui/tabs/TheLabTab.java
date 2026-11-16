package io.asy.fragmented.gui.tabs;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormPayloads;
import io.asy.fragmented.gui.components.BedrockButton;
import io.asy.fragmented.gui.components.BedrockCard;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class TheLabTab extends SlimeCoreTab {
    private boolean isNorthSouth = false;
    private Double topLeftX = null;
    private Double topLeftZ = null;
    private Double bottomRightX = null;
    private Double bottomRightZ = null;

    @Override
    protected void buildCards() {
        SlimeFormConfig config = SlimeFormConfig.get();
        Minecraft client = Minecraft.getInstance();

        // --- Card 1: Safe-Zone Calibration Wizard ---
        BedrockCard calibCard = new BedrockCard(x, y, width, Component.literal("Safe-Zone Calibration Wizard"));
        calibCard.addSwitch(
                Component.literal("Orientation: " + (isNorthSouth ? "North-South" : "West-East")),
                Component.literal("Switch between West-East and North-South safe zone axes."),
                isNorthSouth,
                val -> {
                    isNorthSouth = val;
                    if (stage != null) stage.resetView();
                },
                null
        );

        calibCard.addButton(
                Component.literal(topLeftX == null
                        ? "📍 Sample Current Pos (Top-Left)"
                        : String.format(Locale.ROOT, "Top-Left: [%.2f, %.2f] (Re-sample)", topLeftX, topLeftZ)),
                BedrockButton.Style.NEUTRAL,
                () -> {
                    if (client.player != null) {
                        topLeftX = client.player.getX();
                        topLeftZ = client.player.getZ();
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal(String.format(
                                    Locale.ROOT, "Sampled Top-Left corner: X=%.3f, Z=%.3f", topLeftX, topLeftZ)));
                        }
                    }
                }
        );

        calibCard.addButton(
                Component.literal(bottomRightX == null
                        ? "📍 Sample Current Pos (Bottom-Right)"
                        : String.format(Locale.ROOT, "Bottom-Right: [%.2f, %.2f] (Re-sample)", bottomRightX, bottomRightZ)),
                BedrockButton.Style.NEUTRAL,
                () -> {
                    if (client.player != null) {
                        bottomRightX = client.player.getX();
                        bottomRightZ = client.player.getZ();
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal(String.format(
                                    Locale.ROOT, "Sampled Bottom-Right corner: X=%.3f, Z=%.3f", bottomRightX, bottomRightZ)));
                        }
                    }
                }
        );

        calibCard.addButton(
                Component.literal("✓ Commit Calibrated Zone"),
                BedrockButton.Style.PRIMARY,
                () -> {
                    if (topLeftX != null && bottomRightX != null && ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        double minX = Math.min(topLeftX, bottomRightX);
                        double maxX = Math.max(topLeftX, bottomRightX);
                        double minZ = Math.min(topLeftZ, bottomRightZ);
                        double maxZ = Math.max(topLeftZ, bottomRightZ);
                        int orientationParam = isNorthSouth ? 1 : 0;
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SAMPLE_CALIBRATION_BOUNDS,
                                orientationParam, minX, maxX, minZ, maxZ));
                    }
                }
        );

        calibCard.addButton(
                Component.literal("✕ Reset Calibration Points"),
                BedrockButton.Style.DANGER,
                () -> {
                    topLeftX = null;
                    topLeftZ = null;
                    bottomRightX = null;
                    bottomRightZ = null;
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_CANCEL_CALIBRATION, 0, 0, 0, 0, 0));
                    }
                }
        );
        cards.add(calibCard);

        // --- Card 2: Experimental Features ---
        BedrockCard expCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.experimental"));
        expCard.addSwitch(
                Component.translatable("config.slimeform.do_phase_enabled"),
                Component.translatable("config.slimeform.do_phase_enabled.tooltip"),
                config.doPhaseEnabled,
                val -> {
                    config.doPhaseEnabled = val;
                    SlimeFormConfig.save();
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_DO_PHASE, val ? 1 : 0, 0, 0, 0, 0));
                    }
                },
                null
        );

        expCard.addSwitch(
                Component.translatable("config.slimeform.slime_chunks_enabled"),
                Component.translatable("config.slimeform.slime_chunks_enabled.tooltip"),
                config.slimeChunksEnabled,
                val -> {
                    config.slimeChunksEnabled = val;
                    SlimeFormConfig.save();
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_SLIME_CHUNKS, val ? 1 : 0, 0, 0, 0, 0));
                    }
                },
                null
        );

        expCard.addSlider(
                Component.translatable("config.slimeform.slime_chunk_chance"),
                config.slimeChunkChance, SlimeFormConfig.MIN_SLIME_CHUNK_CHANCE, SlimeFormConfig.MAX_SLIME_CHUNK_CHANCE, true,
                val -> Component.literal("Chunk Chance: " + val.intValue() + "%"),
                val -> {
                    config.slimeChunkChance = val.intValue();
                    SlimeFormConfig.save();
                },
                null
        );

        expCard.addSwitch(
                Component.translatable("config.slimeform.swamp_spawn_enabled"),
                Component.translatable("config.slimeform.swamp_spawn_enabled.tooltip"),
                config.swampSpawnEnabled,
                val -> {
                    config.swampSpawnEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );
        cards.add(expCard);

        // --- Card 3: Developer & Diagnostics Mode (Collapsible Accordion) ---
        BedrockCard debugCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.debug"));
        debugCard.setCollapsible(true, !config.developerMode);

        debugCard.addSwitch(
                Component.literal("Master Developer Mode"),
                Component.literal("Unlocks advanced diagnostics, flee lines, nametags, and coordinate axes."),
                config.developerMode,
                val -> {
                    config.developerMode = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.recovery_flee_path_debug"),
                Component.translatable("config.slimeform.recovery_flee_path_debug.tooltip"),
                config.recoveryFleePathDebug,
                val -> {
                    config.recoveryFleePathDebug = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.recovery_flee_danger_debug"),
                Component.translatable("config.slimeform.recovery_flee_danger_debug.tooltip"),
                config.recoveryFleeDangerDebug,
                val -> {
                    config.recoveryFleeDangerDebug = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.recovery_lineage_debug"),
                Component.translatable("config.slimeform.recovery_lineage_debug.tooltip"),
                config.recoveryLineageDebug,
                val -> {
                    config.recoveryLineageDebug = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.afk_debug"),
                Component.translatable("config.slimeform.afk_debug.tooltip"),
                config.afkDormantDebug,
                val -> {
                    config.afkDormantDebug = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.afk_hud_debug"),
                Component.translatable("config.slimeform.afk_hud_debug.tooltip"),
                config.afkDormantHudDebug,
                val -> {
                    config.afkDormantHudDebug = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.item_debug_show_axes"),
                Component.translatable("config.slimeform.item_debug_show_axes.tooltip"),
                config.itemDebugShowAxes,
                val -> {
                    config.itemDebugShowAxes = val;
                    SlimeFormConfig.save();
                },
                null
        );

        debugCard.addSwitch(
                Component.translatable("config.slimeform.phase_debug"),
                Component.translatable("config.slimeform.phase_debug.tooltip"),
                config.phaseDebugEnabled,
                val -> {
                    config.phaseDebugEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );
        cards.add(debugCard);
    }
}
