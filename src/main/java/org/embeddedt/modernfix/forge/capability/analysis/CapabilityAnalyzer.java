package org.embeddedt.modernfix.forge.capability.analysis;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyzes {@code getCapability} bytecode to determine which capabilities a provider handles.
 */
public class CapabilityAnalyzer {

    private static final ConcurrentHashMap<Class<?>, CapabilityAnalysisResult> cache = new ConcurrentHashMap<>();

    private static final String CAPABILITY_INTERNAL = "net/minecraftforge/common/capabilities/Capability";
    private static final String CAPABILITY_DESC = "Lnet/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY_OPTIONAL_INTERNAL = "net/minecraftforge/common/util/LazyOptional";
    private static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";
    private static final String LAZY_OPTIONAL_DESC = "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String GET_CAPABILITY_DESC = "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC;
    private static final String ICAP_PROVIDER_INTERNAL = "net/minecraftforge/common/capabilities/ICapabilityProvider";

    public static CapabilityAnalysisResult analyze(Class<? extends ICapabilityProvider> clazz) {
        CapabilityAnalysisResult result = cache.get(clazz);
        if (result != null) return result;
        if (!ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_capabilities.bytecode_analysis.CapabilityAnalyzer")) {
            result = new CapabilityAnalysisResult.Indeterminate("bytecode analysis disabled");
        } else {
            result = doAnalyzeSafe(clazz);
        }
        CapabilityAnalysisResult existing = cache.putIfAbsent(clazz, result);
        return existing != null ? existing : result;
    }

    private static CapabilityAnalysisResult doAnalyzeSafe(Class<?> clazz) {
        try {
            return doAnalyze(clazz);
        } catch (Exception e) {
            ModernFix.LOGGER.debug("Capability analysis failed for {}: {}", clazz.getName(), e.getMessage());
            return new CapabilityAnalysisResult.Indeterminate("analysis exception: " + e.getMessage());
        }
    }

