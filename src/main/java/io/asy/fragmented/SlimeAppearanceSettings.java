package io.asy.fragmented;

/** A player's locally configured Slime Form appearance preference. */
public record SlimeAppearanceSettings(int transparencyPercent, boolean slimeTint, boolean slimeShell) {
    public static final SlimeAppearanceSettings DEFAULT = new SlimeAppearanceSettings(0, false, false);

    public SlimeAppearanceSettings {
        transparencyPercent = Math.max(
                SlimeFormConfig.MIN_PLAYER_TRANSPARENCY,
                Math.min(SlimeFormConfig.MAX_PLAYER_TRANSPARENCY, transparencyPercent));
    }

    public static SlimeAppearanceSettings fromConfig(SlimeFormConfig config) {
        return new SlimeAppearanceSettings(
                config.effectivePlayerTransparencyPercent(),
                config.playerTransparencySlimeTint,
                config.playerTransparencySlimeShell);
    }
}
