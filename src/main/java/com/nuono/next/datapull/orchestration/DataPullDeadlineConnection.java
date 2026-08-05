package com.nuono.next.datapull.orchestration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** A borrowed connection that cannot return to its pool before deadline termination completes. */
final class DataPullDeadlineConnection implements InvocationHandler {

    private static final int OPEN = 0;
    private static final int CLOSING = 1;
    private static final int TARGET_CLOSED = 2;
    private static final int CLOSED = 3;

    private final Connection target;
    private final DataPullAdvanceDeadline deadline;
    private final Consumer<Connection> evict;
    private final AtomicInteger lifecycle = new AtomicInteger(OPEN);
    private Connection proxy;

    private DataPullDeadlineConnection(
            Connection target,
            DataPullAdvanceDeadline deadline,
            Consumer<Connection> evict
    ) {
        this.target = target;
        this.deadline = deadline;
        this.evict = evict;
    }

    static Connection bind(Connection target, DataPullAdvanceDeadline deadline)
            throws SQLException {
        return bind(target, deadline, ignored -> { });
    }

    static Connection bind(
            Connection target,
            DataPullAdvanceDeadline deadline,
            Consumer<Connection> evict
    ) throws SQLException {
        DataPullDeadlineConnection handler = new DataPullDeadlineConnection(
                target,
                deadline,
                evict
        );
        Connection proxy = (Connection) Proxy.newProxyInstance(
                DataPullDeadlineConnection.class.getClassLoader(),
                new Class<?>[]{DataPullDeadlineBoundConnection.class},
                handler
        );
        handler.proxy = proxy;
        boolean retained = false;
        try {
            deadline.retainTransientConnection(proxy);
            retained = true;
            target.setNetworkTimeout(
                    DataPullDeadlineTermination.directExecutor(),
                    deadline.remainingNetworkTimeoutMillis()
            );
            return proxy;
        } catch (SQLException | RuntimeException failure) {
            handler.terminateAfterBindFailure(failure, retained);
            throw failure;
        }
    }

    @Override
    public Object invoke(Object ignored, Method method, Object[] arguments) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            if ("equals".equals(name)) return proxy == arguments[0];
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "DeadlineBound[" + target + "]";
        }
        if ("close".equals(name)) {
            close();
            return null;
        }
        if ("isClosed".equals(name) && lifecycle.get() != OPEN) return true;
        if ("deadlineOwner".equals(name)) return deadline;
        if ("getTargetConnection".equals(name)) return target;
        if ("unwrap".equals(name)) {
            Class<?> type = (Class<?>) arguments[0];
            if (type.isInstance(proxy)) return type.cast(proxy);
        }
        if ("isWrapperFor".equals(name)
                && ((Class<?>) arguments[0]).isInstance(proxy)) return true;
        if ("abort".equals(name)) {
            RuntimeException quarantineFailure = quarantine();
            if (lifecycle.get() >= TARGET_CLOSED) {
                if (quarantineFailure != null) throw quarantineFailure;
                return null;
            }
            if (quarantineFailure != null) {
                try {
                    method.invoke(target, arguments);
                } catch (InvocationTargetException abortFailure) {
                    quarantineFailure.addSuppressed(abortFailure.getCause());
                }
                throw quarantineFailure;
            }
        }
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException delegatedFailure) {
            throw delegatedFailure.getCause();
        }
    }

    private void close() throws SQLException {
        if (!lifecycle.compareAndSet(OPEN, CLOSING)) return;
        // Quarantine before cleanup: Hikari may block in statement close/rollback/reset and only
        // recycles at the tail. A quarantined entry is physically closed, never re-borrowed.
        Throwable failure = quarantine();
        if (failure != null) DataPullDeadlineTermination.abortNow(target);
        try {
            target.close();
        } catch (SQLException | RuntimeException closeFailure) {
            failure = append(failure, closeFailure);
            DataPullDeadlineTermination.abortNow(target);
        } finally {
            lifecycle.set(TARGET_CLOSED);
            try {
                deadline.releaseTransientConnection(proxy);
            } catch (RuntimeException releaseFailure) {
                failure = append(failure, releaseFailure);
            } finally {
                lifecycle.set(CLOSED);
            }
        }
        if (failure instanceof SQLException) throw (SQLException) failure;
        if (failure != null) throw (RuntimeException) failure;
    }

    private RuntimeException quarantine() {
        try {
            evict.accept(target);
            return null;
        } catch (RuntimeException unavailable) {
            return unavailable;
        }
    }

    private void terminateAfterBindFailure(Throwable failure, boolean retained) {
        lifecycle.compareAndSet(OPEN, CLOSING);
        RuntimeException quarantineFailure = quarantine();
        if (quarantineFailure != null) failure.addSuppressed(quarantineFailure);
        DataPullDeadlineTermination.abortNow(target);
        try {
            target.close();
        } catch (SQLException | RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        } finally {
            lifecycle.set(TARGET_CLOSED);
            if (retained) {
                try {
                    deadline.releaseTransientConnection(proxy);
                } catch (RuntimeException releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
            }
            lifecycle.set(CLOSED);
        }
    }

    private static Throwable append(Throwable primary, Throwable cleanup) {
        if (primary == null) return cleanup;
        primary.addSuppressed(cleanup);
        return primary;
    }
}
