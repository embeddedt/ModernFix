package org.embeddedt.modernfix.duck;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public interface IExtendedModelBakery {
    ImmutableList<BlockState> getBlockStatesForMRL(StateDefinition<Block, BlockState> stateDefinition, ModelResourceLocation location);
    BakedModel bakeDefault(ResourceLocation modelLocation);
    BakedModel getBakedMissingModel();
    void setBakedMissingModel(BakedModel m);
    UnbakedModel mfix$getUnbakedMissingModel();
}
