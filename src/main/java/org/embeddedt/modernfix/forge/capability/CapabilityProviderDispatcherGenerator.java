package org.embeddedt.modernfix.forge.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    }

    private static final String GENERATED_CLASSES_FOLDER = System.getProperty("modernfix.generatedCapabilityDispatcherClassDumpFolder", "");

    /**
     * Sentinel used in generated guards to skip empty results via reference equality,
     * avoiding a method call to {@code isPresent()}.
     */
    @SuppressWarnings("unused")
    public static final LazyOptional<?> EMPTY = LazyOptional.empty();

    private static final Logger LOGGER = LogManager.getLogger("ModernFix");

    /** Cache key: the ordered provider types plus the analysis flag they were generated under. */
    private record CacheKey(List<Class<? extends ICapabilityProvider>> types, boolean analysisEnabled) {}

    private static final ConcurrentHashMap<CacheKey, MethodHandle> cache =
            new ConcurrentHashMap<>();

    private static final AtomicInteger classCounter = new AtomicInteger(0);
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    // Type descriptors
    private static final String ICAP_PROVIDER_DESC = "Lnet/minecraftforge/common/capabilities/ICapabilityProvider;";
    private static final String CAPABILITY_DESC = "Lnet/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY_OPTIONAL_DESC = "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";
    private static final String LOOKUP_DESC = "Ljava/lang/invoke/MethodHandles$Lookup;";
    private static final String GENERATED_DISPATCHER_INTERNAL_NAME = "org/embeddedt/modernfix/forge/capability/GeneratedDispatcher";

    /**
     * Gets or generates a constructor MethodHandle for the given capability provider types.
     * The constructor takes an array of ICapabilityProvider instances.
     *
     * @param providerTypes The types of capability providers in order
     * @return A MethodHandle to construct the optimized dispatcher
     */
    private static MethodHandle getOrGenerateConstructor(List<Class<? extends ICapabilityProvider>> providerTypes,
                                                         boolean analysisEnabled) {
        return cache.computeIfAbsent(new CacheKey(providerTypes, analysisEnabled),
                key -> generateClass(key.types(), key.analysisEnabled()));
    }

    /**
     * Convenience method that takes an array of providers and returns the constructor.
     */
    private static MethodHandle getOrGenerateConstructor(ICapabilityProvider[] providers, boolean analysisEnabled) {
        List<Class<? extends ICapabilityProvider>> types = new ArrayList<>(providers.length);
        for (ICapabilityProvider provider : providers) {
            types.add(provider.getClass());
        }
        return getOrGenerateConstructor(types, analysisEnabled);
    }

    /** Production entry point: reads the analysis flag from the mixin config (requires the game loaded). */
    public static GeneratedDispatcher getOrGenerateDispatcher(ICapabilityProvider[] providers) {
        boolean analysisEnabled = ModernFixMixinPlugin.activeFeatureLevel().isAtLeast(FeatureLevel.BETA) &&
                ModernFixMixinPlugin.instance.isOptionEnabled("perf.faster_capabilities.bytecode_analysis.CapabilityAnalyzer");
        return getOrGenerateDispatcher(providers, analysisEnabled);
    }

    /** Plugin-independent entry point for tests/benchmarks: caller supplies {@code analysisEnabled} directly. */
    public static GeneratedDispatcher getOrGenerateDispatcher(ICapabilityProvider[] providers, boolean analysisEnabled) {
        var handle = getOrGenerateConstructor(providers, analysisEnabled);
        try {
            return (GeneratedDispatcher)handle.invokeExact((Object)providers);
        } catch (Throwable e) {
            throw new RuntimeException("Error constructing dispatcher", e);
        }
    }

    private static MethodHandle generateClass(List<Class<? extends ICapabilityProvider>> providerTypes,
                                              boolean analysisEnabled) {
        try {
            // Analyze each provider type
            List<CapabilityAnalysisResult> analysisResults = new ArrayList<>(providerTypes.size());
            for (Class<? extends ICapabilityProvider> type : providerTypes) {
                CapabilityAnalysisResult result = analysisEnabled
                        ? CapabilityAnalyzer.analyze(type)
                        : new CapabilityAnalysisResult.Indeterminate("bytecode analysis disabled");
                analysisResults.add(result);
            }

            int generatedClassId = classCounter.incrementAndGet();
            String className = "org.embeddedt.modernfix.forge.capability.CapabilityDispatcher$Generated$" + generatedClassId;

            List<ProviderDispatch> dispatches = buildDispatchList(providerTypes, analysisResults);

            // Assign a stable index to every unique CapabilityRef across all dispatches.
            // We resolve the actual Capability<?> instances here (in Java) so the generated
            // <clinit> only needs simple classDataAt calls - no reflection bytecode needed.
            LinkedHashMap<CapabilityRef, Integer> capRefIndices = collectCapabilityRefs(dispatches);
            List<Capability<?>> capValues = resolveCapabilityValues(capRefIndices);

            LOGGER.debug("Generating capability dispatcher #{} for types: [{}]", () -> generatedClassId, () -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < providerTypes.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(providerTypes.get(i).getName()).append(" -> ").append(formatAnalysisResult(analysisResults.get(i)));
                }
                return sb;
            });

            byte[] classBytes = generateClassBytes(className, providerTypes, dispatches, capRefIndices);

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
            // The constructor is adapted to take an Object and return a GeneratedDispatcher to match
            // the usage in getOrGenerateDispatcher
            return hiddenLookup.findConstructor(
                    hiddenLookup.lookupClass(),
                    MethodType.methodType(void.class, ICapabilityProvider[].class)
            ).asType(MethodType.methodType(GeneratedDispatcher.class, Object.class));
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
     * Collect all unique provider fields (index → fieldDesc) referenced by a dispatch list.
     */
    private static LinkedHashMap<Integer, String> collectProviderFields(List<ProviderDispatch> dispatches) {
        LinkedHashMap<Integer, String> fields = new LinkedHashMap<>();
        for (ProviderDispatch dispatch : dispatches) {
            if (dispatch instanceof ProviderDispatch.Guarded g) {
                fields.putIfAbsent(g.providerIndex(), g.fieldDesc());
            } else if (dispatch instanceof ProviderDispatch.Unguarded u) {
                fields.putIfAbsent(u.providerIndex(), u.fieldDesc());
            }
        }
        return fields;
    }

    private static byte[] generateClassBytes(String className, List<Class<? extends ICapabilityProvider>> providerTypes,
                                             List<ProviderDispatch> dispatches, LinkedHashMap<CapabilityRef, Integer> capRefIndices) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected ClassLoader getClassLoader() {
                return CapabilityProviderDispatcherGenerator.class.getClassLoader();
            }
        };

        String internalName = className.replace('.', '/');

        cw.visit(
                V17,
                ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
                internalName,
                null,
                GENERATED_DISPATCHER_INTERNAL_NAME,
                null
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
        generateGetCapabilityMethod(cw, className, dispatches, capRefIndices);

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
        mg.invokeConstructor(Type.getObjectType(GENERATED_DISPATCHER_INTERNAL_NAME), Method.getMethod("void <init>()"));

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
                                                    Map<CapabilityRef, Integer> capRefIndices) {
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

            if (dispatch instanceof ProviderDispatch.Guarded) {
                di = emitGuardedDispatch(mv, internalName, getCapDesc, dispatches, di, nextLabel, capRefIndices);
            } else {
                var u = (ProviderDispatch.Unguarded) dispatch;
                emitProviderGetCapability(mv, internalName, getCapDesc, u.providerIndex(), u.fieldDesc());
                di++;
            }

            // if ((result = GeneratedDispatcher.nonEmptyOrNull(result)) != null) return result;
            mv.visitMethodInsn(INVOKESTATIC, GENERATED_DISPATCHER_INTERNAL_NAME,
                    "nonEmptyOrNull", "(" + LAZY_OPTIONAL_DESC + ")" + LAZY_OPTIONAL_DESC, false);
            mv.visitVarInsn(ASTORE, 3);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitJumpInsn(IFNULL, nextLabel);
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
        if (fieldDesc.equals(ICAP_PROVIDER_DESC)) {
            mv.visitMethodInsn(INVOKEINTERFACE,
                    "net/minecraftforge/common/capabilities/ICapabilityProvider",
                    "getCapability", getCapDesc, true);
        } else {
            // Field is typed to the concrete provider class: dispatch through its vtable
            // instead of an itable scan. Cheaper in the interpreter/C1, identical under C2.
            mv.visitMethodInsn(INVOKEVIRTUAL,
                    Type.getType(fieldDesc).getInternalName(),
                    "getCapability", getCapDesc, false);
        }
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
