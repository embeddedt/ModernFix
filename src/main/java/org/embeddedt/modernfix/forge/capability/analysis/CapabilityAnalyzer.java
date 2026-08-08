package org.embeddedt.modernfix.forge.capability.analysis;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyzes {@code getCapability} bytecode to determine which capabilities a provider handles.
 */
public class CapabilityAnalyzer {

    private static final Logger LOGGER = LogManager.getLogger("ModernFix");

    private static final ConcurrentHashMap<Class<?>, CapabilityAnalysisResult> cache = new ConcurrentHashMap<>();

    private static final String CAPABILITY_INTERNAL = "net/minecraftforge/common/capabilities/Capability";
    private static final String CAPABILITY_DESC = "Lnet/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY_OPTIONAL_INTERNAL = "net/minecraftforge/common/util/LazyOptional";
    private static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";
    private static final String LAZY_OPTIONAL_DESC = "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String GET_CAPABILITY_DESC = "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC;
    private static final String ICAP_PROVIDER_INTERNAL = "net/minecraftforge/common/capabilities/ICapabilityProvider";

    /**
     * Reflective, cached entry point. The feature gate is enforced by callers (see
     * {@code CapabilityProviderDispatcherGenerator}), keeping this and {@link #analyze(ClassNode, MethodNode)}
     * free of the mixin plugin.
     */
    public static CapabilityAnalysisResult analyze(Class<? extends ICapabilityProvider> clazz) {
        CapabilityAnalysisResult result = cache.get(clazz);
        if (result != null) return result;
        result = doAnalyzeSafe(clazz);
        CapabilityAnalysisResult existing = cache.putIfAbsent(clazz, result);
        return existing != null ? existing : result;
    }

    private static CapabilityAnalysisResult doAnalyzeSafe(Class<?> clazz) {
        try {
            return doAnalyze(clazz);
        } catch (Exception e) {
            LOGGER.debug("Capability analysis failed for {}: {}", clazz.getName(), e.getMessage());
            return new CapabilityAnalysisResult.Indeterminate("analysis exception: " + e.getMessage());
        }
    }

    private static CapabilityAnalysisResult doAnalyze(Class<?> clazz) throws IOException {
        Class<?> declaringClass;
        try {
            declaringClass = clazz.getMethod("getCapability", Capability.class, Direction.class).getDeclaringClass();
        } catch (NoSuchMethodException e) {
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        if (declaringClass.getName().replace('.', '/').equals(ICAP_PROVIDER_INTERNAL)) {
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        ClassNode declaringClassNode = readClass(declaringClass);
        if (declaringClassNode == null) {
            return new CapabilityAnalysisResult.Indeterminate("cannot read bytecode for " + declaringClass.getName());
        }

        MethodNode getCapMethod = findGetCapabilityMethod(declaringClassNode);
        if (getCapMethod == null) {
            return new CapabilityAnalysisResult.Indeterminate("method not found in bytecode for " + declaringClass.getName());
        }

        // Delegated calls (super.getCapability, LazyOptional-returning helpers) need bytecode for other
        // classes, so bind a class-loading resolver over the provider's loader; the pure analysis below does not.
        ClassLoader loader = clazz.getClassLoader();
        DelegationContext context = DelegationContext.root(
                name -> readClassByName(name, loader), declaringClassNode, getCapMethod);
        return analyze(declaringClassNode, getCapMethod, context);
    }

    /**
     * Pure, unit-testable analysis of a {@code getCapability} method, with no class loading, Minecraft,
     * or mixin plugin dependency. Delegated calls to other classes (including {@code super.getCapability})
     * are treated as indeterminate here since they need class loading; same-class helpers are still folded.
     * Use {@link #analyze(Class)} to resolve cross-class and super delegation.
     */
    public static CapabilityAnalysisResult analyze(ClassNode declaringClassNode, MethodNode getCapMethod) {
        // Pure resolver: only the class in hand is available; cross-class / super delegates -> Unknown.
        DelegationContext context = DelegationContext.root(
                name -> name.equals(declaringClassNode.name) ? declaringClassNode : null,
                declaringClassNode, getCapMethod);
        return analyze(declaringClassNode, getCapMethod, context);
    }

    private static CapabilityAnalysisResult analyze(ClassNode declaringClassNode, MethodNode method,
                                                    DelegationContext context) {
        // Local slot of the capability parameter (1 for instance getCapability, but delegates may be static).
        int capSlot = capSlotOf(method);
        if (capSlotReassigned(method, capSlot)) {
            capSlot = -1;
        }

        CapabilitySourceInterpreter interpreter = new CapabilitySourceInterpreter();
        CfgRecordingAnalyzer analyzer = new CfgRecordingAnalyzer(interpreter);
        Frame<SourceValue>[] frames;
        try {
            frames = analyzer.analyze(declaringClassNode.name, method);
        } catch (AnalyzerException e) {
            LOGGER.debug("Capability analysis failed for {}: {}", declaringClassNode.name, e.getMessage());
            return new CapabilityAnalysisResult.Indeterminate("analysis exception: " + e.getMessage());
        }

        // For each instruction, the set of capabilities `cap` may equal there (see CapState). An opaque
        // return is folded into that set when it is finite; an unconstrained cap forces Indeterminate.
        CapState[] capStates = computeCapStates(method, frames, analyzer.successors, capSlot);

        // Classify each ARETURN
        InsnList instructions = method.instructions;
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
                    topOfStack, interpreter, capStates[i], context, capSlot, instructions, capStates);

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
            LOGGER.debug("Capability analysis for {}: {}", declaringClassNode.name, result);
            return result;
        }

        if (knownCaps.isEmpty()) {
            LOGGER.debug("Capability analysis for {}: AlwaysEmpty", declaringClassNode.name);
            return new CapabilityAnalysisResult.AlwaysEmpty();
        }

        CapabilityAnalysisResult result = new CapabilityAnalysisResult.KnownCapabilities(Set.copyOf(knownCaps));
        LOGGER.debug("Capability analysis for {}: {}", declaringClassNode.name, result);
        return result;
    }