    private static CapabilityAnalysisResult doAnalyze(Class<?> clazz) throws IOException, AnalyzerException {
        // Find the class that actually declares getCapability via reflection
        Class<?> declaringClass;
        try {
            declaringClass = clazz.getMethod("getCapability", Capability.class, Direction.class).getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        if (declaringClass.getName().replace('.', '/').equals(ICAP_PROVIDER_INTERNAL)) {
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        String declaringClassName = declaringClass.getName().replace('.', '/');
        ClassNode declaringClassNode = readClass(declaringClass);
        if (declaringClassNode == null) {
            return new CapabilityAnalysisResult.Indeterminate("cannot read bytecode for " + declaringClass.getName());
        }

        MethodNode getCapMethod = findGetCapabilityMethod(declaringClassNode);
        if (getCapMethod == null) {
            return new CapabilityAnalysisResult.Indeterminate("method not found in bytecode for " + declaringClass.getName());
        }

        // Run the source analysis
        CapabilitySourceInterpreter interpreter = new CapabilitySourceInterpreter();
        Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
        Frame<SourceValue>[] frames = analyzer.analyze(declaringClassName, getCapMethod);

        // Build if-guard map: maps instruction indices to CapabilityRef for guarded regions
        List<GuardRegion> guardRegions = findGuardRegions(getCapMethod, frames);

        // Classify each ARETURN
        InsnList instructions = getCapMethod.instructions;
        Set<CapabilityRef> knownCaps = new HashSet<>();
        boolean hasIndeterminate = false;
        String indeterminateReason = null;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);
            if (insn.getOpcode() != Opcodes.ARETURN) continue;

            Frame<SourceValue> frame = frames[i];
            if (frame == null) continue; // dead code

            SourceValue topOfStack = frame.getStack(frame.getStackSize() - 1);

            ReturnClassification classification = classifyReturnSources(
                    topOfStack, interpreter, i, guardRegions, clazz, instructions);

            if (classification instanceof ReturnClassification.Known known) {
                knownCaps.addAll(known.caps);
            } else if (classification instanceof ReturnClassification.Unknown unknown) {
                hasIndeterminate = true;
                indeterminateReason = unknown.reason;
            }
            // Empty: skip
        }

        if (hasIndeterminate) {
            CapabilityAnalysisResult result = new CapabilityAnalysisResult.Indeterminate(indeterminateReason);
            ModernFix.LOGGER.debug("Capability analysis for {}: {}", clazz.getName(), result);
            return result;
        }

        if (knownCaps.isEmpty()) {
            ModernFix.LOGGER.debug("Capability analysis for {}: AlwaysEmpty", clazz.getName());
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        CapabilityAnalysisResult result = new CapabilityAnalysisResult.KnownCapabilities(Set.copyOf(knownCaps));
        ModernFix.LOGGER.debug("Capability analysis for {}: {}", clazz.getName(), result);
        return result;
    }

    private static ReturnClassification classifyReturnSources(
            SourceValue topOfStack,
            CapabilitySourceInterpreter interpreter,
            int returnIndex,
            List<GuardRegion> guardRegions,
            Class<?> originalClass,
            InsnList instructions) {

        Set<CapabilityRef> caps = new HashSet<>();
        List<AbstractInsnNode> unknownSources = new ArrayList<>();
        String unknownReason = null;

        for (AbstractInsnNode source : topOfStack.insns) {
            ReturnClassification sourceClassification = classifySingleSource(
                    source, interpreter, originalClass);

            if (sourceClassification instanceof ReturnClassification.Unknown unknown) {
                unknownSources.add(source);
                unknownReason = unknown.reason;
            } else if (sourceClassification instanceof ReturnClassification.Known known) {
                caps.addAll(known.caps);
            }
        }

        // If any source is unknown, try the guard region fallback before giving up
        if (!unknownSources.isEmpty()) {
            boolean allResolved = false;

            // Check if the return itself is in a guard region
            for (GuardRegion guard : guardRegions) {
                if (returnIndex > guard.guardIndex && returnIndex < guard.targetIndex) {
                    caps.add(guard.capabilityRef);
                    allResolved = true;
                    break;
                }
            }

            // Also check if each unknown source instruction is inside a guard region.
            // This handles ternary patterns where both branches merge into a single
            // ARETURN after the guard, but the unknown value was produced inside it.
            if (!allResolved) {
                allResolved = true;
                for (AbstractInsnNode unknownSource : unknownSources) {
                    int sourceIndex = instructions.indexOf(unknownSource);
                    boolean resolved = false;
                    for (GuardRegion guard : guardRegions) {
                        if (sourceIndex > guard.guardIndex && sourceIndex < guard.targetIndex) {
                            caps.add(guard.capabilityRef);
                            resolved = true;
                            break;
                        }
                    }
                    if (!resolved) {
                        allResolved = false;
                        break;
                    }
                }
            }

            if (!allResolved) {
                return new ReturnClassification.Unknown(unknownReason);
            }
        }

        if (caps.isEmpty()) {
            return ReturnClassification.EMPTY;
        }
        return new ReturnClassification.Known(caps);
    }

    private static ReturnClassification classifySingleSource(
            AbstractInsnNode source,
            CapabilitySourceInterpreter interpreter,
            Class<?> originalClass) {

        if (source instanceof MethodInsnNode methodInsn) {
            // Case: Capability.orEmpty(...)
            if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && methodInsn.owner.equals(CAPABILITY_INTERNAL)
                    && methodInsn.name.equals("orEmpty")) {
                return classifyOrEmptyCall(methodInsn, interpreter);
            }

            // Case: LazyOptional.empty()
            if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                    && methodInsn.owner.equals(LAZY_OPTIONAL_INTERNAL)
                    && methodInsn.name.equals("empty")) {
                return ReturnClassification.EMPTY;
            }

            // Case: LazyOptional.of(null)
            if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                    && methodInsn.owner.equals(LAZY_OPTIONAL_INTERNAL)
                    && methodInsn.name.equals("of")) {
                List<SourceValue> args = interpreter.getCallArguments(methodInsn);
                if (!args.isEmpty()) {
                    SourceValue arg = args.get(0);
                    if (arg.insns.size() == 1
                            && arg.insns.iterator().next().getOpcode() == Opcodes.ACONST_NULL) {
                        return ReturnClassification.EMPTY;
                    }
                }
            }

