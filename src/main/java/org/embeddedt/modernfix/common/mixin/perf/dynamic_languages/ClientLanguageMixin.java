package org.embeddedt.modernfix.common.mixin.perf.dynamic_languages;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamiclanguages.DynamicLanguageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modifies the language system to load/unload the contents of language entries based on GC pressure.
 */
@Mixin(value = ClientLanguage.class, priority = 2000)
@ClientOnlyMixin
public class ClientLanguageMixin {
    /**
     * @author embeddedt
     * @reason collect the list of all known language resources
     */
    @WrapOperation(method = "loadFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/language/ClientLanguage;appendFrom(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;)V"))
    private static void collectResources(String languageName, List<Resource> resources,
                                              Map<String, String> destinationMap,
                                              Map<String, net.minecraft.network.chat.Component> componentMap,
                                              Operation<Void> original,
                                              @Share("usedResources") LocalRef<List<Resource>> usedResources) {
        List<Resource> collected = usedResources.get();
        if (collected == null) {
            collected = new ArrayList<>();
            usedResources.set(collected);
        }
        collected.addAll(resources);
        original.call(languageName, resources, destinationMap, componentMap);
    }

    /**
     * @author embeddedt
     * @reason figure out which keys are dynamically loaded and which are injected by mixins
     */
    @ModifyArg(method = "loadFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/language/ClientLanguage;<init>(Ljava/util/Map;ZLjava/util/Map;)V"), index = 0)
    private static Map<String, String> modifyLanguageMap(Map<String, String> storage, @Share("usedResources") LocalRef<List<Resource>> usedResources) {
        List<Resource> collected = Objects.requireNonNullElse(usedResources.get(), List.of());
        return DynamicLanguageMap.forVanillaData(storage, collected);
    }
}
