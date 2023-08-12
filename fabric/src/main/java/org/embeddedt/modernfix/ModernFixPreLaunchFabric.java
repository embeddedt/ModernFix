package org.embeddedt.modernfix;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.impl.gui.FabricGuiEntry;
import net.fabricmc.loader.impl.gui.FabricStatusTree;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.fabric.mappings.MappingsClearer;
import org.embeddedt.modernfix.spark.SparkLaunchProfiler;
import org.embeddedt.modernfix.util.CommonModUtil;

public class ModernFixPreLaunchFabric implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if(ModernFixMixinPlugin.instance == null) {
            System.err.println("Mixin plugin not loaded yet");
            return;
        }
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spark_profile_launch.OnFabric")) {
            CommonModUtil.runWithoutCrash(() -> SparkLaunchProfiler.start("launch"), "Failed to start profiler");
        }
        if(ModernFixMixinPlugin.instance.isOptionEnabled("perf.clear_fabric_mapping_tables.MappingsClearer")) {
            MappingsClearer.clear();
        }

        // Prevent launching with Continuity when dynamic resources is on
        if(false && ModernFixMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.ContinuityCheck")
                && FabricLoader.getInstance().isModLoaded("continuity")) {
            CommonModUtil.runWithoutCrash(() -> {
                FabricGuiEntry.displayError("Compatibility warning", null, tree -> {
                    FabricStatusTree.FabricStatusTab crashTab = tree.addTab("Warning");
                    crashTab.node.addMessage("Continuity and ModernFix's dynamic resources option are not compatible before Minecraft 1.19.4.", FabricStatusTree.FabricTreeWarningLevel.ERROR);
                    crashTab.node.addMessage("Remove Continuity or disable dynamic resources in the ModernFix config.", FabricStatusTree.FabricTreeWarningLevel.ERROR);
                    tree.tabs.removeIf(tab -> tab != crashTab);
                }, true);
            }, "display Continuity warning");
        }
    }
}
