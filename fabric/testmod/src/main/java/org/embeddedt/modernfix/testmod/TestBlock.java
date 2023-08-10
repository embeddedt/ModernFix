package org.embeddedt.modernfix.testmod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class TestBlock extends Block {
    private static final BlockBehaviour.Properties PROPERTIES = BlockBehaviour.Properties.copy(Blocks.STONE);

    public TestBlock() {
        super(PROPERTIES);
    }
}
