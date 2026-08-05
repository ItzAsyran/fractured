package io.asy.fragmented;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public class SlimeFormModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            SlimeFormConfig config = AutoConfig
                    .getConfigHolder(SlimeFormConfig.class)
                    .getConfig();
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.translatable("config.slimeform.title"))
                    .setSavingRunnable(AutoConfig
                            .getConfigHolder(SlimeFormConfig.class)::save);
            ConfigEntryBuilder entries = builder.entryBuilder();

            ConfigCategory growth = builder.getOrCreateCategory(
                    Component.translatable("config.slimeform.category.growth"));
            growth.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.max_slime_size"),
                            config.maxSlimeSize,
                            SlimeFormConfig.MIN_MAX_SLIME_SIZE,
                            SlimeFormConfig.MAX_MAX_SLIME_SIZE)
                    .setDefaultValue(5)
                    .setSaveConsumer(value -> config.maxSlimeSize = value)
                    .setTooltip(Component.translatable("config.slimeform.max_slime_size.tooltip"))
                    .build());
            growth.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.slime_balls_required"),
                            config.slimeBallsRequired,
                            SlimeFormConfig.MIN_SLIME_BALLS_REQUIRED,
                            SlimeFormConfig.MAX_SLIME_BALLS_REQUIRED)
                    .setDefaultValue(1)
                    .setSaveConsumer(value -> config.slimeBallsRequired = value)
                    .setTooltip(Component.translatable("config.slimeform.slime_balls_required.tooltip"))
                    .build());

            ConfigCategory debug = builder.getOrCreateCategory(
                    Component.translatable("config.slimeform.category.debug"));
            debug.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.rider_offset_x"),
                            (int) Math.round(config.riderOffsetX * 10.0D),
                            -10,
                            10)
                    .setTextGetter(value -> Component.literal(String.format(
                            java.util.Locale.ROOT, "%.1f", value / 10.0D)))
                    .setDefaultValue(0)
                    .setSaveConsumer(value -> config.riderOffsetX = value / 10.0D)
                    .setTooltip(Component.translatable("config.slimeform.rider_offset_x.tooltip"))
                    .build());
            debug.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.rider_offset_y"),
                            (int) Math.round(config.riderOffsetYPerSize * 10.0D),
                            -10,
                            10)
                    .setTextGetter(value -> Component.literal(String.format(
                            java.util.Locale.ROOT, "%.1f", value / 10.0D)))
                    .setDefaultValue(-1)
                    .setSaveConsumer(value -> config.riderOffsetYPerSize = value / 10.0D)
                    .setTooltip(Component.translatable("config.slimeform.rider_offset_y.tooltip"))
                    .build());
            debug.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.rider_offset_z"),
                            (int) Math.round(config.riderOffsetZ * 10.0D),
                            -10,
                            10)
                    .setTextGetter(value -> Component.literal(String.format(
                            java.util.Locale.ROOT, "%.1f", value / 10.0D)))
                    .setDefaultValue(0)
                    .setSaveConsumer(value -> config.riderOffsetZ = value / 10.0D)
                    .setTooltip(Component.translatable("config.slimeform.rider_offset_z.tooltip"))
                    .build());
            debug.addEntry(entries.startBooleanToggle(
                            Component.translatable("config.slimeform.afk_timer_debug"),
                            config.showAfkTimerDebug)
                    .setDefaultValue(false)
                    .setSaveConsumer(value -> config.showAfkTimerDebug = value)
                    .setTooltip(Component.translatable("config.slimeform.afk_timer_debug.tooltip"))
                    .build());

            ConfigCategory recovery = builder.getOrCreateCategory(
                    Component.translatable("config.slimeform.category.recovery"));
            recovery.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.split_duration"),
                            config.splitDurationSeconds,
                            SlimeFormConfig.MIN_SPLIT_DURATION_SECONDS,
                            SlimeFormConfig.MAX_SPLIT_DURATION_SECONDS)
                    .setDefaultValue(30)
                    .setSaveConsumer(value -> config.splitDurationSeconds = value)
                    .setTooltip(Component.translatable("config.slimeform.split_duration.tooltip"))
                    .build());

            ConfigCategory activity = builder.getOrCreateCategory(
                    Component.translatable("config.slimeform.category.activity"));
            activity.addEntry(entries.startBooleanToggle(
                            Component.translatable("config.slimeform.passive_spawning"),
                            config.passiveSlimeSpawning)
                    .setDefaultValue(true)
                    .setSaveConsumer(value -> config.passiveSlimeSpawning = value)
                    .setTooltip(Component.translatable("config.slimeform.passive_spawning.tooltip"))
                    .build());
            activity.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.spawn_chance"),
                            config.passiveSlimeSpawnChance,
                            SlimeFormConfig.MIN_PASSIVE_SPAWN_CHANCE,
                            SlimeFormConfig.MAX_PASSIVE_SPAWN_CHANCE)
                    .setDefaultValue(2)
                    .setSaveConsumer(value -> config.passiveSlimeSpawnChance = value)
                    .setTooltip(Component.translatable("config.slimeform.spawn_chance.tooltip"))
                    .build());
            activity.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.spawn_cooldown"),
                            config.passiveSlimeSpawnCooldownSeconds,
                            SlimeFormConfig.MIN_PASSIVE_SPAWN_COOLDOWN_SECONDS,
                            SlimeFormConfig.MAX_PASSIVE_SPAWN_COOLDOWN_SECONDS)
                    .setDefaultValue(30)
                    .setSaveConsumer(value -> config.passiveSlimeSpawnCooldownSeconds = value)
                    .setTooltip(Component.translatable("config.slimeform.spawn_cooldown.tooltip"))
                    .build());
            activity.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.max_nearby_slimes"),
                            config.maxNearbySpawnedSlimes,
                            SlimeFormConfig.MIN_MAX_NEARBY_SPAWNED_SLIMES,
                            SlimeFormConfig.MAX_MAX_NEARBY_SPAWNED_SLIMES)
                    .setDefaultValue(4)
                    .setSaveConsumer(value -> config.maxNearbySpawnedSlimes = value)
                    .setTooltip(Component.translatable("config.slimeform.max_nearby_slimes.tooltip"))
                    .build());
            activity.addEntry(entries.startBooleanToggle(
                            Component.translatable("config.slimeform.afk_enabled"),
                            config.afkDormantEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(value -> config.afkDormantEnabled = value)
                    .setTooltip(Component.translatable("config.slimeform.afk_enabled.tooltip"))
                    .build());
            activity.addEntry(entries.startIntSlider(
                            Component.translatable("config.slimeform.afk_duration"),
                            config.afkInactivitySeconds,
                            SlimeFormConfig.MIN_AFK_INACTIVITY_SECONDS,
                            SlimeFormConfig.MAX_AFK_INACTIVITY_SECONDS)
                    .setDefaultValue(300)
                    .setSaveConsumer(value -> config.afkInactivitySeconds = value)
                    .setTooltip(Component.translatable("config.slimeform.afk_duration.tooltip"))
                    .build());

            ConfigCategory water = builder.getOrCreateCategory(
                    Component.translatable("config.slimeform.category.water"));
            water.addEntry(entries.startBooleanToggle(
                            Component.translatable("config.slimeform.water_behavior"),
                            config.slimeWaterBehavior)
                    .setDefaultValue(true)
                    .setSaveConsumer(value -> config.slimeWaterBehavior = value)
                    .setTooltip(Component.translatable("config.slimeform.water_behavior.tooltip"))
                    .build());

            return builder.build();
        };
    }

}
