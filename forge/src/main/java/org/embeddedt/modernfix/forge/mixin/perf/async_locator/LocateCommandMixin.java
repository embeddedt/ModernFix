package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import org.embeddedt.modernfix.forge.structure.logic.LocateCommandLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {
	@Inject(
		method = "locate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapFeature(Lnet/minecraft/world/level/levelgen/feature/StructureFeature;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"
		),
		cancellable = true,
		locals = LocalCapture.CAPTURE_FAILSOFT
	)
	private static void findLocationAsync(CommandSourceStack sourceStack, StructureFeature<?> feature, CallbackInfoReturnable<Integer> cir) {
		CommandSource source = ((CommandSourceStackAccess) sourceStack).getSource();
		if (source instanceof ServerPlayer || source instanceof MinecraftServer) {
			LocateCommandLogic.locateAsync(sourceStack, feature);
			cir.setReturnValue(0);
		}
	}
}
