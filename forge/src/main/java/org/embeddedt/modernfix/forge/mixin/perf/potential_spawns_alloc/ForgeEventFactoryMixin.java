package org.embeddedt.modernfix.forge.mixin.perf.potential_spawns_alloc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(ForgeEventFactory.class)
public class ForgeEventFactoryMixin {
    @Redirect(method = "getPotentialSpawns", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/random/WeightedRandomList;create(Ljava/util/List;)Lnet/minecraft/util/random/WeightedRandomList;"))
    private static WeightedRandomList<MobSpawnSettings.SpawnerData> reuseOldList(List<MobSpawnSettings.SpawnerData> items, LevelAccessor level, MobCategory category, BlockPos pos, WeightedRandomList<MobSpawnSettings.SpawnerData> oldList) {
        // Our patched version of PotentialSpawns will return the same list as unwrap() if no one mutated the list
        if(items == oldList.unwrap()) {
            return oldList;
        }
        return WeightedRandomList.create(items);
    }
}
