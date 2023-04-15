package org.embeddedt.modernfix.mixin.perf.skip_first_datapack_reload;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    protected CreateWorldScreenMixin(Component arg) {
        super(arg);
    }
    // TODO: incorporate https://github.com/MinecraftForge/MinecraftForge/pull/9454
}
