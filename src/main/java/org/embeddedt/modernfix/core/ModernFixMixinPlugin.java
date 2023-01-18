package org.embeddedt.modernfix.core;

import cpw.mods.modlauncher.*;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.classloading.ModernFixResourceFinder;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.embeddedt.modernfix.core.config.Option;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

public class ModernFixMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "org.embeddedt.modernfix.mixin.";

    private final Logger logger = LogManager.getLogger("ModernFix");
    public static ModernFixEarlyConfig config = null;

    private static final boolean USE_TRANSFORMER_CACHE = false;

    public ModernFixMixinPlugin() {
        /* We abuse the constructor of a mixin plugin as a safe location to start modifying the classloader */
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
        ModernFixResourceFinder.init();
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

    @Override
    public void onLoad(String mixinPackage) {
        try {
            config = ModernFixEarlyConfig.load(new File("./config/modernfix-mixins.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Could not load configuration file for ModernFix", e);
        }

        this.logger.info("Loaded configuration file for ModernFix: {} options available, {} override(s) found",
                config.getOptionCount(), config.getOptionOverrideCount());
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE_ROOT)) {
            this.logger.error("Expected mixin '{}' to start with package root '{}', treating as foreign and " +
                    "disabling!", mixinClassName, MIXIN_PACKAGE_ROOT);

            return false;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE_ROOT.length());
        Option option = config.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            this.logger.error("No rules matched mixin '{}', treating as foreign and disabling!", mixin);

            return false;
        }

        if (option.isOverridden()) {
            String source = "[unknown]";

            if (option.isUserDefined()) {
                source = "user configuration";
            } else if (option.isModDefined()) {
                source = "mods [" + String.join(", ", option.getDefiningMods()) + "]";
            }

            if (option.isEnabled()) {
                this.logger.warn("Force-enabling mixin '{}' as rule '{}' (added by {}) enables it", mixin,
                        option.getName(), source);
            } else {
                this.logger.warn("Force-disabling mixin '{}' as rule '{}' (added by {}) disables it and children", mixin,
                        option.getName(), source);
            }
        }

        return option.isEnabled();
    }
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}