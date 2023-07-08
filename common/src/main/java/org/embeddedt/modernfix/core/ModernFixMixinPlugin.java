package org.embeddedt.modernfix.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.embeddedt.modernfix.core.config.Option;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.*;

public class ModernFixMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "org.embeddedt.modernfix.mixin.";

    public final Logger logger = LogManager.getLogger("ModernFix");
    public ModernFixEarlyConfig config = null;
    public static ModernFixMixinPlugin instance;

    public ModernFixMixinPlugin() {
        /* invoke early to ensure it gets read on one thread */
        ModernFixPlatformHooks.getCustomModOptions();
        boolean firstConfig = instance == null;
        if(firstConfig) {
            instance = this;
            try {
                config = ModernFixEarlyConfig.load(new File("./config/modernfix-mixins.properties"));
            } catch (Exception e) {
                throw new RuntimeException("Could not load configuration file for ModernFix", e);
            }

            this.logger.info("Loaded configuration file for ModernFix: {} options available, {} override(s) found",
                    config.getOptionCount(), config.getOptionOverrideCount());

            config.getOptionMap().values().forEach(option -> {
                if (option.isOverridden()) {
                    String source = "[unknown]";

                    if (option.isUserDefined()) {
                        source = "user configuration";
                    } else if (option.isModDefined()) {
                        source = "mods [" + String.join(", ", option.getDefiningMods()) + "]";
                    }
                    this.logger.warn("Option '{}' overriden (by {}) to '{}'", option.getName(),
                           source, option.isEnabled());
                }
            });


            if(ModernFixEarlyConfig.OPTIFINE_PRESENT)
                this.logger.fatal("OptiFine detected. Use of ModernFix with OptiFine is not supported due to its impact on launch time and breakage of Forge features.");

            try {
                Class.forName("sun.misc.Unsafe").getDeclaredMethod("defineAnonymousClass", Class.class, byte[].class, Object[].class);
            } catch(ReflectiveOperationException | NullPointerException e) {
                this.logger.info("Applying Nashorn fix");
                Properties properties = System.getProperties();
                properties.setProperty("nashorn.args", properties.getProperty("nashorn.args", "") + " --anonymous-classes=false");
            }

            /* We abuse the constructor of a mixin plugin as a safe location to start modifying the classloader */
            ModernFixPlatformHooks.injectPlatformSpecificHacks();
        }
    }


    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        mixinClassName = ModernFixEarlyConfig.sanitize(mixinClassName);
        if (!mixinClassName.startsWith(MIXIN_PACKAGE_ROOT)) {
            this.logger.error("Expected mixin '{}' to start with package root '{}', treating as foreign and " +
                    "disabling!", mixinClassName, MIXIN_PACKAGE_ROOT);

            return false;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE_ROOT.length());
        if(!instance.isOptionEnabled(mixin))
            return false;
        String disabledBecauseMod = instance.config.getPermanentlyDisabledMixins().get(mixin);
        return disabledBecauseMod == null;
    }

    public boolean isOptionEnabled(String mixin) {
        Option option = instance.config.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            this.logger.error("No rules matched mixin '{}', treating as foreign and disabling!", mixin);

            return false;
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
        ModernFixPlatformHooks.applyASMTransformers(mixinClassName, targetClass);
    }
}