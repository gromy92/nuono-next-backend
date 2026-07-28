package com.nuono.next.competitoranalysis;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class CompetitorMonitoringTaskExecutor implements CompetitorTaskSubmitter {
    private final ThreadPoolExecutor executor;

    CompetitorMonitoringTaskExecutor(
            @Value("${nuono.competitor-analysis.monitor.planner.threads:2}") int threads,
            @Value("${nuono.competitor-analysis.monitor.planner.queue-capacity:100}") int queueCapacity
    ) {
        int poolSize = Math.max(1, threads);
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("competitor-monitor-planner-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void submit(String ignoredAccountKey, Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
