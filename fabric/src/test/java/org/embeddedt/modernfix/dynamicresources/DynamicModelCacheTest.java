package org.embeddedt.modernfix.dynamicresources;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BuiltInModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.embeddedt.modernfix.testing.util.BootstrapMinecraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@BootstrapMinecraft
public class DynamicModelCacheTest {
    @Test
    public void testCacheReturnsNullForNullGetter() {
        DynamicModelCache<Item> cache = new DynamicModelCache(k -> null, true);
        assertNull(cache.get(Items.STONE));
    }

    @Test
    public void testCacheFunctions() {
        BakedModel model = new BuiltInModel(null, null, null, false);
        DynamicModelCache<Item> cache = new DynamicModelCache(k -> model, true);
        assertEquals(model, cache.get(Items.STONE));
    }
}
