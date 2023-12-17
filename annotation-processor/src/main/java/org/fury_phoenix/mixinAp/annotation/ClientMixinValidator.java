package org.fury_phoenix.mixinAp.annotation;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.AbstractMap.SimpleImmutableEntry;

public class ClientMixinValidator {

    private final Messager messager;

    private final Elements elemUtils;

    private final Types types;

    private final boolean debug;

    /*
     * @author Fury_Phoenix
     * @reason This is covariant for ClientMixinValidator.markers
     * whilst the direct reference is not as it doesn't cover Annotation
     */
    private static final Function<net.fabricmc.api.Environment, ?>
    EnvironmentAccessor = net.fabricmc.api.Environment::value;

    private static final Function<net.minecraftforge.api.distmarker.OnlyIn, ?>
    ForgeAccessor = net.minecraftforge.api.distmarker.OnlyIn::value;

    private static final Function<net.neoforged.api.distmarker.OnlyIn, ?>
    NeoForgeAccessor = net.neoforged.api.distmarker.OnlyIn::value;

    /*
     * @author Fury_Phoenix
     * @reason Partial duck-typing
     */
    private static final Map
    <Class<? extends Annotation>, Function<? extends Annotation, ?>>
    markers = Map.of(net.fabricmc.api.Environment.class, EnvironmentAccessor,
    net.minecraftforge.api.distmarker.OnlyIn.class, ForgeAccessor,
    net.neoforged.api.distmarker.OnlyIn.class, NeoForgeAccessor);

    private static final Collection<String> unannotatedClasses = new HashSet<>();

    public ClientMixinValidator(ProcessingEnvironment env) {
        debug = Boolean.valueOf(env.getOptions().get("org.fury_phoenix.mixinAp.validator.debug"));
        messager = env.getMessager();
        elemUtils = env.getElementUtils();
        types = env.getTypeUtils();
    }

    public boolean validateMixin(TypeElement annotatedMixinClass) {
        return targetsClient(annotatedMixinClass) &&
        (annotatedMixinClass.getAnnotation(ClientOnlyMixin.class) == null);
    }

    public boolean targetsClient(TypeElement annotatedMixinClass) {
        return targetsClient(getTargets(annotatedMixinClass));
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
        for (var entry : markers.entrySet()) {
            var marker = te.getAnnotation(entry.getKey());
            if(marker == null) continue;

            // Pretend to accept Annotations
            @SuppressWarnings("unchecked")
            boolean isClient = ((Function<Annotation, ?>)entry.getValue())
            .apply(marker).toString().equals("CLIENT");
            return isClient;
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

    public Map.Entry<? extends CharSequence, ? extends CharSequence>
    getClientMixinEntry(TypeElement annotatedMixinClass) {
        return new SimpleImmutableEntry<>(
            annotatedMixinClass.getQualifiedName(),
            getTargets(annotatedMixinClass)
            .stream()
            .filter(this::targetsClient)
            .map(Object::toString)
            .map(ClientMixinValidator::toSourceString)
            .collect(Collectors.joining(", "))
        );
    }

    private Collection<Object> getTargets(TypeElement mixinAnnotatedClass) {
        Collection<? extends TypeMirror> clzsses = Set.of();
        Collection<? extends String> imaginaries = Set.of();
        TypeMirror MixinElement = elemUtils.getTypeElement(Mixin.class.getName()).asType();
        for (var annotationMirror : mixinAnnotatedClass.getAnnotationMirrors()) {
            if(!annotationMirror.getAnnotationType().equals(MixinElement))
                continue;

            @SuppressWarnings("unchecked")
            var wrappedClzss = (List<? extends AnnotationValue>)
            getAnnotationValue(annotationMirror, "value").getValue();

            clzsses = wrappedClzss.stream()
            .map(AnnotationValue::getValue)
            .map(TypeMirror.class::cast)
            .collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            var wrappedStrings = (List<? extends AnnotationValue>)
            getAnnotationValue(annotationMirror, "targets").getValue();

            imaginaries = wrappedStrings.stream()
            .map(AnnotationValue::getValue)
            .map(String.class::cast)
            .collect(Collectors.toSet());
        }
        return Stream.of(clzsses, imaginaries)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
    }

    public static String toSourceString(String bytecodeName) {
        return bytecodeName.replaceAll("\\/", ".");
    }
}
