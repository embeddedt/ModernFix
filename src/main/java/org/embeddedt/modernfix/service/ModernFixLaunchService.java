package org.embeddedt.modernfix.service;

import com.electronwill.nightconfig.toml.TomlFormat;
import cpw.mods.modlauncher.*;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import org.embeddedt.modernfix.classloading.ModernFixResourceFinder;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.objectweb.asm.Type;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ModernFixLaunchService implements ILaunchPluginService {

    private static final boolean USE_TRANSFORMER_CACHE = false;
    @Override
    public String name() {
        return "modernfix";
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Path[] specialPaths) {
        /* Swap the transformer for ours */
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if(!(loader instanceof TransformingClassLoader)) {
            throw new IllegalStateException("Expected a TransformingClassLoader");
        }
        try {
            if(USE_TRANSFORMER_CACHE) {
                Field classTransformerField = TransformingClassLoader.class.getDeclaredField("classTransformer");
                classTransformerField.setAccessible(true);
                ClassTransformer t = (ClassTransformer)classTransformerField.get(loader);
                TransformStore store = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "transformers");
                LaunchPluginHandler pluginHandler = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "pluginHandler");
                TransformerAuditTrail trail = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "auditTrail");
                classTransformerField.set(loader, new ModernFixCachingClassTransformer(store, pluginHandler, (TransformingClassLoader)loader, trail));
            }
            Field resourceFinderField = TransformingClassLoader.class.getDeclaredField("resourceFinder");
            /* Construct a new list of resource finders, using similar logic to ML */
            resourceFinderField.setAccessible(true);
            Function<String, Enumeration<URL>> resourceFinder = constructResourceFinder();
            resourceFinderField.set(loader, resourceFinder);
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    private Function<String, Enumeration<URL>> constructResourceFinder() throws ReflectiveOperationException {
        Field servicesHandlerField = Launcher.class.getDeclaredField("transformationServicesHandler");
        servicesHandlerField.setAccessible(true);
        Object servicesHandler = servicesHandlerField.get(Launcher.INSTANCE);
        Field serviceLookupField = servicesHandler.getClass().getDeclaredField("serviceLookup");
        serviceLookupField.setAccessible(true);
        Map<String, TransformationServiceDecorator> serviceLookup = (Map<String, TransformationServiceDecorator>)serviceLookupField.get(servicesHandler);
        Method getClassLoaderMethod = TransformationServiceDecorator.class.getDeclaredMethod("getClassLoader");
        getClassLoaderMethod.setAccessible(true);
        Function<String, Enumeration<URL>> resourceEnumeratorLocator = ModernFixResourceFinder::findAllURLsForResource;
        for(TransformationServiceDecorator decorator : serviceLookup.values()) {
            Function<String, Optional<URL>> func = (Function<String, Optional<URL>>)getClassLoaderMethod.invoke(decorator);
            if(func != null)
                resourceEnumeratorLocator = EnumerationHelper.mergeFunctors(resourceEnumeratorLocator, EnumerationHelper.fromOptional(func));
        }
        System.out.println(EnumerationHelper.firstElementOrNull(resourceEnumeratorLocator.apply("net.minecraft.client.Minecraft")));
        return resourceEnumeratorLocator;
    }

    private static final EnumSet<Phase> NEVER = EnumSet.noneOf(Phase.class);

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return NEVER;
    }
}
