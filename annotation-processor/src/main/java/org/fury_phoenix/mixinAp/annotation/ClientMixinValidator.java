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

import net.fabricmc.api.Environment;

import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.fury_phoenix.mixinAp.util.TypedAccessorMap;
import org.spongepowered.asm.mixin.Mixin;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static java.util.AbstractMap.SimpleImmutableEntry;

public class ClientMixinValidator {

    private final Messager messager;

    private final Elements elemUtils;

    private final Types types;

    private final boolean debug;

    private static final TypedAccessorMap<Annotation> markers = new TypedAccessorMap<>();

    private static final Map.Entry<Class<Environment>, Function<? super Environment, ?>>
    FabricAccessor = new SimpleImmutableEntry<>(Environment.class, Environment::value);

    private static final Map.Entry<
        Class<net.minecraftforge.api.distmarker.OnlyIn>,
        Function<? super net.minecraftforge.api.distmarker.OnlyIn, ?>>
    ForgeAccessor = new SimpleImmutableEntry<>(
        net.minecraftforge.api.distmarker.OnlyIn.class,
        net.minecraftforge.api.distmarker.OnlyIn::value
    );

    private static final Map.Entry<
        Class<net.neoforged.api.distmarker.OnlyIn>,
        Function<? super net.neoforged.api.distmarker.OnlyIn, ?>>
    NeoForgeAccessor = new SimpleImmutableEntry<>(
        net.neoforged.api.distmarker.OnlyIn.class,
        net.neoforged.api.distmarker.OnlyIn::value
    );

    static {
        markers.put(FabricAccessor);
        markers.put(ForgeAccessor);
        markers.put(NeoForgeAccessor);
    }

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

            return entry.getValue().apply(marker).toString().equals("CLIENT");
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

    private Collection<Object> getTargets(TypeElement annotatedMixinClass) {
        Collection<? extends TypeMirror> clzsses = Set.of();
        Collection<? extends String> imaginaries = Set.of();
        TypeMirror MixinElement = elemUtils.getTypeElement(Mixin.class.getName()).asType();
        for (var mirror : annotatedMixinClass.getAnnotationMirrors()) {
            if(!types.isSameType(mirror.getAnnotationType(), MixinElement))
                continue;

            @SuppressWarnings("unchecked")
            var wrappedClzss = (List<? extends AnnotationValue>)
            getAnnotationValue(mirror, "value").getValue();

            clzsses = wrappedClzss.stream()
            .map(AnnotationValue::getValue)
            .map(TypeMirror.class::cast)
            .collect(Collectors.toSet());

            @SuppressWarnings("unchecked")
            var wrappedStrings = (List<? extends AnnotationValue>)
            getAnnotationValue(mirror, "targets").getValue();

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
