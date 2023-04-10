package org.embeddedt.modernfix.core;

import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.core.config.ModernFixEarlyConfig;
import org.embeddedt.modernfix.core.config.Option;
import org.embeddedt.modernfix.util.DummyList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModernFixMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "org.embeddedt.modernfix.mixin.";

    private final Logger logger = LogManager.getLogger("ModernFix");
    public static ModernFixEarlyConfig config = null;
    public static ModernFixMixinPlugin instance;

    public ModernFixMixinPlugin() {
        instance = this;
        try {
            config = ModernFixEarlyConfig.load(new File("./config/modernfix-mixins.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Could not load configuration file for ModernFix", e);
        }

        this.logger.info("Loaded configuration file for ModernFix: {} options available, {} override(s) found",
                config.getOptionCount(), config.getOptionOverrideCount());

        /* https://github.com/FabricMC/Mixin/pull/99 */
        try {
            Field groupMembersField = InjectorGroupInfo.class.getDeclaredField("members");
            groupMembersField.setAccessible(true);
            Field noGroupField = InjectorGroupInfo.Map.class.getDeclaredField("NO_GROUP");
            noGroupField.setAccessible(true);
            InjectorGroupInfo noGroup = (InjectorGroupInfo)noGroupField.get(null);
            groupMembersField.set(noGroup, new DummyList<>());
        } catch(RuntimeException | ReflectiveOperationException e) {
            logger.error("Failed to patch mixin memory leak", e);
        }
    }

    @Override
    public void onLoad(String s) {

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
        return isOptionEnabled(mixin);
    }

    public boolean isOptionEnabled(String mixin) {
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
        if(mixinClassName.equals("org.embeddedt.modernfix.mixin.perf.compress_blockstate.BlockStateBaseMixin")) {
            // Delete unused fields off BlockStateBase
            Set<String> fieldsToDelete = Stream.of(
                    "field_235702_f_", // isAir
                    "field_235703_g_", // material
                    "field_235705_i_", // destroySpeed
                    "field_235706_j_", // requiresCorrectToolForDrops
                    "field_235707_k_", // canOcclude
                    "field_235708_l_", // isRedstoneConductor
                    "field_235709_m_", // isSuffocating
                    "field_235710_n_", // isViewBlocking
                    "field_235711_o_", // hasPostProcess
                    "field_235712_p_"  // emissiveRendering
            ).map(name -> ObfuscationReflectionHelper.remapName(INameMappingService.Domain.FIELD, name)).collect(Collectors.toSet());
            targetClass.fields.removeIf(field -> {
                if(fieldsToDelete.contains(field.name)) {
                    logger.info("Removing " + field.name);
                    return true;
                }
                return false;
            });
            for(MethodNode m : targetClass.methods) {
                if(m.name.equals("<init>")) {
                    ListIterator<AbstractInsnNode> iter = m.instructions.iterator();
                    while(iter.hasNext()) {
                        AbstractInsnNode node = iter.next();
                        if(node.getOpcode() == Opcodes.PUTFIELD) {
                            if(fieldsToDelete.contains(((FieldInsnNode)node).name)) {
                                iter.remove();
                            }
                        }
                    }
                }
            }
        }
    }
}