    /**
     * Classifies a single {@code ARETURN}. Recognized sources ({@code orEmpty}, {@code empty}, folded
     * delegates) classify themselves; an opaque source contributes the caps in {@code stateAtReturn} when
     * that set is finite, or forces Indeterminate when {@code cap} is unconstrained (TOP).
     */
    private static ReturnClassification classifyReturnSources(
            SourceValue topOfStack,
            CapabilitySourceInterpreter interpreter,
            CapState stateAtReturn,
            DelegationContext context,
            int capSlot,
            InsnList instructions,
            CapState[] capStates) {

        Set<CapabilityRef> caps = new HashSet<>();
        boolean hasUnknown = false;
        String unknownReason = null;

        boolean stateIsFinite = stateAtReturn != null && !stateAtReturn.isTop();

        for (AbstractInsnNode source : topOfStack.insns) {
            ReturnClassification sourceClassification = classifySingleSource(
                    source, interpreter, context, capSlot);

            if (sourceClassification instanceof ReturnClassification.Known known) {
                caps.addAll(known.caps);
            } else if (sourceClassification instanceof ReturnClassification.Unknown unknown) {
                CapState constraint = stateIsFinite ? stateAtReturn : stateAtSource(source, instructions, capStates);
                if (constraint != null && !constraint.isTop()) {
                    caps.addAll(constraint.caps());
                } else {
                    hasUnknown = true;
                    unknownReason = unknown.reason;
                }
            }
            // Empty: contributes nothing
        }

        if (hasUnknown) {
            return new ReturnClassification.Unknown(unknownReason);
        }
        if (caps.isEmpty()) {
            return ReturnClassification.EMPTY;
        }
        return new ReturnClassification.Known(caps);
    }

    /** The {@link CapState} entering the instruction that produced a returned value, or null if unknown. */
    private static CapState stateAtSource(AbstractInsnNode source, InsnList instructions, CapState[] capStates) {
        int index = instructions.indexOf(source);
        return (index >= 0 && index < capStates.length) ? capStates[index] : null;
    }

