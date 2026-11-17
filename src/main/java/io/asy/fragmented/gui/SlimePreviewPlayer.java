package io.asy.fragmented.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;

public class SlimePreviewPlayer extends RemotePlayer {
    private PlayerSkin customSkin;

    public SlimePreviewPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    public void setSkin(PlayerSkin skin) {
        this.customSkin = skin;
    }

    @Override
    public PlayerSkin getSkin() {
        if (customSkin != null) {
            return customSkin;
        }
        return super.getSkin();
    }

    @Override
    protected PlayerInfo getPlayerInfo() {
        // Crucial offline guard: In main menu, client.getConnection() is null.
        // Overriding this prevents NullPointerException when getting player info offline.
        return null;
    }

    public void updateSkinCustomization(Minecraft client) {
        if (client.options == null) {
            return;
        }
        byte mask = 0;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (client.options.isModelPartEnabled(part)) {
                mask |= (byte) part.getMask();
            }
        }
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, mask);
    }
}
