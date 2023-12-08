package org.fury_phoenix.mixinAp.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
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

    private final ProcessingEnvironment processingEnv;

    private final Messager messager;

    private final Elements elemUtils;

    private final Class<? extends Annotation> markerClass = getMarkerClass();

    private static final Set<String> markers = Set.of(
    "net.fabricmc.api.Environment",
    "net.minecraftforge.api.distmarker.OnlyIn",
    "net.neoforged.api.distmarker.OnlyIn");

    public ClientMixinValidator(ProcessingEnvironment env) {
        typeHandleProvider = AnnotatedMixinsAccessor.getMixinAP(env);
        processingEnv = env;
        messager = env.getMessager();
        elemUtils = env.getElementUtils();
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
        return classTargets.stream()
        .anyMatch(this::targetsClient);
    }

    private boolean targetsClient(Object classTarget) {
        return switch (classTarget) {
            case TypeMirror tm ->
                isClientMarked(tm);
            // If you're using a dollar sign in class names you are insane
            case String s ->
                targetsClient(elemUtils.getTypeElement(toSourceString(s.split("\\$")[0])).asType());
            default ->
                throw new IllegalArgumentException("Unhandled type: " + classTarget.getClass() + "\n"
                + "Stringified contents: " + classTarget.toString());
        };
    }

    private boolean isClientMarked(AnnotatedConstruct ac) {
        TypeHandle handle = getTypeHandle(ac);
        if(handle == null) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Class can't be loaded! " + ac);
            return false;
        }
        IAnnotationHandle marker = handle.getAnnotation(markerClass);

        if(marker == null) return false;

        String[] markerEnum = marker.getValue("value");

        if(markerEnum == null) return false;

        String markerEnumValue = markerEnum[1];
        return markerEnumValue.toString().equals("CLIENT");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> getMarkerClass() {
        for(var annotation : markers) {
            try {
                return (Class<Annotation>)Class.forName(annotation);
            } catch (ClassNotFoundException e) {}
        }
        return null;
    }

    private boolean warn(Object o) {
        messager.printMessage(Diagnostic.Kind.WARNING, o + " can't be loaded, so it is skipped!");
        return false;
    }

    public SimpleImmutableEntry<? extends CharSequence, ? extends CharSequence>
    getEntry(TypeElement annotatedMixinClass) {
        return new SimpleImmutableEntry<>(
            annotatedMixinClass.getQualifiedName(),
            ClientMixinValidator.getTargets(
            getAnnotationHandle(annotatedMixinClass, Mixin.class)
            ).stream().filter(this::targetsClient)
            .map(Object::toString)
            .map(ClientMixinValidator::toSourceString)
            .collect(Collectors.joining(", "))
        );
    }

    private TypeHandle getTypeHandle(Object annotatedClass) {
        return typeHandleProvider.getTypeHandle(annotatedClass);
    }

    private IAnnotationHandle getAnnotationHandle(Object annotatedClass, Class<? extends Annotation> annotation) {
        return getTypeHandle(annotatedClass).getAnnotation(annotation);
    }

    public static String toSourceString(String bytecodeName) {
        return bytecodeName.replaceAll("\\/", ".");
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
}
