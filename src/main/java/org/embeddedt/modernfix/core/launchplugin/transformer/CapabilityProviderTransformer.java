package org.embeddedt.modernfix.core.launchplugin.transformer;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.embeddedt.modernfix.core.launchplugin.CoreLaunchPluginService;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Injects an early-return into {@code CapabilityProvider#areCapsCompatible} that skips lazy
 * initialization when both providers are lazy, uninitialized, of the same class, and carry equal
 * {@code lazyData}. In that case the capabilities are trivially compatible and the full init can
 * be avoided.
 * @author embeddedt
 */
public class CapabilityProviderTransformer implements CoreLaunchPluginService.Transformer {
    private static final String OWNER = "net/minecraftforge/common/capabilities/CapabilityProvider";
    private static final String COMPOUND_TAG = "net/minecraft/nbt/CompoundTag";
    private static final String HELPER_NAME = "mfix$skipLazyInit";
    private static final String HELPER_DESC = "(L" + OWNER + ";L" + OWNER + ";)Z";

    @Override
    public int transform(ClassNode node) {
        String targetDesc = "(L" + OWNER + ";)Z";
        for (MethodNode method : node.methods) {
            if (method.name.equals("areCapsCompatible") && method.desc.equals(targetDesc)) {
                injectCall(method);
                node.methods.add(buildHelper());
                break;
            }
        }
        return ILaunchPluginService.ComputeFlags.COMPUTE_FRAMES;
    }

    private MethodNode buildHelper() {
        MethodNode mn = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                HELPER_NAME, HELPER_DESC, null, null);
        InsnList il = mn.instructions;
        LabelNode returnFalse = new LabelNode();

        // if (!self.isLazy) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "isLazy", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, returnFalse));

        // if (self.initialized) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "initialized", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, returnFalse));

        // if (!other.isLazy) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "isLazy", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFEQ, returnFalse));

        // if (other.initialized) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "initialized", "Z"));
        il.add(new JumpInsnNode(Opcodes.IFNE, returnFalse));

        // if (other.getClass() != self.getClass()) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false));
        il.add(new JumpInsnNode(Opcodes.IF_ACMPNE, returnFalse));

        // if (!Objects.equals(self.lazyData, other.lazyData)) goto returnFalse
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "lazyData", "L" + COMPOUND_TAG + ";"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "lazyData", "L" + COMPOUND_TAG + ";"));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, returnFalse));

        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));

        il.add(returnFalse);
        il.add(new InsnNode(Opcodes.ICONST_0));
        il.add(new InsnNode(Opcodes.IRETURN));

        mn.maxLocals = 2;
        mn.maxStack = 2;
        return mn;
    }

    private void injectCall(MethodNode method) {
        InsnList il = new InsnList();
        LabelNode skip = new LabelNode();

        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.ALOAD, 1));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, HELPER_NAME, HELPER_DESC, false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        il.add(new InsnNode(Opcodes.ICONST_1));
        il.add(new InsnNode(Opcodes.IRETURN));
        il.add(skip);

        method.instructions.insert(il);
    }
}
