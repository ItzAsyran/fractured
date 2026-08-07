package io.asy.fragmented;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SlimeFormExperimentalScreen extends Screen {
    private final Screen returnScreen;

    public SlimeFormExperimentalScreen(Screen returnScreen) {
        super(Component.translatable("config.slimeform.experimental.placeholder.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button ->
                Minecraft.getInstance().setScreen(returnScreen))
                .bounds(width / 2 - 100, height - 40, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("config.slimeform.experimental.disable"), button -> {
            SlimeFormConfig.get().experimentalFeaturesEnabled = false;
            SlimeFormConfig.save();
            Minecraft.getInstance().setScreen(returnScreen);
        }).bounds(width / 2 - 100, height - 70, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 25, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("config.slimeform.experimental.placeholder"),
                width / 2, height / 2 + 5, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
