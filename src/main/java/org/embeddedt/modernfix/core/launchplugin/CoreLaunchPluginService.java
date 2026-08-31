package org.embeddedt.modernfix.core.launchplugin;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.embeddedt.modernfix.core.launchplugin.transformer.CapabilityProviderTransformer;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class CoreLaunchPluginService implements ILaunchPluginService {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModernFixLaunchPlugin");

    public static void install() {
        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>)pluginsField.get(launchPluginHandler);
            var service = new CoreLaunchPluginService();
            var newMap = new LinkedHashMap<>(plugins);
            newMap.put(service.name(), service);
            pluginsField.set(launchPluginHandler, newMap);
        } catch(Exception e) {
            LOGGER.error("Error installing launch plugin service", e);
        }
    }

    @Override
    public String name() {
        return "modernfix";
    }

    private static final EnumSet<Phase> GO = EnumSet.of(Phase.AFTER);
    private static final EnumSet<Phase> NOGO = EnumSet.noneOf(Phase.class);

    private static final Map<String, Transformer> TRANSFORMERS = Map.of(
            "net.minecraftforge.common.capabilities.CapabilityProvider", new CapabilityProviderTransformer()
    );

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return !isEmpty && TRANSFORMERS.containsKey(classType.getClassName()) ? GO : NOGO;
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (classNode == null) {
            return 0;
        }
        var transformer = TRANSFORMERS.get(classType.getClassName());
        if (transformer == null) {
            return 0;
        }
        return transformer.transform(classNode);
    }

    public interface Transformer {
        int transform(ClassNode node);
    }
}
