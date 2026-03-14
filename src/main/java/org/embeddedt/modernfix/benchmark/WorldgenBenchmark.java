package org.embeddedt.modernfix.benchmark;

import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.embeddedt.modernfix.ModernFix;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class WorldgenBenchmark {

    private static final TicketType<Unit> BENCHMARK_TICKET =
            TicketType.create("modernfix_benchmark", (a, b) -> 0);

    private static final List<ChunkStatus> ALL_STATUSES = ChunkStatus.getStatusList().stream()
            .filter(s -> s.getIndex() > ChunkStatus.EMPTY.getIndex()
                    && s.getIndex() < ChunkStatus.INITIALIZE_LIGHT.getIndex())
            .toList();

    private static final int REQUIRED_LOAD_RADIUS = ALL_STATUSES.stream().mapToInt(ChunkStatus::getRange).max().orElse(0);

    public static String run(ServerLevel level, ChunkPos center, int testRadius, int iterations, ChunkStatus startStatus, ChunkStatus stopStatus) {
        int startIndex = ALL_STATUSES.indexOf(startStatus);
        if (startIndex < 0) {
            throw new IllegalArgumentException("Invalid start status: " + startStatus);
        }

        int stopIndex = ALL_STATUSES.indexOf(stopStatus);
        if (stopIndex < 0) {
            throw new IllegalArgumentException("Invalid stop status:" + stopStatus);
        }

        List<ChunkStatus> setupStatuses = ALL_STATUSES.subList(0, startIndex);
        List<ChunkStatus> timedStatuses = ALL_STATUSES.subList(startIndex, stopIndex + 1);

        Context ctx = new Context(level, center, testRadius);
        long[] timings = new long[timedStatuses.size()];

        int testDiameter = 2 * testRadius + 1;
        int numPositions = testDiameter * testDiameter;
        ChunkPos[] testPositions = new ChunkPos[numPositions];
        CompoundTag[] snapshots = new CompoundTag[numPositions];
        ChunkAccess[][] neighborArrays = new ChunkAccess[numPositions][];

        int idx = 0;
        for (int tz = -testRadius; tz <= testRadius; tz++) {
            for (int tx = -testRadius; tx <= testRadius; tx++) {
                ChunkPos testPos = new ChunkPos(center.x + tx, center.z + tz);
                testPositions[idx] = testPos;
                neighborArrays[idx] = ctx.buildNeighborArray(testPos);

                ProtoChunk setupProto = ctx.newProtoChunk(testPos);
                neighborArrays[idx][ctx.centerIndex] = setupProto;
                for (ChunkStatus status : setupStatuses) {
                    status.generate(ctx.executor, level, ctx.generator, ctx.templates,
                            ctx.lightEngine, ctx.noopPromotion, Arrays.asList(neighborArrays[idx])).join();
                }
                snapshots[idx] = ChunkSerializer.write(level, setupProto);
                idx++;
                ModernFix.LOGGER.info("worldgen benchmark setup progress: {}/{}", idx, numPositions);
            }
        }

        ModernFix.LOGGER.info("worldgen benchmark setup complete");

        for (int iter = 0; iter < iterations; iter++) {
            ModernFix.LOGGER.info("worldgen benchmark iteration: {}/{}", iter + 1, iterations);
            for (int p = 0; p < numPositions; p++) {
                ProtoChunk restored = ChunkSerializer.read(
                        level, ctx.poiManager, testPositions[p], snapshots[p]);
                neighborArrays[p][ctx.centerIndex] = restored;
                List<ChunkAccess> neighborList = Arrays.asList(neighborArrays[p]);

                for (int s = 0; s < timedStatuses.size(); s++) {
                    long t0 = System.nanoTime();

                    timedStatuses.get(s).generate(ctx.executor, level, ctx.generator,
                            ctx.templates, ctx.lightEngine, ctx.noopPromotion, neighborList).join();

                    timings[s] += System.nanoTime() - t0;
                }
            }
        }

        ModernFix.LOGGER.info("worldgen benchmark done");

        ctx.cleanup();

        return formatTimings(timedStatuses, timings, testRadius, iterations);
    }

    private static String formatTimings(List<ChunkStatus> statuses, long[] timings, int testRadius, int iterations) {
        int totalChunks = (2 * testRadius + 1) * (2 * testRadius + 1) * iterations;
        StringBuilder sb = new StringBuilder();
        long total = 0;
        for (int i = 0; i < timings.length; i++) {
            total += timings[i];
            String name = BuiltInRegistries.CHUNK_STATUS.getKey(statuses.get(i)).getPath();
            sb.append(String.format("  %-22s %8.1f ms  (%6.2f ms/chunk)\n",
                    name, timings[i] / 1e6, timings[i] / 1e6 / totalChunks));
        }
        sb.append(String.format("  %-22s %8.1f ms  (%6.2f ms/chunk)\n",
                "TOTAL", total / 1e6, total / 1e6 / totalChunks));
        return sb.toString();
    }

    private static class Context {
        final ServerLevel level;
        final ServerChunkCache chunkSource;
        final ChunkPos center;
        final int loadRadius;
        final int loadDiameter;
        final ChunkAccess[] realChunks;
        final int neighborDiameter;
        final int centerIndex;
        final Executor executor;
        final ChunkGenerator generator;
        final ThreadedLevelLightEngine lightEngine;
        final StructureTemplateManager templates;
        final PoiManager poiManager;
        final Function<ChunkAccess, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> noopPromotion;
        private final net.minecraft.core.Registry<Biome> biomeRegistry;

        Context(ServerLevel level, ChunkPos center, int testRadius) {
            this.level = level;
            this.chunkSource = level.getChunkSource();
            this.center = center;
            this.loadRadius = testRadius + REQUIRED_LOAD_RADIUS;
            this.loadDiameter = 2 * loadRadius + 1;
            this.neighborDiameter = 2 * REQUIRED_LOAD_RADIUS + 1;
            this.centerIndex = neighborDiameter * neighborDiameter / 2;
            this.executor = MoreExecutors.directExecutor();
            this.generator = chunkSource.getGenerator();
            this.lightEngine = chunkSource.getLightEngine();
            this.templates = level.getStructureManager();
            this.poiManager = chunkSource.getPoiManager();
            this.noopPromotion = chunk -> CompletableFuture.completedFuture(Either.left(chunk));
            this.biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);

            chunkSource.addRegionTicket(BENCHMARK_TICKET, center, loadRadius, Unit.INSTANCE);

            realChunks = new ChunkAccess[loadDiameter * loadDiameter];
            for (int dz = -loadRadius; dz <= loadRadius; dz++) {
                for (int dx = -loadRadius; dx <= loadRadius; dx++) {
                    LevelChunk real = level.getChunk(center.x + dx, center.z + dz);
                    realChunks[(dz + loadRadius) * loadDiameter + (dx + loadRadius)] =
                            new ImposterProtoChunk(real, false);
                }
            }
        }

        ProtoChunk newProtoChunk(ChunkPos pos) {
            return new ProtoChunk(pos, UpgradeData.EMPTY, level, biomeRegistry, null);
        }

        ChunkAccess[] buildNeighborArray(ChunkPos testPos) {
            int count = neighborDiameter * neighborDiameter;
            ChunkAccess[] array = new ChunkAccess[count];
            int baseX = (testPos.x - REQUIRED_LOAD_RADIUS) - (center.x - loadRadius);
            int baseZ = (testPos.z - REQUIRED_LOAD_RADIUS) - (center.z - loadRadius);
            for (int dz = 0; dz < neighborDiameter; dz++) {
                System.arraycopy(realChunks, (baseZ + dz) * loadDiameter + baseX,
                        array, dz * neighborDiameter, neighborDiameter);
            }
            return array;
        }

        void cleanup() {
            chunkSource.removeRegionTicket(BENCHMARK_TICKET, center, loadRadius, Unit.INSTANCE);
        }
    }
}
