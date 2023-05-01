package org.embeddedt.modernfix.jei.async;

public interface IAsyncJeiStarter {
    static void checkForLoadInterruption() {
        if(((JEIReloadThread)Thread.currentThread()).isStopRequested())
            throw new JEILoadingInterruptedException();
    }
}
