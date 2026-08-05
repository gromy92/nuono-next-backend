package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DataPullDeadlineAwareDataSourceTest {

    @Test
    void nonDpBorrowKeepsThePoolSocketTimeoutUntouched() throws Exception {
        HikariDataSource target = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        when(target.getConnection()).thenReturn(connection);
        DataPullDeadlineAwareDataSource source = new DataPullDeadlineAwareDataSource(target);

        Connection borrowed = source.getConnection();

        assertSame(connection, borrowed);
        verify(connection, never()).getNetworkTimeout();
        verify(connection, never()).setNetworkTimeout(any(Executor.class), anyInt());
    }

    @Test
    void transactionBeginCommandIsBoundedBeforeMyBatisPreparesAStatement() throws Exception {
        HikariDataSource target = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        AtomicInteger networkTimeout = new AtomicInteger(300_000);
        AtomicBoolean abortRanBeforeReturn = new AtomicBoolean();
        when(target.getConnection()).thenReturn(connection);
        when(connection.getNetworkTimeout()).thenAnswer(ignored -> networkTimeout.get());
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.isClosed()).thenReturn(false);
        doAnswer(invocation -> {
            Executor executor = invocation.getArgument(0);
            int timeout = invocation.getArgument(1);
            executor.execute(() -> networkTimeout.set(timeout));
            return null;
        }).when(connection).setNetworkTimeout(any(Executor.class), anyInt());
        doAnswer(invocation -> {
            try {
                Thread.sleep(10_000L);
                return null;
            } catch (InterruptedException deadline) {
                throw new SQLException("transaction begin interrupted", deadline);
            }
        }).when(connection).setAutoCommit(false);
        doAnswer(invocation -> {
            AtomicBoolean ran = new AtomicBoolean();
            ((Executor) invocation.getArgument(0)).execute(() -> ran.set(true));
            abortRanBeforeReturn.set(ran.get());
            return null;
        }).when(connection).abort(any(Executor.class));
        DataPullDeadlineAwareDataSource source = new DataPullDeadlineAwareDataSource(target);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(source)
        );
        long started = System.nanoTime();

        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            assertThrows(RuntimeException.class, () -> transaction.execute(status -> null));
        }

        assertTrue(Duration.ofNanos(System.nanoTime() - started)
                .compareTo(Duration.ofSeconds(1)) < 0);
        assertTrue(abortRanBeforeReturn.get());
        assertTrue(networkTimeout.get() <= 100);
        verify(target).evictConnection(connection);
        verify(connection, never()).close();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void healthyDpCloseQuarantinesThePhysicalConnectionInsteadOfReturningIt() throws Exception {
        HikariDataSource target = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        AtomicInteger networkTimeout = new AtomicInteger(300_000);
        when(target.getConnection()).thenReturn(connection);
        when(connection.getNetworkTimeout()).thenAnswer(ignored -> networkTimeout.get());
        when(connection.isClosed()).thenReturn(false);
        doAnswer(invocation -> {
            ((Executor) invocation.getArgument(0)).execute(
                    () -> networkTimeout.set(invocation.getArgument(1))
            );
            return null;
        }).when(connection).setNetworkTimeout(any(Executor.class), anyInt());
        DataPullDeadlineAwareDataSource source = new DataPullDeadlineAwareDataSource(target);

        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(75))) {
            Connection borrowed = source.getConnection();
            assertTrue(networkTimeout.get() <= 10_000);
            borrowed.close();
        }

        assertTrue(networkTimeout.get() <= 10_000);
        verify(target).evictConnection(connection);
        verify(connection, never()).close();
        verify(connection, never()).abort(any(Executor.class));
    }

    @Test
    void deadlineBorrowFailsClosedWhenTheDatasourceCannotQuarantine() throws Exception {
        DataSource target = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(target.getConnection()).thenReturn(connection);
        DataPullDeadlineAwareDataSource source = new DataPullDeadlineAwareDataSource(target);

        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(5))) {
            Connection borrowed = source.getConnection();
            assertThrows(IllegalStateException.class, borrowed::close);
        }

        verify(connection).abort(any(Executor.class));
        verify(connection).close();
    }
}
