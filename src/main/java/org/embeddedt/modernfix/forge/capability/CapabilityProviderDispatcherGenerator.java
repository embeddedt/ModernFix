package org.embeddedt.modernfix.forge.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalyzer;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityRef;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

/**
 * Generates optimized hidden classes for ICapabilityProvider dispatch.
 * Each generated class unrolls the capability provider array into final fields
 * and performs direct dispatch instead of megamorphic virtual calls.
 */
public class CapabilityProviderDispatcherGenerator {
    /**
     * Describes the dispatch strategy for a single capability provider in the generated class.
     */
    sealed interface ProviderDispatch {
        /** Provider handles a known capability - emit an identity guard before dispatch. */
        record Guarded(int providerIndex, String fieldDesc, CapabilityRef capability) implements ProviderDispatch {}
        /** Provider capabilities are unknown - dispatch unconditionally. */
        record Unguarded(int providerIndex, String fieldDesc) implements ProviderDispatch {}
        /** Multiple guarded dispatches collapsed into an identity-hash switch. */
        record Hash(List<Guarded> entries) implements ProviderDispatch {}
    }

    /**
     * Number of consecutive equality checks that must be performed to switch to a hash map.
     */
    private static final int HASH_DISPATCH_THRESHOLD = 3;

    private static final String GENERATED_CLASSES_FOLDER = System.getProperty("modernfix.generatedCapabilityDispatcherClassDumpFolder", "");

    /**
     * Sentinel used in generated guards to skip empty results via reference equality,
     * avoiding a method call to {@code isPresent()}.
     */
    @SuppressWarnings("unused")
    public static final LazyOptional<?> EMPTY = LazyOptional.empty();

    private static final ConcurrentHashMap<List<Class<? extends ICapabilityProvider>>, MethodHandle> cache =
            new ConcurrentHashMap<>();

    private static final AtomicInteger classCounter = new AtomicInteger(0);
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    // Type descriptors
    private static final String ICAP_PROVIDER_DESC = "Lnet/minecraftforge/common/capabilities/ICapabilityProvider;";
    private static final String CAPABILITY_DESC = "Lnet/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY_OPTIONAL_DESC = "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";
    private static final String LOOKUP_DESC = "Ljava/lang/invoke/MethodHandles$Lookup;";

    /**
     * Gets or generates a constructor MethodHandle for the given capability provider types.
     * The constructor takes an array of ICapabilityProvider instances.
     *
     * @param providerTypes The types of capability providers in order
     * @return A MethodHandle to construct the optimized dispatcher
     */
    private static MethodHandle getOrGenerateConstructor(List<Class<? extends ICapabilityProvider>> providerTypes) {
        return cache.computeIfAbsent(providerTypes, CapabilityProviderDispatcherGenerator::generateClass);
    }

    /**
     * Convenience method that takes an array of providers and returns the constructor.
     */
    private static MethodHandle getOrGenerateConstructor(ICapabilityProvider[] providers) {
        List<Class<? extends ICapabilityProvider>> types = new ArrayList<>(providers.length);
        for (ICapabilityProvider provider : providers) {
            types.add(provider.getClass());
        }
        return getOrGenerateConstructor(types);
    }

    public static ICapabilityProvider getOrGenerateDispatcher(ICapabilityProvider[] providers) {
        var handle = getOrGenerateConstructor(providers);
        try {
            return (ICapabilityProvider)handle.invokeExact((Object)providers);
        } catch (Throwable e) {
            throw new RuntimeException("Error constructing dispatcher", e);
        }
    }

