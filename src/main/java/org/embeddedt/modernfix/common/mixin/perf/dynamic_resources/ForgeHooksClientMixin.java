package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
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
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Mixin(ClientHooks.class)
public class ForgeHooksClientMixin {
    /**
     * Generate a more realistic keySet that contains every item and block model location, to help with mod compat.
     */
    @Redirect(method = "onModifyBakingResult", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"), remap = false)
    private static void postNamespacedKeySetEvent(Event event) {
        if(ModLoader.hasErrors())
            return;
        ModelEvent.ModifyBakingResult bakeEvent = ((ModelEvent.ModifyBakingResult)event);
        Stopwatch globalTimer = Stopwatch.createStarted();
        Stopwatch selfTimer = Stopwatch.createStarted();
        ModelBakeEventHelper helper = new ModelBakeEventHelper(bakeEvent.getModels());
        selfTimer.stop();
        Method acceptEv = ObfuscationReflectionHelper.findMethod(ModContainer.class, "acceptEvent", Event.class);
        Map<String, Stopwatch> times = new Object2ObjectOpenHashMap<>();
        times.put("modernfix", selfTimer);
        ModList.get().forEachModContainer((id, mc) -> {
            Map<ModelResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(id);
            ModelEvent.ModifyBakingResult postedEvent = new ModelEvent.ModifyBakingResult(newRegistry, bakeEvent.getTextureGetter(), bakeEvent.getModelBakery());
            Stopwatch timer = times.computeIfAbsent(id, $ -> Stopwatch.createUnstarted());
            timer.start();
            try {
                acceptEv.invoke(mc, postedEvent);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
            timer.stop();
        });
        globalTimer.stop();
        if (globalTimer.elapsed(TimeUnit.SECONDS) >= 1) {
            ModernFix.LOGGER.warn("Posting dynamic ModelEvent.ModifyBakingResult to mods took {}, breakdown below:", globalTimer);
            times.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Stopwatch>, Duration>comparing(e -> e.getValue().elapsed()).reversed())
                    .filter(e -> e.getValue().elapsed(TimeUnit.MILLISECONDS) > 50)
                    .forEach(entry -> {
                        ModernFix.LOGGER.warn("    {}: {}", entry.getKey(), entry.getValue().toString());
                    });
        }
    }
}
