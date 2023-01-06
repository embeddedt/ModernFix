package org.embeddedt.modernfix.mixin.perf.async_jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.load.PluginCaller;
import org.embeddedt.modernfix.jei.async.IAsyncJeiStarter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(PluginCaller.class)
public class PluginCallerMixin {
    @Inject(method = "callOnPlugins", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;hasNext()Z"), remap = false)
    private static void checkForInterrupt(String title, List<IModPlugin> plugins, Consumer<IModPlugin> func, CallbackInfo ci) {
        IAsyncJeiStarter.checkForLoadInterruption();
    }
}
