package org.fury_phoenix.mixinAp.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

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
    
    public ClientMixinValidator(ProcessingEnvironment env) {
        typeHandleProvider = AnnotatedMixinsAccessor.getMixinAP(env);
        processingEnv = env;
        messager = env.getMessager();
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
                EnvType.CLIENT == getEnvType(tm);
            // If you're using a dollar sign in class names you are insane
            case String s ->
                EnvType.CLIENT == getEnvType(s.split("\\$")[0]);
            default ->
                throw new IllegalArgumentException("Unhandled type: " + classTarget.getClass() + "\n"
                + "Stringified contents: " + classTarget.toString());
        };
    }

    private EnvType getEnvType(Object o) {
        TypeHandle handle = getTypeHandle(o);
        if(handle == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, o + " can't be found, skipping!");
            return null;
        }
        String[] stringEnum = handle.getAnnotation(Environment.class).getValue("value");
        if(stringEnum == null) return null;
        return Enum.valueOf(EnvType.class, stringEnum[1]);
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
