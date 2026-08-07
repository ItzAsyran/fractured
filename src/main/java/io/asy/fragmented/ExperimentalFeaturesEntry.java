package io.asy.fragmented;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

final class ExperimentalFeaturesEntry extends AbstractConfigListEntry<Boolean> {
    private final boolean enabled;
    private final Button button;

    ExperimentalFeaturesEntry(Component fieldName, boolean enabled) {
        super(fieldName, true);
        this.enabled = enabled;
        this.button = Button.builder(Component.translatable(enabled
                        ? "config.slimeform.experimental.open"
                        : "config.slimeform.experimental.enable"), ignored -> open())
                .bounds(0, 0, 160, 20)
                .build();
    }

    private void open() {
        if (enabled) {
            Minecraft.getInstance().setScreen(new SlimeFormExperimentalScreen(getConfigScreen()));
        } else {
            Minecraft.getInstance().setScreen(
                    new SlimeFormExperimentalConfirmationScreen(getConfigScreen()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int index, int x, int y, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        graphics.drawString(Minecraft.getInstance().font, getFieldName(), x, y + 6, 0xFFFFFF);
        button.setX(x + width - 160);
        button.setY(y);
        button.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public int getItemHeight() {
        return 20;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public Boolean getValue() {
        return enabled;
    }

    @Override
    public Optional<Boolean> getDefaultValue() {
        return Optional.of(false);
    }
}