    private static ReturnClassification classifySingleSource(
            AbstractInsnNode source,
            CapabilitySourceInterpreter interpreter,
            DelegationContext context,
            int capSlot) {

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

            // Case: a delegated LazyOptional-returning call (super.getCapability or a helper).
            if (methodInsn.desc.endsWith(")" + LAZY_OPTIONAL_DESC)) {
                return context.resolveDelegate(methodInsn, interpreter, capSlot);
            }

            return new ReturnClassification.Unknown(
                    "unclassified method: " + methodInsn.owner + "." + methodInsn.name + methodInsn.desc);
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

        // arg 0 is the receiver: `receiver.orEmpty(cap, inst)` is non-empty exactly when cap == receiver.
        // The receiver may merge several static Capability fields (e.g. a ternary), so union them all;
        // bail to Unknown on any non-static-field source rather than drop a served cap.
        SourceValue receiver = args.get(0);
        if (receiver.insns.isEmpty()) {
            return new ReturnClassification.Unknown("orEmpty receiver has no recorded source");
        }
        Set<CapabilityRef> caps = new HashSet<>();
        for (AbstractInsnNode recvSource : receiver.insns) {
            if (recvSource instanceof FieldInsnNode fieldInsn
                    && fieldInsn.getOpcode() == Opcodes.GETSTATIC
                    && fieldInsn.desc.equals(CAPABILITY_DESC)) {
                caps.add(new CapabilityRef(fieldInsn.owner, fieldInsn.name));
            } else {
                return new ReturnClassification.Unknown("orEmpty receiver is not exclusively static Capability fields");
            }
        }
        return new ReturnClassification.Known(caps);
    }

    /**
     * Carries the class-loading seam and recursion state for folding delegated {@code getCapability}-
     * style calls (including {@code super.getCapability}). Injected by the entry points so the pure
     * {@link #analyze(ClassNode, MethodNode)} core stays free of class loading.
     */
    private static final class DelegationContext {
        @FunctionalInterface
        interface ClassNodeProvider {
            /** @return the {@link ClassNode} for an internal name, or {@code null} if it cannot be loaded. */
            ClassNode get(String internalName);
        }

        /** Backstop against pathological delegation chains (cycles are caught exactly by {@code visited}). */
        private static final int MAX_DEPTH = 8;

        private final ClassNodeProvider provider;
        private final Set<String> visited;
        private final int depth;

        private DelegationContext(ClassNodeProvider provider, Set<String> visited, int depth) {
            this.provider = provider;
            this.visited = visited;
            this.depth = depth;
        }

        static DelegationContext root(ClassNodeProvider provider, ClassNode rootClass, MethodNode rootMethod) {
            Set<String> visited = new HashSet<>();
            visited.add(methodId(rootClass.name, rootMethod));
            return new DelegationContext(provider, visited, 0);
        }

        private DelegationContext child(String methodId) {
            Set<String> next = new HashSet<>(visited);
            next.add(methodId);
            return new DelegationContext(provider, next, depth + 1);
        }

