package com.cvforge.performance;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.stream.IntStream;

/** Strategy pattern for running the same workload on different executor types. */
interface ExecutionStrategy extends AutoCloseable {
    String name();
    ExecutorService executor();
    default List<Boolean> execute(int tasks, Callable<Boolean> work) throws Exception {
        var futures = IntStream.range(0, tasks).mapToObj(ignored -> executor().submit(work)).toList();
        var results = new java.util.ArrayList<Boolean>(tasks);
        for (var future : futures) results.add(future.get());
        return results;
    }
    @Override default void close() { executor().shutdownNow(); }
}
