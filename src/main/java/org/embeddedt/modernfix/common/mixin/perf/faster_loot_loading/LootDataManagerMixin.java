package org.embeddedt.modernfix.common.mixin.perf.faster_loot_loading;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LootDataManager.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class LootDataManagerMixin {
    /**
     * @author embeddedt
     * @reason inject a marker for vanilla loot tables into the JSON so that we can retrieve it from the deserializer
     */
    @Inject(method = "lambda$scheduleElementParse$5", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/lang/String;Lcom/google/gson/Gson;Ljava/util/Map;)V", shift = At.Shift.AFTER))
    private static void mfix$scanAndCapture(ResourceManager resourceManager, LootDataType lootDataType, Map map, CallbackInfo ci,
                                            @Local(ordinal = 1) Map<ResourceLocation, JsonElement> lootTables) {
        FileToIdConverter converter = FileToIdConverter.json(lootDataType.directory());
        var lootTableResourceMap = converter.listMatchingResources(resourceManager);
        for (var entry : lootTableResourceMap.entrySet()) {
            if (lootTables.get(converter.fileToId(entry.getKey())) instanceof JsonObject obj) {
                var resource = entry.getValue();
                if (resource != null && !resource.isBuiltin()) {
                    obj.addProperty("mfix$isVanillaTable", true);
                }
            }
        }
    }
}
