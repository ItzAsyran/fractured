package io.asy.fragmented;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
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
                .setSavingRunnable(SlimeFormConfig::save);
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory slimeForm = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.slime_form"));
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
        recovery.addEntry(entries.startIntSlider(
                        Component.translatable("config.slimeform.recovery_reform_safety_radius"), config.recoveryReformSafetyRadius,
                        SlimeFormConfig.MIN_RECOVERY_REFORM_SAFETY_RADIUS, SlimeFormConfig.MAX_RECOVERY_REFORM_SAFETY_RADIUS)
                .setDefaultValue(12).setSaveConsumer(value -> config.recoveryReformSafetyRadius = value)
                .setTooltip(Component.translatable("config.slimeform.recovery_reform_safety_radius.tooltip")).build());
        addToggle(recovery, entries, "recovery_flee_path_debug", config.recoveryFleePathDebug,
                value -> config.recoveryFleePathDebug = value);
        addToggle(recovery, entries, "recovery_flee_danger_debug", config.recoveryFleeDangerDebug,
                value -> config.recoveryFleeDangerDebug = value);
        addToggle(recovery, entries, "recovery_lineage_debug", config.recoveryLineageDebug,
                value -> config.recoveryLineageDebug = value);

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
        addToggle(activity, entries, "afk_debug", config.afkDormantDebug,
                value -> config.afkDormantDebug = value);
        addToggle(activity, entries, "water_behavior", config.slimeWaterBehavior,
                value -> config.slimeWaterBehavior = value);

        ConfigCategory visuals = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.visuals"));
        addToggle(visuals, entries, "floating_item_displays", config.floatingItemDisplays,
                value -> config.floatingItemDisplays = value);

        SubCategoryBuilder mainHandOffset = entries.startSubCategory(
                Component.translatable("config.slimeform.visuals.main_hand_offset"));
        addDecimalSlider(mainHandOffset, entries, "item_main_hand_offset_x", config.itemMainHandOffsetX,
                value -> config.itemMainHandOffsetX = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        addDecimalSlider(mainHandOffset, entries, "item_main_hand_offset_y", config.itemMainHandOffsetY,
                value -> config.itemMainHandOffsetY = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        addDecimalSlider(mainHandOffset, entries, "item_main_hand_offset_z", config.itemMainHandOffsetZ,
                value -> config.itemMainHandOffsetZ = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        visuals.addEntry(mainHandOffset.setExpanded(false).build());

        SubCategoryBuilder offHandOffset = entries.startSubCategory(
                Component.translatable("config.slimeform.visuals.off_hand_offset"));
        addDecimalSlider(offHandOffset, entries, "item_off_hand_offset_x", config.itemOffHandOffsetX,
                value -> config.itemOffHandOffsetX = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        addDecimalSlider(offHandOffset, entries, "item_off_hand_offset_y", config.itemOffHandOffsetY,
                value -> config.itemOffHandOffsetY = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        addDecimalSlider(offHandOffset, entries, "item_off_hand_offset_z", config.itemOffHandOffsetZ,
                value -> config.itemOffHandOffsetZ = value, SlimeFormConfig.MIN_ITEM_DISPLAY_OFFSET, SlimeFormConfig.MAX_ITEM_DISPLAY_OFFSET);
        visuals.addEntry(offHandOffset.setExpanded(false).build());

        SubCategoryBuilder transform = entries.startSubCategory(
                Component.translatable("config.slimeform.visuals.transform"));
        addDecimalSlider(transform, entries, "item_display_scale", config.itemDisplayScale,
                value -> config.itemDisplayScale = value, SlimeFormConfig.MIN_ITEM_DISPLAY_SCALE, SlimeFormConfig.MAX_ITEM_DISPLAY_SCALE);
        addDecimalSlider(transform, entries, "item_display_rotation_x", config.itemDisplayRotationX,
                value -> config.itemDisplayRotationX = value, SlimeFormConfig.MIN_ITEM_DISPLAY_ROTATION, SlimeFormConfig.MAX_ITEM_DISPLAY_ROTATION);
        addDecimalSlider(transform, entries, "item_display_rotation_y", config.itemDisplayRotationY,
                value -> config.itemDisplayRotationY = value, SlimeFormConfig.MIN_ITEM_DISPLAY_ROTATION, SlimeFormConfig.MAX_ITEM_DISPLAY_ROTATION);
        addDecimalSlider(transform, entries, "item_display_rotation_z", config.itemDisplayRotationZ,
                value -> config.itemDisplayRotationZ = value, SlimeFormConfig.MIN_ITEM_DISPLAY_ROTATION, SlimeFormConfig.MAX_ITEM_DISPLAY_ROTATION);
        visuals.addEntry(transform.setExpanded(false).build());

        SubCategoryBuilder animation = entries.startSubCategory(
                Component.translatable("config.slimeform.visuals.animation"));
        addDecimalSlider(animation, entries, "item_display_bob_amplitude", config.itemDisplayBobAmplitude,
                value -> config.itemDisplayBobAmplitude = value, SlimeFormConfig.MIN_ITEM_DISPLAY_BOB, SlimeFormConfig.MAX_ITEM_DISPLAY_BOB);
        visuals.addEntry(animation.setExpanded(false).build());
        addToggle(visuals, entries, "item_debug_show_axes", config.itemDebugShowAxes,
                value -> config.itemDebugShowAxes = value);

        ConfigCategory experimental = builder.getOrCreateCategory(
                Component.translatable("config.slimeform.category.experimental"));
        SubCategoryBuilder flowState = entries.startSubCategory(
                Component.translatable("config.slimeform.experimental.flow_state"));
            addToggle(flowState, entries, "flow_state_enabled", config.flowStateEnabled,
                    value -> config.flowStateEnabled = value);
            addToggle(flowState, entries, "flow_state_debug", config.flowStateDebug,
                    value -> config.flowStateDebug = value);
            addToggle(flowState, entries, "flow_state_auto_jump", config.flowStateAutoJump,
                value -> config.flowStateAutoJump = value);
        addIntSlider(flowState, entries, "flow_state_transform_seconds", config.flowStateTransformSeconds,
                1, 10, value -> config.flowStateTransformSeconds = value, 2);
        addIntSlider(flowState, entries, "flow_state_exit_seconds", config.flowStateExitSeconds,
                1, 15, value -> config.flowStateExitSeconds = value, 5);
        experimental.addEntry(flowState.setExpanded(true).build());

        return builder.build();
    }

    private static void addToggle(ConfigCategory category, ConfigEntryBuilder entries, String key,
                                  boolean current, java.util.function.Consumer<Boolean> save) {
        category.addEntry(entries.startBooleanToggle(Component.translatable("config.slimeform." + key), current)
                .setDefaultValue(current).setSaveConsumer(save)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addToggle(SubCategoryBuilder category, ConfigEntryBuilder entries, String key,
                                  boolean current, java.util.function.Consumer<Boolean> save) {
        category.add(entries.startBooleanToggle(Component.translatable("config.slimeform." + key), current)
                .setDefaultValue(current).setSaveConsumer(save)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addIntSlider(ConfigCategory category, ConfigEntryBuilder entries, String key, int current,
                                     int min, int max, java.util.function.IntConsumer save, int defaultValue) {
        category.addEntry(entries.startIntSlider(Component.translatable("config.slimeform." + key), current, min, max)
                .setDefaultValue(defaultValue).setSaveConsumer(save::accept)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addIntSlider(SubCategoryBuilder category, ConfigEntryBuilder entries, String key, int current,
                                     int min, int max, java.util.function.IntConsumer save, int defaultValue) {
        category.add(entries.startIntSlider(Component.translatable("config.slimeform." + key), current, min, max)
                .setDefaultValue(defaultValue).setSaveConsumer(save::accept)
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build());
    }

    private static void addDecimalSlider(ConfigCategory category, ConfigEntryBuilder entries, String key, double current,
                                         java.util.function.DoubleConsumer save, double min, double max) {
        category.addEntry(decimalSlider(entries, key, current, save, min, max));
    }

    private static void addDecimalSlider(SubCategoryBuilder category, ConfigEntryBuilder entries, String key, double current,
                                         java.util.function.DoubleConsumer save, double min, double max) {
        category.add(decimalSlider(entries, key, current, save, min, max));
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
                .setTooltip(Component.translatable("config.slimeform." + key + ".tooltip")).build();
    }
}
