package io.asy.fragmented.gui.tabs;

import io.asy.fragmented.SlimeFormClient;
import io.asy.fragmented.SlimeFormConfig;
import io.asy.fragmented.SlimeFormSounds;
import io.asy.fragmented.gui.SlimeStageWidget;
import io.asy.fragmented.gui.components.BedrockButton;
import io.asy.fragmented.gui.components.BedrockCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public class AppearanceAudioTab extends SlimeCoreTab {
    @Override
    protected void buildCards() {
        SlimeFormConfig config = SlimeFormConfig.get();
        Minecraft client = Minecraft.getInstance();

        // --- Card 1: Player Avatar (Client Look) ---
        BedrockCard avatarCard = new BedrockCard(x, y, width, Component.literal("Player Avatar Shaders"));
        avatarCard.addSlider(
                Component.translatable("config.slimeform.player_transparency"),
                config.playerTransparencyPercent, SlimeFormConfig.MIN_PLAYER_TRANSPARENCY, SlimeFormConfig.MAX_PLAYER_TRANSPARENCY, true,
                val -> Component.literal("Transparency: " + val.intValue() + "%"),
                val -> {
                    config.playerTransparencyPercent = val.intValue();
                    SlimeFormConfig.save();
                    SlimeFormClient.syncPlayerAppearance();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.AVATAR_SHADERS)
        );

        avatarCard.addSwitch(
                Component.translatable("config.slimeform.player_transparency_slime_tint"),
                Component.translatable("config.slimeform.player_transparency_slime_tint.tooltip"),
                config.playerTransparencySlimeTint,
                val -> {
                    config.playerTransparencySlimeTint = val;
                    SlimeFormConfig.save();
                    SlimeFormClient.syncPlayerAppearance();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.AVATAR_SHADERS)
        );

        avatarCard.addSwitch(
                Component.translatable("config.slimeform.player_transparency_slime_shell"),
                Component.translatable("config.slimeform.player_transparency_slime_shell.tooltip"),
                config.playerTransparencySlimeShell,
                val -> {
                    config.playerTransparencySlimeShell = val;
                    SlimeFormConfig.save();
                    SlimeFormClient.syncPlayerAppearance();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.AVATAR_SHADERS)
        );

        avatarCard.addSwitch(
                Component.translatable("config.slimeform.slime_footstep_particles"),
                Component.translatable("config.slimeform.slime_footstep_particles.tooltip"),
                config.slimeFootstepParticles,
                val -> {
                    config.slimeFootstepParticles = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.AVATAR_SHADERS)
        );
        cards.add(avatarCard);

        // --- Card 2: Mounted Slime Alignment ---
        BedrockCard riderCard = new BedrockCard(x, y, width, Component.literal("Mounted Slime Alignment"));
        riderCard.addSlider(
                Component.translatable("config.slimeform.rider_offset_x"),
                config.riderOffsetX, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET, false,
                val -> Component.literal(String.format(Locale.ROOT, "Rider Offset X: %.2f", val)),
                val -> {
                    config.riderOffsetX = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.MOUNTED_RIDER)
        );

        riderCard.addSlider(
                Component.translatable("config.slimeform.rider_offset_y"),
                config.riderOffsetYPerSize, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET, false,
                val -> Component.literal(String.format(Locale.ROOT, "Rider Offset Y: %.2f", val)),
                val -> {
                    config.riderOffsetYPerSize = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.MOUNTED_RIDER)
        );

        riderCard.addSlider(
                Component.translatable("config.slimeform.rider_offset_z"),
                config.riderOffsetZ, SlimeFormConfig.MIN_RIDER_OFFSET, SlimeFormConfig.MAX_RIDER_OFFSET, false,
                val -> Component.literal(String.format(Locale.ROOT, "Rider Offset Z: %.2f", val)),
                val -> {
                    config.riderOffsetZ = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.MOUNTED_RIDER)
        );
        cards.add(riderCard);

        // --- Card 3: Floating Items in Belly ---
        BedrockCard itemCard = new BedrockCard(x, y, width, Component.literal("Floating Items in Belly"));
        itemCard.addSwitch(
                Component.translatable("config.slimeform.floating_item_displays"),
                Component.translatable("config.slimeform.floating_item_displays.tooltip"),
                config.floatingItemDisplays,
                val -> {
                    config.floatingItemDisplays = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.FLOATING_ITEMS)
        );

        itemCard.addSlider(
                Component.translatable("config.slimeform.item_display_scale"),
                config.itemDisplayScale, SlimeFormConfig.MIN_ITEM_DISPLAY_SCALE, SlimeFormConfig.MAX_ITEM_DISPLAY_SCALE, false,
                val -> Component.literal(String.format(Locale.ROOT, "Item Scale: %.2f", val)),
                val -> {
                    config.itemDisplayScale = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.FLOATING_ITEMS)
        );

        itemCard.addSlider(
                Component.translatable("config.slimeform.item_display_rotation_x"),
                config.itemDisplayRotationX, SlimeFormConfig.MIN_ITEM_DISPLAY_ROTATION, SlimeFormConfig.MAX_ITEM_DISPLAY_ROTATION, false,
                val -> Component.literal(String.format(Locale.ROOT, "Rotation X: %.1f°", val)),
                val -> {
                    config.itemDisplayRotationX = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.FLOATING_ITEMS)
        );

        itemCard.addSlider(
                Component.translatable("config.slimeform.item_display_bob_amplitude"),
                config.itemDisplayBobAmplitude, SlimeFormConfig.MIN_ITEM_DISPLAY_BOB, SlimeFormConfig.MAX_ITEM_DISPLAY_BOB, false,
                val -> Component.literal(String.format(Locale.ROOT, "Bob Amplitude: %.2f", val)),
                val -> {
                    config.itemDisplayBobAmplitude = val;
                    SlimeFormConfig.save();
                },
                () -> stage.setPreviewMode(SlimeStageWidget.PreviewMode.FLOATING_ITEMS)
        );
        cards.add(itemCard);

        // --- Card 4: Slime Soundboard ---
        BedrockCard soundCard = new BedrockCard(x, y, width, Component.translatable("config.slimeform.category.sounds"));
        soundCard.addSwitch(
                Component.translatable("config.slimeform.slime_sounds_enabled"),
                Component.translatable("config.slimeform.slime_sounds_enabled.tooltip"),
                config.slimeSoundsEnabled,
                val -> {
                    config.slimeSoundsEnabled = val;
                    SlimeFormConfig.save();
                },
                null
        );

        addSoundEntry(soundCard, "Jump", config.slimeSoundJump, SoundEvents.SLIME_JUMP, val -> config.slimeSoundJump = val);
        addSoundEntry(soundCard, "Landing", config.slimeSoundLanding, SoundEvents.SLIME_SQUISH, val -> config.slimeSoundLanding = val);
        addSoundEntry(soundCard, "Footsteps", config.slimeSoundFootsteps, SoundEvents.SLIME_SQUISH_SMALL, val -> config.slimeSoundFootsteps = val);
        addSoundEntry(soundCard, "Hurt", config.slimeSoundHurt, SoundEvents.SLIME_HURT, val -> config.slimeSoundHurt = val);
        addSoundEntry(soundCard, "Death", config.slimeSoundDeath, SoundEvents.SLIME_DEATH, val -> config.slimeSoundDeath = val);
        addSoundEntry(soundCard, "Attack", config.slimeSoundAttack, SoundEvents.SLIME_ATTACK, val -> config.slimeSoundAttack = val);
        addSoundEntry(soundCard, "Size-Up Squish", config.slimeSoundGrowth, SoundEvents.SLIME_SQUISH, val -> config.slimeSoundGrowth = val);
        cards.add(soundCard);
    }

    private void addSoundEntry(BedrockCard card, String name, boolean initial, SoundEvent sound, java.util.function.Consumer<Boolean> onToggle) {
        card.addSwitch(
                Component.literal(name),
                Component.literal("Toggle slime " + name.toLowerCase() + " sound."),
                initial,
                val -> {
                    onToggle.accept(val);
                    SlimeFormConfig.save();
                },
                null
        );
        card.addButton(
                Component.literal("🔊 Preview " + name),
                BedrockButton.Style.NEUTRAL,
                () -> Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F))
        );
    }
}
