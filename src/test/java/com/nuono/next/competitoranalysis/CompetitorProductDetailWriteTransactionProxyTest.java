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

class CompetitorProductDetailWriteTransactionProxyTest {
    @Test
    void snapshotFailureUsesSpringProxyAndRollsBackTransaction() {
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
            CompetitorWatchProductRow watch = watch();
            CompetitorProductRow competitor = competitor();
            NoonProductDetail detail = detail();

            assertTrue(AopUtils.isAopProxy(writeGuard));
            when(mapper.lockWatchProductForDetailWrite(watch.getId())).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return watch;
            });
            when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                    watch.getId(),
                    competitor.getId()
            )).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return competitor;
            });
            when(mapper.updateCompetitorProductFromDetail(any())).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return 1;
            });
            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                throw new IllegalStateException("snapshot write failed");
            }).when(snapshotService).recordProductDetailSnapshot(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
            );

            assertThrows(
                    IllegalStateException.class,
                    () -> writeGuard.writeIfCurrent(
                            watch,
                            competitor,
                            CompetitorProductDetailTarget.competitor(
                                    competitor.getId(),
                                    competitor.getNoonProductCode(),
                                    competitor.getCanonicalUrl()
                            ),
                            detail,
                            220124L,
                            601L
                    )
            );

            assertEquals(1, transactionManager.getRollbackCount());
            assertEquals(0, transactionManager.getCommitCount());
        }
    }

    private static CompetitorWatchProductRow watch() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductRow competitor() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200010L);
        row.setWatchProductId(180123L);
        row.setNoonProductCode("ZCOMP001");
        row.setCodeType("Z_CODE");
        row.setCanonicalUrl("https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static NoonProductDetail detail() {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode("ZCOMP001");
        detail.setCodeType("Z_CODE");
        detail.setDetailUrl("https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        detail.setTitleEn("Detail title");
        detail.setSnapshotHash("detail-hash-ZCOMP001");
        return detail;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        CompetitorAnalysisMapper competitorAnalysisMapper() {
            return mock(CompetitorAnalysisMapper.class);
        }

        @Bean
        CompetitorProductSnapshotService competitorProductSnapshotService() {
            return mock(CompetitorProductSnapshotService.class);
        }

        @Bean
        CompetitorProductDetailWriteGuard competitorProductDetailWriteGuard(
                CompetitorAnalysisMapper mapper,
                CompetitorProductSnapshotService snapshotService
        ) {
            return new CompetitorProductDetailWriteGuard(mapper, snapshotService);
        }

        @Bean
        CountingTransactionManager transactionManager() {
            return new CountingTransactionManager();
        }
    }

    static final class CountingTransactionManager extends AbstractPlatformTransactionManager {
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // The base class publishes the active transaction to TransactionSynchronizationManager.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }

        int getCommitCount() {
            return commitCount;
        }

        int getRollbackCount() {
            return rollbackCount;
        }
    }
}
