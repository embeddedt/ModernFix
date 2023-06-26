package org.embeddedt.modernfix.fabric.mappings;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Designed for Fabric Loader 0.14.x, probably has issues on other versions. The entire thing is wrapped in a try-catch
 * so it should never cause crashes, just fail to work.
 */
public class MappingsClearer {
    @SuppressWarnings("unchecked")
    public static void clear() {
        if(FabricLoader.getInstance().isDevelopmentEnvironment())
            return; // never do this in dev
        CommonModUtil.runWithoutCrash(() -> {
            // clear notch->intermediary mappings
            MappingConfiguration config = FabricLauncherBase.getLauncher().getMappingConfiguration();
            Field mappingsField = MappingConfiguration.class.getDeclaredField("mappings");
            mappingsField.setAccessible(true);
            mappingsField.set(config, TinyMappingFactory.EMPTY_TREE);

            // clear useless intermediary->intermediary mappings
            MappingResolver resolver = FabricLoader.getInstance().getMappingResolver();
            Class<?> targetResolverClz = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl");
            if(targetResolverClz.isAssignableFrom(resolver.getClass())) {
                // hopefully still Loader 0.14.x, proceed
                Class<?> namespaceDataClz = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl$NamespaceData");
                Constructor<?> constructor = namespaceDataClz.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object theData = constructor.newInstance();
                Field mapField = resolver.getClass().getDeclaredField("namespaceDataMap");
                mapField.setAccessible(true);
                Map<String, Object> theMap = (Map<String, Object>)mapField.get(resolver);
                theMap.replace("intermediary", theData);
            }
        }, "Failed to clear mappings");
    }
}
