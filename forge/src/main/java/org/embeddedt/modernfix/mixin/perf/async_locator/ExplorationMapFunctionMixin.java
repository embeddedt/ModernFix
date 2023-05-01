package org.embeddedt.modernfix.mixin.perf.async_locator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.modernfix.structure.logic.ExplorationMapFunctionLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ExplorationMapFunction.class)
public class ExplorationMapFunctionMixin {
	@Shadow
	@Final
	StructureFeature<?> destination;

	@Shadow
	@Final
	MapDecoration.Type mapDecoration;

	@Shadow
	@Final
	byte zoom;

	@Shadow
	@Final
	int searchRadius;

	@Shadow
	@Final
	boolean skipKnownStructures;

	@Inject(
		method = "run",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapFeature(Lnet/minecraft/world/level/levelgen/feature/StructureFeature;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"
		),
		locals = LocalCapture.CAPTURE_FAILSOFT,
		cancellable = true
	)
	public void updateMapAsync(
		ItemStack pStack,
		LootContext pContext,
		CallbackInfoReturnable<ItemStack> cir,
		Vec3 vec3,
		ServerLevel serverlevel
	) {
		ItemStack mapStack = ExplorationMapFunctionLogic.updateMapAsync(
			serverlevel, new BlockPos(vec3), zoom, searchRadius, skipKnownStructures, mapDecoration, destination
		);
		cir.setReturnValue(mapStack);
	}
}
