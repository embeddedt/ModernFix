package org.embeddedt.modernfix.jei.async;

public class JEIReloadThread extends Thread {
    private volatile boolean stopRequested;

    public JEIReloadThread(Runnable runnable, String s) {
        super(runnable, s);
        this.stopRequested = false;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }
}
