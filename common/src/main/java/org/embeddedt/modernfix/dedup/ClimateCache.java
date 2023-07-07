package org.embeddedt.modernfix.dedup;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.minecraft.world.level.biome.Climate;

public class ClimateCache {
    public static final Interner<Climate.Parameter> MFIX_INTERNER = Interners.newStrongInterner();
}
