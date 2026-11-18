package io.asy.fragmented.gui.tabs;

import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormPayloads;
import io.asy.fragmented.SlimeFormState;
import io.asy.fragmented.gui.SlimeStageWidget;
import io.asy.fragmented.gui.components.BedrockButton;
import io.asy.fragmented.gui.components.BedrockCard;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class GameplayMechanicsTab extends SlimeCoreTab {
    @Override
    protected void buildCards() {
        SlimeFormConfig config = SlimeFormConfig.get();
        Minecraft client = Minecraft.getInstance();
        boolean isActive = client.player != null && SlimeFormState.isClientVisualSlimeForm(client.player);

        // --- Card 1: Slime Form Core ---
        BedrockCard coreCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.slime_form"));
        coreCard.addSwitch(
                Component.literal("Slime Form (Active)"),
                Component.literal("Immediately toggles slime form on or off for your player."),
                isActive,
                val -> {
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_TOGGLE_FORM, 0, 0, 0, 0, 0));
                    }
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE)
        );

        coreCard.addSwitch(
                Component.translatable("config.slimeform.auto_activate"),
                Component.translatable("config.slimeform.auto_activate.tooltip"),
                config.autoActivateSlimeForm,
                val -> {
                    config.autoActivateSlimeForm = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE)
        );

        coreCard.addSlider(
                Component.translatable("config.slimeform.max_slime_size"),
                config.maxSlimeSize, SlimeFormConfig.MIN_MAX_SLIME_SIZE, SlimeFormConfig.MAX_MAX_SLIME_SIZE, true,
                val -> Component.literal("Max Size: " + val.intValue()),
                val -> {
                    config.maxSlimeSize = val.intValue();
                    SlimeFormConfig.save();
                    if (stage != null) {
                        stage.clearCustomSlimeSize();
                    }
                },
                () -> {
                    if (stage != null) {
                        stage.clearCustomSlimeSize();
                        stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE);
                    }
                }
        );

        coreCard.addSlider(
                Component.translatable("config.slimeform.slime_balls_required"),
                config.slimeBallsRequired, SlimeFormConfig.MIN_SLIME_BALLS_REQUIRED, SlimeFormConfig.MAX_SLIME_BALLS_REQUIRED, true,
                val -> Component.literal("Balls per Size-Up: " + val.intValue()),
                val -> {
                    config.slimeBallsRequired = val.intValue();
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE)
        );
        cards.add(coreCard);

        // --- Card 2: Powers & Morph ---
        BedrockCard morphCard = new BedrockCard(x, y, width, Component.literal("Powers & Morph"));
        morphCard.addSwitch(
                Component.translatable("config.slimeform.slime_morph_enabled"),
                Component.translatable("config.slimeform.slime_morph_enabled.tooltip"),
                config.slimeMorphEnabled,
                val -> {
                    config.slimeMorphEnabled = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE)
        );

        morphCard.addButton(
                Component.literal("⚡ Quick Trigger Morph (H)"),
                BedrockButton.Style.PRIMARY,
                () -> {
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_TRIGGER_MORPH, 0, 0, 0, 0, 0));
                    }
                }
        );

        int initialMorphSize = client.player != null ? SlimeFormState.getMorphSize(client.player) : config.effectiveMaxSlimeSize();
        morphCard.addSlider(
                Component.literal("Custom Morph Size"),
                initialMorphSize, 1, Math.max(initialMorphSize, config.effectiveMaxSlimeSize()), true,
                val -> Component.literal("Target Morph Size: " + val.intValue()),
                val -> {
                    if (stage != null) {
                        stage.setPreviewSlimeSize(val.intValue());
                    }
                    if (ClientPlayNetworking.canSend(SlimeFormPayloads.CORE_ACTION_TYPE)) {
                        ClientPlayNetworking.send(new SlimeFormPayloads.SlimeCoreActionPayload(
                                SlimeFormPayloads.SlimeCoreActionPayload.ACTION_SET_MORPH_SIZE, val.intValue(), 0, 0, 0, 0));
                    }
                },
                () -> {
                    if (stage != null) {
                        stage.setPreviewMode(SlimeStageWidget.PreviewMode.SLIME_SCALE);
                        int currentMorph = client.player != null ? SlimeFormState.getMorphSize(client.player) : config.effectiveMaxSlimeSize();
                        stage.setPreviewSlimeSize(currentMorph);
                    }
                }
        );

        morphCard.addSlider(
                Component.translatable("config.slimeform.slime_morph_transform_seconds"),
                config.slimeMorphTransformSeconds, 1, 10, true,
                val -> Component.literal("Transform Time: " + val.intValue() + "s"),
                val -> {
                    config.slimeMorphTransformSeconds = val.intValue();
                    SlimeFormConfig.save();
                },
                null
        );

        morphCard.addSlider(
                Component.translatable("config.slimeform.slime_morph_exit_seconds"),
                config.slimeMorphExitSeconds, 1, 15, true,
                val -> Component.literal("Exit Time: " + val.intValue() + "s"),
                val -> {
                    config.slimeMorphExitSeconds = val.intValue();
                    SlimeFormConfig.save();
                },
                null
        );
        cards.add(morphCard);

        // --- Card 3: Survival & Recovery ---
        BedrockCard recoveryCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.recovery"));
        recoveryCard.addSlider(
                Component.translatable("config.slimeform.split_duration"),
                config.splitDurationSeconds, SlimeFormConfig.MIN_SPLIT_DURATION_SECONDS, SlimeFormConfig.MAX_SPLIT_DURATION_SECONDS, true,
                val -> Component.literal("Split Duration: " + val.intValue() + "s"),
                val -> {
                    config.splitDurationSeconds = val.intValue();
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.RECOVERY_RING)
        );

        recoveryCard.addSlider(
                Component.translatable("config.slimeform.recovery_reform_safety_radius"),
                config.recoveryReformSafetyRadius, SlimeFormConfig.MIN_RECOVERY_REFORM_SAFETY_RADIUS, SlimeFormConfig.MAX_RECOVERY_REFORM_SAFETY_RADIUS, true,
                val -> Component.literal("Safety Radius: " + val.intValue() + " blocks"),
                val -> {
                    config.recoveryReformSafetyRadius = val.intValue();
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.RECOVERY_RING)
        );

        recoveryCard.addSwitch(
                Component.translatable("config.slimeform.recovery_hostile_reform_block"),
                Component.translatable("config.slimeform.recovery_hostile_reform_block.tooltip"),
                config.recoveryHostileReformBlock,
                val -> {
                    config.recoveryHostileReformBlock = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.RECOVERY_RING)
        );
        cards.add(recoveryCard);

        // --- Card 4: World Habits ---
        BedrockCard worldCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.activity"));
        worldCard.addSwitch(
                Component.translatable("config.slimeform.water_behavior"),
                Component.translatable("config.slimeform.water_behavior.tooltip"),
                config.slimeWaterBehavior,
                val -> {
                    config.slimeWaterBehavior = val;
                    SlimeFormConfig.save();
                },
                null
        );

        worldCard.addSwitch(
                Component.translatable("config.slimeform.afk_enabled"),
                Component.translatable("config.slimeform.afk_enabled.tooltip"),
                config.afkDormantEnabled,
                val -> {
                    config.afkDormantEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );

        worldCard.addSlider(
                Component.translatable("config.slimeform.afk_duration"),
                config.afkInactivitySeconds, SlimeFormConfig.MIN_AFK_INACTIVITY_SECONDS, SlimeFormConfig.MAX_AFK_INACTIVITY_SECONDS, true,
                val -> Component.literal("AFK Idle Delay: " + val.intValue() + "s"),
                val -> {
                    config.afkInactivitySeconds = val.intValue();
                    SlimeFormConfig.save();
                },
                null
        );
        cards.add(worldCard);

        // --- Card 5: Absorb Mechanic ---
        BedrockCard absorbCard = new BedrockCard(x, y, width, Component.literal("Absorb Mechanic"));
        absorbCard.addSwitch(
                Component.literal("Absorb Mechanic"),
                Component.literal("Allows morphed slimes and large fragments to absorb touching entities into their gelatinous body."),
                config.absorbEnabled,
                val -> {
                    config.absorbEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );

        absorbCard.addSwitch(
                Component.literal("Absorb Players"),
                Component.literal("Allows absorbing other players on contact."),
                config.absorbPlayersEnabled,
                val -> {
                    config.absorbPlayersEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );

        absorbCard.addSwitch(
                Component.literal("Absorb Bosses"),
                Component.literal("Allows absorbing Withers, Elder Guardians, and Wardens (requires high size & capacity)."),
                config.absorbBossesEnabled,
                val -> {
                    config.absorbBossesEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );

        absorbCard.addSlider(
                Component.literal("Absorb Capacity Factor"),
                config.absorbCapacityFactor,
                SlimeFormConfig.MIN_ABSORB_CAPACITY_FACTOR,
                SlimeFormConfig.MAX_ABSORB_CAPACITY_FACTOR,
                false,
                val -> Component.literal(String.format(java.util.Locale.ROOT, "Capacity: %.2fx", val)),
                val -> {
                    config.absorbCapacityFactor = val;
                    SlimeFormConfig.save();
                },
                null
        );

        absorbCard.addSlider(
                Component.literal("Re-absorb Cooldown"),
                config.absorbReabsorbCooldownTicks,
                0, 100, true,
                val -> Component.literal(String.format(java.util.Locale.ROOT, "Cooldown: %d ticks (%.1fs)", val.intValue(), val.intValue() / 20.0f)),
                val -> {
                    config.absorbReabsorbCooldownTicks = val.intValue();
                    SlimeFormConfig.save();
                },
                null
        );
        cards.add(absorbCard);
    }
}
