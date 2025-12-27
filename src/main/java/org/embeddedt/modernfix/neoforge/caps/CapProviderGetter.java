package org.embeddedt.modernfix.neoforge.caps;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CapProviderGetter {
    private static final MethodHandle BLOCK_CAP_PROVIDERS, ITEM_CAP_PROVIDERS, ENTITY_CAP_PROVIDERS;
    private static final boolean FOUND_PROVIDERS;

    static {
        MethodHandle bProvider, iProvider, eProvider;
        boolean found;
        try {
            bProvider = obtainForClass(BlockCapability.class);
            iProvider = obtainForClass(ItemCapability.class);
            eProvider = obtainForClass(EntityCapability.class);
            found = true;
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
            bProvider = null;
            iProvider = null;
            eProvider = null;
            found = false;
        }
        BLOCK_CAP_PROVIDERS = bProvider;
        ITEM_CAP_PROVIDERS = iProvider;
        ENTITY_CAP_PROVIDERS = eProvider;
        FOUND_PROVIDERS = found;
    }

    private static MethodHandle obtainForClass(Class<? extends BaseCapability> clz) throws ReflectiveOperationException{
        Field field = clz.getDeclaredField("providers");
        field.setAccessible(true);
        return MethodHandles.publicLookup().unreflectGetter(field);
    }

    private static final Map<?, List<ICapabilityProvider<?, ?, ?>>> DUMMY_MAP = new HashMap<>();

    public static <T extends BaseCapability> Map<?, List<ICapabilityProvider<?, ?, ?>>> getProviderMap(T cap) {
        if(!FOUND_PROVIDERS) {
            return DUMMY_MAP;
        }

        try {
            if(cap instanceof BlockCapability<?,?> blockCap) {
                return (Map<?, List<ICapabilityProvider<?, ?, ?>>>)BLOCK_CAP_PROVIDERS.invokeExact(blockCap);
            } else if(cap instanceof ItemCapability<?,?> itemCap) {
                return (Map<?, List<ICapabilityProvider<?, ?, ?>>>)ITEM_CAP_PROVIDERS.invokeExact(itemCap);
            } else if(cap instanceof EntityCapability<?,?> entityCap) {
                return (Map<?, List<ICapabilityProvider<?, ?, ?>>>)ENTITY_CAP_PROVIDERS.invokeExact(entityCap);
            } else {
                return DUMMY_MAP;
            }
        } catch(Throwable e) {
            throw new RuntimeException("Couldn't get map", e);
        }
    }

    public static void deduplicateCap(BaseCapability<?, ?> cap) {
        var map = getProviderMap(cap);
        if(map.isEmpty()) {
            return;
        }
        ObjectOpenHashSet<List<ICapabilityProvider<?, ?, ?>>> capLists = new ObjectOpenHashSet<>();
        int hits = 0;
        for(Map.Entry<?, List<ICapabilityProvider<?, ?, ?>>> entry : map.entrySet()) {
            var v = entry.getValue();
            var canonicalList = capLists.get(v);
            if(canonicalList == null) {
                canonicalList = List.copyOf(v);
                // This works because List.equals/hashCode is well-defined across any List implementation
                capLists.add(canonicalList);
            } else {
                hits++;
            }
            entry.setValue(canonicalList);
        }
        //ModernFix.LOGGER.info("Deduplicated {}/{} lists", hits, map.size());
    }
}
