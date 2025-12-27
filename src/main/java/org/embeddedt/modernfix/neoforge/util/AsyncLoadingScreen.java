package org.embeddedt.modernfix.neoforge.util;

import net.neoforged.fml.loading.ImmediateWindowHandler;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class AsyncLoadingScreen extends Thread implements AutoCloseable {
    private final long theWindow;
    private final AtomicBoolean keepRunning;

    private static int splashThreadNum = 1;

    private static GLCapabilities caps;

    public AsyncLoadingScreen() {
        this.setName("ModernFix splash thread " + splashThreadNum++);
        this.theWindow = GLFW.glfwGetCurrentContext();
        if(caps == null)
            caps = GL.createCapabilities();
        if(this.theWindow == 0)
            throw new IllegalStateException("No context found but async loading screen was requested");
        this.keepRunning = new AtomicBoolean(true);
        this.start();
    }

    @Override
    public synchronized void start() {
        GLFW.glfwMakeContextCurrent(0);
        super.start();
    }

    @Override
    public void run() {
        GLFW.glfwMakeContextCurrent(theWindow);
        GL.setCapabilities(caps);
        while(keepRunning.get()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
            ImmediateWindowHandler.renderTick();
        }
        GLFW.glfwMakeContextCurrent(0);
    }

    @Override
    public void close() {
        keepRunning.set(false);
        try {
            this.join();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        GLFW.glfwMakeContextCurrent(theWindow);
    }
}
