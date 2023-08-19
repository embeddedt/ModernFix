package org.embeddedt.modernfix.testmod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public class TestBlockItem extends BlockItem {
    private static final Item.Properties PROPERTIES = new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS);

    public TestBlockItem(TestBlock block) {
        super(block, PROPERTIES);
    }
}