    private static MethodHandle generateClass(List<Class<? extends ICapabilityProvider>> providerTypes) {
        try {
            // Analyze each provider type
            List<CapabilityAnalysisResult> analysisResults = new ArrayList<>(providerTypes.size());
            for (Class<? extends ICapabilityProvider> type : providerTypes) {
                CapabilityAnalysisResult result = CapabilityAnalyzer.analyze(type);
                analysisResults.add(result);
            }

            int generatedClassId = classCounter.incrementAndGet();
            String className = "org.embeddedt.modernfix.forge.capability.CapabilityDispatcher$Generated$" + generatedClassId;

            List<ProviderDispatch> dispatches = optimizeDispatches(buildDispatchList(providerTypes, analysisResults));

            // Assign a stable index to every unique CapabilityRef across all dispatches.
            // We resolve the actual Capability<?> instances here (in Java) so the generated
            // <clinit> only needs simple classDataAt calls - no reflection bytecode needed.
            LinkedHashMap<CapabilityRef, Integer> capRefIndices = collectCapabilityRefs(dispatches);
            List<Capability<?>> capValues = resolveCapabilityValues(capRefIndices);

            // Resolve each capability's identity hash now, from the singleton instances that will
            // also be present at runtime. These become the LOOKUPSWITCH keys in the generated dispatch.
            Map<CapabilityRef, Integer> capRefHashes = new HashMap<>();
            for (Map.Entry<CapabilityRef, Integer> entry : capRefIndices.entrySet()) {
                capRefHashes.put(entry.getKey(), System.identityHashCode(capValues.get(entry.getValue())));
            }

            ModernFix.LOGGER.debug("Generating capability dispatcher #{} for types: [{}]", () -> generatedClassId, () -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < providerTypes.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(providerTypes.get(i).getName()).append(" -> ").append(formatAnalysisResult(analysisResults.get(i)));
                }
                return sb;
            });

            byte[] classBytes = generateClassBytes(className, providerTypes, dispatches, capRefIndices, capRefHashes);

            // Define the hidden class, injecting the resolved Capability instances as class data.
            // The generated <clinit> retrieves them via MethodHandles.classDataAt so it never
            // needs to perform reflection itself - private fields are handled transparently here.
            MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClassWithClassData(
                    classBytes,
                    capValues,
                    true,
                    MethodHandles.Lookup.ClassOption.NESTMATE
            );

            if (!GENERATED_CLASSES_FOLDER.isBlank()) {
                Path path = Paths.get(GENERATED_CLASSES_FOLDER, "generatedDispatcher" + generatedClassId + ".class");
                Files.createDirectories(path.getParent());
                Files.write(path, classBytes);
            }

            // Return a MethodHandle to the constructor
            // Constructor signature: (ICapabilityProvider[])V
            // The constructor is adapted to take an Object and return an ICapabilityProvider to match
            // the usage in getOrGenerateDispatcher
            return hiddenLookup.findConstructor(
                    hiddenLookup.lookupClass(),
                    MethodType.methodType(void.class, ICapabilityProvider[].class)
            ).asType(MethodType.methodType(ICapabilityProvider.class, Object.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate capability dispatcher class", e);
        }
    }

    /**
     * Collects all unique {@link CapabilityRef}s referenced by {@code dispatches} in encounter order,
     * assigning each a stable list index for use with {@code classDataAt}.
     */
    private static LinkedHashMap<CapabilityRef, Integer> collectCapabilityRefs(List<ProviderDispatch> dispatches) {
        LinkedHashMap<CapabilityRef, Integer> result = new LinkedHashMap<>();
        for (ProviderDispatch dispatch : dispatches) {
            if (dispatch instanceof ProviderDispatch.Guarded g) {
                result.putIfAbsent(g.capability(), result.size());
            } else if (dispatch instanceof ProviderDispatch.Hash hash) {
                for (ProviderDispatch.Guarded g : hash.entries()) {
                    result.putIfAbsent(g.capability(), result.size());
                }
            }
        }
        return result;
    }

