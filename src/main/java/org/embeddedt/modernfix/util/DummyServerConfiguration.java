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
    public DatapackCodec getDatapackCodec() {
        return DatapackCodec.VANILLA_CODEC;
    }

    @Override
    public void setDatapackCodec(DatapackCodec codec) {

    }

    @Override
    public boolean isModded() {
        return true;
    }

    @Override
    public Set<String> getServerBranding() {
        return ImmutableSet.of("forge");
    }

    @Override
    public void addServerBranding(String name, boolean isModded) {

    }

    @Nullable
    @Override
    public CompoundNBT getCustomBossEventData() {
        return null;
    }

    @Override
    public void setCustomBossEventData(@Nullable CompoundNBT nbt) {

    }

    @Override
    public IServerWorldInfo getServerWorldInfo() {
        return null;
    }

    @Override
    public WorldSettings getWorldSettings() {
        return null;
    }

    @Override
    public CompoundNBT serialize(DynamicRegistries registries, @Nullable CompoundNBT hostPlayerNBT) {
        return null;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getStorageVersionId() {
        return 0;
    }

    @Override
    public String getWorldName() {
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
    public boolean areCommandsAllowed() {
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
    public GameRules getGameRulesInstance() {
        return null;
    }

    @Override
    public CompoundNBT getHostPlayerNBT() {
        return null;
    }

    @Override
    public CompoundNBT getDragonFightData() {
        return null;
    }

    @Override
    public void setDragonFightData(CompoundNBT nbt) {

    }

    @Override
    public DimensionGeneratorSettings getDimensionGeneratorSettings() {
        return null;
    }

    @Override
    public Lifecycle getLifecycle() {
        return Lifecycle.stable();
    }
}