        /**
         * Folds a delegated {@code LazyOptional}-returning call into the classification of its target,
         * when all of:
         * <ol>
         *   <li>our {@code cap} is passed straight through to the target's sole {@code Capability}
         *       parameter (so the target's served set, which is relative to that parameter, transfers);</li>
         *   <li>the target resolves to a method that is <em>provably the one that runs</em> - never an
         *       overridable virtual call, whose runtime target a subtype could change (see the gate below);</li>
         *   <li>it does not recurse cyclically or beyond {@link #MAX_DEPTH}.</li>
         * </ol>
         * Any failure yields {@code Unknown} (i.e. Indeterminate), which is always safe.
         */
        ReturnClassification resolveDelegate(MethodInsnNode call, CapabilitySourceInterpreter interpreter,
                                             int callerCapSlot) {
            // (1) Identify the target's sole Capability parameter and require our cap flows into it.
            Type[] params = Type.getArgumentTypes(call.desc);
            int capParam = -1;
            for (int p = 0; p < params.length; p++) {
                if (params[p].getDescriptor().equals(CAPABILITY_DESC)) {
                    if (capParam >= 0) {
                        return new ReturnClassification.Unknown("delegate has multiple Capability parameters");
                    }
                    capParam = p;
                }
            }
            if (capParam < 0) {
                return new ReturnClassification.Unknown("delegate has no Capability parameter");
            }
            boolean staticCall = call.getOpcode() == Opcodes.INVOKESTATIC;
            List<SourceValue> args = interpreter.getCallArguments(call);
            int argIndex = staticCall ? capParam : capParam + 1; // receiver occupies arg 0 when non-static
            if (argIndex >= args.size() || !isCapParamLoad(args.get(argIndex), callerCapSlot)) {
                return new ReturnClassification.Unknown("delegate is not called with our cap parameter");
            }

            // Resolve the target method, walking up from the call's owner (JVM-style method resolution).
            ClassNode declaringNode = provider.get(call.owner);
            if (declaringNode == null) {
                return new ReturnClassification.Unknown("cannot load delegate owner " + call.owner);
            }
            MethodNode target = findMethod(declaringNode, call.name, call.desc);
            while (target == null) {
                String superName = declaringNode.superName;
                if (superName == null) {
                    // Exhausted the hierarchy without finding an implementation: it serves nothing.
                    return ReturnClassification.EMPTY;
                }
                ClassNode next = provider.get(superName);
                if (next == null) {
                    return new ReturnClassification.Unknown("cannot load delegate super " + superName);
                }
                declaringNode = next;
                target = findMethod(declaringNode, call.name, call.desc);
            }

            // (2) Soundness gate: the resolved target must be the method that actually runs. INVOKESPECIAL
            // (super/private) and INVOKESTATIC are exact by construction; for a virtual/interface call we
            // require the target itself to be non-overridable. NB: since Java 11 nestmates, javac may emit
            // INVOKEVIRTUAL even for private calls, so we check the resolved method's flags, not the opcode.
            boolean provablyExact = staticCall
                    || call.getOpcode() == Opcodes.INVOKESPECIAL
                    || (target.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) != 0
                    || (declaringNode.access & Opcodes.ACC_FINAL) != 0;
            if (!provablyExact) {
                return new ReturnClassification.Unknown(
                        "overridable virtual delegate " + call.owner + "." + call.name);
            }

            // (3) Recursion guard - delegation (unlike super chains) can cycle.
            String id = methodId(declaringNode.name, target);
            if (visited.contains(id)) {
                return new ReturnClassification.Unknown("delegation cycle at " + id);
            }
            if (depth >= MAX_DEPTH) {
                return new ReturnClassification.Unknown("delegation depth exceeded at " + id);
            }

            CapabilityAnalysisResult result = analyze(declaringNode, target, child(id));
            if (result instanceof CapabilityAnalysisResult.KnownCapabilities known) {
                return new ReturnClassification.Known(known.capabilities());
            } else if (result instanceof CapabilityAnalysisResult.AlwaysEmpty) {
                return ReturnClassification.EMPTY;
            } else if (result instanceof CapabilityAnalysisResult.Indeterminate ind) {
                return new ReturnClassification.Unknown("delegate: " + ind.reason());
            }
            return ReturnClassification.EMPTY;
        }
    }

    // ---- cap-parameter path-condition dataflow -------------------------------------------------

