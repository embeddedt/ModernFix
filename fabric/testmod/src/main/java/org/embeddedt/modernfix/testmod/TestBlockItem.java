package org.embeddedt.modernfix.testmod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class TestBlockItem extends BlockItem {
    private static final Item.Properties PROPERTIES = new Item.Properties();

    public TestBlockItem(TestBlock block) {
        super(block, PROPERTIES);
    }
}
