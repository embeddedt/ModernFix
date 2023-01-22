package org.embeddedt.modernfix.classloading.service;

import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Used as a hook to class load on the ModLauncher class loader.
 *
 * We also need to ensure the ModernFix JAR is removed from the exclusions list, to ensure it loads as a regular mod.
 */
public class EarlyLoadingService implements ITransformationService {
    private static final Logger LOGGER = LogManager.getLogger("ModernFixEarlyLoadingService");
    public Class<?> cachingTransformerClass;
    @Nonnull
    @Override
    public String name() {
        return "modernfix";
    }

    public EarlyLoadingService() {
        LOGGER.info("ModernFix (very) early loading");


        try {
            ClassLoader loader = EarlyLoadingService.class.getClassLoader();
            Class.forName("cpw.mods.modlauncher.ClassTransformer", true, loader);
            /* Allow ModernFix to be scanned like a mod */
            Field transformersField = ModDirTransformerDiscoverer.class.getDeclaredField("transformers");
            transformersField.setAccessible(true);
            List<Path> transformers = (List<Path>)transformersField.get(null);
            transformers.removeIf(path -> path.toString().contains("modernfix"));

            /* Load our new transformer */
            cachingTransformerClass = Class.forName("cpw.mods.modlauncher.ModernFixCachingClassTransformer", true, loader);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }

    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return Collections.emptyList();
    }
}
