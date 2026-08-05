package com.nuono.next.datapull.orchestration;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Registers a DP connection immediately after pool checkout, before transaction begin commands. */
final class DataPullDeadlineAwareDataSource extends DelegatingDataSource implements AutoCloseable {

    DataPullDeadlineAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.current();
        return deadline == null
                ? connection
                : DataPullDeadlineConnection.bind(connection, deadline, this::evict);
    }

    private void evict(Connection connection) {
        DataSource target = getTargetDataSource();
        if (!(target instanceof HikariDataSource)) {
            throw new IllegalStateException("DP deadline datasource requires Hikari eviction");
        }
        ((HikariDataSource) target).evictConnection(connection);
    }

    boolean hasUnwrappableTarget() {
        DataSource target = getTargetDataSource();
        if (!(target instanceof HikariDataSource)) return false;
        try {
            return unwrap(HikariDataSource.class) == target;
        } catch (SQLException unavailable) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        DataSource target = getTargetDataSource();
        if (target instanceof AutoCloseable) ((AutoCloseable) target).close();
    }
}
