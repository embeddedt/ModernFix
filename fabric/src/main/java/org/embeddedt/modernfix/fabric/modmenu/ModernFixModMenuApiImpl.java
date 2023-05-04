package org.embeddedt.modernfix.fabric.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.embeddedt.modernfix.screen.ModernFixConfigScreen;

@SuppressWarnings("unused")
public class ModernFixModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<ModernFixConfigScreen> getModConfigScreenFactory() {
        return ModernFixConfigScreen::new;
    }
}
