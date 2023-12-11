package org.fury_phoenix.mixinAp.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
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

    private static final Iterable<String> markers = Set.of(
    "net.fabricmc.api.Environment",
    "net.minecraftforge.api.distmarker.OnlyIn",
    "net.neoforged.api.distmarker.OnlyIn");

    private static final Collection<String> unannotatedClasses = new HashSet<>();

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
        for (var marker : getPlatformMarkers(markers)) {
            IAnnotationHandle handle = getAnnotationHandle(te, marker);
            if(!handle.exists()) continue;

            String[] enumValue = handle.getValue("value");
            if(enumValue==null) continue;

            return enumValue[1].equals("CLIENT");
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

    private static Iterable<Class<? extends Annotation>>
    getPlatformMarkers(Iterable<String> markers) {
        Set<Class<? extends Annotation>> platformClasses = new HashSet<>();
        for(var marker : markers) {
            getMarkerClass(marker).ifPresent(platformClasses::add);
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

    public static String toSourceString(String bytecodeName) {
        return bytecodeName.replaceAll("\\/", ".");
    }
}
