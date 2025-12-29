package org.embeddedt.modernfix.common.mixin.perf.deduplicate_wall_shapes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author embeddedt
 * @reason Avoid excessive memory usage in modpacks that add many variations of wall blocks. The strategy here requires
 * multiple memoization maps, but is also the simplest way to do it without having to literally replicate the vanilla
 * logic (as that uses blockstates rather than property maps).
 */
@Mixin(WallBlock.class)
public class WallBlockMixin {
    @Unique
    private static final ConcurrentHashMap<Vector3d, VoxelShape> COLUMN_CACHE = new ConcurrentHashMap<>();
    @Unique
    private static final ConcurrentHashMap<List<Double>, VoxelShape> BOX_Z_CACHE = new ConcurrentHashMap<>();
    @Unique
    private static final ConcurrentHashMap<VoxelShape, Map<Direction, VoxelShape>> ROTATE_HORZ_CACHE = new ConcurrentHashMap<>();
    @Unique
    private static final ConcurrentHashMap<Pair<VoxelShape, VoxelShape>, VoxelShape> OR_CACHE = new ConcurrentHashMap<>();

    @WrapOperation(method = "makeShapes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;column(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape memoizeColumn(double sizeXZ, double minY, double postHeight, Operation<VoxelShape> original) {
        return COLUMN_CACHE.computeIfAbsent(new Vector3d(sizeXZ, minY, postHeight), l -> original.call(sizeXZ, minY, postHeight));
    }

    @WrapOperation(method = "makeShapes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;boxZ(DDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape memoizeBoxZ(double sizeX, double minY, double maxY, double minZ, double maxZ, Operation<VoxelShape> original) {
        return BOX_Z_CACHE.computeIfAbsent(List.of(sizeX, minY, maxY, minZ, maxZ), l -> original.call(sizeX, minY, maxY, minZ, maxZ));
    }

    @WrapOperation(method = "makeShapes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;rotateHorizontal(Lnet/minecraft/world/phys/shapes/VoxelShape;)Ljava/util/Map;"))
    private Map<Direction, VoxelShape> memoizeRotateHorizontal(VoxelShape north, Operation<Map<Direction, VoxelShape>> original) {
        return ROTATE_HORZ_CACHE.computeIfAbsent(north, l -> original.call(north));
    }

    @WrapOperation(method = "lambda$makeShapes$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;or(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape memoizeOr(VoxelShape one, VoxelShape two, Operation<VoxelShape> original) {
        return OR_CACHE.computeIfAbsent(Pair.of(one, two), l -> original.call(one, two));
    }
}
