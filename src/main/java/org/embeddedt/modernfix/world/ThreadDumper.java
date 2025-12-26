package org.embeddedt.modernfix.world;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadDumper {
    private static final String STACKTRACE_TAIL = "\t...\n\n";
    public static String obtainThreadDump() {
        ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);
        StringBuilder sb = new StringBuilder();
        sb.append("Thread Dump:\n");
        for(ThreadInfo threadinfo : athreadinfo) {
            String tInfo = threadinfo.toString();
            StackTraceElement[] elements = threadinfo.getStackTrace();
            if(elements.length > 8) {
                if(tInfo.endsWith(STACKTRACE_TAIL))
                    tInfo = tInfo.substring(0, tInfo.length() - STACKTRACE_TAIL.length());
                else
                    tInfo = tInfo + "extended trace:\n";
            }
            sb.append(tInfo);
            if(elements.length > 8) {
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
