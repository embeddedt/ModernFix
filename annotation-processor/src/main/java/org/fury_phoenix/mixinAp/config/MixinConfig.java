package org.fury_phoenix.mixinAp.config;

import java.util.List;
import com.google.gson.annotations.SerializedName;

public record MixinConfig(
    boolean required,
    String minVersion,
    @SerializedName("package")
    String packageName,
    String plugin,
    String compatabilityLevel,
    @SerializedName("mixins")
    List<String> commonMixins,
    @SerializedName("client")
    List<String> clientMixins,
    InjectorOptions injectors, OverwriteOptions overwrites
    ) {
    public MixinConfig(String packageName, List<String> commonMixins, List<String> clientMixins) {
        this(true, "0.8", packageName, "org.embeddedt.modernfix.core.ModernFixMixinPlugin", "JAVA_8",
        commonMixins, clientMixins, InjectorOptions.DEFAULT, OverwriteOptions.DEFAULT);
    }
    public record InjectorOptions(int defaultRequire) {
        public static final InjectorOptions DEFAULT = new InjectorOptions(1);
    }
    public record OverwriteOptions(boolean conformVisibility) {
        public static final OverwriteOptions DEFAULT = new OverwriteOptions(true);
    }
}