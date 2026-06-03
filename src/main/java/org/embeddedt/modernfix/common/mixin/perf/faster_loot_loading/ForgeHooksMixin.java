package org.embeddedt.modernfix.common.mixin.perf.faster_loot_loading;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

import static net.minecraftforge.common.ForgeHooks.loadLootTable;

@Mixin(value = ForgeHooks.class, remap = false)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ForgeHooksMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    private static boolean mfix$isVanillaTable(JsonElement data) {
        if (!(data instanceof JsonObject obj)) {
            return false;
        }
        var vanillaMarker = obj.getAsJsonPrimitive("mfix$isVanillaTable");
        if (vanillaMarker == null) {
            return false;
        }
        return vanillaMarker.getAsBoolean();
    }

    /**
     * @author embeddedt
     * @reason avoid getResource() call per loot table by using injected marker
     */
    @Overwrite
    public static TriFunction<ResourceLocation, JsonElement, ResourceManager, Optional<LootTable>> getLootTableDeserializer(Gson gson, String directory) {
        return (location, data, resourceManager) -> {
            try {
                boolean custom = !mfix$isVanillaTable(data);
                return Optional.ofNullable(loadLootTable(gson, location, data, custom));
            } catch (Exception exception) {
                LOGGER.error("Couldn't parse element {}:{}", directory, location, exception);
                return Optional.empty();
            }
        };
    }
}
