package io.asy.fragmented;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class SlimeFormModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> createConfigScreen(parent);
    }

    static Screen createConfigScreen(Screen parent) {
        SlimeFormConfig config = SlimeFormConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.slimeform.title"))
                .setSavingRunnable(() -> {
                    SlimeFormConfig.save();
                    SlimeFormClient.syncPlayerAppearance();
                });
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory slimeForm = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.slime_form"));
        addToggle(slimeForm, entries, "auto_activate", config.autoActivateSlimeForm,
                value -> config.autoActivateSlimeForm = value);
        slimeForm.addEntry(entries.startIntSlider(
                        Component.translatable("config.slimeform.max_slime_size"), config.maxSlimeSize,
                        SlimeFormConfig.MIN_MAX_SLIME_SIZE, SlimeFormConfig.MAX_MAX_SLIME_SIZE)
                .setDefaultValue(5).setSaveConsumer(value -> config.maxSlimeSize = value)
                .setTooltip(Component.translatable("config.slimeform.max_slime_size.tooltip")).build());
        slimeForm.addEntry(entries.startIntSlider(
                        Component.translatable("config.slimeform.slime_balls_required"), config.slimeBallsRequired,
                        SlimeFormConfig.MIN_SLIME_BALLS_REQUIRED, SlimeFormConfig.MAX_SLIME_BALLS_REQUIRED)
                .setDefaultValue(1).setSaveConsumer(value -> config.slimeBallsRequired = value)
                .setTooltip(Component.translatable("config.slimeform.slime_balls_required.tooltip")).build());
        addDecimalSlider(slimeForm, entries, "rider_offset_x", config.riderOffsetX,
                value -> config.riderOffsetX = value, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET);
        addDecimalSlider(slimeForm, entries, "rider_offset_y", config.riderOffsetYPerSize,
                value -> config.riderOffsetYPerSize = value, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET);
        addDecimalSlider(slimeForm, entries, "rider_offset_z", config.riderOffsetZ,
                value -> config.riderOffsetZ = value, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET);

        ConfigCategory recovery = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.recovery"));
        recovery.addEntry(entries.startIntSlider(
                        Component.translatable("config.slimeform.split_duration"), config.splitDurationSeconds,
                        SlimeFormConfig.MIN_SPLIT_DURATION_SECONDS, SlimeFormConfig.MAX_SPLIT_DURATION_SECONDS)
                .setDefaultValue(30).setSaveConsumer(value -> config.splitDurationSeconds = value)
                .setTooltip(Component.translatable("config.slimeform.split_duration.tooltip")).build());
        addToggle(recovery, entries, "recovery_hostile_reform_block", config.recoveryHostileReformBlock,
                value -> config.recoveryHostileReformBlock = value);
        addToggle(recovery, entries, "recovery_reform_cost", config.recoveryReformCost,
                value -> config.recoveryReformCost = value);

        ConfigCategory activity = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.activity"));
        addToggle(activity, entries, "passive_spawning", config.passiveSlimeSpawning,
                value -> config.passiveSlimeSpawning = value);
        addIntSlider(activity, entries, "spawn_chance", config.passiveSlimeSpawnChance,
                SlimeFormConfig.MIN_PASSIVE_SPAWN_CHANCE, SlimeFormConfig.MAX_PASSIVE_SPAWN_CHANCE,
                value -> config.passiveSlimeSpawnChance = value, 2);
        addIntSlider(activity, entries, "spawn_cooldown", config.passiveSlimeSpawnCooldownSeconds,
                SlimeFormConfig.MIN_PASSIVE_SPAWN_COOLDOWN_SECONDS, SlimeFormConfig.MAX_PASSIVE_SPAWN_COOLDOWN_SECONDS,
                value -> config.passiveSlimeSpawnCooldownSeconds = value, 30);
        addIntSlider(activity, entries, "max_nearby_slimes", config.maxNearbySpawnedSlimes,
                SlimeFormConfig.MIN_MAX_NEARBY_SPAWNED_SLIMES, SlimeFormConfig.MAX_MAX_NEARBY_SPAWNED_SLIMES,
                value -> config.maxNearbySpawnedSlimes = value, 4);
        addToggle(activity, entries, "afk_enabled", config.afkDormantEnabled,
                value -> config.afkDormantEnabled = value);
        addIntSlider(activity, entries, "afk_duration", config.afkInactivitySeconds,
                SlimeFormConfig.MIN_AFK_INACTIVITY_SECONDS, SlimeFormConfig.MAX_AFK_INACTIVITY_SECONDS,
                value -> config.afkInactivitySeconds = value, 300);
        addToggle(activity, entries, "water_behavior", config.slimeWaterBehavior,
                value -> config.slimeWaterBehavior = value);

        ConfigCategory visuals = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.visuals"));
        addToggle(visuals, entries, "slime_footstep_particles", config.slimeFootstepParticles,
                value -> config.slimeFootstepParticles = value);
        addToggle(visuals, entries, "floating_item_displays", config.floatingItemDisplays,
                value -> config.floatingItemDisplays = value);

        ConfigCategory experimental = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.experimental"));
        addToggle(experimental, entries, "do_phase_enabled", config.doPhaseEnabled,
                value -> config.doPhaseEnabled = value);
        addToggle(experimental, entries, "slime_chunks_enabled", config.slimeChunksEnabled,
                value -> config.slimeChunksEnabled = value);
        addIntSlider(experimental, entries, "slime_chunk_chance", config.slimeChunkChance,
                SlimeFormConfig.MIN_SLIME_CHUNK_CHANCE, SlimeFormConfig.MAX_SLIME_CHUNK_CHANCE,
                value -> config.slimeChunkChance = value, 50);
        addToggle(experimental, entries, "slime_morph_enabled", config.slimeMorphEnabled,
                value -> config.slimeMorphEnabled = value);
        addToggle(experimental, entries, "swamp_spawn_enabled", config.swampSpawnEnabled,
                value -> config.swampSpawnEnabled = value);
        addIntSlider(experimental, entries, "player_transparency", config.playerTransparencyPercent,
                SlimeFormConfig.MIN_PLAYER_TRANSPARENCY, SlimeFormConfig.MAX_PLAYER_TRANSPARENCY,
                value -> config.playerTransparencyPercent = value, 0);
        addToggle(experimental, entries, "player_transparency_slime_tint",
                config.playerTransparencySlimeTint,
                value -> config.playerTransparencySlimeTint = value);
        addToggle(experimental, entries, "player_transparency_slime_shell",
                config.playerTransparencySlimeShell,
                value -> config.playerTransparencySlimeShell = value);
        addIntSlider(experimental, entries, "slime_morph_transform_seconds",
                config.slimeMorphTransformSeconds, 1, 10,
                value -> config.slimeMorphTransformSeconds = value, 2);
        addIntSlider(experimental, entries, "slime_morph_exit_seconds",
                config.slimeMorphExitSeconds, 1, 15,
                value -> config.slimeMorphExitSeconds = value, 5);

        ConfigCategory debug = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.debug"));
        addToggle(debug, entries, "recovery_flee_path_debug", config.recoveryFleePathDebug,
                value -> config.recoveryFleePathDebug = value);
        addToggle(debug, entries, "recovery_flee_danger_debug", config.recoveryFleeDangerDebug,
                value -> config.recoveryFleeDangerDebug = value);
        addToggle(debug, entries, "recovery_lineage_debug", config.recoveryLineageDebug,
                value -> config.recoveryLineageDebug = value);
        addToggle(debug, entries, "afk_debug", config.afkDormantDebug,
                value -> config.afkDormantDebug = value);
        addToggle(debug, entries, "afk_hud_debug", config.afkDormantHudDebug,
                value -> config.afkDormantHudDebug = value);
        addToggle(debug, entries, "phase_debug", config.phaseDebugEnabled,
                value -> config.phaseDebugEnabled = value);

        return builder.build();
    }

    private static void addToggle(ConfigCategory category, ConfigEntryBuilder entries, String key,
                                  boolean current, java.util.function.Consumer<Boolean> save) {
        category.addEntry(entries.startBooleanToggle(Component.translatable("config.slimeform." + key), current)
                .setDefaultValue(current).setSaveConsumer(save)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addIntSlider(ConfigCategory category, ConfigEntryBuilder entries, String key, int current,
                                     int min, int max, java.util.function.IntConsumer save, int defaultValue) {
        category.addEntry(entries.startIntSlider(Component.translatable("config.slimeform." + key), current, min, max)
                .setDefaultValue(defaultValue).setSaveConsumer(save::accept)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addDecimalSlider(ConfigCategory category, ConfigEntryBuilder entries, String key, double current,
                                         java.util.function.DoubleConsumer save, double min, double max) {
        category.addEntry(decimalSlider(entries, key, current, save, min, max));
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry decimalSlider(
            ConfigEntryBuilder entries, String key, double current,
            java.util.function.DoubleConsumer save, double min, double max) {
        int scaledCurrent = (int) Math.round(current * 100.0D);
        int scaledMin = (int) Math.round(min * 100.0D);
        int scaledMax = (int) Math.round(max * 100.0D);
        return entries.startIntSlider(Component.translatable("config.slimeform." + key), scaledCurrent, scaledMin, scaledMax)
                .setTextGetter(value -> Component.literal(String.format(Locale.ROOT, "%.2f", value / 100.0D)))
                .setDefaultValue(scaledCurrent).setSaveConsumer(value -> save.accept(value / 100.0D))
                .build();
    }
}
