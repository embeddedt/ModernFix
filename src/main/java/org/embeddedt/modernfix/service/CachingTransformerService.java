package org.embeddedt.modernfix.service;

import cpw.mods.modlauncher.*;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumSet;

public class CachingTransformerService implements ILaunchPluginService {
    @Override
    public String name() {
        return "modernfixcache";
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Path[] specialPaths) {
        /* Swap the transformer for ours */
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if(!(loader instanceof TransformingClassLoader)) {
            throw new IllegalStateException("Expected a TransformingClassLoader");
        }
        try {
            Field classTransformerField = TransformingClassLoader.class.getDeclaredField("classTransformer");
            classTransformerField.setAccessible(true);
            ClassTransformer t = (ClassTransformer)classTransformerField.get(loader);
            TransformStore store = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "transformers");
            LaunchPluginHandler pluginHandler = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "pluginHandler");
            TransformerAuditTrail trail = ObfuscationReflectionHelper.getPrivateValue(ClassTransformer.class, t, "auditTrail");
            classTransformerField.set(loader, new ModernFixCachingClassTransformer(store, pluginHandler, (TransformingClassLoader)loader, trail));
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    private static final EnumSet<Phase> NEVER = EnumSet.noneOf(Phase.class);

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return NEVER;
    }
}
