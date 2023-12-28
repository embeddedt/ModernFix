package org.embeddedt.modernfix.forge.classloading;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModValidator;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.commons.lang3.tuple.Pair;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.util.CommonModUtil;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ATInjector {
    public static void injectModATs() {
        CommonModUtil.runWithoutCrash(() -> {
            ModValidator validator = ObfuscationReflectionHelper.getPrivateValue(FMLLoader.class, null, "modValidator");
            List<ModFile> modFiles = ObfuscationReflectionHelper.getPrivateValue(ModValidator.class, validator, "candidateMods");
            List<Pair<ModFile, Path>> list = modFiles.stream()
                    .filter(file -> file.getAccessTransformer().isPresent())
                    .map(file -> Pair.of(file, file.getAccessTransformer().get()))
                    .collect(Collectors.toList());
            if(list.size() > 0) {
                ModernFixMixinPlugin.instance.logger.warn("Applying ATs from {} mods despite being in errored state, this might cause a crash!", list.size());
                for(var pair : list) {
                    try {
                        FMLLoader.addAccessTransformer(pair.getRight(), pair.getLeft());
                    } catch(RuntimeException e) {
                        ModernFixMixinPlugin.instance.logger.error("Exception occured applying AT from {}", pair.getLeft().getFileName(), e);
                    }
                }
            }

            // inject into Launcher.INSTANCE.launchPlugins and wrap the mixin plugin, so that mixin transformations
            // are not applied
            try {
                Launcher launcher = Launcher.INSTANCE;
                Field launchPlugins = Launcher.class.getDeclaredField("launchPlugins");
                launchPlugins.setAccessible(true);

                LaunchPluginHandler handler = (LaunchPluginHandler) launchPlugins.get(launcher);
                Field plugins = LaunchPluginHandler.class.getDeclaredField("plugins");
                plugins.setAccessible(true);

                //noinspection unchecked
                Map<String, ILaunchPluginService> map = (Map<String, ILaunchPluginService>) plugins.get(handler);
                Map<String, ILaunchPluginService> newMap = new HashMap<>(map);
                NonTransformingLaunchPluginService.class.getName(); // trigger classloading, just to be safe
                newMap.replaceAll((name, plugin) -> {
                    if(plugin.getClass().getName().startsWith("org.spongepowered.asm.launch.MixinLaunchPlugin")) {
                        ModernFixMixinPlugin.instance.logger.warn("Disabling plugin '{}': {}", name, plugin.getClass().getName());
                        return new NonTransformingLaunchPluginService(plugin);
                    } else {
                        return plugin;
                    }
                });
                plugins.set(handler, newMap);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }, "applying mod ATs in errored state");
    }

    static class NonTransformingLaunchPluginService implements ILaunchPluginService {

        private final ILaunchPluginService delegate;

        NonTransformingLaunchPluginService(ILaunchPluginService delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        private static final EnumSet<Phase> NEVER = EnumSet.noneOf(Phase.class);

        @Override
        public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
            return NEVER;
        }

        @Override
        public void offerResource(Path resource, String name) {
            delegate.offerResource(resource, name);
        }

        @Override
        public void addResources(List<SecureJar> resources) {
            delegate.addResources(resources);
        }

        @Override
        public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
            delegate.initializeLaunch(transformerLoader, specialPaths);
        }

        @Override
        public <T> T getExtension() {
            return delegate.getExtension();
        }

        @Override
        public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
            delegate.customAuditConsumer(className, auditDataAcceptor);
        }
    }
}
