package org.embeddedt.modernfix.mixin.feature.reduce_loading_screen_freezes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.Util;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Redirect(method = "uploadTextures", at = @At(value = "INVOKE", target = "Ljava/util/Set;forEach(Ljava/util/function/Consumer;)V", ordinal = 0))
    private void bakeAndTickGUI(Set instance, Consumer consumer) {
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Baking models"));
        CompletableFuture<Void> modelBakingFuture = CompletableFuture.runAsync(() -> {
            instance.forEach(consumer);
        }, Util.backgroundExecutor());
        /* allow the GUI to continue running */
        Minecraft.getInstance().managedBlock(modelBakingFuture::isDone);
    }
}
