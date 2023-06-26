package org.embeddedt.modernfix;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.fabric.spark.SparkLaunchProfiler;
import org.embeddedt.modernfix.util.CommonModUtil;

public class ModernFixPreLaunchFabric implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        if(ModernFixMixinPlugin.instance.isOptionEnabled("feature.spark_profile_launch.OnFabric")) {
            CommonModUtil.runWithoutCrash(() -> SparkLaunchProfiler.start("launch"), "Failed to start profiler");
        }
    }
}
