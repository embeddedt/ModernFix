package org.embeddedt.modernfix.world;

import net.minecraft.DefaultUncaughtExceptionHandlerWithName;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class IntegratedWatchdog extends Thread {
    private static final Logger LOGGER = LogManager.getLogger();

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
                ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);
                StringBuilder sb = new StringBuilder();
                sb.append("Thread Dump:\n");
                for(ThreadInfo threadinfo : athreadinfo) {
                    sb.append(threadinfo);
                    StackTraceElement[] elements = threadinfo.getStackTrace();
                    if(elements.length > 8) {
                        sb.append("extended trace:\n");
                        for(int i = 8; i < elements.length; i++) {
                            sb.append("\tat ");
                            sb.append(elements[i]);
                            sb.append('\n');
                        }
                    }
                    sb.append('\n');
                }
                LOGGER.error(sb);
                nextTick = 0;
                curTime = 0;
            }
            try {
                Thread.sleep(nextTick + MAX_TICK_DELTA - curTime);
            } catch(InterruptedException ignored) {
            }
        }
    }
}
