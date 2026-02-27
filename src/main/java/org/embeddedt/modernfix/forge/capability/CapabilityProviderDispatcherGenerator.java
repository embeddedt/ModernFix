package org.embeddedt.modernfix.forge.capability;

import net.minecraftforge.common.capabilities.ICapabilityProvider;
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
import java.util.List;
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
    private static final String GENERATED_CLASSES_FOLDER = System.getProperty("modernfix.generatedCapabilityDispatcherClassDumpFolder", "");

    private static final ConcurrentHashMap<List<Class<? extends ICapabilityProvider>>, MethodHandle> cache =
            new ConcurrentHashMap<>();

    private static final AtomicInteger classCounter = new AtomicInteger(0);
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

    // Type descriptors
    private static final String ICAP_PROVIDER_DESC = "Lnet/minecraftforge/common/capabilities/ICapabilityProvider;";
    private static final String CAPABILITY_DESC = "Lnet/minecraftforge/common/capabilities/Capability;";
    private static final String LAZY_OPTIONAL_DESC = "Lnet/minecraftforge/common/util/LazyOptional;";
    private static final String DIRECTION_DESC = "Lnet/minecraft/core/Direction;";

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

    private static byte[] generateClassBytes(String className, List<Class<? extends ICapabilityProvider>> providerTypes, List<CapabilityAnalysisResult> analysisResults) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Class declaration: implements ICapabilityProvider
        cw.visit(
                V17,
                ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
                className.replace('.', '/'),
                null,
                "java/lang/Object",
                new String[] { "net/minecraftforge/common/capabilities/ICapabilityProvider" }
        );

        // Compute field descriptors: use concrete type when possible for JIT devirtualization
        String[] fieldDescs = new String[providerTypes.size()];
        for (int i = 0; i < providerTypes.size(); i++) {
            Class<? extends ICapabilityProvider> type = providerTypes.get(i);
            fieldDescs[i] = (!type.isHidden() && Modifier.isPublic(type.getModifiers()))
                    ? Type.getDescriptor(type) : ICAP_PROVIDER_DESC;
        }

        // Generate final fields for each provider
        for (int i = 0; i < providerTypes.size(); i++) {
            cw.visitField(
                    ACC_PRIVATE | ACC_FINAL,
                    "provider" + i,
                    fieldDescs[i],
                    null,
                    null
            ).visitEnd();
        }

        // Generate constructor
        generateConstructor(cw, className, providerTypes.size(), fieldDescs);

        // Generate getCapability method with sided parameter
        generateGetCapabilityMethod(cw, className, fieldDescs, analysisResults);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateConstructor(ClassWriter cw, String className, int providerCount, String[] fieldDescs) {
        Method constructor = Method.getMethod("void <init>(net.minecraftforge.common.capabilities.ICapabilityProvider[])");
        GeneratorAdapter mg = new GeneratorAdapter(ACC_PUBLIC, constructor, null, null, cw);

        // Call super constructor
        mg.loadThis();
        mg.invokeConstructor(Type.getType(Object.class), Method.getMethod("void <init>()"));

        // Unpack array into final fields
        for (int i = 0; i < providerCount; i++) {
            Type fieldType = Type.getType(fieldDescs[i]);
            mg.loadThis();              // this
            mg.loadArg(0);              // array
            mg.push(i);                 // index
            mg.arrayLoad(Type.getType(ICAP_PROVIDER_DESC)); // array[i]
            if (!fieldDescs[i].equals(ICAP_PROVIDER_DESC)) {
                mg.checkCast(fieldType);
            }
            mg.putField(
                    Type.getObjectType(className.replace('.', '/')),
                    "provider" + i,
                    fieldType
            );
        }

        mg.returnValue();
        mg.endMethod();
    }

    private static void generateGetCapabilityMethod(ClassWriter cw, String className, String[] fieldDescs, List<CapabilityAnalysisResult> analysisResults) {
        int providerCount = fieldDescs.length;

        // Method: <T> LazyOptional<T> getCapability(Capability<T>, Direction)
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "getCapability",
                "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC,
                "<T:Ljava/lang/Object;>(" + CAPABILITY_DESC.replace(";", "<TT;>;") + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC.replace(";", "<TT;>;"),
                null
        );

        mv.visitCode();

        // Generate unrolled dispatch loop
        // For each provider, call getCapability and check if present
        Label endLabel = new Label();

        for (int i = 0; i < providerCount; i++) {
            CapabilityAnalysisResult analysis = analysisResults.get(i);
            Label nextLabel = new Label();

            // AlwaysEmpty: skip code generation for this provider entirely
            if (analysis instanceof CapabilityAnalysisResult.AlwaysEmpty) {
                continue;
            }

            // KnownCapabilities: emit guard checks before dispatch
            if (analysis instanceof CapabilityAnalysisResult.KnownCapabilities known
                    && known.capabilities().size() <= 5) {
                if (known.capabilities().size() == 1) {
                    // Single cap: if (cap != KNOWN_CAP) goto nextProvider
                    CapabilityRef ref = known.capabilities().iterator().next();
                    mv.visitVarInsn(ALOAD, 1); // cap parameter
                    mv.visitFieldInsn(GETSTATIC, ref.owner(), ref.fieldName(), CAPABILITY_DESC);
                    mv.visitJumpInsn(IF_ACMPNE, nextLabel);
                } else {
                    // Multiple caps: check each, jump to callProvider on match
                    Label callProvider = new Label();
                    for (CapabilityRef ref : known.capabilities()) {
                        mv.visitVarInsn(ALOAD, 1); // cap parameter
                        mv.visitFieldInsn(GETSTATIC, ref.owner(), ref.fieldName(), CAPABILITY_DESC);
                        mv.visitJumpInsn(IF_ACMPEQ, callProvider);
                    }
                    // No match, skip this provider
                    mv.visitJumpInsn(GOTO, nextLabel);
                    mv.visitLabel(callProvider);
                }
            }
            // Indeterminate: no guard, fall through to dispatch

            // LazyOptional<T> result = this.providerN.getCapability(cap, side);
            mv.visitVarInsn(ALOAD, 0);  // this
            mv.visitFieldInsn(
                    GETFIELD,
                    className.replace('.', '/'),
                    "provider" + i,
                    fieldDescs[i]
            );
            mv.visitVarInsn(ALOAD, 1);  // cap parameter
            mv.visitVarInsn(ALOAD, 2);  // side parameter
            mv.visitMethodInsn(
                    INVOKEINTERFACE,
                    "net/minecraftforge/common/capabilities/ICapabilityProvider",
                    "getCapability",
                    "(" + CAPABILITY_DESC + DIRECTION_DESC + ")" + LAZY_OPTIONAL_DESC,
                    true
            );

            // Store result in local variable
            mv.visitVarInsn(ASTORE, 3);

            // if (result == null) continue to next;
            mv.visitVarInsn(ALOAD, 3);
            mv.visitJumpInsn(IFNULL, nextLabel);

            // if (result.isPresent()) return result;
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "net/minecraftforge/common/util/LazyOptional",
                    "isPresent",
                    "()Z",
                    false
            );
            mv.visitJumpInsn(IFEQ, nextLabel);

            // return result
            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ARETURN);

            mv.visitLabel(nextLabel);
        }

        // If no provider returned a capability, return empty
        mv.visitLabel(endLabel);
        mv.visitMethodInsn(
                INVOKESTATIC,
                "net/minecraftforge/common/util/LazyOptional",
                "empty",
                "()" + LAZY_OPTIONAL_DESC,
                false
        );
        mv.visitInsn(ARETURN);

        mv.visitMaxs(0, 0);  // Computed by COMPUTE_MAXS
        mv.visitEnd();
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