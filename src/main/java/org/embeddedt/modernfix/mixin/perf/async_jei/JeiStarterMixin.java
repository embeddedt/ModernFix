package org.embeddedt.modernfix.mixin.perf.async_jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.config.*;
import mezz.jei.config.sorting.RecipeCategorySortingConfig;
import mezz.jei.gui.textures.Textures;
import mezz.jei.ingredients.IIngredientSorter;
import mezz.jei.load.PluginCaller;
import mezz.jei.startup.JeiStarter;
import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.jei.async.IAsyncJeiStarter;
import org.embeddedt.modernfix.jei.async.JEILoadingInterruptedException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CancellationException;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

@Mixin(JeiStarter.class)
@RequiresMod("jei")
public class JeiStarterMixin {
    @Shadow(remap = false) private boolean started;

    @Inject(method = "start", at = @At(value = "INVOKE", target = "Lmezz/jei/util/ErrorUtil;checkNotEmpty(Ljava/util/Collection;Ljava/lang/String;)V", ordinal = 0, shift = At.Shift.AFTER), remap = false)
    private void setStartedFlag(List<IModPlugin> plugins, Textures textures, IClientConfig clientConfig, IEditModeConfig editModeConfig, IIngredientFilterConfig ingredientFilterConfig, IWorldConfig worldConfig, BookmarkConfig bookmarkConfig, IModIdHelper modIdHelper, RecipeCategorySortingConfig recipeCategorySortingConfig, IIngredientSorter ingredientSorter, CallbackInfo ci) {
        /* We need to set this ASAP so the reload system will restart the async load if needed */
        started = true;
    }

    @Redirect(method = "start", at = @At(value = "INVOKE", target = "Lmezz/jei/load/PluginCaller;callOnPlugins(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V"), remap = false)
    private void callOnPluginsViaMainThread(String title, List<IModPlugin> plugins, Consumer<IModPlugin> func) {
        PluginCaller.callOnPlugins(title, plugins, plugin -> {
            try {
                Minecraft.getInstance().executeBlocking(() -> func.accept(plugin));
            } catch(CancellationException | CompletionException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
