package org.embeddedt.modernfix.forge.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.embeddedt.modernfix.ModernFix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ObjectHolderClearer {
    /**
     * Many of the built-in Forge holders needlessly hold on to an exception.
     */
    public static void clearThrowables() {
        Set<Consumer<Predicate<ResourceLocation>>> holders = ObfuscationReflectionHelper.getPrivateValue(ObjectHolderRegistry.class, null, "objectHolders");
        if(holders != null) {
            int numCleared = 0;
            HashMap<Class<?>, Field> throwableField = new HashMap<>();
            Throwable singletonThrowable = new Throwable("[This stacktrace was cleared to save memory]");
            try {
                for(Consumer<Predicate<ResourceLocation>> holder : holders) {
                    Field target = throwableField.computeIfAbsent(holder.getClass(), clz -> {
                        Field[] clzFields = clz.getDeclaredFields();
                        for(Field f : clzFields) {
                            if(Throwable.class.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                return f;
                            }
                        }
                        return null;
                    });
                    if(target != null) {
                        target.set(holder, singletonThrowable);
                        numCleared++;
                    }
                }
            } catch(RuntimeException | ReflectiveOperationException | NoClassDefFoundError ignored) {
            }
            ModernFix.LOGGER.debug("Cleared " + numCleared + " object holder stacktrace references");
        }
    }

    public static void removeRedundantHolders() {
        try {
            Field holdersField = ObjectHolderRegistry.class.getDeclaredField("objectHolders");
            holdersField.setAccessible(true);
            Set<Consumer<Predicate<ResourceLocation>>> holders = (Set<Consumer<Predicate<ResourceLocation>>>)holdersField.get(null);

            Class<?> refClass = Class.forName("net.minecraftforge.registries.ObjectHolderRef");
            Field registryField = refClass.getDeclaredField("registry");
            registryField.setAccessible(true);
            Field injectedObjectField = refClass.getDeclaredField("injectedObject");
            injectedObjectField.setAccessible(true);

            Method getOverrideOwnersMethod = ForgeRegistry.class.getDeclaredMethod("getOverrideOwners");
            getOverrideOwnersMethod.setAccessible(true);

            HashMap<ForgeRegistry<?>, Map<ResourceLocation, String>> overrideCache = new HashMap<>();
            int removed = 0;

            var it = holders.iterator();
            while (it.hasNext()) {
                var holder = it.next();
                if (!refClass.isInstance(holder))
                    continue;
                ForgeRegistry<?> registry = (ForgeRegistry<?>)registryField.get(holder);
                ResourceLocation injectedObject = (ResourceLocation)injectedObjectField.get(holder);
                Map<ResourceLocation, String> overrides = overrideCache.computeIfAbsent(registry, r -> {
                    try {
                        return (Map<ResourceLocation, String>)getOverrideOwnersMethod.invoke(r);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                });
                if (!overrides.containsKey(injectedObject)) {
                    it.remove();
                    removed++;
                }
            }
            ModernFix.LOGGER.debug("Removed {} redundant object holders", removed);
        } catch (Exception e) {
            ModernFix.LOGGER.error("Failed to remove object holders", e);
        }
    }
}