    /**
     * Resolves the actual {@link Capability} instances for all refs at class-generation time.
     * Uses reflection (with {@code setAccessible}) so private fields are handled without any
     * reflection bytecode appearing in the generated class.
     * <p>
     * Field lookup is delegated to {@link #getRefField(Class, CapabilityRef)}
     */
    private static List<Capability<?>> resolveCapabilityValues(LinkedHashMap<CapabilityRef, Integer> capRefIndices) {
        @SuppressWarnings("unchecked")
        Capability<?>[] caps = new Capability[capRefIndices.size()];
        for (Map.Entry<CapabilityRef, Integer> entry : capRefIndices.entrySet()) {
            CapabilityRef ref = entry.getKey();
            try {
                Class<?> clazz = Class.forName(ref.owner().replace('/', '.'), false,
                        CapabilityProviderDispatcherGenerator.class.getClassLoader());
                Field field = getRefField(clazz, ref);
                caps[entry.getValue()] = (Capability<?>) field.get(null);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to resolve capability field " + ref, e);
            }
        }
        return Arrays.asList(caps);
    }

    /**
     * Resolves the {@link Field} for the given {@link CapabilityRef},
     * falls back to the implemented interfaces if no match is found.
     */
    private static @NotNull Field getRefField(Class<?> clazz, CapabilityRef ref) throws NoSuchFieldException {
        Field field = null;
        try {
            field = clazz.getDeclaredField(ref.fieldName());
        } catch (NoSuchFieldException ignored) {
            for (Class<?> iface : clazz.getInterfaces()) {
                try {
                    field = iface.getDeclaredField(ref.fieldName());
                    break;
                } catch (NoSuchFieldException ignored1) {}
            }
        }
        if (field == null) throw new NoSuchFieldException(ref.fieldName());
        field.setAccessible(true);
        return field;
    }

    /**
     * Build the dispatch list describing how each provider should be handled.
     */
    static List<ProviderDispatch> buildDispatchList(List<Class<? extends ICapabilityProvider>> providerTypes, List<CapabilityAnalysisResult> analysisResults) {
        List<ProviderDispatch> dispatches = new ArrayList<>(providerTypes.size());
        for (int i = 0; i < providerTypes.size(); i++) {
            Class<? extends ICapabilityProvider> type = providerTypes.get(i);
            String fieldDesc = (!type.isHidden() && Modifier.isPublic(type.getModifiers()))
                    ? Type.getDescriptor(type) : ICAP_PROVIDER_DESC;

            CapabilityAnalysisResult analysis = analysisResults.get(i);
            if (analysis instanceof CapabilityAnalysisResult.AlwaysEmpty) {
                // No dispatch needed - provider never returns a capability
            } else if (analysis instanceof CapabilityAnalysisResult.KnownCapabilities known
                    && known.capabilities().size() <= 5) {
                for (CapabilityRef ref : known.capabilities()) {
                    dispatches.add(new ProviderDispatch.Guarded(i, fieldDesc, ref));
                }
            } else {
                dispatches.add(new ProviderDispatch.Unguarded(i, fieldDesc));
            }
        }
        return dispatches;
    }

    /**
     * Collapse runs of 3+ consecutive Guarded dispatches into Hash dispatches.
     * Duplicate CapabilityRefs within a run are kept as trailing Guarded entries
     * after the Hash to preserve sequential fallthrough semantics.
     */
    static List<ProviderDispatch> optimizeDispatches(List<ProviderDispatch> dispatches) {
        List<ProviderDispatch> result = new ArrayList<>(dispatches.size());
        int i = 0;
        while (i < dispatches.size()) {
            // Collect a run of consecutive Guarded entries
            int runStart = i;
            while (i < dispatches.size() && dispatches.get(i) instanceof ProviderDispatch.Guarded) {
                i++;
            }

            List<ProviderDispatch> run = dispatches.subList(runStart, i);
            if (run.isEmpty()) {
                // Not a Guarded entry, pass through
                result.add(dispatches.get(i));
                i++;
                continue;
            }

            if (!tryCollapseToHash(run, result)) {
                result.addAll(run);
            }
        }
        return result;
    }

