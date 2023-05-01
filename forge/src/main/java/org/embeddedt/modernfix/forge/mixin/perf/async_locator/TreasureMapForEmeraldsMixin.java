package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.embeddedt.modernfix.forge.structure.logic.MerchantLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Random;

@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$TreasureMapForEmeralds")
public class TreasureMapForEmeraldsMixin {
	@Shadow
	@Final
	private int emeraldCost;

	@Shadow
	@Final
	private MapDecoration.Type destinationType;

	@Shadow
	@Final
	private int maxUses;

	@Shadow
	@Final
	private int villagerXp;

	@Shadow
	@Final
	private StructureFeature<?> destination;

	/*
		Intercept TreasureMapForEmeralds#getOffer call right before it calls ServerLevel#findNearestMapFeature to pass
		the logic over to an async task. Instead of returning the complete map or null, we'll have to always return an
	 	incomplete filled map and later update it with the details when we have them.
	 */
	@Inject(
		method = "getOffer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapFeature(Lnet/minecraft/world/level/levelgen/feature/StructureFeature;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"
		),
		cancellable = true
	)
	public void updateMapAsync(Entity pTrader, Random pRand, CallbackInfoReturnable<MerchantOffer> callbackInfo) {
		String displayName = "filled_map." + this.destination.getFeatureName().toLowerCase(Locale.ROOT);
		MerchantOffer offer = MerchantLogic.updateMapAsync(
			pTrader, emeraldCost, displayName, destinationType, maxUses, villagerXp, destination
		);
		if (offer != null) {
			callbackInfo.setReturnValue(offer);
		}
	}
}
