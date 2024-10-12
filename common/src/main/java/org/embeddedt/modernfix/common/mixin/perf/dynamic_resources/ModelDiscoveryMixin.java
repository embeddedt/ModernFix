package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.HashSet;
import java.util.Set;

@Mixin(ModelDiscovery.class)
@ClientOnlyMixin
public class ModelDiscoveryMixin {
    /**
     * @author embeddedt
     * @reason nothing is mandatory at launch, we load things dynamically
     */
    @Overwrite
    private static Set<ModelResourceLocation> listMandatoryModels() {
        return new HashSet<>();
    }
}
