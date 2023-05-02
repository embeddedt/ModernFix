package org.embeddedt.modernfix.forge.mixin.perf.async_jei;

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
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.jei.async.JEILoadingInterruptedException;
import org.embeddedt.modernfix.jei.async.JEIReloadThread;
import org.embeddedt.modernfix.forge.util.JEIUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientLifecycleHandler.class)
@RequiresMod("jei")
@ClientOnlyMixin
public class ClientLifecycleHandlerMixin {
    @Shadow(remap = false) @Final private JeiStarter starter;
    @Shadow(remap = false) @Final private List<IModPlugin> plugins;
    @Shadow(remap = false) @Final private Textures textures;
    @Shadow(remap = false) @Final private IClientConfig clientConfig;
    @Shadow(remap = false) @Final private IEditModeConfig editModeConfig;
    @Shadow(remap = false) @Final private IngredientFilterConfig ingredientFilterConfig;
    @Shadow(remap = false) @Final private WorldConfig worldConfig;
    @Shadow(remap = false) @Final private BookmarkConfig bookmarkConfig;
    @Shadow(remap = false) @Final private IModIdHelper modIdHelper;
    @Shadow(remap = false) @Final private RecipeCategorySortingConfig recipeCategorySortingConfig;
    @Shadow(remap = false) @Final private IIngredientSorter ingredientSorter;
    private volatile JEIReloadThread reloadThread = null;
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
        JEIUtil.registerLoadingRenderer(() -> reloadThread != null);
    }

    private void cancelPreviousStart() {
        JEIReloadThread currentReloadThread = reloadThread;
        if(currentReloadThread != null) {
            currentReloadThread.requestStop();
            if(currentReloadThread.isAlive()) {
                ModernFix.LOGGER.warn("Blocking until JEI thread terminates");
                Minecraft.getInstance().managedBlock(() -> !currentReloadThread.isAlive());
            }
            reloadThread = null;
        }
    }

    private static int numReloads = 1;

    private void startJEIAsync(Runnable whenFinishedCb) {
        cancelPreviousStart();
        if(Minecraft.getInstance().level == null)
            return;
        ModernFix.LOGGER.info("Starting new JEI thread.");
        JEIReloadThread newThread = new JEIReloadThread(() -> {
            if(((JEIReloadThread)Thread.currentThread()).isStopRequested())
                return;
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
        }, "ModernFix JEI Reload Thread " + numReloads++);
        newThread.setPriority(Thread.MIN_PRIORITY);
        reloadThread = newThread;
        newThread.start();
    }


}
