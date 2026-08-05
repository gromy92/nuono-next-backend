package com.nuono.next.datapull.orchestration;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One monotonic, thread-bound deadline for a DP advance phase.
 *
 * <p>The watchdog interrupts the owning worker and aborts every JDBC resource that a deadline-aware
 * MyBatis call has retained. Callers must keep the scope open through transaction completion.</p>
 */
public final class DataPullAdvanceDeadline implements AutoCloseable {

    private static final int ACTIVE = 0;
    private static final int EXPIRING = 1;
    private static final int EXPIRED = 2;
    private static final int CLOSED = 3;
    private static final long DB_BOUND_NANOS = TimeUnit.SECONDS.toNanos(
            DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
    private static final ThreadLocal<DataPullAdvanceDeadline> CURRENT = new ThreadLocal<>();
    private static final ScheduledThreadPoolExecutor WATCHDOGS = watchdogs();

    private final Thread owner;
    private final long deadlineNanos;
    private final DataPullRuntimeStopSignal stopSignal;
    private final AtomicInteger state = new AtomicInteger(ACTIVE);
    private final CountDownLatch expiryDispatched = new CountDownLatch(1);
    private final DataPullDeadlineTermination termination = new DataPullDeadlineTermination();
    private final ConcurrentHashMap<Connection, AtomicInteger> transientConnections =
            new ConcurrentHashMap<>();
    private final ScheduledFuture<?> watchdog;

    private DataPullAdvanceDeadline(long deadlineNanos, DataPullRuntimeStopSignal stopSignal) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("nested DP advance deadlines are not supported");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("DP worker is already interrupted");
        }
        this.owner = Thread.currentThread();
        this.deadlineNanos = deadlineNanos;
        this.stopSignal = stopSignal;
        CURRENT.set(this);
        this.watchdog = WATCHDOGS.schedule(
                this::expire,
                Math.max(0L, remainingNanos()),
                TimeUnit.NANOSECONDS
        );
        if (stopSignal != null) stopSignal.register(this);
    }

    public static DataPullAdvanceDeadline open(Duration budget) {
        return openUntil(deadlineAfter(budget));
    }

    static DataPullAdvanceDeadline openUntil(long deadlineNanos) {
        return new DataPullAdvanceDeadline(deadlineNanos, null);
    }

    static DataPullAdvanceDeadline open(Duration budget, DataPullRuntimeStopSignal stopSignal) {
        return openUntil(deadlineAfter(budget), stopSignal);
    }

    static DataPullAdvanceDeadline openUntil(
            long deadlineNanos,
            DataPullRuntimeStopSignal stopSignal
    ) {
        DataPullRuntimeStopSignal signal = java.util.Objects.requireNonNull(
                stopSignal,
                "stopSignal"
        );
        return new DataPullAdvanceDeadline(deadlineNanos, signal);
    }

    static long deadlineAfter(Duration budget) {
        Duration value = requirePositive(budget, "budget");
        long now = System.nanoTime();
        long nanos;
        try {
            nanos = value.toNanos();
        } catch (ArithmeticException overflow) {
            nanos = Long.MAX_VALUE;
        }
        try {
            return Math.addExact(now, nanos);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static long earlier(long firstDeadlineNanos, long secondDeadlineNanos) {
        return remainingFrom(firstDeadlineNanos) <= remainingFrom(secondDeadlineNanos)
                ? firstDeadlineNanos
                : secondDeadlineNanos;
    }

    /** Caps a blocking transport timeout to the current DP phase's remaining wall-clock budget. */
    public static Duration capRemaining(Duration maximum) {
        Duration requested = requirePositive(maximum, "maximum");
        DataPullAdvanceDeadline current = CURRENT.get();
        if (current == null) return requested;
        long remaining = current.requireRemainingNanos();
        long requestedNanos;
        try {
            requestedNanos = requested.toNanos();
        } catch (ArithmeticException overflow) {
            requestedNanos = Long.MAX_VALUE;
        }
        return Duration.ofNanos(Math.min(remaining, requestedNanos));
    }

    static DataPullAdvanceDeadline current() {
        return CURRENT.get();
    }

    int remainingNetworkTimeoutMillis() {
        long remaining = Math.min(requireRemainingNanos(), DB_BOUND_NANOS);
        long millis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining));
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }

    int remainingQueryTimeoutSeconds() {
        long remaining = Math.min(requireRemainingNanos(), DB_BOUND_NANOS);
        long seconds = Math.max(1L, (remaining + 999_999_999L) / 1_000_000_000L);
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }

    void retainTransientConnection(Connection connection) {
        synchronized (termination) {
            requireOwner();
            requireRemainingNanos();
            transientConnections.compute(connection, (ignored, count) -> {
                if (count == null) return new AtomicInteger(1);
                count.incrementAndGet();
                return count;
            });
        }
    }

    boolean releaseTransientConnection(Connection connection) {
        return releaseConnection(() -> transientConnections.computeIfPresent(
                connection, (ignored, count) ->
                count.decrementAndGet() <= 0 ? null : count
        ));
    }

    boolean retains(Connection connection) {
        return transientConnections.containsKey(connection);
    }

    boolean hasTransientConnections() {
        return !transientConnections.isEmpty();
    }

    boolean isExpired() {
        if (state.get() == EXPIRING || state.get() == EXPIRED) return true;
        if (remainingNanos() <= 0L) expire();
        return state.get() == EXPIRING || state.get() == EXPIRED;
    }

    static void requireRemaining() {
        DataPullAdvanceDeadline current = CURRENT.get();
        if (current != null) current.requireRemainingNanos();
    }

    @Override
    public void close() {
        requireOwner();
        Connection[] leaked = transientConnections.keySet().toArray(Connection[]::new);
        if (leaked.length != 0) expire();
        int previous;
        while (true) {
            previous = state.get();
            if (previous == EXPIRING) {
                awaitExpiryDispatch();
                continue;
            }
            if (state.compareAndSet(previous, CLOSED)) break;
        }
        watchdog.cancel(false);
        if (previous == EXPIRED) termination.awaitCompletion();
        IllegalStateException leakFailure = termination.closeLeaked(leaked);
        if (CURRENT.get() == this) CURRENT.remove();
        transientConnections.clear();
        if (stopSignal != null) stopSignal.unregister(this);
        if (previous == EXPIRED) {
            // Remove only the interrupt raised by this scope before the worker enters its next phase.
            Thread.interrupted();
            if (stopSignal != null && stopSignal.isStopping()) owner.interrupt();
        }
        if (leakFailure != null) throw leakFailure;
    }

    void cancelForShutdown() {
        expire();
    }

    private long requireRemainingNanos() {
        long remaining = remainingNanos();
        if (state.get() != ACTIVE || remaining <= 0L) {
            expire();
            throw new IllegalStateException("DP_ADVANCE_DEADLINE_EXCEEDED");
        }
        return remaining;
    }

    private long remainingNanos() {
        return remainingFrom(deadlineNanos);
    }

    private static long remainingFrom(long deadlineNanos) {
        return deadlineNanos - System.nanoTime();
    }

    private void expire() {
        synchronized (termination) {
            if (!state.compareAndSet(ACTIVE, EXPIRING)) return;
            try {
                owner.interrupt();
                for (Connection connection : transientConnections.keySet()) {
                    termination.abortBound(connection);
                }
            } finally {
                state.compareAndSet(EXPIRING, EXPIRED);
                expiryDispatched.countDown();
            }
        }
    }

    private void awaitExpiryDispatch() {
        boolean interrupted = false;
        while (true) {
            try {
                expiryDispatched.await();
                break;
            } catch (InterruptedException deadlineInterrupt) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.interrupted();
    }

    private boolean awaitTerminationIfExpired() {
        if (state.get() == ACTIVE && remainingNanos() <= 0L) expire();
        if (state.get() == EXPIRING) awaitExpiryDispatch();
        boolean expired = state.get() == EXPIRED;
        if (expired) termination.awaitCompletion();
        return expired;
    }

    private boolean releaseConnection(Runnable remove) {
        synchronized (termination) {
            if (state.get() == ACTIVE && remainingNanos() > 0L) {
                remove.run();
                return false;
            }
        }
        boolean expired = awaitTerminationIfExpired();
        synchronized (termination) {
            remove.run();
        }
        return expired;
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner || CURRENT.get() != this) {
            throw new IllegalStateException("DP advance deadline used outside its worker");
        }
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static ScheduledThreadPoolExecutor watchdogs() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                4,
                task -> daemon(task, "dp-deadline-watchdog")
        );
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
}