            // Case: super.getCapability(...)
            if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL
                    && methodInsn.name.equals("getCapability")
                    && methodInsn.desc.equals(GET_CAPABILITY_DESC)) {
                return classifySuperDelegation(originalClass);
            }
        }

        if (source instanceof MethodInsnNode m) {
            return new ReturnClassification.Unknown("unclassified method: " + m.owner + "." + m.name + m.desc);
        }
        return new ReturnClassification.Unknown("unclassified source: " + source.getClass().getSimpleName()
                + " opcode=" + source.getOpcode());
    }

    private static ReturnClassification classifyOrEmptyCall(
            MethodInsnNode methodInsn, CapabilitySourceInterpreter interpreter) {
        List<SourceValue> args = interpreter.getCallArguments(methodInsn);
        if (args.isEmpty()) {
            return new ReturnClassification.Unknown("orEmpty call with no recorded arguments");
        }

        // arg 0 is the receiver (the Capability instance)
        SourceValue receiver = args.get(0);
        for (AbstractInsnNode recvSource : receiver.insns) {
            if (recvSource instanceof FieldInsnNode fieldInsn
                    && fieldInsn.getOpcode() == Opcodes.GETSTATIC
                    && fieldInsn.desc.equals(CAPABILITY_DESC)) {
                return new ReturnClassification.Known(
                        Set.of(new CapabilityRef(fieldInsn.owner, fieldInsn.name)));
            }
        }

        return new ReturnClassification.Unknown("orEmpty receiver is not a static Capability field");
    }

    private static ReturnClassification classifySuperDelegation(Class<?> originalClass) {
        Class<?> superClass = originalClass.getSuperclass();
        if (superClass == null || superClass == Object.class) {
            return ReturnClassification.EMPTY;
        }

        @SuppressWarnings("unchecked")
        Class<? extends ICapabilityProvider> superProvider =
                (Class<? extends ICapabilityProvider>) superClass;
        CapabilityAnalysisResult superResult = analyze(superProvider);
        if (superResult instanceof CapabilityAnalysisResult.KnownCapabilities known) {
            return new ReturnClassification.Known(known.capabilities());
        } else if (superResult instanceof CapabilityAnalysisResult.AlwaysEmpty) {
            return ReturnClassification.EMPTY;
        } else if (superResult instanceof CapabilityAnalysisResult.Indeterminate ind) {
            return new ReturnClassification.Unknown("super delegation: " + ind.reason());
        }
        return ReturnClassification.EMPTY;
    }

    private static List<GuardRegion> findGuardRegions(MethodNode method, Frame<SourceValue>[] frames) {
        List<GuardRegion> regions = new ArrayList<>();
        InsnList instructions = method.instructions;

        for (int i = 0; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);
            int opcode = insn.getOpcode();
            if (opcode != Opcodes.IF_ACMPEQ && opcode != Opcodes.IF_ACMPNE) continue;

            Frame<SourceValue> frame = frames[i];
            if (frame == null || frame.getStackSize() < 2) continue;

            SourceValue val1 = frame.getStack(frame.getStackSize() - 2);
            SourceValue val2 = frame.getStack(frame.getStackSize() - 1);

            // Check if one traces to ALOAD 1 (cap parameter) and other to GETSTATIC Capability
            CapabilityRef capRef = findCapRef(val1);
            if (capRef == null) capRef = findCapRef(val2);
            boolean hasParamLoad = isCapParamLoad(val1) || isCapParamLoad(val2);

            if (capRef != null && hasParamLoad) {
                JumpInsnNode jumpInsn = (JumpInsnNode) insn;
                int targetIndex = instructions.indexOf(jumpInsn.label);

                if (opcode == Opcodes.IF_ACMPNE) {
                    // if (cap != KNOWN_CAP) goto target -> the code between insn and target is for KNOWN_CAP
                    regions.add(new GuardRegion(capRef, i, targetIndex));
                } else {
                    // IF_ACMPEQ: if (cap == KNOWN_CAP) goto target -> the target is the guarded code
                    // Find the end of the guarded region (next label or return)
                    // For simplicity, mark from target to the next unconditional branch or return
                    // TODO: that simplification may have edge cases
                    int endIndex = findGuardedRegionEnd(instructions, targetIndex);
                    regions.add(new GuardRegion(capRef, targetIndex, endIndex));
                }
            }
        }

        // Extend guard regions for forward jumps that land beyond the guard target.
        // This handles compound conditions like (cap == X && cond) compiled as:
        //   if_acmpne L_false   // guard: [here, L_false)
        //   evaluate cond
        //   ifeq L_true         // forward jump beyond L_false
        //   L_false: empty(); areturn
        //   L_true: cast(); areturn   // <-- also guarded by cap == X
        int baseSize = regions.size();
        for (int r = 0; r < baseSize; r++) {
            GuardRegion guard = regions.get(r);
            for (int j = guard.guardIndex + 1; j < guard.targetIndex; j++) {
                AbstractInsnNode inner = instructions.get(j);
                if (inner instanceof JumpInsnNode jump) {
                    int jumpTarget = instructions.indexOf(jump.label);
                    if (jumpTarget >= guard.targetIndex) {
                        int endIndex = findGuardedRegionEnd(instructions, jumpTarget);
                        regions.add(new GuardRegion(guard.capabilityRef, jumpTarget, endIndex));
                    }
                }
            }
        }

        return regions;
    }

    private static int findGuardedRegionEnd(InsnList instructions, int startIndex) {
        for (int i = startIndex; i < instructions.size(); i++) {
            AbstractInsnNode insn = instructions.get(i);
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.GOTO || opcode == Opcodes.ARETURN
                    || opcode == Opcodes.RETURN || opcode == Opcodes.ATHROW) {
                return i + 1;
            }
        }
        return instructions.size();
    }

    private static CapabilityRef findCapRef(SourceValue sv) {
        CapabilityRef ref = null;
        for (AbstractInsnNode src : sv.insns) {
            if (src instanceof FieldInsnNode fieldInsn
                    && fieldInsn.getOpcode() == Opcodes.GETSTATIC
                    && fieldInsn.desc.equals(CAPABILITY_DESC)) {
                if (ref == null) {
                    ref = new CapabilityRef(fieldInsn.owner, fieldInsn.name);
                } else if (!ref.owner().equals(fieldInsn.owner) || !ref.fieldName().equals(fieldInsn.name)) {
                    return null; // ambiguous: multiple different capability fields
                }
            } else {
                return null; // non-capability source
            }
        }
        return ref;
    }

    private static boolean isCapParamLoad(SourceValue sv) {
        if (sv.insns.isEmpty()) return false;
        for (AbstractInsnNode src : sv.insns) {
            if (!(src instanceof VarInsnNode varInsn)
                    || varInsn.getOpcode() != Opcodes.ALOAD
                    || varInsn.var != 1) {
                return false;
            }
        }
        return true;
    }

    private static MethodNode findGetCapabilityMethod(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("getCapability") && method.desc.equals(GET_CAPABILITY_DESC)) {
                return method;
            }
        }
        return null;
    }

    private static ClassNode readClass(Class<?> clazz) throws IOException {
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            ClassReader reader = new ClassReader(is);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            return node;
        }
    }

    private sealed interface ReturnClassification {
        ReturnClassification EMPTY = new Empty();

        record Known(Set<CapabilityRef> caps) implements ReturnClassification {}
        record Empty() implements ReturnClassification {}
        record Unknown(String reason) implements ReturnClassification {}
    }

    private record GuardRegion(CapabilityRef capabilityRef, int guardIndex, int targetIndex) {}
}
