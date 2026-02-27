package org.embeddedt.modernfix.forge.capability.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;

/**
 * Extends {@link SourceInterpreter} with two enhancements:
 * <ul>
 *   <li>Propagates source values through copy operations (ALOAD/ASTORE), so that
 *       {@code result = cap.orEmpty(...); return result;} traces back to the INVOKEVIRTUAL.</li>
 *   <li>Records argument {@link SourceValue}s for each call instruction, so we can later
 *       inspect the receiver/arguments of {@code orEmpty()} calls.</li>
 * </ul>
 */
public class CapabilitySourceInterpreter extends SourceInterpreter {

    private final Map<AbstractInsnNode, List<SourceValue>> callArguments = new HashMap<>();

    public CapabilitySourceInterpreter() {
        super(ASM9);
    }

    /**
     * Returns the recorded argument SourceValues for a given call instruction.
     */
    public List<SourceValue> getCallArguments(AbstractInsnNode insn) {
        return callArguments.getOrDefault(insn, Collections.emptyList());
    }

    @Override
    public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
        // Propagate sources through loads/stores instead of creating a new source.
        // However, if the value has no sources (e.g. a method parameter), fall back to
        // the default behavior so the ALOAD instruction itself is recorded as the source.
        if (value.insns.isEmpty()) {
            return super.copyOperation(insn, value);
        }
        return value;
    }

    @Override
    public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
        callArguments.put(insn, new ArrayList<>(values));
        return super.naryOperation(insn, values);
    }
}
