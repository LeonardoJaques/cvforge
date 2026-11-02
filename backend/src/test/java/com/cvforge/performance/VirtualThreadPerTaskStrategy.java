package com.cvforge.performance;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class VirtualThreadPerTaskStrategy implements ExecutionStrategy {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @Override public String name() { return "virtual-thread-per-task"; }
    @Override public ExecutorService executor() { return executor; }
}
