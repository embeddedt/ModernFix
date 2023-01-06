package org.embeddedt.modernfix.mixin.perf.async_jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.config.*;
import mezz.jei.config.sorting.RecipeCategorySortingConfig;
import mezz.jei.events.EventBusHelper;
import mezz.jei.events.PlayerJoinedWorldEvent;
import mezz.jei.gui.textures.Textures;
import mezz.jei.ingredients.IIngredientSorter;
import mezz.jei.startup.ClientLifecycleHandler;
import mezz.jei.startup.JeiStarter;
import mezz.jei.startup.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.jei.async.JEILoadingInterruptedException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientLifecycleHandler.class)
public class ClientLifecycleHandlerMixin {
    @Shadow @Final private JeiStarter starter;
    @Shadow @Final private List<IModPlugin> plugins;
    @Shadow @Final private Textures textures;
    @Shadow @Final private IClientConfig clientConfig;
    @Shadow @Final private IEditModeConfig editModeConfig;
    @Shadow @Final private IngredientFilterConfig ingredientFilterConfig;
    @Shadow @Final private WorldConfig worldConfig;
    @Shadow @Final private BookmarkConfig bookmarkConfig;
    @Shadow @Final private IModIdHelper modIdHelper;
    @Shadow @Final private RecipeCategorySortingConfig recipeCategorySortingConfig;
    @Shadow @Final private IIngredientSorter ingredientSorter;
    private volatile Thread reloadThread = null;
    @Inject(method = "setupJEI", at = @At(value = "INVOKE", target = "Lmezz/jei/startup/ClientLifecycleHandler;startJEI()V"), cancellable = true, remap = false)
    private void startAsync(CallbackInfo ci) {
        ci.cancel();
        startJEIAsync(() -> Minecraft.getInstance().execute(() -> EventBusHelper.post(new PlayerJoinedWorldEvent())));
    }

    /**
     * @author embeddedt
     * @reason force JEI starts to be asynchronous
     */
    @Overwrite(remap = false)
    public void startJEI() {
        startJEIAsync(() -> {});
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void setupCancellationHandler(NetworkHandler networkHandler, Textures textures, CallbackInfo ci) {
        EventBusHelper.addListener(this, ClientPlayerNetworkEvent.LoggedOutEvent.class, event -> cancelPreviousStart());
    }

    private void cancelPreviousStart() {
        Thread currentReloadThread = reloadThread;
        if(currentReloadThread != null) {
            currentReloadThread.interrupt();
            Minecraft.getInstance().managedBlock(currentReloadThread::isAlive);
            reloadThread = null;
        }
    }

    private void startJEIAsync(Runnable whenFinishedCb) {
        cancelPreviousStart();
        Thread newThread = new Thread(() -> {
            try {
                starter.start(
                        plugins,
                        textures,
                        clientConfig,
                        editModeConfig,
                        ingredientFilterConfig,
                        worldConfig,
                        bookmarkConfig,
                        modIdHelper,
                        recipeCategorySortingConfig,
                        ingredientSorter);
            } catch(JEILoadingInterruptedException e) {
                ModernFix.LOGGER.warn("JEI loading interrupted prematurely (this is normal)");
            }
            whenFinishedCb.run();
            reloadThread = null;
        }, "JEI Reload Thread");
        newThread.setPriority(Thread.MIN_PRIORITY);
        reloadThread = newThread;
        newThread.start();
    }


}
