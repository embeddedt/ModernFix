package org.embeddedt.modernfix.world;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadDumper {
    public static String obtainThreadDump() {
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
        return sb.toString();
    }
}
