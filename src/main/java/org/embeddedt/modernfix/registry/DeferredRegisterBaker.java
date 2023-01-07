package org.embeddedt.modernfix.registry;

import com.google.common.base.Stopwatch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModWorkManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.util.AsyncStopwatch;
import org.embeddedt.modernfix.util.CachedSupplier;
import org.embeddedt.modernfix.util.OrderedParallelModDispatcher;

import java.util.*;
import java.util.concurrent.TimeUnit;
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
            Set<String> modErrors = Collections.synchronizedSet(new HashSet<>());
            HashMap<String, List<CachedSupplier<?>>> registrySupplierMap = supplierMap.get(registry);
            if(registrySupplierMap == null)
                return;
            Stopwatch realtimeStopwatch = Stopwatch.createStarted();
            AsyncStopwatch cpuStopwatch = new AsyncStopwatch();
            OrderedParallelModDispatcher.dispatchBlocking(ModWorkManager.parallelExecutor(), modId -> {
                List<CachedSupplier<?>> suppliersToCompute = registrySupplierMap.get(modId);
                if (suppliersToCompute == null || suppliersToCompute.size() == 0) {
                    return;
                }
                cpuStopwatch.startMeasuringAsync();
                for (CachedSupplier<?> supplier : suppliersToCompute) {
                    try {
                        supplier.compute();
                    } catch(RuntimeException e) {
                        ModernFix.LOGGER.debug("Exception encountered while caching supplier", e);
                        modErrors.add(modId);
                    }
                }
                cpuStopwatch.stopMeasuringAsync();
            });
            realtimeStopwatch.stop();
            if(modErrors.size() > 0)
                ModernFix.LOGGER.warn("The following mods had errors while caching suppliers (this is likely safe): [" + String.join(", ", modErrors) + "]");
            ModernFix.LOGGER.info("CPU time spent constructing " + registry + " suppliers: " + cpuStopwatch.getCpuTime()/1000f + " seconds");
            ModernFix.LOGGER.info("Real time spent constructing " + registry + " suppliers: " + realtimeStopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
        }
    }
}
