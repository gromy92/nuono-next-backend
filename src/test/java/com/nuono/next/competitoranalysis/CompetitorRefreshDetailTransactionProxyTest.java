package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CompetitorRefreshDetailTransactionProxyTest {
    @Test
    void snapshotFailureRollsBackLeaseTargetAndProductWritesTogether() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            CompetitorProductDetailWriteGuard writeGuard =
                    context.getBean(CompetitorProductDetailWriteGuard.class);
            CompetitorAnalysisMapper mapper =
                    context.getBean(CompetitorAnalysisMapper.class);
            CompetitorProductSnapshotService snapshotService =
                    context.getBean(CompetitorProductSnapshotService.class);
            CountingTransactionManager transactionManager =
                    context.getBean(CountingTransactionManager.class);
            CompetitorWatchProductRow watch = watchProduct();
            CompetitorProductRow product = competitorProduct();

            assertTrue(AopUtils.isAopProxy(writeGuard));
            when(mapper.lockRunningRefreshTask(150001L)).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return 150001L;
            });
            when(mapper.heartbeatRunningRefreshTask(
                    org.mockito.ArgumentMatchers.eq(150001L),
                    any(LocalDateTime.class)
            )).thenReturn(1);
            when(mapper.lockRunningRefreshRun(
                    150001L, 220001L, 180001L
            )).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return 220001L;
            });
            when(mapper.lockWatchProductForDetailWrite(180001L))
                    .thenReturn(watch);
            when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                    180001L, 200001L
            )).thenReturn(product);
            when(mapper.updateCompetitorProductFromDetail(any()))
                    .thenReturn(1);
            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                throw new IllegalStateException("snapshot write failed");
            }).when(snapshotService).recordProductDetailSnapshot(
                    any(), any(), any(), any(), any()
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> writeGuard.write(
                            150001L,
                            220001L,
                            watch,
                            product,
                            productUpdate(),
                            detail(),
                            501L
                    )
            );

            assertEquals(1, transactionManager.rollbackCount);
            assertEquals(0, transactionManager.commitCount);
        }
    }

    @Test
    void unexpectedKeywordWriteFailureRollsBackTheWholeKeywordTransaction() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            CompetitorKeywordRefreshTransactionRunner transactionRunner =
                    context.getBean(CompetitorKeywordRefreshTransactionRunner.class);
            CompetitorAnalysisMapper mapper =
                    context.getBean(CompetitorAnalysisMapper.class);
            CountingTransactionManager transactionManager =
                    context.getBean(CountingTransactionManager.class);
            when(mapper.nextKeywordRunId()).thenReturn(230001L);
            when(mapper.insertSearchResult(any())).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return 1;
            });

            assertTrue(AopUtils.isAopProxy(transactionRunner));
            assertThrows(
                    IllegalStateException.class,
                    () -> transactionRunner.runKeyword(
                            150001L,
                            220001L,
                            watchProduct(),
                            keyword(),
                            501L
                    )
            );

            assertEquals(1, transactionManager.rollbackCount);
            assertEquals(0, transactionManager.commitCount);
        }
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductRow competitorProduct() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200001L);
        row.setWatchProductId(180001L);
        row.setNoonProductCode("ZCOMP001");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180001L);
        row.setKeyword("laundry basket");
        return row;
    }

    private static CompetitorProductInsertCommand productUpdate() {
        CompetitorProductInsertCommand command = new CompetitorProductInsertCommand();
        command.setId(200001L);
        command.setWatchProductId(180001L);
        command.setNoonProductCode("ZCOMP001");
        return command;
    }

    private static NoonProductDetail detail() {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode("ZCOMP001");
        return detail;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        CompetitorAnalysisMapper mapper() {
            return mock(CompetitorAnalysisMapper.class);
        }

        @Bean
        CompetitorProductSnapshotService snapshotService() {
            return mock(CompetitorProductSnapshotService.class);
        }

        @Bean
        CompetitorRefreshLeaseGuard leaseGuard(CompetitorAnalysisMapper mapper) {
            return new CompetitorRefreshLeaseGuard(mapper);
        }

        @Bean
        CompetitorProductDetailWriteGuard writeGuard(
                CompetitorAnalysisMapper mapper,
                CompetitorProductSnapshotService snapshotService,
                CompetitorRefreshLeaseGuard leaseGuard
        ) {
            return new CompetitorProductDetailWriteGuard(
                    mapper, snapshotService, leaseGuard
            );
        }

        @Bean
        CompetitorKeywordRefreshRunner failingKeywordRunner(
                CompetitorAnalysisMapper mapper
        ) {
            return ignored -> {
                mapper.insertSearchResult(new CompetitorSearchResultInsertCommand());
                throw new IllegalStateException("rank fact insert failed");
            };
        }

        @Bean
        CompetitorKeywordRefreshTransactionRunner keywordTransactionRunner(
                CompetitorAnalysisMapper mapper,
                CompetitorKeywordRefreshRunner failingKeywordRunner,
                CompetitorRefreshLeaseGuard leaseGuard
        ) {
            return new CompetitorKeywordRefreshTransactionRunner(
                    mapper, failingKeywordRunner, leaseGuard
            );
        }

        @Bean
        CountingTransactionManager transactionManager() {
            return new CountingTransactionManager();
        }
    }

    static final class CountingTransactionManager
            extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
