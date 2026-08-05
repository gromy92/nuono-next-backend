package com.nuono.next.datapull.orchestration;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/** Exposes the production package-private deadline datasource to exact-path MySQL tests. */
public final class DataPullDeadlineMySqlTestSupport {
    private DataPullDeadlineMySqlTestSupport() {
    }

    public static DataSource deadlineAware(HikariDataSource source) {
        return new DataPullDeadlineAwareDataSource(source);
    }
}
