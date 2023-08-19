package org.embeddedt.modernfix.core;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.embeddedt.modernfix.core.config.Option;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.world.ThreadDumper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.File;
import java.util.*;

public class ModernFixMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "org.embeddedt.modernfix.mixin.";

    public final Logger logger = LogManager.getLogger("ModernFix");
    public ModernFixEarlyConfig config = null;
    public static ModernFixMixinPlugin instance;

    public ModernFixMixinPlugin() {
        /* invoke early to ensure it gets read on one thread */
        ModernFixPlatformHooks.INSTANCE.getCustomModOptions();
        boolean firstConfig = instance == null;
        if(firstConfig) {
            instance = this;
            try {
                config = ModernFixEarlyConfig.load(new File("./config/modernfix-mixins.properties"));
            } catch (Exception e) {
                throw new RuntimeException("Could not load configuration file for ModernFix", e);
            }

            this.logger.info("Loaded configuration file for ModernFix {}: {} options available, {} override(s) found",
                    ModernFixPlatformHooks.INSTANCE.getVersionString(), config.getOptionCount(), config.getOptionOverrideCount());

            config.getOptionMap().values().forEach(option -> {
                if (option.isOverridden()) {
                    String source = "[unknown]";

                    if (option.isUserDefined()) {
                        source = "user configuration";
                    } else if (!ModernFixPlatformHooks.INSTANCE.isEarlyLoadingNormally()) {
                        source = "load error";
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
            ModernFixPlatformHooks.INSTANCE.injectPlatformSpecificHacks();

            if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spam_thread_dump.ThreadDumper")) {
                // run once to trigger classloading
                ThreadDumper.obtainThreadDump();
                Thread t = new Thread() {
                    public void run() {
                        while(true) {
                            try {
                                Thread.sleep(60000);
                                logger.error("------ DEBUG THREAD DUMP (occurs every 60 seconds) ------");
                                logger.error(ThreadDumper.obtainThreadDump());
                            } catch(InterruptedException | RuntimeException e) {}
                        }
                    }
                };
                t.setDaemon(true);
                t.start();
            }
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
            String msg = "No rules matched mixin '{}', treating as foreign and disabling!";
            if(ModernFixPlatformHooks.INSTANCE.isDevEnv())
                this.logger.error(msg, mixin);
            else
                this.logger.debug(msg, mixin);

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
        if(mixinClassName.equals("org.embeddedt.modernfix.common.mixin.perf.reduce_blockstate_cache_rebuilds.BlockStateBaseMixin")) {
            try {
                applyBlockStateCacheScan(targetClass);
            } catch(RuntimeException e) {
                ModernFixMixinPlugin.instance.logger.error("Applying blockstate cache ASM patch failed", e);
            }
        }
        ModernFixPlatformHooks.INSTANCE.applyASMTransformers(mixinClassName, targetClass);
    }

    private void applyBlockStateCacheScan(ClassNode targetClass) {
        Set<String> initCacheMethodNames = ImmutableSet.of("m_60611_", "func_215692_c", "method_26200", "initCache");
        Set<String> whitelistedInjections = ImmutableSet.of(
                "getFluidState", "method_26227", "m_60819_", "func_204520_s"
        );
        Map<String, MethodNode> injectorMethodNames = new HashMap<>();
        Map<String, MethodNode> allMethods = new HashMap<>();
        Map<String, String> injectorMixinSource = new HashMap<>();
        String descriptor = Type.getDescriptor(MixinMerged.class);
        for(MethodNode m : targetClass.methods) {
            if((m.access & Opcodes.ACC_STATIC) != 0)
                continue;
            allMethods.put(m.name, m);
            Set<AnnotationNode> seenNodes = new HashSet<>();
            if(m.invisibleAnnotations != null) {
                for(AnnotationNode ann : m.invisibleAnnotations) {
                    if(ann.desc.equals(descriptor)) {
                        seenNodes.add(ann);
                    }
                }
            }
            if(m.visibleAnnotations != null) {
                for(AnnotationNode ann : m.visibleAnnotations) {
                    if(ann.desc.equals(descriptor)) {
                        seenNodes.add(ann);
                    }
                }
            }
            if(seenNodes.size() > 0) {
                injectorMethodNames.put(m.name, m);
                for(AnnotationNode node : seenNodes) {
                    for(int i = 0; i < node.values.size(); i += 2) {
                        if(Objects.equals(node.values.get(i), "mixin")) {
                            injectorMixinSource.put(m.name, (String)node.values.get(i + 1));
                            break;
                        }
                    }
                }
            }
        }
        Set<String> cacheCalledInjectors = new HashSet<>();
        // Search for initCache in the class
        for(MethodNode m : targetClass.methods) {
            if((m.access & Opcodes.ACC_STATIC) != 0)
                continue;
            if(initCacheMethodNames.contains(m.name)) {
                // This is it. Check for any injectors it calls
                for(AbstractInsnNode n : m.instructions) {
                    if(n instanceof MethodInsnNode) {
                        MethodInsnNode invoke = (MethodInsnNode)n;
                        if(((MethodInsnNode)n).owner.equals(targetClass.name) && injectorMethodNames.containsKey(((MethodInsnNode)n).name)) {
                            cacheCalledInjectors.add(invoke.name);
                        }
                    }
                }
                break;
            }
        }
        Set<String> accessedFieldNames = new HashSet<>();

        // Make a map of all injected methods called by initCache
        Map<String, MethodNode> writingMethods = new HashMap<>(injectorMethodNames);
        writingMethods.keySet().retainAll(cacheCalledInjectors);

        // Recursively check the injected methods for any methods they may call
        int previousSize = 0;
        Set<String> checkedCalls = new HashSet<>();
        while(writingMethods.size() > previousSize) {
            previousSize = writingMethods.size();
            List<String> keysToCheck = new ArrayList<>(writingMethods.keySet());
            for(String name : keysToCheck) {
                if(!checkedCalls.add(name))
                    continue;
                for(AbstractInsnNode n : writingMethods.get(name).instructions) {
                    if(n instanceof MethodInsnNode) {
                        MethodInsnNode invokeNode = (MethodInsnNode)n;
                        if(invokeNode.owner.equals(targetClass.name)) {
                            MethodNode theMethod = allMethods.get(invokeNode.name);
                            if(theMethod != null)
                                writingMethods.put(invokeNode.name, theMethod);
                        }
                    }
                }
            }
        }

        // We now know all methods that have been injected into initCache, and their callers. See what fields they write to
        writingMethods.forEach((name, method) -> {
            if(cacheCalledInjectors.contains(name)) {
                for(AbstractInsnNode n : method.instructions) {
                    if(n instanceof FieldInsnNode) {
                        FieldInsnNode fieldAcc = (FieldInsnNode)n;
                        if(fieldAcc.getOpcode() == Opcodes.PUTFIELD && fieldAcc.owner.equals(targetClass.name)) {
                            accessedFieldNames.add(fieldAcc.name);
                        }
                    }
                }
            }
        });
        // Lastly, scan all injected methods and see if they retrieve from the field. If so, inject a generateCache
        // call at the start.
        injectorMethodNames.forEach((name, method) -> {
            // skip whitelisted injectors, and injectors called by initCache itself (to prevent recursion)
            if(whitelistedInjections.contains(name) || cacheCalledInjectors.contains(name))
                return;
            boolean needInjection = false;
            for(AbstractInsnNode n : method.instructions) {
                if(n instanceof FieldInsnNode) {
                    FieldInsnNode fieldAcc = (FieldInsnNode)n;
                    if(fieldAcc.getOpcode() == Opcodes.GETFIELD && accessedFieldNames.contains(fieldAcc.name)) {
                        needInjection = true;
                        break;
                    }
                }
            }
            if(needInjection) {
                ModernFixMixinPlugin.instance.logger.info("Injecting BlockStateBase cache population hook into {} from {}",
                        name, injectorMixinSource.getOrDefault(name, "[unknown mixin]"));
                // inject this.mfix$generateCache() at method head
                InsnList injection = new InsnList();
                injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injection.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, targetClass.name, "mfix$generateCache", "()V"));
                method.instructions.insert(injection);
            }
        });
    }
}