    /**
     * Attempt to collapse a run of Guarded dispatches into a Hash.
     * Returns true if a Hash was emitted, false if the run should be kept as-is.
     */
    private static boolean tryCollapseToHash(List<ProviderDispatch> run, List<ProviderDispatch> result) {
        if (run.size() < HASH_DISPATCH_THRESHOLD) {
            return false;
        }

        // Deduplicate by CapabilityRef - first occurrence goes into the hash,
        // duplicates are kept as trailing Guarded entries for fallthrough
        Set<CapabilityRef> seen = new HashSet<>();
        List<ProviderDispatch.Guarded> hashEntries = new ArrayList<>();
        List<ProviderDispatch.Guarded> duplicates = new ArrayList<>();
        for (ProviderDispatch dispatch : run) {
            ProviderDispatch.Guarded g = (ProviderDispatch.Guarded) dispatch;
            if (seen.add(g.capability())) {
                hashEntries.add(g);
            } else {
                duplicates.add(g);
            }
        }

        if (hashEntries.size() < HASH_DISPATCH_THRESHOLD) {
            return false;
        }

        result.add(new ProviderDispatch.Hash(hashEntries));
        result.addAll(duplicates);
        return true;
    }

    /**
     * Collect all unique provider fields (index → fieldDesc) referenced by a dispatch list,
     * including those inside Hash entries.
     */
    private static LinkedHashMap<Integer, String> collectProviderFields(List<ProviderDispatch> dispatches) {
        LinkedHashMap<Integer, String> fields = new LinkedHashMap<>();
        for (ProviderDispatch dispatch : dispatches) {
            if (dispatch instanceof ProviderDispatch.Guarded g) {
                fields.putIfAbsent(g.providerIndex(), g.fieldDesc());
            } else if (dispatch instanceof ProviderDispatch.Unguarded u) {
                fields.putIfAbsent(u.providerIndex(), u.fieldDesc());
            } else if (dispatch instanceof ProviderDispatch.Hash hash) {
                for (ProviderDispatch.Guarded g : hash.entries()) {
                    fields.putIfAbsent(g.providerIndex(), g.fieldDesc());
                }
            }
        }
        return fields;
    }

