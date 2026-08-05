package com.nuono.next.datapull.orchestration;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.pool.HikariPool;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** Registers a DP connection immediately after pool checkout, before transaction begin commands. */
final class DataPullDeadlineAwareDataSource extends DelegatingDataSource implements AutoCloseable {

    DataPullDeadlineAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        DataPullAdvanceDeadline.requireRemaining();
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        DataPullAdvanceDeadline.requireRemaining();
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        DataPullAdvanceDeadline deadline = DataPullAdvanceDeadline.current();
        return deadline == null
                ? connection
                : DataPullDeadlineConnection.bind(connection, deadline, this::evict, true);
    }

    private void evict(Connection connection) {
        DataSource target = getTargetDataSource();
        if (!(target instanceof HikariDataSource)) {
            throw new IllegalStateException("DP deadline datasource requires Hikari eviction");
        }
        HikariDataSource hikari = (HikariDataSource) target;
        hikari.evictConnection(connection);
        HikariPoolMXBean state = hikari.getHikariPoolMXBean();
        if (state instanceof HikariPool && state.getThreadsAwaitingConnection() > 0) {
            // Hikari 4 does not refill a minimumIdle=0 pool for borrowers that were already
            // waiting when an active entry was explicitly evicted.
            ((HikariPool) state).addBagItem(state.getThreadsAwaitingConnection());
        }
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
