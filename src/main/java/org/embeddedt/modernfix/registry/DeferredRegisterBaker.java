package org.embeddedt.modernfix.registry;

import com.google.common.base.Stopwatch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.CachedSupplier;
import org.embeddedt.modernfix.util.OrderedParallelModDispatcher;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DeferredRegisterBaker {
    private static final HashMap<ResourceLocation, HashMap<String, List<CachedSupplier<?>>>> supplierMap = new HashMap<>();
    public static <T> Supplier<T> cacheForComputationLater(ResourceLocation registry, String modid, Supplier<T> supplier) {
        synchronized (supplierMap) {
            HashMap<String, List<CachedSupplier<?>>> registrySupplierMap = supplierMap.computeIfAbsent(registry, reg -> new HashMap<>());
            List<CachedSupplier<?>> modSupplierList = registrySupplierMap.computeIfAbsent(modid, id -> new ArrayList<>());
            CachedSupplier<T> cacher = new CachedSupplier<>(supplier);
            modSupplierList.add(cacher);
            return cacher;
        }
    }

    public static void bakeSuppliers(ResourceLocation registry) {
        synchronized (supplierMap) {
            HashMap<String, List<CachedSupplier<?>>> registrySupplierMap = supplierMap.get(registry);
            if(registrySupplierMap == null)
                return;
            Stopwatch realtimeStopwatch = Stopwatch.createStarted();
            AtomicLong cpuLong = new AtomicLong(0);
            OrderedParallelModDispatcher.dispatchBlocking(modId -> {
                List<CachedSupplier<?>> suppliersToCompute = registrySupplierMap.get(modId);
                if (suppliersToCompute == null || suppliersToCompute.size() == 0) {
                    return;
                }
                Stopwatch stopwatch = Stopwatch.createStarted();
                for (CachedSupplier<?> supplier : suppliersToCompute) {
                    supplier.compute();
                }
                stopwatch.stop();
                cpuLong.addAndGet(stopwatch.elapsed(TimeUnit.MILLISECONDS));
            });
            realtimeStopwatch.stop();
            ModernFix.LOGGER.info("CPU time spent constructing " + registry + " suppliers: " + cpuLong.get()/1000f + " seconds");
            ModernFix.LOGGER.info("Real time spent constructing " + registry + " suppliers: " + realtimeStopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
        }
    }
}
