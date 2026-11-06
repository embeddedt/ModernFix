package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.ModelBakeEventHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Mixin(ClientHooks.class)
@ClientOnlyMixin
public class MixinClientHooks {
    /**
     * @author embeddedt
     * @reason Give each mod its own view of the model maps, and report which mods are slow to handle the event, since
     * accessing many models is expensive with dynamic resources
     */
    @Redirect(method = "onModifyBakingResult", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private static void postPerModModifyBakingResult(Event event) {
        var bakeEvent = (ModelEvent.ModifyBakingResult)event;
        if (ModLoader.hasErrors()) {
            ModLoader.postEvent(bakeEvent);
            return;
        }
        var helper = new ModelBakeEventHelper(bakeEvent.getBakingResult());
        postPerMod("ModelEvent.ModifyBakingResult", id -> new ModelEvent.ModifyBakingResult(helper.wrapResult(id), bakeEvent.getTextureGetter(), bakeEvent.getModelBakery()));
    }

    /**
     * @author embeddedt
     * @reason Post a separate event per mod so they (a) see the wrapped maps and (b) are not able to break other mods
     * by trying to modify the event object (see https://github.com/embeddedt/ModernFix/issues/610 for an example).
     */
    @Redirect(method = "onModelBake", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private static void postPerModBakingCompleted(Event event) {
        var bakeEvent = (ModelEvent.BakingCompleted)event;
        if (ModLoader.hasErrors()) {
            ModLoader.postEvent(bakeEvent);
            return;
        }
        var helper = new ModelBakeEventHelper(bakeEvent.getBakingResult());
        postPerMod("ModelEvent.BakingCompleted", id -> new ModelEvent.BakingCompleted(bakeEvent.getModelManager(), helper.wrapResult(id), bakeEvent.getModelBakery()));
    }

    private static <T extends Event & IModBusEvent> void postPerMod(String eventName, Function<String, T> eventFactory) {
        Stopwatch globalTimer = Stopwatch.createStarted();
        Map<String, T> events = new Object2ObjectOpenHashMap<>();
        Map<String, Stopwatch> times = new Object2ObjectOpenHashMap<>();
        // Each mod keeps one event across all phases, and phases stay global, matching ModLoader.postEvent ordering
        for (EventPriority phase : EventPriority.values()) {
            ModList.get().forEachModInOrder(mc -> {
                T event = events.computeIfAbsent(mc.getModId(), eventFactory);
                Stopwatch timer = times.computeIfAbsent(mc.getModId(), $ -> Stopwatch.createUnstarted());
                timer.start();
                try {
                    mc.acceptEvent(phase, event);
                } finally {
                    timer.stop();
                }
            });
        }
        globalTimer.stop();
        if (globalTimer.elapsed(TimeUnit.SECONDS) >= 1) {
            ModernFix.LOGGER.warn("Posting dynamic {} to mods took {}, breakdown below:", eventName, globalTimer);
            times.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Stopwatch>, Duration>comparing(e -> e.getValue().elapsed()).reversed())
                    .filter(e -> e.getValue().elapsed(TimeUnit.MILLISECONDS) > 50)
                    .forEach(entry -> ModernFix.LOGGER.warn("    {}: {}", entry.getKey(), entry.getValue()));
        }
    }
}
