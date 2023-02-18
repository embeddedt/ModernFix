package org.embeddedt.modernfix.structure;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.thread.SidedThreadGroups;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.NotNull;

import java.sql.Struct;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ModernFix.MODID)
public class AsyncLocator {
	private static ExecutorService LOCATING_EXECUTOR_SERVICE = null;
	private static final AtomicInteger poolNum = new AtomicInteger(1);

	private AsyncLocator() {}

	private static void setupExecutorService() {
		shutdownExecutorService();

		int threads = 1; // very unlikely we need more than one
		ModernFix.LOGGER.info("Starting locating executor service with thread pool size of {}", threads);
		LOCATING_EXECUTOR_SERVICE = Executors.newFixedThreadPool(
			threads,
			new ThreadFactory() {

				private final AtomicInteger threadNum = new AtomicInteger(1);
				private final String namePrefix = "asynclocator-" + poolNum.getAndIncrement() + "-thread-";

				@Override
				public Thread newThread(@NotNull Runnable r) {
					return new Thread(SidedThreadGroups.SERVER, r, namePrefix + threadNum.getAndIncrement());
				}
			}
		);
	}

	private static void shutdownExecutorService() {
		if (LOCATING_EXECUTOR_SERVICE != null) {
			ModernFix.LOGGER.info("Shutting down locating executor service");
			LOCATING_EXECUTOR_SERVICE.shutdown();
		}
	}

	@SubscribeEvent
	public static void handleServerAboutToStartEvent(FMLServerAboutToStartEvent ignoredEvent) {
		setupExecutorService();
	}

	@SubscribeEvent
	public static void handleServerStoppingEvent(FMLServerStoppingEvent ignoredEvent) {
		shutdownExecutorService();
	}

	/**
	 * Queues a task to locate a feature using {@link ServerLevel#findNearestMapFeature(TagKey, BlockPos, int, boolean)}
	 * and returns a {@link LocateTask} with the futures for it.
	 */
	public static LocateTask<BlockPos> locateLevel(
			ServerLevel level,
			Collection<StructureFeature<?>> structure,
			BlockPos pos,
			int searchRadius,
			boolean skipKnownStructures
	) {
		ModernFix.LOGGER.debug(
			"Creating locate task for {} in {} around {} within {} chunks",
				structure, level, pos, searchRadius
		);
		CompletableFuture<BlockPos> completableFuture = new CompletableFuture<>();
		Future<?> future = LOCATING_EXECUTOR_SERVICE.submit(
			() -> doLocateLevel(completableFuture, level, structure, pos, searchRadius, skipKnownStructures)
		);
		return new LocateTask<>(level.getServer(), completableFuture, future);
	}

	/**
	 * Queues a task to locate a feature using
	 * {@link ChunkGenerator#findNearestMapFeature(ServerLevel, HolderSet, BlockPos, int, boolean)} and returns a
	 * {@link LocateTask} with the futures for it.
	 */
	public static LocateTask<Pair<BlockPos, StructureFeature<?>>> locateChunkGen(
		ServerLevel level,
		Collection<StructureFeature<?>> structureSet,
		BlockPos pos,
		int searchRadius,
		boolean skipKnownStructures
	) {
		ModernFix.LOGGER.debug(
			"Creating locate task for {} in {} around {} within {} chunks",
			structureSet, level, pos, searchRadius
		);
		CompletableFuture<Pair<BlockPos, StructureFeature<?>>> completableFuture = new CompletableFuture<>();
		Future<?> future = LOCATING_EXECUTOR_SERVICE.submit(
			() -> doLocateChunkGenerator(completableFuture, level, structureSet, pos, searchRadius, skipKnownStructures)
		);
		return new LocateTask<>(level.getServer(), completableFuture, future);
	}

	private static String structureSetToString(Collection<StructureFeature<?>> collection) {
		return "[" + collection.stream().map(StructureFeature::getRegistryName).map(ResourceLocation::toString).collect(Collectors.joining(", ")) + "]";
	}

