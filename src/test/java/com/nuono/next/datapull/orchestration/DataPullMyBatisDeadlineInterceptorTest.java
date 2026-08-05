package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.junit.jupiter.api.Test;

class DataPullMyBatisDeadlineInterceptorTest {

    private static final Method PREPARE = method("prepare", Connection.class, Integer.class);
    private static final Method QUERY = method("query", Statement.class, ResultHandler.class);
    private static final Method UPDATE = method("update", Statement.class);

    @Test
    void blockedStatementGetsRemainingTimeoutAndCompletedConnectionAbort() throws Throwable {
        DataPullMyBatisDeadlineInterceptor interceptor =
                new DataPullMyBatisDeadlineInterceptor();
        Connection connection = connection();
        AtomicBoolean abortRanBeforeReturn = new AtomicBoolean();
        doAnswer(invocation -> {
            AtomicBoolean ran = new AtomicBoolean();
            ((Executor) invocation.getArgument(0)).execute(() -> ran.set(true));
            abortRanBeforeReturn.set(ran.get());
            return null;
        }).when(connection).abort(any(Executor.class));
        Statement statement = mock(Statement.class);
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.query(eq(statement), isNull())).thenAnswer(ignored -> {
            try {
                Thread.sleep(10_000L);
                return java.util.List.of();
            } catch (InterruptedException deadline) {
                throw new SQLException("deadline interrupted query", deadline);
            }
        });

        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, ignored);
            when(statement.getConnection()).thenReturn(bound);
            when(statement.getQueryTimeout()).thenReturn(0);
            when(handler.prepare(bound, null)).thenReturn(statement);
            interceptor.intercept(new Invocation(
                    handler,
                    PREPARE,
                    new Object[]{bound, null}
            ));
            InvocationTargetException failure = assertThrows(
                    InvocationTargetException.class,
                    () -> interceptor.intercept(new Invocation(
                            handler,
                            QUERY,
                            new Object[]{statement, null}
                    ))
            );
            assertTrue(failure.getCause() instanceof SQLException);
            bound.close();
        }

        verify(statement, times(2)).setQueryTimeout(1);
        verify(connection, timeout(1_000)).abort(any(Executor.class));
        verify(statement, never()).cancel();
        assertTrue(abortRanBeforeReturn.get());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void completedStatementStaysAbortableUntilItsBorrowedConnectionIsReturned() throws Throwable {
        DataPullMyBatisDeadlineInterceptor interceptor =
                new DataPullMyBatisDeadlineInterceptor();
        Connection connection = connection();
        Statement statement = mock(Statement.class);
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.update(statement)).thenReturn(1);
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, ignored);
            when(statement.getConnection()).thenReturn(bound);
            when(statement.getQueryTimeout()).thenReturn(0);
            when(handler.prepare(bound, null)).thenReturn(statement);
            interceptor.intercept(new Invocation(
                    handler,
                    PREPARE,
                    new Object[]{bound, null}
            ));
            interceptor.intercept(new Invocation(
                    handler,
                    UPDATE,
                    new Object[]{statement}
            ));
            assertThrows(InterruptedException.class, () -> Thread.sleep(10_000L));
            bound.close();
        }

        verify(connection, timeout(1_000)).abort(any(Executor.class));
        verify(statement, never()).cancel();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void completedNonTransactionalStatementCannotAbortAReusedConnectionLater()
            throws Throwable {
        DataPullMyBatisDeadlineInterceptor interceptor =
                new DataPullMyBatisDeadlineInterceptor();
        Connection connection = connection();
        Statement statement = mock(Statement.class);
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.update(statement)).thenReturn(1);

        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(100))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, ignored);
            when(statement.getConnection()).thenReturn(bound);
            when(statement.getQueryTimeout()).thenReturn(0);
            when(handler.prepare(bound, null)).thenReturn(statement);
            interceptor.intercept(new Invocation(
                    handler,
                    PREPARE,
                    new Object[]{bound, null}
            ));
            interceptor.intercept(new Invocation(
                    handler,
                    UPDATE,
                    new Object[]{statement}
            ));
            bound.close();
            assertThrows(InterruptedException.class, () -> Thread.sleep(10_000L));
        }

        verify(connection, never()).abort(any(Executor.class));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void myBatisLoggingProxyCanUnwrapWithoutLosingTheBorrowDeadlineBinding()
            throws Throwable {
        DataPullMyBatisDeadlineInterceptor interceptor =
                new DataPullMyBatisDeadlineInterceptor();
        Connection connection = connection();
        Statement statement = mock(Statement.class);
        StatementHandler handler = mock(StatementHandler.class);

        try (DataPullAdvanceDeadline deadline =
                     DataPullAdvanceDeadline.open(Duration.ofSeconds(75))) {
            Connection bound = DataPullDeadlineConnection.bind(connection, deadline);
            Connection logged = ConnectionLogger.newInstance(bound, mock(Log.class), 1);
            when(statement.getConnection()).thenReturn(logged);
            when(statement.getQueryTimeout()).thenReturn(0);
            when(handler.prepare(logged, null)).thenReturn(statement);

            interceptor.intercept(new Invocation(
                    handler,
                    PREPARE,
                    new Object[]{logged, null}
            ));

            verify(statement).setQueryTimeout(10);
            bound.close();
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getNetworkTimeout()).thenReturn(0);
        when(connection.isClosed()).thenReturn(false);
        return connection;
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return StatementHandler.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
