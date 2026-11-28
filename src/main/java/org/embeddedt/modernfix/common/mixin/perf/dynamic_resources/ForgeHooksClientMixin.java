package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.neoforge.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Mixin(ClientHooks.class)
public class ForgeHooksClientMixin {
    /**
     * The helper built for the last ModifyBakingResult, handed to the BakingCompleted event of the same reload.
     */
    @Unique
    private static volatile ModelBakeEventHelper mfix$lastHelper;

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
        mfix$lastHelper = helper;
        selfTimer.stop();
        Map<String, Stopwatch> times = new Object2ObjectOpenHashMap<>();
        times.put("modernfix", selfTimer);
        List<ModContainer> mods = ModList.get().getSortedMods();
        List<Event> events = new ArrayList<>(mods.size());
        for (ModContainer mc : mods) {
            Map<ModelResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(mc.getModId());
            events.add(new ModelEvent.ModifyBakingResult(newRegistry, bakeEvent.getTextureGetter(), bakeEvent.getModelBakery()));
        }
        postInModOrder(mods, events, times);
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

    /**
     * Post a separate BakingCompleted event per mod so they (a) see the wrapped registry and (b) are not able to
     * break other mods by trying to modify the event object (see https://github.com/embeddedt/ModernFix/issues/610
     * for an example).
     */
    @Redirect(method = "onModelBake", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"), remap = false)
    private static void postNamespacedKeySetBakingCompletedEvent(Event event) {
        if(ModLoader.hasErrors())
            return;
        ModelEvent.BakingCompleted bakeEvent = ((ModelEvent.BakingCompleted)event);
        ModelManager modelManager = bakeEvent.getModelManager();
        // reuse the model location universe computed for ModifyBakingResult earlier in this reload; building it
        // again here would block the render thread for another 1-2 s in large packs
        ModelBakeEventHelper previous = mfix$lastHelper;
        mfix$lastHelper = null;
        ModelBakeEventHelper helper = previous != null ? previous.withRegistry(bakeEvent.getModels()) : new ModelBakeEventHelper(bakeEvent.getModels());
        List<ModContainer> mods = ModList.get().getSortedMods();
        List<Event> events = new ArrayList<>(mods.size());
        for (ModContainer mc : mods) {
            Map<ModelResourceLocation, BakedModel> newRegistry = helper.wrapRegistry(mc.getModId());
            events.add(new ModelEvent.BakingCompleted(modelManager, newRegistry, bakeEvent.getModelBakery()));
        }
        postInModOrder(mods, events, null);
    }

    /**
     * Dispatches each mod's own event instance in the same order as {@link ModLoader#postEvent}: every priority phase
     * in turn, and within a phase, mods in dependency order. Mods rely on this (e.g. via ordering="AFTER") to see and
     * wrap models that other mods replaced in the same event.
     */
    private static void postInModOrder(List<ModContainer> mods, List<Event> events, Map<String, Stopwatch> times) {
        Method acceptEv = ObfuscationReflectionHelper.findMethod(ModContainer.class, "acceptEvent", EventPriority.class, Event.class);
        for (EventPriority phase : EventPriority.values()) {
            for (int i = 0; i < mods.size(); i++) {
                ModContainer mc = mods.get(i);
                Stopwatch timer = times != null ? times.computeIfAbsent(mc.getModId(), $ -> Stopwatch.createUnstarted()) : null;
                if (timer != null) {
                    timer.start();
                }
                try {
                    acceptEv.invoke(mc, phase, events.get(i));
                } catch(ReflectiveOperationException e) {
                    e.printStackTrace();
                }
                if (timer != null) {
                    timer.stop();
                }
            }
        }
    }
}
