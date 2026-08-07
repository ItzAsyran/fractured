package io.asy.fragmented;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SlimeFormExperimentalConfirmationScreen extends Screen {
    private final Screen returnScreen;

    public SlimeFormExperimentalConfirmationScreen(Screen returnScreen) {
        super(Component.translatable("config.slimeform.experimental.confirm.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("config.slimeform.experimental.yes"), button -> {
            SlimeFormConfig.get().experimentalFeaturesEnabled = true;
            SlimeFormConfig.save();
            Minecraft.getInstance().setScreen(new SlimeFormExperimentalScreen(returnScreen));
        }).bounds(center - 105, height / 2 + 35, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.slimeform.experimental.no"), button -> {
            SlimeFormConfig.get().experimentalFeaturesEnabled = false;
            SlimeFormConfig.save();
            Minecraft.getInstance().setScreen(returnScreen);
        }).bounds(center + 5, height / 2 + 35, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("config.slimeform.experimental.confirm.message"),
                width / 2, height / 2 - 15, 0xFFCC55);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