	private static void doLocateLevel(
		CompletableFuture<BlockPos> completableFuture,
		ServerLevel level,
		Collection<StructureFeature<?>> structureTag,
		BlockPos pos,
		int searchRadius,
		boolean skipExistingChunks
	) {
		String structures = structureSetToString(structureTag);
		ModernFix.LOGGER.debug(
			"Trying to locate {} in {} around {} within {} chunks",
				structures, level, pos, searchRadius
		);
		Optional<BlockPos> thePosition = structureTag.stream()
				.map(tag -> level.findNearestMapFeature(tag, pos, searchRadius, skipExistingChunks))
				.filter(Objects::nonNull)
				.findFirst();
		if (!thePosition.isPresent())
			ModernFix.LOGGER.debug("No {} found", structures);
		else
			ModernFix.LOGGER.debug("Found {} at {}", structures, thePosition.get());
		completableFuture.complete(thePosition.orElse(null));
	}

	@SuppressWarnings({"rawtypes", "unchecked" })
	private static void doLocateChunkGenerator(
		CompletableFuture<Pair<BlockPos, StructureFeature<?>>> completableFuture,
		ServerLevel level,
		Collection<StructureFeature<?>> structureSet,
 		BlockPos pos,
		int searchRadius,
		boolean skipExistingChunks
	) {
		String structures = structureSetToString(structureSet);
		ModernFix.LOGGER.debug(
			"Trying to locate {} in {} around {} within {} chunks",
			structures, level, pos, searchRadius
		);
		Optional<Pair<BlockPos, StructureFeature>> foundStructure = structureSet.stream()
				.map(feature -> Pair.of(level.getChunkSource().getGenerator()
						.findNearestMapFeature(level, feature, pos, searchRadius, skipExistingChunks), (StructureFeature)feature))
				.filter(pair -> pair.getFirst() != null)
				.findFirst();
		if (!foundStructure.isPresent())
			ModernFix.LOGGER.debug("No {} found", structures);
		else
			ModernFix.LOGGER.debug("Found {} at {}", structures, foundStructure.get().getFirst());
		completableFuture.complete((Pair<BlockPos, StructureFeature<?>>)(Object)foundStructure.orElse(null));
	}

	/**
	 * Holder of the futures for an async locate task as well as providing some helper functions.
	 * The completableFuture will be completed once the call to
	 * {@link ServerLevel#findNearestMapFeature(TagKey, BlockPos, int, boolean)} has completed, and will hold the
	 * result of it.
	 * The taskFuture is the future for the {@link Runnable} itself in the executor service.
	 */
	public static class LocateTask<T> {
		private final MinecraftServer server;
		private final CompletableFuture<T> completableFuture;
		private final Future<?> taskFuture;
		public LocateTask(MinecraftServer server, CompletableFuture<T> completableFuture, Future<?> taskFuture) {
			this.server = server;
			this.completableFuture = completableFuture;
			this.taskFuture = taskFuture;
		}
		/**
		 * Helper function that calls {@link CompletableFuture#thenAccept(Consumer)} with the given action.
		 * Bear in mind that the action will be executed from the task's thread. If you intend to change any game data,
		 * it's strongly advised you use {@link #thenOnServerThread(Consumer)} instead so that it's queued and executed
		 * on the main server thread instead.
		 */
		public LocateTask<T> then(Consumer<T> action) {
			completableFuture.thenAccept(action);
			return this;
		}

		/**
		 * Helper function that calls {@link CompletableFuture#thenAccept(Consumer)} with the given action on the server
		 * thread.
		 */
		public LocateTask<T> thenOnServerThread(Consumer<T> action) {
			completableFuture.thenAccept(pos -> server.submit(() -> action.accept(pos)));
			return this;
		}

		/**
		 * Helper function that cancels both completableFuture and taskFuture.
		 */
		public void cancel() {
			taskFuture.cancel(true);
			completableFuture.cancel(false);
		}
	}
}
