package org.embeddedt.modernfix.forge.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
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
    @Redirect(method = "onModelBake", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/ModLoader;postEvent(Lnet/minecraftforge/eventbus/api/Event;)V"))
    private static void postNamespacedKeySetEvent(ModLoader loader, Event event) {
        if(!ModLoader.isLoadingStateValid())
            return;
        ModelEvent.BakingCompleted bakeEvent = ((ModelEvent.BakingCompleted)event);
        ModelBakeEventHelper helper = new ModelBakeEventHelper(bakeEvent.getModels());
        Method acceptEv = ObfuscationReflectionHelper.findMethod(ModContainer.class, "acceptEvent", Event.class);
        ModList.get().forEachModContainer((id, mc) -> {
            Map<ResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(id);
            ModelEvent.BakingCompleted postedEvent = new ModelEvent.BakingCompleted(bakeEvent.getModelManager(), newRegistry, bakeEvent.getModelBakery());
            try {
                acceptEv.invoke(mc, postedEvent);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
        });
    }
}
