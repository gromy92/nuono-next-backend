package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Proves a waiting borrower cannot receive a connection while its prior abort is still pending. */
class DataPullDatabaseDeadlineConcurrencyMySqlTest {

    @Test
    void abortedSinglePoolConnectionIsReplacedBeforeTheWaitingBorrowerRuns() throws Exception {
        String url = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        try (HikariDataSource pool = pool(url)) {
            FixtureService service = service(new DataPullDeadlineAwareDataSource(pool));
            long expiredConnectionId = service.connectionId();
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> blocked = workers.submit(() -> {
                    try (DataPullAdvanceDeadline ignored =
                                 DataPullAdvanceDeadline.open(Duration.ofMillis(500))) {
                        assertThrows(RuntimeException.class, service::sleepThirtySeconds);
                    }
                    assertFalse(Thread.currentThread().isInterrupted());
                });
                awaitActive(pool);
                Future<Long> waitingBorrower = workers.submit(service::connectionId);

                blocked.get(3, TimeUnit.SECONDS);
                assertNotEquals(
                        expiredConnectionId,
                        waitingBorrower.get(3, TimeUnit.SECONDS)
                );
                assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
                assertTrue(pool.getHikariPoolMXBean().getIdleConnections() >= 1);
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(1, TimeUnit.SECONDS));
            }
        }
    }

    private HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"));
        config.setPassword(System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000L);
        config.addDataSourceProperty("connectTimeout", "1000");
        config.addDataSourceProperty("socketTimeout", "300000");
        config.addDataSourceProperty("queryTimeoutKillsConnection", "true");
        return new HikariDataSource(config);
    }

    private FixtureService service(DataSource source) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setPlugins(new DataPullMyBatisDeadlineInterceptor());
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(FixtureMapper.class);
        FixtureMapper mapper = new SqlSessionTemplate(factory).getMapper(FixtureMapper.class);
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
        attribute.setTimeout(DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
        MatchAlwaysTransactionAttributeSource attributes =
                new MatchAlwaysTransactionAttributeSource();
        attributes.setTransactionAttribute(attribute);
        TransactionInterceptor advice = new TransactionInterceptor(
                new DataSourceTransactionManager(source),
                attributes
        );
        ProxyFactory proxy = new ProxyFactory(new FixtureService(mapper));
        proxy.addAdvice(advice);
        return (FixtureService) proxy.getProxy();
    }

    private void awaitActive(HikariDataSource pool) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (pool.getHikariPoolMXBean().getActiveConnections() != 1
                && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(1, pool.getHikariPoolMXBean().getActiveConnections());
    }

    interface FixtureMapper {
        @Select("SELECT SLEEP(30)")
        Integer sleepThirtySeconds();

        @Select("SELECT CONNECTION_ID()")
        long connectionId();
    }

    static class FixtureService {
        private final FixtureMapper mapper;

        FixtureService(FixtureMapper mapper) {
            this.mapper = mapper;
        }

        public void sleepThirtySeconds() {
            mapper.sleepThirtySeconds();
        }

        public long connectionId() {
            return mapper.connectionId();
        }
    }
}
