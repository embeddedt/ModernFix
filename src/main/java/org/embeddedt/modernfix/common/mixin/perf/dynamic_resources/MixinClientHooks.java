package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.ClientHooks;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Mixin(ClientHooks.class)
@ClientOnlyMixin
public class MixinClientHooks {
    /**
     * @author embeddedt
     * @reason Report which mods are slow to handle the bake event, since accessing many models is expensive with
     * dynamic resources
     */
    @Redirect(method = "onModifyBakingResult", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModLoader;postEvent(Lnet/neoforged/bus/api/Event;)V"))
    private static <T extends Event & IModBusEvent> void postTimedEvent(T event) {
        if (ModLoader.hasErrors()) {
            ModLoader.postEvent(event);
            return;
        }
        Stopwatch globalTimer = Stopwatch.createStarted();
        Map<String, Stopwatch> times = new Object2ObjectOpenHashMap<>();
        for (EventPriority phase : EventPriority.values()) {
            ModList.get().forEachModInOrder(mc -> {
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
            ModernFix.LOGGER.warn("Posting dynamic ModelEvent.ModifyBakingResult to mods took {}, breakdown below:", globalTimer);
            times.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Stopwatch>, Duration>comparing(e -> e.getValue().elapsed()).reversed())
                    .filter(e -> e.getValue().elapsed(TimeUnit.MILLISECONDS) > 50)
                    .forEach(entry -> ModernFix.LOGGER.warn("    {}: {}", entry.getKey(), entry.getValue()));
        }
    }
}
