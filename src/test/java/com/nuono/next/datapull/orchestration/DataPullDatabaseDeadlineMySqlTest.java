package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataPullDatabaseDeadlineMySqlTest {
    private HikariDataSource dataSource;
    private BlockingService service;

    @BeforeAll
    void connect() throws Exception {
        String url = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(url);
        hikari.setUsername(System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"));
        hikari.setPassword(System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD"));
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(1_000L);
        hikari.addDataSourceProperty("connectTimeout", "1000");
        hikari.addDataSourceProperty("socketTimeout", "300000");
        hikari.addDataSourceProperty("queryTimeoutKillsConnection", "true");
        dataSource = new HikariDataSource(hikari);
        service = transactionalService(new DataPullDeadlineAwareDataSource(dataSource));
        execute("DROP TABLE IF EXISTS dp_deadline_fixture");
        execute("CREATE TABLE dp_deadline_fixture ("
                + "id INT PRIMARY KEY, value_no INT NOT NULL) ENGINE=InnoDB");
        execute("INSERT INTO dp_deadline_fixture (id, value_no) VALUES (1, 0), (2, 0)");
    }

    @BeforeEach
    void reset() throws Exception {
        execute("UPDATE dp_deadline_fixture SET value_no = 0");
    }

    @AfterAll
    void close() throws Exception {
        if (dataSource == null) return;
        execute("DROP TABLE IF EXISTS dp_deadline_fixture");
        dataSource.close();
    }

    @Test
    void serverSleepIsAbortedAndPoolRecovers() {
        long started = System.nanoTime();
        try (DataPullAdvanceDeadline ignored =
                     DataPullAdvanceDeadline.open(Duration.ofMillis(500))) {
            assertThrows(RuntimeException.class, service::sleepThirtySeconds);
        }

        assertTrue(elapsed(started).compareTo(Duration.ofSeconds(3)) < 0);
        assertPoolRecovered();
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void rowLockTimeoutRollsBackEarlierWriteAndPoolRecovers() throws Exception {
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            try (Statement statement = locker.createStatement()) {
                statement.executeQuery(
                        "SELECT value_no FROM dp_deadline_fixture WHERE id = 1 FOR UPDATE"
                );
            }
            long started = System.nanoTime();
            try {
                try (DataPullAdvanceDeadline ignored =
                             DataPullAdvanceDeadline.open(Duration.ofMillis(500))) {
                    assertThrows(RuntimeException.class, service::writeThenBlockOnLockedRow);
                }
                assertTrue(elapsed(started).compareTo(Duration.ofSeconds(3)) < 0);
            } finally {
                locker.rollback();
            }
        }

        assertPoolRecovered();
        assertEquals(0, service.value(2));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void shutdownCancelsTheActiveTransactionAndRollsBackItsEarlierWrite() throws Exception {
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        CountDownLatch writeCompleted = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> advance = worker.submit(() -> {
                try (DataPullAdvanceDeadline ignored = DataPullAdvanceDeadline.open(
                        Duration.ofSeconds(75), stopSignal
                )) {
                    assertThrows(
                            RuntimeException.class,
                            () -> service.writeThenSleep(writeCompleted)
                    );
                }
            });
            assertTrue(writeCompleted.await(1, TimeUnit.SECONDS));

            stopSignal.markStopping();
            stopSignal.awaitQuiescence(Duration.ofSeconds(5));
            advance.get(3, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(1, TimeUnit.SECONDS));
        }

        assertEquals(0, service.value(2));
        assertEquals(0, stopSignal.activeDeadlineCount());
    }

    @Test
    void representativeDeadlineTransactionsStayWithinTheChurnBudget() {
        Set<Long> physicalConnections = new HashSet<>();
        long started = System.nanoTime();
        for (int advance = 0; advance < 20; advance++) {
            try (DataPullAdvanceDeadline ignored =
                         DataPullAdvanceDeadline.open(Duration.ofSeconds(10))) {
                physicalConnections.add(service.connectionId());
            }
        }

        assertEquals(20, physicalConnections.size());
        assertTrue(elapsed(started).compareTo(Duration.ofSeconds(75)) < 0);
        assertTrue(dataSource.getHikariPoolMXBean().getTotalConnections() <= 1);
        assertPoolRecovered();
    }

    private BlockingService transactionalService(DataSource source) throws Exception {
        DataPullMyBatisDeadlineInterceptor deadline =
                new DataPullMyBatisDeadlineInterceptor();
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setPlugins(deadline);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(FixtureMapper.class);
        SqlSessionTemplate session = new SqlSessionTemplate(factory);
        FixtureMapper mapper = session.getMapper(FixtureMapper.class);
        PlatformTransactionManager transactions = new DataSourceTransactionManager(source);
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
        attribute.setTimeout(DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
        MatchAlwaysTransactionAttributeSource attributes =
                new MatchAlwaysTransactionAttributeSource();
        attributes.setTransactionAttribute(attribute);
        TransactionInterceptor advice = new TransactionInterceptor(transactions, attributes);
        ProxyFactory proxy = new ProxyFactory(new BlockingService(mapper));
        proxy.addAdvice(advice);
        return (BlockingService) proxy.getProxy();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    private void assertPoolRecovered() {
        long started = System.nanoTime();
        assertEquals(0, service.value(1));
        assertTrue(elapsed(started).compareTo(Duration.ofSeconds(1)) < 0);
        assertEquals(0, dataSource.getHikariPoolMXBean().getActiveConnections());
        assertTrue(dataSource.getHikariPoolMXBean().getIdleConnections() >= 1);
    }

    interface FixtureMapper {
        @Select("SELECT SLEEP(30)")
        Integer sleepThirtySeconds();

        @Update("UPDATE dp_deadline_fixture SET value_no = value_no + 1 WHERE id = #{id}")
        int increment(@Param("id") int id);

        @Select("SELECT value_no FROM dp_deadline_fixture WHERE id = #{id}")
        int value(@Param("id") int id);

        @Select("SELECT CONNECTION_ID()")
        long connectionId();

    }

    static class BlockingService {
        private final FixtureMapper mapper;

        BlockingService(FixtureMapper mapper) {
            this.mapper = mapper;
        }

        public void sleepThirtySeconds() {
            mapper.sleepThirtySeconds();
        }

        public void writeThenBlockOnLockedRow() {
            mapper.increment(2);
            mapper.increment(1);
        }

        public int value(int id) {
            return mapper.value(id);
        }

        public long connectionId() {
            return mapper.connectionId();
        }

        public void writeThenSleep(CountDownLatch writeCompleted) {
            mapper.increment(2);
            writeCompleted.countDown();
            mapper.sleepThirtySeconds();
        }

    }
}