    /**
     * Forward worklist fixpoint computing, for each instruction, the {@link CapState} of {@code cap}
     * reaching it (indexed by instruction position; {@code null} = unreachable). CFG edges come from ASM's
     * {@link Analyzer} via {@link CfgRecordingAnalyzer}. The only refinements are {@code cap == CONSTANT}
     * guards: a matched {@code IF_ACMP*} narrows the state along its taken/fall-through edges, while every
     * other instruction passes its state through unchanged.
     */
    private static CapState[] computeCapStates(MethodNode method, Frame<SourceValue>[] frames,
                                               Int2ObjectMap<IntSet> cfgSuccessors, int capSlot) {
        InsnList instructions = method.instructions;
        int n = instructions.size();
        CapState[] in = new CapState[n];
        IntArrayFIFOQueue work = new IntArrayFIFOQueue();

        if (n > 0) {
            in[0] = CapState.TOP;
            work.enqueue(0);
        }

        while (!work.isEmpty()) {
            int i = work.dequeueInt();
            CapState cur = in[i];
            if (cur == null) continue;
            AbstractInsnNode insn = instructions.get(i);
            int op = insn.getOpcode();

            CapabilityRef guardCap = matchedGuardCap(i, insn, frames, capSlot);
            if (guardCap != null) {
                JumpInsnNode jump = (JumpInsnNode) insn;
                int target = instructions.indexOf(jump.label);
                CapState takenState, fallState;
                if (op == Opcodes.IF_ACMPEQ) { // branch taken when cap == guardCap
                    takenState = cur.assertEq(guardCap);
                    fallState = cur.assertNe(guardCap);
                } else { // IF_ACMPNE: branch taken when cap != guardCap
                    takenState = cur.assertNe(guardCap);
                    fallState = cur.assertEq(guardCap);
                }
                propagate(in, work, target, takenState);
                propagate(in, work, i + 1, fallState);
                continue;
            }

            IntSet succs = cfgSuccessors.get(i);
            if (succs != null) {
                for (IntIterator it = succs.iterator(); it.hasNext(); ) {
                    propagate(in, work, it.nextInt(), cur);
                }
            }
        }
        return in;
    }

    private static void propagate(CapState[] in, IntArrayFIFOQueue work, int idx, CapState incoming) {
        if (idx < 0 || idx >= in.length || incoming == null) return;
        CapState old = in[idx];
        CapState merged = (old == null) ? incoming : old.join(incoming);
        if (old == null || !old.sameAs(merged)) {
            in[idx] = merged;
            work.enqueue(idx);
        }
    }

    /**
     * An {@link Analyzer} that records the control-flow graph ASM computes while producing the frames.
     * Overriding the edge hooks lets us reuse ASM's opcode-aware CFG (normal <em>and</em> exception
     * edges) instead of hand-enumerating every branching instruction. {@code successors} maps each
     * instruction index to its successor indices.
     */
    private static final class CfgRecordingAnalyzer extends Analyzer<SourceValue> {
        final Int2ObjectMap<IntSet> successors = new Int2ObjectOpenHashMap<>();

        CfgRecordingAnalyzer(Interpreter<SourceValue> interpreter) {
            super(interpreter);
        }

        private void record(int from, int to) {
            IntSet set = successors.get(from);
            if (set == null) {
                set = new IntLinkedOpenHashSet();
                successors.put(from, set);
            }
            set.add(to);
        }

        @Override
        protected void newControlFlowEdge(int insnIndex, int successorIndex) {
            record(insnIndex, successorIndex);
        }

        @Override
        protected boolean newControlFlowExceptionEdge(int insnIndex, int successorIndex) {
            record(insnIndex, successorIndex);
            return true;
        }
    }

