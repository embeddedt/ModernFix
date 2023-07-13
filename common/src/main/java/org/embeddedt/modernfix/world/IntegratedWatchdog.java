package org.embeddedt.modernfix.world;

import com.mojang.logging.LogUtils;
import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;

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

    public void run() {
        while(true) {
            MinecraftServer server = this.server.get();
            if(server == null || !server.isRunning())
                return;
            long nextTick = server.getNextTickTime();
            long curTime = Util.getMillis();
            long delta = curTime - nextTick;
            if(delta > MAX_TICK_DELTA) {
                LOGGER.error("A single server tick has taken {}, more than {} milliseconds", delta, MAX_TICK_DELTA);
                LOGGER.error(ThreadDumper.obtainThreadDump());
                nextTick = 0;
                curTime = 0;
            }
            server = null; /* allow GC */
            try {
                Thread.sleep(nextTick + MAX_TICK_DELTA - curTime);
            } catch(InterruptedException ignored) {
            }
        }
    }
}
