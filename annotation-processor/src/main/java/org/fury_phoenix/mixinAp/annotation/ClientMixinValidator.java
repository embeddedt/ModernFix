package org.fury_phoenix.mixinAp.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    
    public ClientMixinValidator(ProcessingEnvironment env) {
        typeHandleProvider = AnnotatedMixinsAccessor.getMixinAP(env);
        processingEnv = env;
    }
    // some sort of javac bug with method reference resolution for mixed staticness
    public boolean targetsClient(TypeElement annotatedMixinClass) {
        return targetsClient(
        ClientMixinValidator.getTargets(
            getAnnotationHandle(annotatedMixinClass, Mixin.class)
        )) && !getAnnotationHandle(annotatedMixinClass, ClientOnlyMixin.class).exists();
    }

    private boolean targetsClient(List<?> classTargets) {
        return classTargets.stream()
        .anyMatch(this::targetsClient);
    }

    private boolean targetsClient(Object classTarget) {
        return switch (classTarget) {
            case null -> throw new IllegalArgumentException("Can't be empty!");
            case TypeMirror tm ->
                EnvType.CLIENT == getEnvType(tm);
            // If you're using a dollar sign in class names you are insane
            case String s && (getEnvType(s) != null) ->
                EnvType.CLIENT == getEnvType(s);
            case String s -> warn(s);
            default ->
                throw new IllegalArgumentException("Unhandled type: " + classTarget.getClass() + "\n"
                + "Stringified contents: " + classTarget.toString());
        };
    }

    private EnvType getEnvType(Object o) {
        TypeHandle handle = getTypeHandle(o);
        if(handle.isImaginary())
            return null;
        Environment env = handle.getElement().getAnnotation(Environment.class);
        if(env == null)
            return null;
        return env.value();
    }

    private boolean warn(String s) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, s + "can't be loaded, so it is skipped!");
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
