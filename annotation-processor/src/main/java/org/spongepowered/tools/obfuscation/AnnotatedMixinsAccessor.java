package org.spongepowered.tools.obfuscation;

import javax.annotation.processing.ProcessingEnvironment;

import org.spongepowered.tools.obfuscation.interfaces.ITypeHandleProvider;

public class AnnotatedMixinsAccessor {
    public static ITypeHandleProvider getMixinAP(ProcessingEnvironment processingEnv) {
        return AnnotatedMixins.getMixinsForEnvironment(processingEnv);
    }
}
