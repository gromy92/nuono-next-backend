package com.nuono.next.datapull.orchestration;

import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;

/** Applies the current DP phase deadline to every MyBatis JDBC blocking boundary. */
@Intercepts({
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "parameterize",
                args = {Statement.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "query",
                args = {Statement.class, ResultHandler.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "queryCursor",
                args = {Statement.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "update",
                args = {Statement.class}
        ),
        @Signature(
                type = StatementHandler.class,
                method = "batch",
                args = {Statement.class}
        )
})
public final class DataPullMyBatisDeadlineInterceptor implements Interceptor {

    private final java.util.Map<Statement, Binding> bindings = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.current();
        if (deadline == null) return invocation.proceed();
        String method = invocation.getMethod().getName();
        if ("prepare".equals(method)) return prepare(invocation, deadline);
        if ("parameterize".equals(method)) return parameterize(invocation);
        return execute(invocation, deadline);
    }

    private Object prepare(Invocation invocation, DataPullAdvanceDeadline deadline)
            throws Throwable {
        Connection connection = (Connection) invocation.getArgs()[0];
        requireBorrowBinding(deadline, connection);
        applyNetworkTimeout(
                connection,
                connection.getNetworkTimeout(),
                deadline.remainingNetworkTimeoutMillis()
        );
        Statement statement = (Statement) invocation.proceed();
        applyQueryTimeout(statement, deadline.remainingQueryTimeoutSeconds());
        bindings.put(statement, new Binding(deadline, connection));
        return statement;
    }

    private Object parameterize(Invocation invocation) throws Throwable {
        Statement statement = (Statement) invocation.getArgs()[0];
        try {
            return invocation.proceed();
        } catch (Throwable failure) {
            release(statement, bindings.remove(statement));
            throw failure;
        }
    }

    private Object execute(Invocation invocation, DataPullAdvanceDeadline deadline)
            throws Throwable {
        Statement statement = (Statement) invocation.getArgs()[0];
        Binding binding = bindings.get(statement);
        if (binding == null) {
            Connection connection = statement.getConnection();
            requireBorrowBinding(deadline, connection);
            binding = new Binding(deadline, connection);
            bindings.put(statement, binding);
        }
        if (binding.deadline != deadline) {
            throw new IllegalStateException("DP MyBatis statement crossed a deadline scope");
        }
        applyNetworkTimeout(
                binding.connection,
                binding.connection.getNetworkTimeout(),
                deadline.remainingNetworkTimeoutMillis()
        );
        applyQueryTimeout(statement, deadline.remainingQueryTimeoutSeconds());
        try {
            if ("queryCursor".equals(invocation.getMethod().getName())) {
                throw new IllegalStateException("DP MyBatis cursors are not deadline-safe");
            }
            return invocation.proceed();
        } finally {
            bindings.remove(statement);
        }
    }

    private void release(Statement statement, Binding binding) {
        if (binding != null) bindings.remove(statement);
    }

    private void requireBorrowBinding(
            DataPullAdvanceDeadline deadline,
            Connection connection
    ) throws java.sql.SQLException {
        DataPullDeadlineBoundConnection bound = boundConnection(connection);
        if (bound == null
                || bound.deadlineOwner() != deadline
                || !deadline.retains(bound)) {
            throw new IllegalStateException("DP_CONNECTION_NOT_DEADLINE_BOUND");
        }
    }

    private DataPullDeadlineBoundConnection boundConnection(Connection connection)
            throws java.sql.SQLException {
        if (connection instanceof DataPullDeadlineBoundConnection) {
            return (DataPullDeadlineBoundConnection) connection;
        }
        if (connection.isWrapperFor(DataPullDeadlineBoundConnection.class)) {
            return connection.unwrap(DataPullDeadlineBoundConnection.class);
        }
        return null;
    }

    private void applyNetworkTimeout(
            Connection connection,
            int existing,
            int remainingMillis
    )
            throws java.sql.SQLException {
        if (existing == 0 || existing > remainingMillis) {
            connection.setNetworkTimeout(
                    DataPullDeadlineTermination.directExecutor(),
                    remainingMillis
            );
        }
    }

    private void applyQueryTimeout(Statement statement, int remainingSeconds)
            throws java.sql.SQLException {
        int existing = statement.getQueryTimeout();
        if (existing == 0 || existing > remainingSeconds) {
            statement.setQueryTimeout(remainingSeconds);
        }
    }

    private static final class Binding {
        private final DataPullAdvanceDeadline deadline;
        private final Connection connection;

        private Binding(DataPullAdvanceDeadline deadline, Connection connection) {
            this.deadline = deadline;
            this.connection = connection;
        }
    }
}
