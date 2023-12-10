package org.fury_phoenix.mixinAp.annotation;

import com.google.common.base.Throwables;

import java.lang.annotation.Annotation;
import java.lang.invoke.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final Collection<String> markers = Set.of(
    "net.fabricmc.api.Environment",
    "net.minecraftforge.api.distmarker.OnlyIn",
    "net.neoforged.api.distmarker.OnlyIn");

    private static final Collection<String> markerEnums = Set.of(
    "net.fabricmc.api.EnvType",
    "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.api.distmarker.Dist");

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

    private boolean targetsClient(List<?> classTargets) {
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
        Annotation marker = te.getAnnotation(getMarkerClass(markers));
        if(marker == null) {
            if(debug && unannotatedClasses.add(te.toString())) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                "Missing " + getMarkerClass(markers).getCanonicalName() + " on " + te + "!");
            }
            return false;
        }
        try {
            Object value = getAccessor().invoke(marker);
            return value.toString().equals("CLIENT");
        } catch (Throwable e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Fatal error:" +
            Throwables.getStackTraceAsString(e));
        }
        return false;
    }

    private boolean warn(Object o) {
        messager.printMessage(Diagnostic.Kind.WARNING,
        toSourceString(o.toString()) + " can't be loaded, so it is skipped!");
        return false;
    }

    private MethodHandle getAccessor() throws ReflectiveOperationException {
        Class<? extends Annotation> markerClass = getMarkerClass(markers);
        Class<? extends Enum<?>> markerEnumClass = getMarkerEnumClass(markerEnums);
        MethodType enumValueAccessorType = MethodType.methodType(markerEnumClass);
        return lookup.findVirtual(markerClass, "value", enumValueAccessorType);
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

    private static List<Object> getTargets(IAnnotationHandle mixinAnnotation) {
        Collection<? extends TypeMirror> clzss = mixinAnnotation.getList("value");
        Collection<? extends String> imaginary = mixinAnnotation.getList("targets");
        List<Object> targets =
        Stream.of(clzss, imaginary)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
        return targets;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> getMarkerClass(Collection<String> markerSet) {
        for(var annotation : markerSet) {
            try {
                return (Class<Annotation>)Class.forName(annotation);
            } catch (ClassNotFoundException e) {}
        }
        throw new RuntimeException();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Enum<?>> getMarkerEnumClass(Collection<String> enumSet) {
        for(var enumClass : enumSet) {
            try {
                return (Class<Enum<?>>)Class.forName(enumClass);
            } catch (ClassNotFoundException e) {}
        }
        throw new RuntimeException();
    }

    public static String toSourceString(String bytecodeName) {
        return bytecodeName.replaceAll("\\/", ".");
    }

}
