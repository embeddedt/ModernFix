package org.embeddedt.modernfix.world;

import com.mojang.logging.LogUtils;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.embeddedt.modernfix.duck.ITimeTrackingServer;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.OptionalLong;

public class IntegratedWatchdog extends Thread {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WeakReference<MinecraftServer> server;

    private static final long MAX_TICK_DELTA = 40*1000;

    public IntegratedWatchdog(MinecraftServer server) {
        this.server = new WeakReference<>(server);
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandlerWithName(LOGGER));
        this.setName("ModernFix integrated server watchdog");
    }

    private OptionalLong getLastTickStart() {
        MinecraftServer server = this.server.get();
        if(server == null || !server.isRunning())
            return OptionalLong.empty();
        return OptionalLong.of(((ITimeTrackingServer)server).mfix$getLastTickStartTime());
    }

    public void run() {
        while(true) {
            OptionalLong lastTickStart = getLastTickStart();
            if(!lastTickStart.isPresent()) {
                return;
            }
            if(lastTickStart.getAsLong() < 0) {
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException ignored) {}
                continue;
            }
            long curTime = Util.getMillis();
            long delta = curTime - lastTickStart.getAsLong();
            if(delta > MAX_TICK_DELTA) {
                LOGGER.error("A single server tick has taken {}, more than {} milliseconds", delta, MAX_TICK_DELTA);
                LOGGER.error(ThreadDumper.obtainThreadDump());
                delta = 0;
            }
            try {
                Thread.sleep(MAX_TICK_DELTA - delta);
            } catch(InterruptedException ignored) {
            }
        }
    }
}
