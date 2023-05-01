package org.embeddedt.modernfix.forge.mixin.perf.reduce_blockstate_cache_rebuilds;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;
import org.embeddedt.modernfix.util.BakeReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(GameData.class)
public class GameDataMixin {
    @Inject(method = "freezeData", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 1), remap = false)
    private static void markFreezeBakeReason(CallbackInfo ci) {
        BakeReason.setCurrentBakeReason(BakeReason.FREEZE);
    }

    @Inject(method = "freezeData", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/GameData;fireRemapEvent(Ljava/util/Map;Z)V"), remap = false)
    private static void markEmptyBakeReason1(CallbackInfo ci) {
        BakeReason.setCurrentBakeReason(null);
    }

    @Inject(method = "revertTo", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 1), remap = false)
    private static void markRevertBakeReason(CallbackInfo ci) {
        BakeReason.setCurrentBakeReason(BakeReason.REVERT);
    }

    @Inject(method = "revertTo", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/BiMap;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 1, shift = At.Shift.AFTER), remap = false)
    private static void markEmptyBakeReason2(CallbackInfo ci) {
        BakeReason.setCurrentBakeReason(null);
    }

    @Inject(method = "injectSnapshot", at = @At("HEAD"), remap = false)
    private static void markSnapshotInjectBakeReason(Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot, boolean injectFrozenData, boolean isLocalWorld, CallbackInfoReturnable<Multimap<ResourceLocation, ResourceLocation>> cir) {
        BakeReason.setCurrentBakeReason(isLocalWorld ? BakeReason.LOCAL_SNAPSHOT_INJECT : BakeReason.REMOTE_SNAPSHOT_INJECT);
    }

    @Inject(method = "injectSnapshot", at = @At("RETURN"), remap = false)
    private static void markEmptyBakeReason2(Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot, boolean injectFrozenData, boolean isLocalWorld, CallbackInfoReturnable<Multimap<ResourceLocation, ResourceLocation>> cir) {
        BakeReason.setCurrentBakeReason(null);
    }
}
