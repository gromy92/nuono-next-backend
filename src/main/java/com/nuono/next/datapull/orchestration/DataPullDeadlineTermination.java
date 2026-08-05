package com.nuono.next.datapull.orchestration;

import java.sql.Connection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Completes JDBC cancellation before a deadline-bound connection can return to the pool. */
final class DataPullDeadlineTermination {

    private static final Executor DIRECT = Runnable::run;
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "dp-deadline-termination");
        thread.setDaemon(true);
        return thread;
    });

    private final Set<CompletableFuture<Void>> tasks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static Executor directExecutor() {
        return DIRECT;
    }

    static void abortNow(Connection connection) {
        abort(connection);
    }

    void abortBound(Connection connection) {
        tasks.add(CompletableFuture.runAsync(() -> abort(connection), WORKERS));
    }

    void awaitCompletion() {
        CompletableFuture<?>[] pending = tasks.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(pending).join();
    }

    IllegalStateException closeLeaked(Connection[] leaked) {
        if (leaked.length == 0) return null;
        IllegalStateException failure = new IllegalStateException(
                "DP_DEADLINE_CONNECTION_LEAK"
        );
        for (Connection connection : leaked) {
            try {
                connection.close();
            } catch (Exception closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
        return failure;
    }

    private static void abort(Connection connection) {
        try {
            // Do not consult proxy.isClosed(): close may be waiting on this exact abort barrier.
            connection.abort(DIRECT);
        } catch (Exception ignored) {
            // The owning JDBC operation observes its own interrupted/failed state.
        }
    }
}
