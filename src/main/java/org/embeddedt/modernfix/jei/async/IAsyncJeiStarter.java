package org.embeddedt.modernfix.jei.async;

public interface IAsyncJeiStarter {
    static void checkForLoadInterruption() {
        if(Thread.currentThread().isInterrupted())
            throw new JEILoadingInterruptedException();
    }
}
