package org.embeddedt.modernfix.structure.logic;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.fml.loading.Java9BackportUtils;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.structure.AsyncLocator;

import java.util.function.BiConsumer;

// TODO: Need to test this
public class ExplorationMapFunctionLogic {
	private static final int MAX_STACK_SIZE = 64;
	private ExplorationMapFunctionLogic() {}

	public static void invalidateMap(ItemStack mapStack, ServerLevel level, BlockPos pos) {
		handleUpdateMapInChest(mapStack, level, pos, (handler, slot) -> {
			if (handler instanceof IItemHandlerModifiable) {
				((IItemHandlerModifiable)handler).setStackInSlot(slot, new ItemStack(Items.MAP));
			} else {
				handler.extractItem(slot, MAX_STACK_SIZE, false);
				handler.insertItem(slot, new ItemStack(Items.MAP), false);
			}
		});
	}

	public static void updateMap(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos pos,
		int scale,
		MapDecoration.Type destinationType,
		BlockPos invPos
	) {
		CommonLogic.updateMap(mapStack, level, pos, scale, destinationType);
		// Shouldn't need to set the stack in its slot again, as we're modifying the same instance
		handleUpdateMapInChest(mapStack, level, invPos, (handler, slot) -> {});
	}

	public static void handleUpdateMapInChest(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos invPos,
		BiConsumer<IItemHandler, Integer> handleSlotFound
	) {
		BlockEntity be = level.getBlockEntity(invPos);
		if (be != null) {
			Java9BackportUtils.ifPresentOrElse(be.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).resolve(),
				itemHandler -> {
					for (int i = 0; i < itemHandler.getSlots(); i++) {
						ItemStack slotStack = itemHandler.getStackInSlot(i);
						if (slotStack == mapStack) {
							handleSlotFound.accept(itemHandler, i);
							CommonLogic.broadcastChestChanges(level, be);
							return;
						}
					}
				},
				() -> ModernFix.LOGGER.warn(
					"Couldn't find item handler capability on chest {} at {}",
					be.getClass().getSimpleName(), invPos
				)
			);
		} else {
			ModernFix.LOGGER.warn(
				"Couldn't find block entity on chest {} at {}",
				level.getBlockState(invPos), invPos
			);
		}
	}

	public static void handleLocationFound(
		ItemStack mapStack,
		ServerLevel level,
		BlockPos pos,
		int scale,
		MapDecoration.Type destinationType,
		BlockPos invPos
	) {
		if (pos == null) {
			invalidateMap(mapStack, level, invPos);
		} else {
			updateMap(mapStack, level, pos, scale, destinationType, invPos);
		}
	}

	public static ItemStack updateMapAsync(
		ServerLevel level,
		BlockPos blockPos,
		int scale,
		int searchRadius,
		boolean skipKnownStructures,
		MapDecoration.Type destinationType,
		StructureFeature<?> destination
	) {
		ItemStack mapStack = CommonLogic.createEmptyMap();
		AsyncLocator.locateLevel(level, ImmutableSet.of(destination), blockPos, searchRadius, skipKnownStructures)
			.thenOnServerThread(pos -> handleLocationFound(mapStack, level, pos, scale, destinationType, blockPos));
		return mapStack;
	}
}
