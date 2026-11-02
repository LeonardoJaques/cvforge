package com.cvforge.performance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PlatformThreadPoolStrategy implements ExecutionStrategy {
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    @Override public String name() { return "platform-thread-pool"; }
    @Override public ExecutorService executor() { return executor; }
}
