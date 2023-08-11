package org.embeddedt.modernfix.fabric.api.dynresources;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ModelScanController {
    public static final List<Predicate<ResourceLocation>> SCAN_PREDICATES = new ArrayList<>();
    public static boolean shouldScanAndTestWrapping(ResourceLocation location) {
        if(SCAN_PREDICATES.size() > 0) {
            for(Predicate<ResourceLocation> predicate : SCAN_PREDICATES) {
                if(!predicate.test(location))
                    return false;
            }
        }
        return true;
    }
}
