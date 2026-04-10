package org.embeddedt.modernfix.common.mixin.perf.dynamic_languages;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamiclanguages.DynamicLanguageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Modifies the language system to load/unload the contents of language entries based on GC pressure.
 */
@Mixin(ClientLanguage.class)
@ClientOnlyMixin
public class ClientLanguageMixin {
    private static final ThreadLocal<Boolean> MFIX_MODIFY_APPEND_SEMANTICS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * @author embeddedt
     * @reason modify the semantics of appendFrom so that it's used to do a prepass
     */
    @ModifyArg(method = "appendFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"), index = 1)
    private static BiConsumer<String, ?> changeSemanticsOfConsumer(BiConsumer<String, ?> consumer, @Local(ordinal = 0, argsOnly = true) Map<String, Object> destinationMap, @Local(ordinal = 0) Resource resource) {
        return MFIX_MODIFY_APPEND_SEMANTICS.get() ? ((k, v) -> destinationMap.put(k, resource)) : consumer;
    }

    /**
     * @author embeddedt
     * @reason collect resources that own keys with a prepass
     */
    @WrapOperation(method = "loadFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/ClientLanguage;appendFrom(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)V"))
    private static void trackEntrySource(String languageName, List<Resource> resources, Map<String, String> destinationMap, Operation<Void> original) {
        MFIX_MODIFY_APPEND_SEMANTICS.set(true);
        try {
            original.call(languageName, resources, destinationMap);
        } finally {
            MFIX_MODIFY_APPEND_SEMANTICS.remove();
        }
    }

    /**
     * @author embeddedt
     * @reason figure out which keys are dynamically loaded and which are injected by mixins
     */
    @ModifyArg(method = "loadFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/ClientLanguage;<init>(Ljava/util/Map;Z)V"), index = 0)
    private static Map<String, String> modifyLanguageMap(Map<String, ?> storage) {
        return DynamicLanguageMap.forStorage(Map.copyOf(storage));
    }
}