    /**
     * If the instruction at {@code i} is a clean {@code cap == CONSTANT} reference comparison (one
     * operand traces to the {@code cap} parameter at {@code capSlot}, the other to a static
     * {@code Capability} field), returns that capability; otherwise {@code null}.
     */
    private static CapabilityRef matchedGuardCap(int i, AbstractInsnNode insn, Frame<SourceValue>[] frames, int capSlot) {
        int op = insn.getOpcode();
        if (op != Opcodes.IF_ACMPEQ && op != Opcodes.IF_ACMPNE) return null;
        Frame<SourceValue> frame = frames[i];
        if (frame == null || frame.getStackSize() < 2) return null;

        SourceValue val1 = frame.getStack(frame.getStackSize() - 2);
        SourceValue val2 = frame.getStack(frame.getStackSize() - 1);

        CapabilityRef capRef = findCapRef(val1);
        if (capRef == null) capRef = findCapRef(val2);
        boolean hasParamLoad = isCapParamLoad(val1, capSlot) || isCapParamLoad(val2, capSlot);

        return (capRef != null && hasParamLoad) ? capRef : null;
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

    /** True iff every source of {@code sv} is {@code ALOAD capSlot} - i.e. the value is the cap parameter. */
    private static boolean isCapParamLoad(SourceValue sv, int capSlot) {
        if (capSlot < 0 || sv.insns.isEmpty()) return false;
        for (AbstractInsnNode src : sv.insns) {
            if (!(src instanceof VarInsnNode varInsn)
                    || varInsn.getOpcode() != Opcodes.ALOAD
                    || varInsn.var != capSlot) {
                return false;
            }
        }
        return true;
    }

    /** True if the method ever stores to {@code capSlot}, invalidating parameter-based guard reasoning. */
    private static boolean capSlotReassigned(MethodNode method, int capSlot) {
        if (capSlot < 0) return false;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof VarInsnNode varInsn
                    && varInsn.getOpcode() == Opcodes.ASTORE
                    && varInsn.var == capSlot) {
                return true;
            }
        }
        return false;
    }

    /** The local-variable slot of the (sole) {@code Capability}-typed parameter, or -1 if there is none. */
    private static int capSlotOf(MethodNode method) {
        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        int slot = isStatic ? 0 : 1; // slot 0 is `this` for instance methods
        for (Type argType : Type.getArgumentTypes(method.desc)) {
            if (argType.getDescriptor().equals(CAPABILITY_DESC)) {
                return slot;
            }
            slot += argType.getSize(); // long/double occupy two slots
        }
        return -1;
    }

    private static MethodNode findGetCapabilityMethod(ClassNode classNode) {
        return findMethod(classNode, "getCapability", GET_CAPABILITY_DESC);
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String desc) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                return method;
            }
        }
        return null;
    }

    private static String methodId(String ownerInternalName, MethodNode method) {
        return ownerInternalName + "." + method.name + method.desc;
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

    /** Loads a {@link ClassNode} by internal name from {@code loader}; returns null if unavailable. */
    private static ClassNode readClassByName(String internalName, ClassLoader loader) {
        ClassLoader cl = (loader != null) ? loader : CapabilityAnalyzer.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(internalName + ".class")) {
            if (is == null) return null;
            ClassReader reader = new ClassReader(is);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            return node;
        } catch (IOException e) {
            return null;
        }
    }

    private sealed interface ReturnClassification {
        ReturnClassification EMPTY = new Empty();

        record Known(Set<CapabilityRef> caps) implements ReturnClassification {}
        record Empty() implements ReturnClassification {}
        record Unknown(String reason) implements ReturnClassification {}
    }

    /**
     * Abstract value for the {@code cap} parameter at a program point: either TOP (any capability,
     * encoded as {@code caps == null}) or the finite set of capabilities cap may equal (empty = an
     * unreachable point). A join semilattice under union with TOP as top; its bounded height (subsets
     * of the capabilities named in the method's guards) guarantees the fixpoint terminates.
     */
    private static final class CapState {
        static final CapState TOP = new CapState(null);

        /** {@code null} means TOP (any capability); otherwise the exact set cap may equal. */
        private final Set<CapabilityRef> caps;

        private CapState(Set<CapabilityRef> caps) {
            this.caps = caps;
        }

        boolean isTop() {
            return caps == null;
        }

        /** The exact cap set; only valid when {@code !isTop()}. */
        Set<CapabilityRef> caps() {
            return caps;
        }

        /** Join = set union, with TOP absorbing. */
        CapState join(CapState other) {
            if (other == null) return this;
            if (this.isTop() || other.isTop()) return TOP;
            Set<CapabilityRef> union = new HashSet<>(this.caps);
            union.addAll(other.caps);
            return new CapState(union);
        }

        boolean sameAs(CapState other) {
            if (other == null) return false;
            if (this.isTop() || other.isTop()) return this.isTop() && other.isTop();
            return this.caps.equals(other.caps);
        }

        /** Refine along a {@code cap == x} edge. */
        CapState assertEq(CapabilityRef x) {
            if (isTop()) return new CapState(Set.of(x));
            return new CapState(caps.contains(x) ? Set.of(x) : Set.of());
        }

        /** Refine along a {@code cap != x} edge. TOP minus one capability is still TOP. */
        CapState assertNe(CapabilityRef x) {
            if (isTop()) return TOP;
            Set<CapabilityRef> narrowed = new HashSet<>(caps);
            narrowed.remove(x);
            return new CapState(narrowed);
        }
    }
}
