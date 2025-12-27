package org.embeddedt.modernfix.common.mixin.perf.mojang_registry_size;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minor mixin to avoid duplicate empty neighbor tables, used when FerriteCore is not present. Won't be enabled in 99% of
 * modded environments but is useful for testing in dev without dragging in Fabric API.
 */
@Mixin(StateHolder.class)
@RequiresMod("!ferritecore")
public class StateHolderMixin {
    @Shadow private Table<Property<?>, Comparable<?>, ?> neighbours;

    /* optimize the case where block has no properties */
    @Inject(method = "populateNeighbours", at = @At("RETURN"), require = 0)
    private void replaceEmptyTable(CallbackInfo ci) {
        if((this.neighbours instanceof ArrayTable || this.neighbours instanceof HashBasedTable) && this.neighbours.isEmpty())
            this.neighbours = ImmutableTable.of();
    }
}
