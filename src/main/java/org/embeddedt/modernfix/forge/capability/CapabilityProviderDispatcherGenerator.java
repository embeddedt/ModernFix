package org.embeddedt.modernfix.forge.capability;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalyzer;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityRef;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        /** Multiple guarded dispatches collapsed into a Map lookup. */
        record Hash(int mapIndex, List<Guarded> entries) implements ProviderDispatch {}
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
    private static final String MAP_DESC = "Ljava/util/Map;";
    private static final String MAP_SIGNATURE = "Ljava/util/Map<Lnet/minecraftforge/common/capabilities/Capability<*>;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;>;";

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
        List<Class<? extends ICapabilityProvider>> types = Arrays.stream(providers)
                .<Class<? extends ICapabilityProvider>>map(ICapabilityProvider::getClass)
                .toList();
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

            ModernFix.LOGGER.debug("Generating capability dispatcher #{} for types: [{}]", () -> generatedClassId, () -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < providerTypes.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(providerTypes.get(i).getName()).append(" -> ").append(formatAnalysisResult(analysisResults.get(i)));
                }
                return sb;
            });

            byte[] classBytes = generateClassBytes(className, providerTypes, analysisResults);

            // Define the hidden class
            MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                    classBytes,
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
        int mapIndex = 0;
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

            if (!tryCollapseToHash(run, mapIndex, result)) {
                result.addAll(run);
            } else {
                mapIndex++;
            }
        }
        return result;
    }

    /**
     * Attempt to collapse a run of Guarded dispatches into a Hash.
     * Returns true if a Hash was emitted, false if the run should be kept as-is.
     */
    private static boolean tryCollapseToHash(List<ProviderDispatch> run, int mapIndex, List<ProviderDispatch> result) {
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

        result.add(new ProviderDispatch.Hash(mapIndex, hashEntries));
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
            }
            // Hash entries don't need provider fields - map reads from constructor array
        }
        return fields;
    }

    private static byte[] generateClassBytes(String className, List<Class<? extends ICapabilityProvider>> providerTypes, List<CapabilityAnalysisResult> analysisResults) {
        List<ProviderDispatch> dispatches = optimizeDispatches(buildDispatchList(providerTypes, analysisResults));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected ClassLoader getClassLoader() {
                return CapabilityProviderDispatcherGenerator.class.getClassLoader();
            }
        };

        // Class declaration: implements ICapabilityProvider
        cw.visit(
                V17,
                ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
                className.replace('.', '/'),
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

        // Generate map fields for Hash dispatches
        for (ProviderDispatch dispatch : dispatches) {
            if (dispatch instanceof ProviderDispatch.Hash hash) {
                cw.visitField(ACC_PRIVATE | ACC_FINAL, "capMap" + hash.mapIndex(), MAP_DESC, MAP_SIGNATURE, null).visitEnd();
            }
        }

        // Generate constructor
        generateConstructor(cw, className, providerFields, dispatches);

        // Generate getCapability method with sided parameter
        generateGetCapabilityMethod(cw, className, dispatches);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateConstructor(ClassWriter cw, String className, Map<Integer, String> providerFields, List<ProviderDispatch> dispatches) {
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

        // Build hash maps
        for (ProviderDispatch dispatch : dispatches) {
            if (dispatch instanceof ProviderDispatch.Hash hash) {
                generateMapConstruction(mg, classType, hash);
            }
        }

        mg.returnValue();
        mg.endMethod();
    }

    private static void generateMapConstruction(GeneratorAdapter mg, Type classType, ProviderDispatch.Hash hash) {
        List<ProviderDispatch.Guarded> entries = hash.entries();
        mg.loadThis(); // for PUTFIELD at the end

        mg.push(entries.size());
        mg.visitTypeInsn(ANEWARRAY, "java/util/Map$Entry");
        for (int i = 0; i < entries.size(); i++) {
            ProviderDispatch.Guarded g = entries.get(i);
            mg.dup();
            mg.push(i);
            mg.visitFieldInsn(GETSTATIC, g.capability().owner(), g.capability().fieldName(), CAPABILITY_DESC);
            mg.loadArg(0);
            mg.push(g.providerIndex());
            mg.arrayLoad(Type.getType(ICAP_PROVIDER_DESC));
            mg.visitMethodInsn(INVOKESTATIC, "java/util/Map", "entry",
                    "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;", true);
            mg.visitInsn(AASTORE);
        }
        mg.visitMethodInsn(INVOKESTATIC, "java/util/Map", "ofEntries",
                "([Ljava/util/Map$Entry;)Ljava/util/Map;", true);

        mg.putField(classType, "capMap" + hash.mapIndex(), Type.getType(MAP_DESC));
    }

    private static void generateGetCapabilityMethod(ClassWriter cw, String className, List<ProviderDispatch> dispatches) {
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
        // slot 4 = ICapabilityProvider provider (Hash paths only)
        boolean usesProviderLocal = dispatches.stream().anyMatch(d -> d instanceof ProviderDispatch.Hash);

        for (int di = 0; di < dispatches.size(); ) {
            ProviderDispatch dispatch = dispatches.get(di);
            Label nextLabel = new Label();

            if (dispatch instanceof ProviderDispatch.Hash hash) {
                emitHashDispatch(mv, internalName, getCapDesc, hash, nextLabel);
                di++;
            } else if (dispatch instanceof ProviderDispatch.Guarded) {
                di = emitGuardedDispatch(mv, internalName, getCapDesc, dispatches, di, nextLabel);
            } else {
                var u = (ProviderDispatch.Unguarded) dispatch;
                emitProviderGetCapability(mv, internalName, getCapDesc, u.providerIndex(), u.fieldDesc());
                di++;
            }

            // If not a hash lookup, then optimistically check for the exact LazyOptional.empty() value to avoid
            // an isPresent call
            if (!(dispatch instanceof ProviderDispatch.Hash)) {
                // if (result == EMPTY) goto next
                mv.visitVarInsn(ALOAD, 3);
                mv.visitFieldInsn(GETSTATIC,
                        "org/embeddedt/modernfix/forge/capability/CapabilityProviderDispatcherGenerator",
                        "EMPTY", LAZY_OPTIONAL_DESC);
                mv.visitJumpInsn(IF_ACMPEQ, nextLabel);
            }

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
        if (usesProviderLocal) {
            mv.visitLocalVariable("provider", ICAP_PROVIDER_DESC, null, methodStart, methodEnd, 4);
        }

        mv.visitMaxs(0, 0);  // Computed by COMPUTE_MAXS
        mv.visitEnd();
    }

    private static void emitHashDispatch(MethodVisitor mv, String internalName, String getCapDesc,
                                          ProviderDispatch.Hash hash, Label nextLabel) {
        // ICapabilityProvider provider = (ICapabilityProvider) this.capMapN.get(cap);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, internalName, "capMap" + hash.mapIndex(), MAP_DESC);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitTypeInsn(CHECKCAST, "net/minecraftforge/common/capabilities/ICapabilityProvider");
        mv.visitVarInsn(ASTORE, 4);

        // if (provider == null) goto next
        mv.visitVarInsn(ALOAD, 4);
        mv.visitJumpInsn(IFNULL, nextLabel);

        // LazyOptional<T> result = provider.getCapability(cap, side)
        mv.visitVarInsn(ALOAD, 4);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEINTERFACE,
                "net/minecraftforge/common/capabilities/ICapabilityProvider",
                "getCapability", getCapDesc, true);
        mv.visitVarInsn(ASTORE, 3);
    }

    /**
     * Emit guarded dispatch for one or more consecutive Guarded entries sharing the same providerIndex.
     * When multiple caps map to the same provider, an OR-chain guard is emitted instead of separate dispatches.
     *
     * @return the updated dispatch index (past the consumed group)
     */
    private static int emitGuardedDispatch(MethodVisitor mv, String internalName, String getCapDesc,
                                           List<ProviderDispatch> dispatches, int di, Label nextLabel) {
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
            mv.visitFieldInsn(GETSTATIC, ref.owner(), ref.fieldName(), CAPABILITY_DESC);
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