package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderTestFixtures;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Real-MySQL proof that one blocked scheduler phase cannot consume the next phase's budget. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataPullRuntimeSchedulerPhaseMySqlTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T04:00:00Z"), ZoneOffset.UTC);
    private HikariDataSource pool;
    private BlockingMapper mapper;

    @BeforeAll
    void connect() throws Exception {
        String url = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"));
        config.setPassword(System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD"));
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000L);
        config.addDataSourceProperty("connectTimeout", "1000");
        config.addDataSourceProperty("socketTimeout", "300000");
        config.addDataSourceProperty("queryTimeoutKillsConnection", "true");
        pool = new HikariDataSource(config);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(new DataPullDeadlineAwareDataSource(pool));
        factoryBean.setPlugins(new DataPullMyBatisDeadlineInterceptor());
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(BlockingMapper.class);
        mapper = new SqlSessionTemplate(factory).getMapper(BlockingMapper.class);
    }

    @AfterAll
    void close() {
        if (pool != null) pool.close();
    }

    @ParameterizedTest(name = "blocked {0} phase")
    @EnumSource(
            value = Phase.class,
            names = {"RECONCILE", "DISPATCH", "MAINTENANCE"}
    )
    void blockedDatabasePhaseReleasesTheSchedulerForTheNextCompleteTick(Phase blocked) {
        Duration phaseBudget = Duration.ofMillis(500);
        AtomicReference<Phase> blocker = new AtomicReference<>(blocked);
        AtomicInteger reconciled = new AtomicInteger();
        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger maintained = new AtomicInteger();
        DataPullRuntimeStopSignal stopSignal = new DataPullRuntimeStopSignal();
        RuntimeExecutor executor = new RuntimeExecutor(
                new DataPullJobRegistry(List.of()),
                new InMemoryDataPullTaskStore(), CLOCK);
        DataPullRuntimeCoordinator coordinator = new DataPullRuntimeCoordinator(
                ignored -> {
                    blockIf(Phase.RECONCILE, blocker);
                    return reconciled.incrementAndGet();
                },
                (now, maximum, lease, leader) -> {
                    blockIf(Phase.DISPATCH, blocker);
                    dispatched.incrementAndGet();
                    return List.of();
                },
                executor,
                List.of(ignored -> {
                    blockIf(Phase.MAINTENANCE, blocker);
                    maintained.incrementAndGet();
                }),
                Runnable::run,
                CLOCK,
                DataPullRuntimeLeaderTestFixtures.alwaysLeader(
                        "dp:mysql-phase-" + blocked.name().toLowerCase(),
                        LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC)),
                Duration.ofMinutes(5), 1, 1, stopSignal, phaseBudget);
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        properties.setSchedulerInitialDelayMs(60_000L);
        properties.setSchedulerFixedDelayMs(60_000L);
        DataPullRuntimeScheduler scheduler = new DataPullRuntimeScheduler(
                coordinator, properties, CLOCK, ThreadPoolTaskScheduler::new,
                stopSignal, Duration.ofSeconds(2));

        scheduler.start();
        try {
            long started = System.nanoTime();
            scheduler.runSafely();
            assertTrue(Duration.ofNanos(System.nanoTime() - started)
                    .compareTo(Duration.ofSeconds(3)) < 0);
            assertFalse(Thread.currentThread().isInterrupted());

            blocker.set(Phase.NONE);
            scheduler.runSafely();
            assertTrue(reconciled.get() >= 1);
            assertTrue(dispatched.get() >= 1);
            assertEquals(1, maintained.get());
            assertEquals(1, mapper.one());
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            assertTrue(pool.getHikariPoolMXBean().getIdleConnections() >= 1);
        } finally {
            scheduler.stop();
        }
    }

    private void blockIf(Phase phase, AtomicReference<Phase> blocker) {
        if (blocker.get() == phase) mapper.sleepThirtySeconds();
    }

    enum Phase { RECONCILE, DISPATCH, MAINTENANCE, NONE }

    interface BlockingMapper {
        @Select("SELECT SLEEP(30)")
        Integer sleepThirtySeconds();

        @Select("SELECT 1")
        int one();
    }
}
