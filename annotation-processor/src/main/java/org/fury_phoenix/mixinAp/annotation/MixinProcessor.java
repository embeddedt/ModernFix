package org.fury_phoenix.mixinAp.annotation;

import com.google.auto.service.AutoService;
import com.google.common.base.Throwables;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import org.fury_phoenix.mixinAp.config.MixinConfig;

@SupportedAnnotationTypes({"org.spongepowered.asm.mixin.Mixin", "org.embeddedt.modernfix.annotation.ClientOnlyMixin"})
@SupportedOptions({"rootProject.name", "project.name"})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class MixinProcessor extends AbstractProcessor {

    // Remember to call toString when using aliases
    private static final Map<String, String> aliases = Map.of(
    "Mixin", "mixins",
    "ClientOnlyMixin", "client"
    );

    private String rootProjectName;

    private final Map<String, List<String>> mixinConfigList = new HashMap<>();

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        rootProjectName = processingEnv.getOptions().get("rootProject.name");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(roundEnv.processingOver()){
            // set difference of mixins and client
            List<String> commonSet = mixinConfigList.get("mixins");
            commonSet.removeAll(mixinConfigList.get("client"));
            // create record for serialization, compute package name
            String packageName = commonSet.get(0).split("(?<=mixin)")[0];
            finalizeMixinConfig();
            generateMixinConfig(
                new MixinConfig(packageName,
                    mixinConfigList.get("mixins"),
                    mixinConfigList.get("client")
                )
            );
        } else {
            processMixins(annotations, roundEnv);
        }
        return false;
    }

    private void generateMixinConfig(Object config) {
        try (Writer mixinConfigWriter = processingEnv.getFiler()
        .createResource(StandardLocation.SOURCE_OUTPUT, "", computeMixinConfigPath())
        .openWriter()) {
            String mixinConfig = new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(config);

            mixinConfigWriter.write(mixinConfig);
            mixinConfigWriter.write("\n");
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Fatal error:" +
            Throwables.getStackTraceAsString(e));
        }
    }
    
    private String computeMixinConfigPath() {
        var projectName = processingEnv.getOptions().get("project.name");
        return "resources/" + 
        rootProjectName + 
        (projectName != null ? "-" + projectName : "") +
        ".mixins.json";
    }

    private void processMixins(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedMixins = roundEnv.getElementsAnnotatedWith(annotation);

            List<String> mixins = annotatedMixins.stream()
            .filter(TypeElement.class::isInstance)
            .map(TypeElement.class::cast)
            .map(TypeElement::toString)
            .collect(Collectors.toList());

            mixinConfigList.putIfAbsent(aliases.get(annotation.getSimpleName().toString()), mixins);
        }
    }
    
    private void finalizeMixinConfig() {
        // relativize class names
        for(var list : mixinConfigList.values()) {
            list.replaceAll(className -> className.split("(?<=mixin.)")[1]);
        }
    }
}