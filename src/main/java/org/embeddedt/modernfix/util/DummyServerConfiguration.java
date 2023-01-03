package org.embeddedt.modernfix.util;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Lifecycle;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraft.world.storage.IServerWorldInfo;

import javax.annotation.Nullable;
import java.util.Set;

public class DummyServerConfiguration implements IServerConfiguration {
    @Override
    public DatapackCodec getDataPackConfig() {
        return DatapackCodec.DEFAULT;
    }

    @Override
    public void setDataPackConfig(DatapackCodec codec) {

    }

    @Override
    public boolean wasModded() {
        return true;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.of("forge");
    }

    @Override
    public void setModdedInfo(String name, boolean isModded) {

    }

    @Nullable
    @Override
    public CompoundNBT getCustomBossEvents() {
        return null;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundNBT nbt) {

    }

    @Override
    public IServerWorldInfo overworldData() {
        return null;
    }

    @Override
    public WorldSettings getLevelSettings() {
        return null;
    }

    @Override
    public CompoundNBT createTag(DynamicRegistries registries, @Nullable CompoundNBT hostPlayerNBT) {
        return null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public String getLevelName() {
        return null;
    }

    @Override
    public GameType getGameType() {
        return null;
    }

    @Override
    public void setGameType(GameType type) {

    }

    @Override
    public boolean getAllowCommands() {
        return false;
    }

    @Override
    public Difficulty getDifficulty() {
        return null;
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {

    }

    @Override
    public boolean isDifficultyLocked() {
        return false;
    }

    @Override
    public void setDifficultyLocked(boolean locked) {

    }

    @Override
    public GameRules getGameRules() {
        return null;
    }

    @Override
    public CompoundNBT getLoadedPlayerTag() {
        return null;
    }

    @Override
    public CompoundNBT endDragonFightData() {
        return null;
    }

    @Override
    public void setEndDragonFightData(CompoundNBT nbt) {

    }

    @Override
    public DimensionGeneratorSettings worldGenSettings() {
        return null;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return Lifecycle.stable();
    }
}
