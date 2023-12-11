package org.fury_phoenix.mixinAp.annotation;

import com.google.common.base.Throwables;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.util.asm.IAnnotationHandle;
import org.spongepowered.tools.obfuscation.AnnotatedMixinsAccessor;
import org.spongepowered.tools.obfuscation.interfaces.ITypeHandleProvider;
import org.spongepowered.tools.obfuscation.mirror.TypeHandle;

import static java.util.AbstractMap.SimpleImmutableEntry;

public class ClientMixinValidator {

    private final ITypeHandleProvider typeHandleProvider;

    private final Messager messager;

    private final Elements elemUtils;

    private final Types types;

    private final boolean debug;

    private static final Map<String, String> markers = Map.of(
    "net.fabricmc.api.Environment", "net.fabricmc.api.EnvType",
    "net.minecraftforge.api.distmarker.OnlyIn", "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.api.distmarker.OnlyIn", "net.neoforged.api.distmarker.Dist");

    private static final Collection<String> unannotatedClasses = new HashSet<>();

    private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    public ClientMixinValidator(ProcessingEnvironment env)
    throws ReflectiveOperationException {
        typeHandleProvider = AnnotatedMixinsAccessor.getMixinAP(env);
        debug = Boolean.valueOf(env.getOptions().get("org.fury_phoenix.mixinAp.validator.debug"));
        messager = env.getMessager();
        elemUtils = env.getElementUtils();
        types = env.getTypeUtils();
    }

    public boolean validateMixin(TypeElement annotatedMixinClass) {
        return targetsClient(annotatedMixinClass) &&
        !getAnnotationHandle(annotatedMixinClass, ClientOnlyMixin.class).exists();
    }

    public boolean targetsClient(TypeElement annotatedMixinClass) {
        return targetsClient(
        ClientMixinValidator.getTargets(
            getAnnotationHandle(annotatedMixinClass, Mixin.class)
        ));
    }

    private boolean targetsClient(Collection<?> classTargets) {
        return classTargets.stream().anyMatch(this::targetsClient);
    }

    private boolean targetsClient(Object classTarget) {
        return switch (classTarget) {
            case TypeElement te ->
                isClientMarked(te);
            case TypeMirror tm -> {
                var el = types.asElement(tm);
                yield el != null ? targetsClient(el) : warn("TypeMirror of " + tm);
            }
            // If you're using a dollar sign in class names you are insane
            case String s -> {
                var te =
                elemUtils.getTypeElement(toSourceString(s.split("\\$")[0]));
                yield te != null ? targetsClient(te) : warn(s);
            }
            default ->
                throw new IllegalArgumentException("Unhandled type: "
                + classTarget.getClass() + "\n" + "Stringified contents: "
                + classTarget.toString());
        };
    }

    private boolean isClientMarked(TypeElement te) {
        for (var entry : getPlatformClasses(markers).entrySet()) {
            Annotation marker = te.getAnnotation(entry.getKey());
            if(marker == null) continue;

            Optional<MethodHandle> accessor = getAccessor(entry.getKey(), entry.getValue());
            Optional<String> enumValue =  accessor.map((mh) -> this.invoke(mh, marker))
            .map(Object::toString);
            if(enumValue.isPresent())
                return enumValue.orElseThrow().equals("CLIENT");
            return false;
        }
        if(debug && unannotatedClasses.add(te.toString())) {
            messager.printMessage(Diagnostic.Kind.WARNING,
            "No marker annotations present on " + te + "!");
        }
        return false;
    }

    private boolean warn(Object o) {
        messager.printMessage(Diagnostic.Kind.WARNING,
        toSourceString(o.toString()) + " can't be loaded, so it is skipped!");
        return false;
    }

    private static Optional<MethodHandle> getAccessor(Class<? extends Annotation> markerClass,
    Class<? extends Enum<?>> enumClass) {
        MethodType enumValueAccessorType = MethodType.methodType(enumClass);
        try {
        return Optional.of(lookup.findVirtual(markerClass, "value", enumValueAccessorType));
        } catch (ReflectiveOperationException e) {}
        return Optional.empty();
    }

    public SimpleImmutableEntry<? extends CharSequence, ? extends CharSequence>
    getEntry(TypeElement annotatedMixinClass) {
        return new SimpleImmutableEntry<>(
            annotatedMixinClass.getQualifiedName(),
            ClientMixinValidator.getTargets(
            getAnnotationHandle(annotatedMixinClass, Mixin.class)
            ).stream()
            .filter(this::targetsClient)
            .map(Object::toString)
            .map(ClientMixinValidator::toSourceString)
            .collect(Collectors.joining(", "))
        );
    }

    private TypeHandle getTypeHandle(Object annotatedClass) {
        return typeHandleProvider.getTypeHandle(annotatedClass);
    }

    private IAnnotationHandle getAnnotationHandle
    (Object annotatedClass, Class<? extends Annotation> annotation) {
        return getTypeHandle(annotatedClass).getAnnotation(annotation);
    }

    private static Collection<Object> getTargets(IAnnotationHandle mixinAnnotation) {
        Collection<? extends TypeMirror> clzss = mixinAnnotation.getList("value");
        Collection<? extends String> imaginary = mixinAnnotation.getList("targets");
        return Stream.of(clzss, imaginary)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
    }

    private static Map<Class<? extends Annotation>, Class<? extends Enum<?>>>
    getPlatformClasses(Map<String, String> map) {
        Map<Class<? extends Annotation>, Class<? extends Enum<?>>> platformClasses = new HashMap<>();
        for(var entry : map.entrySet()) {
            Optional<Class<? extends Annotation>> annotation = getMarkerClass(entry.getKey());
            Optional<Class<? extends Enum<?>>> enumClz = getMarkerEnumClass(entry.getValue());
            if(!annotation.isEmpty() && !enumClz.isEmpty())
                platformClasses.put(annotation.orElseThrow(), enumClz.orElseThrow());
        }
        return platformClasses;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends Annotation>> getMarkerClass(String marker) {
        try {
            return Optional.of((Class<? extends Annotation>)Class.forName(marker));
        } catch (ClassNotFoundException e) {}
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static Optional<Class<? extends Enum<?>>> getMarkerEnumClass(String enumClz) {
        try {
            Optional.of((Class<? extends Enum<?>>)Class.forName(enumClz));
        } catch (ClassNotFoundException e) {}
        return Optional.empty();
    }

    public static String toSourceString(String bytecodeName) {
        return bytecodeName.replaceAll("\\/", ".");
    }

    private Object invoke(MethodHandle mh, Annotation marker) {
        try { return mh.invoke(marker); }
        catch (Throwable e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Fatal error:" +
            Throwables.getStackTraceAsString(e));
        }
        return null;
    }
}
