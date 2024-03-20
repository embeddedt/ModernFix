package org.embeddedt.modernfix.forge.mixin.perf.potential_spawns_alloc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mixin(WorldEvent.PotentialSpawns.class)
public class PotentialSpawnsMixin {
    @Shadow(remap = false) @Final @Mutable private List<MobSpawnSettings.SpawnerData> view;
    @Shadow(remap = false) @Final @Mutable private List<MobSpawnSettings.SpawnerData> list;

    private static final ArrayList<MobSpawnSettings.SpawnerData> SENTINEL = new ArrayList<>();

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 1))
    private ArrayList<?> avoidListAlloc1() {
        return SENTINEL;
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 0))
    private ArrayList<?> avoidListAlloc2(Collection c) {
        return SENTINEL;
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Collections;unmodifiableList(Ljava/util/List;)Ljava/util/List;"))
    private List<?> avoidListAlloc3(List<?> l) {
        return null;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initializeSmartLists(LevelAccessor level, MobCategory category, BlockPos pos, WeightedRandomList<MobSpawnSettings.SpawnerData> oldList, CallbackInfo ci) {
        this.view = oldList.unwrap();
        this.list = null;
    }

    private void mfix$populateList() {
        if(this.list == null) {
            this.list = new ArrayList<>(this.view);
            this.view = Collections.unmodifiableList(this.list);
        }
    }

    @Inject(method = {"addSpawnerData" }, at = @At("HEAD"), remap = false)
    private void populateList(MobSpawnSettings.SpawnerData data, CallbackInfo ci) {
        mfix$populateList();
    }

    @Inject(method = {"removeSpawnerData" }, at = @At("HEAD"), remap = false)
    private void populateList(MobSpawnSettings.SpawnerData data, CallbackInfoReturnable<Boolean> cir) {
        mfix$populateList();
    }
}
