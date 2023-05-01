package org.embeddedt.modernfix.forge.structure.logic;

import com.google.common.collect.ImmutableSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.embeddedt.modernfix.forge.mixin.perf.async_locator.EyeOfEnderAccess;
import org.embeddedt.modernfix.forge.structure.AsyncLocator;

public class EnderEyeItemLogic {
	private EnderEyeItemLogic() {}

	public static void locateAsync(ServerLevel level, Player player, EyeOfEnder eyeOfEnder, EnderEyeItem enderEyeItem) {
		StructureFeature<?> targetFeature;
		if(ModList.get().isLoaded("betterstrongholds"))
			targetFeature = ForgeRegistries.STRUCTURE_FEATURES.getValue(new ResourceLocation("betterstrongholds", "stronghold"));
		else
			targetFeature = StructureFeature.STRONGHOLD;
		AsyncLocator.locateChunkGen(
			level,
			ImmutableSet.of(targetFeature),
			player.blockPosition(),
			100,
			false
		).thenOnServerThread(pos -> {
			((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(false);
			if (pos != null) {
				eyeOfEnder.signalTo(pos.getFirst());
				CriteriaTriggers.USED_ENDER_EYE.trigger((ServerPlayer) player, pos.getFirst());
				player.awardStat(Stats.ITEM_USED.get(enderEyeItem));
			} else {
				// Set the entity's life to long enough that it dies
				((EyeOfEnderAccess) eyeOfEnder).setLife(Integer.MAX_VALUE - 100);
			}
		});
		((EyeOfEnderData) eyeOfEnder).setLocateTaskOngoing(true);
	}
}
