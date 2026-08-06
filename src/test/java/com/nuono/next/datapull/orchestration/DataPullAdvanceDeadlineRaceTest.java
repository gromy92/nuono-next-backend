package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DataPullAdvanceDeadlineRaceTest {

    @Test
    void expiredConnectionCannotBeReleasedBeforeItsDirectAbortCompletes() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        CountDownLatch abortStarted = new CountDownLatch(1);
        CountDownLatch allowAbort = new CountDownLatch(1);
        AtomicBoolean releaseReturned = new AtomicBoolean();
        AtomicBoolean observedPrematureRelease = new AtomicBoolean();
        doAnswer(invocation -> {
            ((Executor) invocation.getArgument(0)).execute(() -> {
                abortStarted.countDown();
                await(allowAbort);
            });
            return null;
        }).when(connection).abort(any(Executor.class));
        doAnswer(ignored -> {
            await(abortStarted);
            return null;
        }).when(connection).close();
        Thread controller = new Thread(() -> {
            await(abortStarted);
            observedPrematureRelease.set(releaseReturned.get());
            allowAbort.countDown();
        });

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, deadline);
            controller.start();
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException expected) {
                // Deadline owns this interrupt.
            }
            bound.close();
            releaseReturned.set(true);
        }

        controller.join(1_000L);
        assertFalse(controller.isAlive());
        assertFalse(observedPrematureRelease.get());
        verify(connection).abort(any(Executor.class));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void everyJdbcBoundaryIsCappedAtTenSecondsInsideTheLargerJobBudget() {
        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(75))) {
            assertTrue(deadline.remainingNetworkTimeoutMillis() <= 10_000);
            assertTrue(deadline.remainingQueryTimeoutSeconds() <= 10);
        }
    }

    @Test
    void connectionStaysAbortableUntilItsBlockingPhysicalCloseFinishes() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(300_000);
        when(connection.isClosed()).thenReturn(false);
        CountDownLatch abortStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            ((Executor) invocation.getArgument(0)).execute(abortStarted::countDown);
            return null;
        }).when(connection).abort(any(Executor.class));
        doAnswer(ignored -> {
            boolean interrupted = false;
            while (true) {
                try {
                    if (abortStarted.await(1, TimeUnit.SECONDS)) break;
                } catch (InterruptedException deadline) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return null;
        }).when(connection).close();

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, deadline);
            bound.close();
        }

        assertTrue(abortStarted.await(1, TimeUnit.SECONDS));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void abortAfterLogicalCloseEvictsButNeverTouchesAReturnedPhysicalConnection()
            throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(300_000);
        when(connection.isClosed()).thenReturn(false);
        AtomicBoolean evicted = new AtomicBoolean();

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5))) {
            Connection bound = DataPullDeadlineConnection.bind(
                    connection,
                    deadline,
                    ignored -> evicted.set(true)
            );
            bound.close();
            bound.abort(DataPullDeadlineTermination.directExecutor());
        }

        assertTrue(evicted.get());
        verify(connection, never()).abort(any(Executor.class));
    }

    @Test
    void quarantineFailureStillAbortsClosesAndReleasesTheConnection() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5))) {
            Connection bound = DataPullDeadlineConnection.bind(
                    connection,
                    deadline,
                    ignored -> {
                        throw new IllegalStateException("eviction unavailable");
                    }
            );

            assertThrows(IllegalStateException.class, bound::close);

            assertFalse(deadline.retains(bound));
            verify(connection).abort(any(Executor.class));
            verify(connection).close();
        }
    }

    @Test
    void bindFailureQuarantinesBeforeAbortAndCloseAndClearsTheRegistry() throws Exception {
        Connection connection = mock(Connection.class);
        List<String> cleanupOrder = new ArrayList<>();
        doAnswer(ignored -> {
            throw new SQLException("network timeout unavailable");
        }).when(connection).setNetworkTimeout(any(Executor.class), anyInt());
        doAnswer(invocation -> {
            cleanupOrder.add("abort");
            ((Executor) invocation.getArgument(0)).execute(() -> { });
            return null;
        }).when(connection).abort(any(Executor.class));
        doAnswer(ignored -> {
            cleanupOrder.add("close");
            return null;
        }).when(connection).close();

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5))) {
            assertThrows(SQLException.class, () -> DataPullDeadlineConnection.bind(
                    connection,
                    deadline,
                    ignored -> cleanupOrder.add("evict")
            ));

            assertFalse(deadline.hasTransientConnections());
            assertTrue(cleanupOrder.equals(List.of("evict", "abort", "close")));
        }
    }

    @Test
    void runtimeCloseFailureStillAbortsReleasesAndReachesItsTerminalLifecycle()
            throws Exception {
        Connection connection = mock(Connection.class);
        RuntimeException closeFailure = new IllegalStateException("driver close failed");
        doAnswer(ignored -> {
            throw closeFailure;
        }).when(connection).close();

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, deadline);

            assertTrue(assertThrows(RuntimeException.class, bound::close) == closeFailure);

            assertFalse(deadline.retains(bound));
            bound.close();
            verify(connection).abort(any(Executor.class));
        }
    }

    @Test
    void deadlineScopeLeakIsAbortedQuarantinedClosedAndReported() throws Exception {
        Connection connection = mock(Connection.class);
        AtomicBoolean evicted = new AtomicBoolean();
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.open(Duration.ofSeconds(5));
        DataPullDeadlineConnection.bind(
                connection,
                deadline,
                ignored -> evicted.set(true)
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                deadline::close
        );

        assertTrue(failure.getMessage().contains("DP_DEADLINE_CONNECTION_LEAK"));
        assertTrue(evicted.get());
        assertFalse(deadline.hasTransientConnections());
        verify(connection).abort(any(Executor.class));
        verify(connection).close();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("deadline race fixture interrupted", interrupted);
        }
    }
}
