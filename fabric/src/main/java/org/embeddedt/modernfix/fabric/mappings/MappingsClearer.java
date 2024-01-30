package org.embeddedt.modernfix.fabric.mappings;

import net.fabricmc.loader.api.*;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.launch.MappingConfiguration;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * Designed for Fabric Loader 0.14.x, probably has issues on other versions. The entire thing is wrapped in a try-catch
 * so it should never cause crashes, just fail to work.
 */
public class MappingsClearer {
    private static final Version LOADER_015;

    static {
        try {
            LOADER_015 = Version.parse("0.15.0");
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void clear() {
        if(FabricLoader.getInstance().isDevelopmentEnvironment())
            return; // never do this in dev

        Optional<Version> loaderVersion = FabricLoader.getInstance().getModContainer("fabricloader").map(m -> m.getMetadata().getVersion());
        if(!loaderVersion.isPresent() || LOADER_015.compareTo(loaderVersion.get()) < 0) {
            // Fabric Loader is probably too new, abort
            return;
        }

        CommonModUtil.runWithoutCrash(() -> {
            // For now, force the mapping resolver to be initialized. Fabric Loader 0.14.23 stops initializing it early,
            // which means that we actually need to keep the TinyMappingFactory tree around for initialization to work
            // later. We force init it now because then we can store less in memory.
            // Comparing heap dumps on 0.14.23 suggests a savings of 20MB by doing it our way, since many mods will
            // end up needing the mapping resolver.
            // This will need to be revisited when https://github.com/FabricMC/fabric-loader/pull/812 is merged.
            FabricLoader.getInstance().getMappingResolver();

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
