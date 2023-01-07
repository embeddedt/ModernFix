package org.embeddedt.modernfix.util;

import com.google.common.base.Stopwatch;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Iterates over all mods in the game, parallelizing where possible while preserving dependency ordering.
 *
 * Can also be given a list of mods to skip.
 */
public class OrderedParallelModDispatcher {
    public static void dispatchBlocking(Consumer<String> task, Collection<String> modIDsToFilter) {
        HashSet<String> finishedMods = new HashSet<>(modIDsToFilter);
        HashMap<String, CompletableFuture<?>> submittedFutures = new HashMap<>();
        int numMods = ModList.get().getMods().size();
        Semaphore jobWaitingSemaphore = new Semaphore(0);
        ArrayList<ModInfo> remainingModList = new ArrayList<>(ModList.get().getMods());
        while(finishedMods.size() < numMods) {
            remainingModList.removeIf(modInfo -> {
                if(finishedMods.contains(modInfo.getModId()))
                    return true;
                boolean allDependenciesLoaded = true;
                for(IModInfo.ModVersion dep : modInfo.getDependencies()) {
                    if(dep.isMandatory() && !finishedMods.contains(dep.getModId())) {
                        allDependenciesLoaded = false;
                        break;
                    }
                }
                if(!allDependenciesLoaded)
                    return false;
                Optional<? extends ModContainer> modContainerOpt = ModList.get().getModContainerById(modInfo.getModId());
                if(!modContainerOpt.isPresent())
                    throw new IllegalStateException("Can't find mod container");
                ModContainer container = modContainerOpt.get();
                submittedFutures.put(modInfo.getModId(), CompletableFuture.runAsync(() -> {
                    Supplier<?> contextExtension = ObfuscationReflectionHelper.getPrivateValue(ModContainer.class, container, "contextExtension");
                    ModLoadingContext.get().setActiveContainer(container, contextExtension.get());
                    task.accept(modInfo.getModId());
                    jobWaitingSemaphore.release();
                }, ModWorkManager.parallelExecutor()));
                return true;
            });
            try {
                jobWaitingSemaphore.acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("Unexpected interruption", e);
            }
            submittedFutures.entrySet().removeIf(entry -> {
                if(entry.getValue().isDone()) {
                    finishedMods.add(entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }

    public static void dispatchBlocking(Consumer<String> task) {
        dispatchBlocking(task, Collections.emptyList());
    }
}
