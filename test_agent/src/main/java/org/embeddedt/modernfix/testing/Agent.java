package org.embeddedt.modernfix.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ListIterator;

public class Agent {
    /**
     * Simple agent that transforms Fabric Loader to never mark game JARs as system libraries.
     *
     * Ugly, but usable workaround for <a href="https://github.com/FabricMC/fabric-loader/issues/817">issue #817</a>
     * on the Loader bug tracker.
     */
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                if(className.equals("net/fabricmc/loader/impl/game/LibClassifier")) {
                    ClassNode node = new ClassNode();
                    ClassReader reader = new ClassReader(classfileBuffer);
                    reader.accept(node, 0);
                    for(MethodNode m : node.methods) {
                        if(m.name.equals("<init>")) {
                            ListIterator<AbstractInsnNode> iter = m.instructions.iterator();
                            int addMatches = 0;
                            while(iter.hasNext()) {
                                AbstractInsnNode n = iter.next();
                                if(n instanceof MethodInsnNode) {
                                    MethodInsnNode invokeNode = (MethodInsnNode)n;
                                    if(invokeNode.name.equals("add") && invokeNode.owner.equals("java/util/Set") && invokeNode.desc.equals("(Ljava/lang/Object;)Z")) {
                                        addMatches++;
                                        if(addMatches == 2) {
                                            iter.set(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/embeddedt/modernfix/testing/AgentHooks", "addLibraryWithCheck", "(Ljava/util/Set;Ljava/lang/Object;)Z", false));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    byte[] finalArray = writer.toByteArray();
                    //dumpDebugClass(className, finalArray);
                    return finalArray;
                }
                return classfileBuffer;
            }
        });
    }
}
