package com.cvforge.performance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyBenchmarkTest {
    @Test void comparesBlockingWorkloadAcrossThreadStrategies() throws Exception {
        int tasks = 160;
        List<ExecutionStrategy> strategies = List.of(new PlatformThreadPoolStrategy(), new VirtualThreadPerTaskStrategy());
        try {
            for (ExecutionStrategy strategy : strategies) {
                long started = System.nanoTime();
                List<Boolean> virtualFlags = strategy.execute(tasks, () -> {
                    Thread.sleep(20);
                    return Thread.currentThread().isVirtual();
                });
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
                assertEquals(tasks, virtualFlags.size());
                assertEquals(tasks, virtualFlags.stream().filter(flag -> flag == strategy.name().startsWith("virtual")).count());
                System.out.printf("BENCHMARK strategy=%s tasks=%d blocking_ms=20 elapsed_ms=%d throughput_rps=%.1f%n",
                        strategy.name(), tasks, elapsedMillis, tasks * 1000.0 / elapsedMillis);
            }
        } finally {
            strategies.forEach(ExecutionStrategy::close);
        }
    }
}
