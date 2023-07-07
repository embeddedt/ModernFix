package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.forge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Map;

@Mixin(ForgeHooksClient.class)
public class ForgeHooksClientMixin {
    /**
     * Generate a more realistic keySet that contains every item and block model location, to help with mod compat.
     */
    @Redirect(method = "onModelBake", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;postEvent(Lnet/minecraftforge/eventbus/api/Event;)V"), remap = false)
    private static void postNamespacedKeySetEvent(ModLoader loader, Event event) {
        if(!ModLoader.isLoadingStateValid())
            return;
        ModelBakeEvent bakeEvent = ((ModelBakeEvent)event);
        ModelBakeEventHelper helper = new ModelBakeEventHelper(bakeEvent.getModelRegistry());
        Method acceptEv = ObfuscationReflectionHelper.findMethod(ModContainer.class, "acceptEvent", Event.class);
        ModList.get().forEachModContainer((id, mc) -> {
            Map<ResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(id);
            ModelBakeEvent postedEvent = new ModelBakeEvent(bakeEvent.getModelManager(), newRegistry, bakeEvent.getModelLoader());
            try {
                acceptEv.invoke(mc, postedEvent);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
        });
    }
}
