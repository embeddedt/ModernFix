package org.fury_phoenix.mixinAp.config;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.StandardLocation;

public record MixinConfig(
    boolean required,
    String minVersion,
    @SerializedName("package")
    String packageName,
    String plugin,
    String compatibilityLevel,
    @SerializedName("mixins")
    List<String> commonMixins,
    @SerializedName("client")
    List<String> clientMixins,
    InjectorOptions injectors, OverwriteOptions overwrites
    ) {
    public MixinConfig(String packageName, List<String> commonMixins, List<String> clientMixins) {
        this(true, "0.8", packageName, "org.embeddedt.modernfix.core.ModernFixMixinPlugin", "JAVA_17",
        commonMixins, clientMixins, InjectorOptions.DEFAULT, OverwriteOptions.DEFAULT);
    }
    public record InjectorOptions(int defaultRequire) {
        public static final InjectorOptions DEFAULT = new InjectorOptions(1);
    }
    public record OverwriteOptions(boolean conformVisibility) {
        public static final OverwriteOptions DEFAULT = new OverwriteOptions(true);
    }

    public void generateMixinConfig(ProcessingEnvironment env) throws IOException {
        try (
        Writer mixinConfigWriter = env.getFiler()
        .createResource(StandardLocation.SOURCE_OUTPUT, "",
            MixinConfig.computeMixinConfigPath(
                Optional.of(env.getOptions().get("rootProject.name")),
                Optional.ofNullable(env.getOptions().get("project.name"))
            )
        ).openWriter()
        ) {
            String mixinConfig = new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(this);

            mixinConfigWriter.write(mixinConfig);
            mixinConfigWriter.write("\n");
        } catch (IOException e) { throw e; }
    }

    private static String computeMixinConfigPath(Optional<String> rootProjectName, Optional<String> projectName) {
        return "resources/" +
        rootProjectName.get() +
        (projectName.isPresent() ? "-" : "") +
        projectName.orElse("") +
        ".mixins.json";
    }
}
