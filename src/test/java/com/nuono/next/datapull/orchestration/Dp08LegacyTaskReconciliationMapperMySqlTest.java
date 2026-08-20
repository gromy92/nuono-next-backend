package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class Dp08LegacyTaskReconciliationMapperMySqlTest {

    @Test
    void countsOnlyActiveLegacyTasksAndRunsOnMySql() throws Exception {
        String url = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        try (Connection connection = DriverManager.getConnection(
                url,
                System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"),
                System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD")
        )) {
            createTemporaryTables(connection);
            Dp08LegacyTaskReconciliationMapper mapper = mapper(connection);

            assertEquals(0, mapper.countActiveRows());
            execute(connection,
                    "INSERT INTO operational_task VALUES "
                            + "('OPERATIONS_COMPETITOR_REFRESH','QUEUED',b'0'),"
                            + "('OPERATIONS_COMPETITOR_MONITORING','SUCCEEDED',b'0'),"
                            + "('OTHER','RUNNING',b'0'),"
                            + "('OPERATIONS_COMPETITOR_MONITORING_CYCLE','RUNNING',b'1')");
            execute(connection,
                    "INSERT INTO operations_competitor_search_run VALUES "
                            + "('RUNNING',b'0'),('SUCCEEDED',b'0'),('QUEUED',b'1')");

            assertEquals(2, mapper.countActiveRows());
        }
    }

    private static Dp08LegacyTaskReconciliationMapper mapper(Connection connection)
            throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(new SingleConnectionDataSource(connection, true));
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(Dp08LegacyTaskReconciliationMapper.class);
        return new SqlSessionTemplate(factory)
                .getMapper(Dp08LegacyTaskReconciliationMapper.class);
    }

    private static void createTemporaryTables(Connection connection) throws Exception {
        execute(connection, "CREATE TEMPORARY TABLE operational_task ("
                + "task_type VARCHAR(64) NOT NULL,status VARCHAR(32) NOT NULL,"
                + "is_deleted BIT(1) NOT NULL)");
        execute(connection, "CREATE TEMPORARY TABLE operations_competitor_search_run ("
                + "status VARCHAR(32) NOT NULL,is_deleted BIT(1) NOT NULL)");
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
