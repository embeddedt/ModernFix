package org.embeddedt.modernfix.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.embeddedt.modernfix.ModernFix;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Iterates over all mods in the game, parallelizing where possible while preserving dependency ordering.
 *
 * Can also be given a list of mods to skip.
 */
public class OrderedParallelModDispatcher {
    private static final Marker DISPATCHER = MarkerManager.getMarker("OrderedParallelModDispatcher");
    public static void dispatchBlocking(Executor executor, Consumer<String> task, Collection<String> modIDsToFilter) {
        Set<String> finishedMods = Collections.synchronizedSet(new HashSet<>(modIDsToFilter));
        HashMap<String, CompletableFuture<?>> submittedFutures = new HashMap<>();
        int numMods = ModList.get().getMods().size();
        Semaphore jobWaitingSemaphore = new Semaphore(0);
        ArrayList<ModInfo> remainingModList = new ArrayList<>(ModList.get().getMods());
        while(finishedMods.size() < numMods) {
            remainingModList.removeIf(modInfo -> {
                if(finishedMods.contains(modInfo.getModId()))
                    return true;
                List<String> missingDependencies = modInfo.getDependencies().stream()
                        .filter(IModInfo.ModVersion::isMandatory)
                        .map(IModInfo.ModVersion::getModId)
                        .filter(modId -> !finishedMods.contains(modId))
                        .collect(Collectors.toList());
                if(missingDependencies.size() > 0) {
                    //ModernFix.LOGGER.debug(DISPATCHER, "Cannot process " + modInfo.getModId() + ", as it is waiting on mods: [" + String.join(", ", missingDependencies) + "]");
                    return false;
                }
                Optional<? extends ModContainer> modContainerOpt = ModList.get().getModContainerById(modInfo.getModId());
                if(!modContainerOpt.isPresent())
                    throw new IllegalStateException("Can't find mod container");
                ModContainer container = modContainerOpt.get();
                //ModernFix.LOGGER.debug(DISPATCHER, "Submitting job for " + modInfo.getModId());
                submittedFutures.put(modInfo.getModId(), CompletableFuture.runAsync(() -> {
                    Supplier<?> contextExtension = ObfuscationReflectionHelper.getPrivateValue(ModContainer.class, container, "contextExtension");
                    ModLoadingContext.get().setActiveContainer(container, contextExtension.get());
                    try {
                        task.accept(modInfo.getModId());
                    } catch(RuntimeException e) {
                        e.printStackTrace();
                    }
                    /*
                     * We cannot rely on the main thread to correctly mark us as done, as it might start running
                     * before the future is marked as complete. So we add the mod to the finished set ourselves.
                     */
                    finishedMods.add(modInfo.getModId());
                    jobWaitingSemaphore.release();
                    //ModLoadingContext.get().setActiveContainer(null, null);
                }, executor));
                return true;
            });
            Preconditions.checkState(submittedFutures.size() > 0, "The semaphore will block forever!");
            //ModernFix.LOGGER.debug(DISPATCHER, "Waiting for one of [" + String.join(", ", submittedFutures.keySet()) + "] to finish...");
            try {
                jobWaitingSemaphore.acquire();
            } catch(InterruptedException e) {
                throw new RuntimeException("Unexpected interruption", e);
            }
            submittedFutures.entrySet().removeIf(entry -> {
                if(entry.getValue().isDone()) {
                    //ModernFix.LOGGER.debug(DISPATCHER, "Job finished for " + entry.getKey());
                    return true;
                }
                return false;
            });
        }
    }

    public static void dispatchBlocking(Executor executor, Consumer<String> task) {
        dispatchBlocking(executor, task, Collections.emptyList());
    }
}
