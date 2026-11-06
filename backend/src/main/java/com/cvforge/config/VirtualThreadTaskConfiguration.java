package com.cvforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/** Explicit bounded virtual-thread executor for asynchronous I/O work. */
@Configuration
@EnableAsync
public class VirtualThreadTaskConfiguration {
    @Bean(name = "applicationTaskExecutor")
    public SimpleAsyncTaskExecutor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("cvforge-vt-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(64);
        return executor;
    }
}
