package org.embeddedt.modernfix.forge.structure.logic;

import com.google.common.collect.ImmutableSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import org.embeddedt.modernfix.forge.mixin.perf.async_locator.LocateCommandAccess;
import org.embeddedt.modernfix.forge.structure.AsyncLocator;

public class LocateCommandLogic {
	private LocateCommandLogic() {}

	public static void locateAsync(CommandSourceStack sourceStack, StructureFeature<?> feature) {
		BlockPos originPos = new BlockPos(sourceStack.getPosition());
		AsyncLocator.locateLevel(sourceStack.getLevel(), ImmutableSet.of(feature), originPos, 100, false)
			.thenOnServerThread(pair -> {
				if (pair != null) {
					LocateCommand.showLocateResult(sourceStack, feature.getFeatureName(), originPos, pair, "commands.locate.success");
				} else {
					sourceStack.sendFailure(
						new TextComponent(
							LocateCommandAccess.getErrorFailed().create().getMessage()
						)
					);
				}
			});
	}
}
