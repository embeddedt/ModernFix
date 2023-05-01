package org.embeddedt.modernfix.mixin.perf.async_locator;

import net.minecraft.advancements.critereon.UsedEnderEyeTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.phys.HitResult;
import org.embeddedt.modernfix.structure.logic.EnderEyeItemLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(EnderEyeItem.class)
public class EnderEyeItemMixin {
	/*
		Intercept EnderEyeItem#use call and return BlockPos.ZERO instead. It won't be used in the EyeOfEnder entity
		created later either, as we need to set the actual location ourselves.
	 */
	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapFeature(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/feature/StructureFeature;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"
		)
	)
	public BlockPos levelFindNearestMapFeature(
		ChunkGenerator generator,
		ServerLevel level,
		StructureFeature<?> structureFeature,
		BlockPos pPos,
		int pRadius,
		boolean pSkipExistingChunks
	) {
		return BlockPos.ZERO;
	}

	// Start the async locate task here so we have the eye of ender entity for context
	@Inject(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/EyeOfEnder;setItem(Lnet/minecraft/world/item/ItemStack;)V"
		),
		locals = LocalCapture.CAPTURE_FAILEXCEPTION
	)
	public void startAsyncLocateTask(
		Level pLevel,
		Player pPlayer,
		InteractionHand pHand,
		CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir,
		ItemStack itemstack,
		HitResult hitresult,
		BlockPos blockpos,
		EyeOfEnder eyeofender
	) {
		EnderEyeItemLogic.locateAsync((ServerLevel)pLevel, pPlayer, eyeofender, (EnderEyeItem) (Object) this);
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/EyeOfEnder;signalTo(Lnet/minecraft/core/BlockPos;)V"
		)
	)
	public void eyeOfEnderSignalTo(EyeOfEnder eyeOfEnder, BlockPos blockpos) {
		// Do nothing - we'll do this later if a location is found
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/advancements/critereon/UsedEnderEyeTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"
		)
	)
	public void triggerUsedEnderEyeCriteria(UsedEnderEyeTrigger trigger, ServerPlayer player, BlockPos pos) {
		// Do nothing - we'll do this later if a location is found
	}

	@Redirect(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"
		)
	)
	public void playerAwardStat(Player instance, Stat<?> pStat) {
		// Do nothing - we'll do this later if a location is found
	}
}
