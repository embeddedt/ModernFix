package org.embeddedt.modernfix.agent;

import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.function.Function;

public class Agent {
    public static void agentmain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new EarlyTransformer());
    }

    private static class EarlyTransformer implements ClassFileTransformer {

        private static final ImmutableMap<String, Function<ClassNode, ClassNode>> TRANSFORMERS = ImmutableMap.<String, Function<ClassNode, ClassNode>>builder()
                .put("net/minecraftforge/fml/loading/moddiscovery/Scanner", EarlyTransformer::transformScanner)
                .build();

        private static ClassNode transformScanner(ClassNode input) {
            for(MethodNode method : input.methods) {
                if(method.name.equals("fileVisitor")) {
                    for(int i = 0; i < method.instructions.size(); i++) {
                        AbstractInsnNode ainsn = method.instructions.get(i);
                        if(ainsn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode minsn = (MethodInsnNode)ainsn;
                            if(minsn.name.equals("accept") && minsn.owner.equals("org/objectweb/asm/ClassReader")) {
                                method.instructions.set(minsn.getPrevious(), new LdcInsnNode(ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES));
                                return input;
                            }
                        }
                    }
                }
            }
            return input;
        }

        @Override
        public byte[] transform(ClassLoader classLoader, String s, Class<?> aClass, ProtectionDomain protectionDomain, byte[] bytes) throws IllegalClassFormatException {
            Function<ClassNode, ClassNode> func = TRANSFORMERS.get(s);
            if(func != null) {
                ClassReader reader = new ClassReader(bytes);
                ClassNode node = new ClassNode(Opcodes.ASM9);
                reader.accept(node, 0);
                node = func.apply(node);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            } else
                return bytes;
        }
    }
}
