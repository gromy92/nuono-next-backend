package com.nuono.next.noon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class NoonAccountTaskQueue {

    private final ConcurrentMap<String, CompletableFuture<Void>> taskTails = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> accountLocks = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor;

    public NoonAccountTaskQueue(
            @Value("${nuono.noon.max-concurrent-account-tasks:3}") int maxConcurrentAccountTasks
    ) {
        int poolSize = Math.max(1, maxConcurrentAccountTasks);
        this.taskExecutor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("nuono-noon-task-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void submit(String accountKey, Runnable task) {
        String normalizedKey = normalizeKey(accountKey);
        if (!StringUtils.hasText(normalizedKey)) {
            CompletableFuture.runAsync(task, taskExecutor);
            return;
        }

        synchronized (accountLocks.computeIfAbsent(normalizedKey, key -> new Object())) {
            CompletableFuture<Void> previousTail = taskTails.getOrDefault(
                    normalizedKey,
                    CompletableFuture.completedFuture(null)
            );
            CompletableFuture<Void> nextTail = previousTail
                    .handle((ignored, throwable) -> null)
                    .thenRunAsync(task, taskExecutor);
            taskTails.put(normalizedKey, nextTail);
            nextTail.whenComplete((ignored, throwable) -> taskTails.compute(
                    normalizedKey,
                    (key, currentTail) -> currentTail == nextTail ? null : currentTail
            ));
        }
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    @PreDestroy
    void shutdown() {
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            taskExecutor.shutdownNow();
        }
    }
}
