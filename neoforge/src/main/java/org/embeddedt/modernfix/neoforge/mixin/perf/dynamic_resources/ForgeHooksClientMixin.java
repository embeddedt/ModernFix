package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.neoforge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Mixin(ClientHooks.class)
public class ForgeHooksClientMixin {
    /**
     * Generate a more realistic keySet that contains every item and block model location, to help with mod compat.
     */
    @Redirect(method = "onModifyBakingResult", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"), remap = false)
    private static void postNamespacedKeySetEvent(Event event) {
        if(!ModLoader.isLoadingStateValid())
            return;
        ModelEvent.ModifyBakingResult bakeEvent = ((ModelEvent.ModifyBakingResult)event);
        ModelBakeEventHelper helper = new ModelBakeEventHelper(bakeEvent.getModels());
        Method acceptEv = ObfuscationReflectionHelper.findMethod(ModContainer.class, "acceptEvent", Event.class);
        ModList.get().forEachModContainer((id, mc) -> {
            Map<ResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(id);
            ModelEvent.ModifyBakingResult postedEvent = new ModelEvent.ModifyBakingResult(newRegistry, bakeEvent.getTextureGetter(), bakeEvent.getModelBakery());
            Stopwatch timer = Stopwatch.createStarted();
            try {
                acceptEv.invoke(mc, postedEvent);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
            timer.stop();
            if(timer.elapsed(TimeUnit.SECONDS) >= 1) {
                ModernFix.LOGGER.warn("Mod '{}' took {} in the model bake event", id, timer);
            }
        });
    }
}
