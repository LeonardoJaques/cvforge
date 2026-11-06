package com.cvforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadTaskConfigurationTest {
    @Test
    void configuredAsyncExecutorRunsTasksOnVirtualThreads() throws Exception {
        SimpleAsyncTaskExecutor executor = new VirtualThreadTaskConfiguration().applicationTaskExecutor();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean virtual = new AtomicBoolean();
        try {
            executor.execute(() -> {
                virtual.set(Thread.currentThread().isVirtual());
                completed.countDown();
            });
            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertTrue(virtual.get());
        } finally {
            executor.close();
        }
    }
}
