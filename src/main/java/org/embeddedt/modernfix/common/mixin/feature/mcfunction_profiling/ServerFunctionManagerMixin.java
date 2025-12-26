package org.embeddedt.modernfix.common.mixin.feature.mcfunction_profiling;

import com.google.common.base.Stopwatch;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import org.embeddedt.modernfix.duck.IProfilingServerFunctionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

@Mixin(ServerFunctionManager.class)
public class ServerFunctionManagerMixin implements IProfilingServerFunctionManager {
    @Shadow @Final private static ResourceLocation TICK_FUNCTION_TAG;

    private final Map<ResourceLocation, Stopwatch> mfix$functionWatches = new Object2ObjectOpenHashMap<>();

    @Inject(method = "executeTagFunctions", at = @At("HEAD"))
    private void resetWatches(Collection<CommandFunction> functionObjects, ResourceLocation identifier, CallbackInfo ci) {
        mfix$functionWatches.values().forEach(Stopwatch::reset);
    }

    @Inject(method = "executeTagFunctions", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)I"))
    private void startWatch(Collection<CommandFunction> functionObjects, ResourceLocation identifier, CallbackInfo ci, @Local(ordinal = 0) CommandFunction function, @Share("stopwatch") LocalRef<Stopwatch> watchRef) {
        watchRef.set(null);
        if (identifier == TICK_FUNCTION_TAG) {
            var watch = mfix$functionWatches.computeIfAbsent(function.getId(), i -> Stopwatch.createUnstarted());
            watch.start();
            watchRef.set(watch);
        }
    }

    @Inject(method = "executeTagFunctions", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerFunctionManager;execute(Lnet/minecraft/commands/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)I", shift = At.Shift.AFTER))
    private void stopWatch(Collection<CommandFunction> functionObjects, ResourceLocation identifier, CallbackInfo ci, @Share("stopwatch") LocalRef<Stopwatch> watchRef) {
        var watch = watchRef.get();
        if (watch != null && watch.isRunning()) {
            watch.stop();
        }
    }

    @Inject(method = "executeTagFunctions", at = @At("RETURN"))
    private void pruneUnusedWatches(Collection<CommandFunction> functionObjects, ResourceLocation identifier, CallbackInfo ci) {
        mfix$functionWatches.values().removeIf(watch -> watch.elapsed().isZero());
    }

    @Override
    public String mfix$getProfilingResults() {
        var list = new ArrayList<>(mfix$functionWatches.entrySet());
        list.sort(Comparator.<Map.Entry<ResourceLocation, Stopwatch>, Duration>comparing(e -> e.getValue().elapsed()).reversed());
        StringBuilder sb = new StringBuilder();
        for (var entry : list) {
            sb.append(entry.getKey().toString());
            sb.append(" - ");
            sb.append(entry.getValue().toString());
            sb.append('\n');
        }
        return sb.toString();
    }
}
