package io.asy.fragmented;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.asy.fragmented.gui.SlimeCoreScreen;

public class SlimeFormModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SlimeCoreScreen::new;
    }
}
