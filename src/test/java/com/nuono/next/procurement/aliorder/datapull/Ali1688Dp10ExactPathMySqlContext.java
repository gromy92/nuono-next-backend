package com.nuono.next.procurement.aliorder.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.checkpoint.MyBatisDataPullScopeProgressStore;
import com.nuono.next.datapull.orchestration.DataPullDeadlineMySqlTestSupport;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullMyBatisDeadlineInterceptor;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10ApplyStageMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import com.nuono.next.procurement.aliorder.Ali1688Dp10FactPersistenceTestSupport;
import com.nuono.next.procurement.aliorder.Ali1688Dp10FactTransaction;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Real mapper graph and Spring transaction proxies for the DP10 MySQL capacity gate. */
final class Ali1688Dp10ExactPathMySqlContext implements AutoCloseable {
    private final HikariDataSource pool;
    private final AnnotationConfigApplicationContext spring;

    Ali1688Dp10ExactPathMySqlContext(String url, String username, String password)
            throws Exception {
        pool = pool(url, username, password);
        DataSource deadlineSource = DataPullDeadlineMySqlTestSupport.deadlineAware(pool);
        SqlSessionFactory factory = sessionFactory(deadlineSource);
        SqlSessionTemplate session = new SqlSessionTemplate(factory);
        Ali1688Dp10RuntimeMapper runtime = mapper(session, Ali1688Dp10RuntimeMapper.class);
        Ali1688Dp10StageMapper stage = mapper(session, Ali1688Dp10StageMapper.class);
        Ali1688Dp10ApplyStageMapper apply = mapper(session, Ali1688Dp10ApplyStageMapper.class);
        Ali1688HistoricalOrderMapper facts = mapper(
                session, Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper lookup = mapper(
                session, Ali1688Dp10FactLookupMapper.class);
        DataPullScopeProgressMapper progress = mapper(
                session, DataPullScopeProgressMapper.class);

        spring = new AnnotationConfigApplicationContext();
        spring.getEnvironment().setActiveProfiles("local-db");
        spring.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "dp10-exact-path",
                Map.of(DataPullExecutionMode.PROPERTY, DataPullExecutionMode.RUNTIME.name())));
        spring.register(TransactionConfiguration.class);
        spring.registerBean(DataSource.class, () -> deadlineSource);
        spring.registerBean(PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(deadlineSource));
        spring.registerBean(Ali1688Dp10RuntimeMapper.class, () -> runtime);
        spring.registerBean(Ali1688Dp10StageMapper.class, () -> stage);
        spring.registerBean(Ali1688Dp10ApplyStageMapper.class, () -> apply);
        spring.registerBean(Ali1688HistoricalOrderMapper.class, () -> facts);
        spring.registerBean(Ali1688Dp10FactLookupMapper.class, () -> lookup);
        spring.registerBean(DataPullScopeProgressMapper.class, () -> progress);
        spring.registerBean(ObjectMapper.class,
                () -> new ObjectMapper().findAndRegisterModules());
        spring.registerBean(DataPullScopeProgressStore.class,
                () -> new MyBatisDataPullScopeProgressStore(progress));
        spring.registerBean(Ali1688Dp10FactSegmentWriter.class,
                () -> Ali1688Dp10FactPersistenceTestSupport.productionWriter(facts, lookup));
        spring.registerBean(Ali1688Dp10MyBatisPageStageStore.class,
                () -> new Ali1688Dp10MyBatisPageStageStore(
                        stage, runtime, spring.getBean(ObjectMapper.class)));
        spring.registerBean(Ali1688Dp10BoundedStageStore.class,
                () -> new Ali1688Dp10BoundedStageStore(
                        apply, stage, runtime, spring.getBean(ObjectMapper.class)));
        spring.registerBean(Ali1688Dp10FactTransaction.class,
                () -> new Ali1688Dp10FactTransaction(
                        runtime,
                        spring.getBean(Ali1688Dp10FactSegmentWriter.class),
                        spring.getBean(DataPullScopeProgressStore.class),
                        spring.getBean(Ali1688Dp10BoundedStageStore.class)));
        spring.refresh();
    }

    HikariDataSource pool() {
        return pool;
    }

    Ali1688Dp10MyBatisPageStageStore stageStore() {
        return spring.getBean(Ali1688Dp10MyBatisPageStageStore.class);
    }

    Ali1688Dp10FactTransaction factTransaction() {
        return spring.getBean(Ali1688Dp10FactTransaction.class);
    }

    private SqlSessionFactory sessionFactory(DataSource source) throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(source);
        bean.setPlugins(new DataPullMyBatisDeadlineInterceptor());
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        SqlSessionFactory factory = Objects.requireNonNull(bean.getObject());
        for (Class<?> type : new Class<?>[]{
                Ali1688Dp10RuntimeMapper.class,
                Ali1688Dp10StageMapper.class,
                Ali1688Dp10ApplyStageMapper.class,
                Ali1688HistoricalOrderMapper.class,
                Ali1688Dp10FactLookupMapper.class,
                DataPullScopeProgressMapper.class
        }) {
            factory.getConfiguration().addMapper(type);
        }
        return factory;
    }

    private <T> T mapper(SqlSessionTemplate session, Class<T> type) {
        return session.getMapper(type);
    }

    private HikariDataSource pool(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000L);
        config.addDataSourceProperty("connectTimeout", "1000");
        config.addDataSourceProperty("socketTimeout", "300000");
        config.addDataSourceProperty("queryTimeoutKillsConnection", "true");
        return new HikariDataSource(config);
    }

    @Override
    public void close() {
        spring.close();
        pool.close();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
    }
}
