package com.nuono.next.datapull.orchestration;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Shared lifecycle barrier for scheduler phases, workers, and their deadline aborts. */
final class DataPullRuntimeStopSignal {
    private final Object monitor = new Object();
    private final Set<DataPullAdvanceDeadline> deadlines = new HashSet<>();
    private boolean stopping;
    private int workers;

    void markRunning() {
        synchronized (monitor) {
            if (!deadlines.isEmpty() || workers != 0) {
                throw new IllegalStateException("DP runtime cannot restart before quiescence");
            }
            stopping = false;
        }
    }

    void markStopping() {
        DataPullAdvanceDeadline[] active;
        synchronized (monitor) {
            stopping = true;
            active = deadlines.toArray(DataPullAdvanceDeadline[]::new);
        }
        for (DataPullAdvanceDeadline deadline : active) deadline.cancelForShutdown();
    }

    boolean isStopping() {
        synchronized (monitor) {
            return stopping;
        }
    }

    void register(DataPullAdvanceDeadline deadline) {
        boolean cancel;
        synchronized (monitor) {
            cancel = stopping;
            deadlines.add(deadline);
        }
        if (cancel) deadline.cancelForShutdown();
    }

    void unregister(DataPullAdvanceDeadline deadline) {
        synchronized (monitor) {
            deadlines.remove(deadline);
            monitor.notifyAll();
        }
    }

    void workerScheduled() {
        synchronized (monitor) {
            workers++;
        }
    }

    void workerFinished() {
        synchronized (monitor) {
            if (--workers < 0) throw new IllegalStateException("DP worker barrier underflow");
            monitor.notifyAll();
        }
    }

    void awaitQuiescence(Duration timeout) {
        long remaining = positiveNanos(timeout);
        long end = System.nanoTime() + remaining;
        synchronized (monitor) {
            while (!deadlines.isEmpty() || workers != 0) {
                if (remaining <= 0L) {
                    throw new IllegalStateException(
                            "DP_RUNTIME_STOP_TIMEOUT:deadlines=" + deadlines.size()
                                    + ",workers=" + workers
                    );
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("DP runtime stop was interrupted", interrupted);
                }
                remaining = end - System.nanoTime();
            }
        }
    }

    int activeDeadlineCount() {
        synchronized (monitor) {
            return deadlines.size();
        }
    }

    int activeWorkerCount() {
        synchronized (monitor) {
            return workers;
        }
    }

    private long positiveNanos(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            return timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