    private static byte[] generateClassBytes(String className, List<Class<? extends ICapabilityProvider>> providerTypes,
                                             List<ProviderDispatch> dispatches, LinkedHashMap<CapabilityRef, Integer> capRefIndices,
                                             Map<CapabilityRef, Integer> capRefHashes) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected ClassLoader getClassLoader() {
                return CapabilityProviderDispatcherGenerator.class.getClassLoader();
            }
        };

        String internalName = className.replace('.', '/');

        // Class declaration: implements ICapabilityProvider
        cw.visit(
                V17,
                ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
                internalName,
                null,
                "java/lang/Object",
                new String[] { "net/minecraftforge/common/capabilities/ICapabilityProvider" }
        );

        // Generate final fields for each distinct provider
        LinkedHashMap<Integer, String> providerFields = collectProviderFields(dispatches);
        for (var entry : providerFields.entrySet()) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE | ACC_FINAL, "provider" + entry.getKey(), entry.getValue(), null, null);
            if (entry.getValue().equals(ICAP_PROVIDER_DESC)) {
                String originalName = providerTypes.get(entry.getKey()).getName();
                AnnotationVisitor av = fv.visitAnnotation("Lorg/embeddedt/modernfix/forge/capability/OriginalType;", false);
                av.visit("value", originalName);
                av.visitEnd();
            }
            fv.visitEnd();
        }

        // Generate one static final field per unique CapabilityRef.
        // These are populated in <clinit> via MethodHandles.classDataAt, which reads the
        // Capability<?> instances injected by defineHiddenClassWithClassData. This avoids
        // any reflection bytecode in the generated class and handles private fields transparently.
        for (Map.Entry<CapabilityRef, Integer> entry : capRefIndices.entrySet()) {
            cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                    capRefFieldName(entry.getValue()), CAPABILITY_DESC, null, null).visitEnd();
        }

        // Generate <clinit> to load capability instances from class data
        if (!capRefIndices.isEmpty()) {
            generateClinit(cw, internalName, capRefIndices);
        }

        // Generate constructor
        generateConstructor(cw, className, providerFields);

        // Generate getCapability method with sided parameter
        generateGetCapabilityMethod(cw, className, dispatches, capRefIndices, capRefHashes);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String capRefFieldName(int index) {
        return "capRef" + index;
    }

    /**
     * Generates {@code <clinit>} that loads each capability from class data injected at define time.
     * The bytecode is simply: {@code capRefN = MethodHandles.classDataAt(lookup(), "", Capability.class, N)}.
     */
    private static void generateClinit(ClassWriter cw, String internalName, LinkedHashMap<CapabilityRef, Integer> capRefIndices) {
        MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        for (int i = 0; i < capRefIndices.size(); i++) {
            // MethodHandles.lookup()
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup",
                    "()" + LOOKUP_DESC, false);
            // "_"  (classDataAt requires this exact name)
            mv.visitLdcInsn("_");
            // Capability.class
            mv.visitLdcInsn(Type.getType(CAPABILITY_DESC));
            // index
            mv.visitLdcInsn(i);
            // MethodHandles.classDataAt(lookup, name, type, index) → Object
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "classDataAt",
                    "(" + LOOKUP_DESC + "Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, "net/minecraftforge/common/capabilities/Capability");
            mv.visitFieldInsn(PUTSTATIC, internalName, capRefFieldName(i), CAPABILITY_DESC);
        }

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Emits a load of the capability constant for {@code ref} from the generated class's own static field.
     */
    private static void emitCapabilityLoad(MethodVisitor mv, String internalName, CapabilityRef ref,
                                           Map<CapabilityRef, Integer> capRefIndices) {
        mv.visitFieldInsn(GETSTATIC, internalName, capRefFieldName(capRefIndices.get(ref)), CAPABILITY_DESC);
    }

    private static void generateConstructor(ClassWriter cw, String className, Map<Integer, String> providerFields) {
        Method constructor = Method.getMethod("void <init>(net.minecraftforge.common.capabilities.ICapabilityProvider[])");
        GeneratorAdapter mg = new GeneratorAdapter(ACC_PUBLIC, constructor, null, null, cw);
        Type classType = Type.getObjectType(className.replace('.', '/'));

        // Call super constructor
        mg.loadThis();
        mg.invokeConstructor(Type.getType(Object.class), Method.getMethod("void <init>()"));

        // Unpack array into provider fields
        for (var entry : providerFields.entrySet()) {
            int idx = entry.getKey();
            String desc = entry.getValue();
            Type fieldType = Type.getType(desc);
            mg.loadThis();
            mg.loadArg(0);              // array
            mg.push(idx);               // index
            mg.arrayLoad(Type.getType(ICAP_PROVIDER_DESC));
            if (!desc.equals(ICAP_PROVIDER_DESC)) {
                mg.checkCast(fieldType);
            }
            mg.putField(classType, "provider" + idx, fieldType);
        }

        mg.returnValue();
        mg.endMethod();
    }

    private static void generateGetCapabilityMethod(ClassWriter cw, String className, List<ProviderDispatch> dispatches,
                                                    Map<CapabilityRef, Integer> capRefIndices,
                                                    Map<CapabilityRef, Integer> capRefHashes) {
        // Method: <T> LazyOptional<T> getCapability(Capability<T>, Direction)
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getCapability",
                "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC,
                "<T:Ljava/lang/Object;>(" + CAPABILITY_DESC.replace(";", "<TT;>;") + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC.replace(";", "<TT;>;"),
                null
        );

        mv.visitCode();

        Label methodStart = new Label();
        Label methodEnd = new Label();
        mv.visitLabel(methodStart);

        String internalName = className.replace('.', '/');
        String getCapDesc = "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC;

        // slot 3 = LazyOptional<T> result (all paths)
        for (int di = 0; di < dispatches.size(); ) {
            ProviderDispatch dispatch = dispatches.get(di);
            Label nextLabel = new Label();

            if (dispatch instanceof ProviderDispatch.Hash hash) {
                emitHashDispatch(mv, internalName, getCapDesc, hash, nextLabel, capRefIndices, capRefHashes);
                di++;
            } else if (dispatch instanceof ProviderDispatch.Guarded) {
                di = emitGuardedDispatch(mv, internalName, getCapDesc, dispatches, di, nextLabel, capRefIndices);
            } else {
                var u = (ProviderDispatch.Unguarded) dispatch;
                emitProviderGetCapability(mv, internalName, getCapDesc, u.providerIndex(), u.fieldDesc());
                di++;
            }

            // Optimistically check for the exact LazyOptional.empty() value to avoid an isPresent call.
            // (For a Hash dispatch, the no-match case has already jumped to nextLabel, so result is set here.)
            // if (result == EMPTY) goto next
            mv.visitVarInsn(ALOAD, 3);
            mv.visitFieldInsn(GETSTATIC,
                    "org/embeddedt/modernfix/forge/capability/CapabilityProviderDispatcherGenerator",
                    "EMPTY", LAZY_OPTIONAL_DESC);
            mv.visitJumpInsn(IF_ACMPEQ, nextLabel);

            // if (result.isPresent()) return result
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL,
                    "net/minecraftforge/common/util/LazyOptional",
                    "isPresent", "()Z", false);
            mv.visitJumpInsn(IFEQ, nextLabel);

            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ARETURN);

            mv.visitLabel(nextLabel);
        }

        // If no provider returned a capability, return empty
        mv.visitMethodInsn(
                INVOKESTATIC,
                "net/minecraftforge/common/util/LazyOptional",
                "empty",
                "()" + LAZY_OPTIONAL_DESC,
                false
        );
        mv.visitInsn(ARETURN);

        mv.visitLabel(methodEnd);

        // Local variable table for clean decompilation
        String capSig = CAPABILITY_DESC.replace(";", "<TT;>;");
        String resultSig = LAZY_OPTIONAL_DESC.replace(";", "<TT;>;");
        mv.visitLocalVariable("cap", CAPABILITY_DESC, capSig, methodStart, methodEnd, 1);
        mv.visitLocalVariable("side", DIRECTION_DESC, null, methodStart, methodEnd, 2);
        mv.visitLocalVariable("result", LAZY_OPTIONAL_DESC, resultSig, methodStart, methodEnd, 3);

        mv.visitMaxs(0, 0);  // Computed by COMPUTE_MAXS
        mv.visitEnd();
    }

    /**
     * Emits an O(1) dispatch over a group of guarded entries using a single {@code LOOKUPSWITCH}
     * over {@code System.identityHashCode(cap)}, following the string-switch translation strategy
     * from JEP 325. Each switch arm narrows to the candidate entries sharing that hash; a
     * reference-equality guard against the expected capability (handling the rare event of hash
     * collisions, including collisions between distinct capabilities, which share a single case)
     * then dispatches straight to the matching provider field. A miss - either an unmatched hash or
     * a failed guard - jumps to {@code nextLabel}.
     * <p>
     * On a match, {@code result} (slot 3) is set and control falls through to the shared
     * empty/isPresent check emitted by the caller.
     */
    private static void emitHashDispatch(MethodVisitor mv, String internalName, String getCapDesc,
                                         ProviderDispatch.Hash hash, Label nextLabel,
                                         Map<CapabilityRef, Integer> capRefIndices,
                                         Map<CapabilityRef, Integer> capRefHashes) {
        List<ProviderDispatch.Guarded> entries = hash.entries();
        int n = entries.size();

        // Group entry indices by their capability's identity hash so colliding entries share one case.
        TreeMap<Integer, List<Integer>> byHash = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            int h = capRefHashes.get(entries.get(i).capability());
            byHash.computeIfAbsent(h, k -> new ArrayList<>()).add(i);
        }

        // switch (System.identityHashCode(cap))
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "identityHashCode",
                "(Ljava/lang/Object;)I", false);

        int[] keys = new int[byHash.size()];
        Label[] caseLabels = new Label[byHash.size()];
        int k = 0;
        for (Integer key : byHash.keySet()) {
            keys[k] = key;
            caseLabels[k] = new Label();
            k++;
        }
        mv.visitLookupSwitchInsn(nextLabel, keys, caseLabels);

        // On a match, dispatch and jump here; falls through to the caller's empty/isPresent check.
        Label matchEnd = new Label();

        k = 0;
        for (List<Integer> group : byHash.values()) {
            mv.visitLabel(caseLabels[k++]);
            int m = group.size();
            for (int j = 0; j < m; j++) {
                ProviderDispatch.Guarded g = entries.get(group.get(j));
                // if (cap != capRef) try the next colliding candidate, or give up on the last one
                Label fail = (j < m - 1) ? new Label() : nextLabel;
                mv.visitVarInsn(ALOAD, 1);
                emitCapabilityLoad(mv, internalName, g.capability(), capRefIndices);
                mv.visitJumpInsn(IF_ACMPNE, fail);
                // result = providerN.getCapability(cap, side)
                emitProviderGetCapability(mv, internalName, getCapDesc, g.providerIndex(), g.fieldDesc());
                mv.visitJumpInsn(GOTO, matchEnd);
                if (j < m - 1) {
                    mv.visitLabel(fail);
                }
            }
        }

        mv.visitLabel(matchEnd);
    }

    /**
     * Emit guarded dispatch for one or more consecutive Guarded entries sharing the same providerIndex.
     * When multiple caps map to the same provider, an OR-chain guard is emitted instead of separate dispatches.
     *
     * @return the updated dispatch index (past the consumed group)
     */
    private static int emitGuardedDispatch(MethodVisitor mv, String internalName, String getCapDesc,
                                           List<ProviderDispatch> dispatches, int di, Label nextLabel,
                                           Map<CapabilityRef, Integer> capRefIndices) {
        var guarded = (ProviderDispatch.Guarded) dispatches.get(di);

        // Peek ahead to collect consecutive Guarded entries with same providerIndex
        int groupEnd = di + 1;
        while (groupEnd < dispatches.size()
                && dispatches.get(groupEnd) instanceof ProviderDispatch.Guarded next
                && next.providerIndex() == guarded.providerIndex()) {
            groupEnd++;
        }

        // OR-chain: IF_ACMPEQ matchLabel for each cap except the last, IF_ACMPNE nextLabel for the last
        Label matchLabel = new Label();
        for (int gi = di; gi < groupEnd; gi++) {
            var g = (ProviderDispatch.Guarded) dispatches.get(gi);
            CapabilityRef ref = g.capability();
            mv.visitVarInsn(ALOAD, 1);
            emitCapabilityLoad(mv, internalName, ref, capRefIndices);
            if (gi < groupEnd - 1) {
                mv.visitJumpInsn(IF_ACMPEQ, matchLabel);
            } else {
                mv.visitJumpInsn(IF_ACMPNE, nextLabel);
            }
        }
        mv.visitLabel(matchLabel);

        emitProviderGetCapability(mv, internalName, getCapDesc, guarded.providerIndex(), guarded.fieldDesc());
        return groupEnd;
    }

    private static void emitProviderGetCapability(MethodVisitor mv, String internalName, String getCapDesc,
                                                  int providerIndex, String fieldDesc) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "provider" + providerIndex, fieldDesc);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEINTERFACE,
                "net/minecraftforge/common/capabilities/ICapabilityProvider",
                "getCapability", getCapDesc, true);
        mv.visitVarInsn(ASTORE, 3);
    }

    private static String formatAnalysisResult(CapabilityAnalysisResult result) {
        if (result instanceof CapabilityAnalysisResult.AlwaysEmpty) {
            return "always empty (skipped)";
        } else if (result instanceof CapabilityAnalysisResult.KnownCapabilities known) {
            return "known caps: " + known.capabilities().stream()
                    .map(ref -> ref.owner() + "#" + ref.fieldName())
                    .collect(Collectors.joining(", "));
        } else if (result instanceof CapabilityAnalysisResult.Indeterminate ind) {
            return "indeterminate (" + ind.reason() + ")";
        }
        return result.toString();
    }
}
