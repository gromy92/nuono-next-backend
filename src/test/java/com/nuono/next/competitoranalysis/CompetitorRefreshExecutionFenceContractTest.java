package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorProductDetailWriteMapper;
import com.nuono.next.infrastructure.mapper.CompetitorRefreshExecutionMapper;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CompetitorRefreshExecutionFenceContractTest {
    @Test
    void taskAndRunLeaseUseStableLockOrderAndStrictIdentity() throws Exception {
        String taskSql = selectSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "lockRunningRefreshTask", Long.class
                )
        );
        String heartbeatSql = updateSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "heartbeatRunningRefreshTask",
                        Long.class,
                        java.time.LocalDateTime.class
                )
        );
        String runSql = selectSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "lockRunningRefreshRun",
                        Long.class,
                        Long.class,
                        Long.class
                )
        );

        assertTrue(taskSql.contains("TASK_TYPE = 'OPERATIONS_COMPETITOR_REFRESH'"));
        assertTrue(taskSql.contains("STATUS = 'RUNNING'"));
        assertTrue(taskSql.contains("FOR UPDATE"));
        assertTrue(heartbeatSql.contains("GMT_UPDATED = #{UPDATEDAT}"));
        assertTrue(heartbeatSql.contains("STATUS = 'RUNNING'"));
        assertTrue(runSql.contains("TASK_ID = #{TASKID}"));
        assertTrue(runSql.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
        assertTrue(runSql.contains("STATUS = 'RUNNING'"));
        assertTrue(runSql.contains("FOR UPDATE"));
    }

    @Test
    void terminalWritesAreRunningCasAndLatestRunCannotMoveBackward() throws Exception {
        String completeSql = updateSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "completeRunningRefreshRun",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        String.class,
                        String.class,
                        Long.class
                )
        );
        String failSql = updateSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "failRunningRefreshRun",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class
                )
        );
        String latestSql = updateSql(
                CompetitorRefreshExecutionMapper.class.getMethod(
                        "updateLatestRefreshRunIfNotOlder",
                        Long.class,
                        Long.class,
                        String.class,
                        Long.class
                )
        );

        for (String sql : java.util.List.of(completeSql, failSql)) {
            assertTrue(sql.contains("TASK_ID = #{TASKID}"));
            assertTrue(sql.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
            assertTrue(sql.contains("STATUS = 'RUNNING'"));
        }
        assertTrue(latestSql.contains(
                "LATEST_RUN_ID IS NULL OR LATEST_RUN_ID <= #{RUNID}"
        ));
    }

    @Test
    void detailLocksOnlyCurrentActiveAndConfirmedTargets() throws Exception {
        String watchSql = selectSql(
                CompetitorProductDetailWriteMapper.class.getMethod(
                        "lockWatchProductForDetailWrite", Long.class
                )
        );
        String productSql = selectSql(
                CompetitorProductDetailWriteMapper.class.getMethod(
                        "lockConfirmedCompetitorProductForDetailWrite",
                        Long.class,
                        Long.class
                )
        );
        String productUpdateSql = updateSql(
                CompetitorProductDetailWriteMapper.class.getMethod(
                        "updateCompetitorProductFromDetail",
                        CompetitorProductInsertCommand.class
                )
        );
        String checkpointSql = updateSql(
                CompetitorProductDetailWriteMapper.class.getMethod(
                        "checkpointRunningDetailTask",
                        Long.class,
                        String.class
                )
        );

        assertTrue(watchSql.contains("STATUS = 'ACTIVE'"));
        assertTrue(watchSql.contains("FOR UPDATE"));
        assertTrue(productSql.contains("REVIEW_STATUS = 'CONFIRMED'"));
        assertTrue(productSql.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
        assertTrue(productSql.contains("FOR UPDATE"));
        assertTrue(productUpdateSql.contains("UPPER(NOON_PRODUCT_CODE)"));
        assertTrue(productUpdateSql.contains("REVIEW_STATUS = 'CONFIRMED'"));
        assertTrue(checkpointSql.contains("ID = #{TASKID}"));
        assertTrue(checkpointSql.contains("STATUS = 'RUNNING'"));
    }

    @Test
    void businessSegmentsRequireNewTransactionsAndFinalizationIsTransactional()
            throws Exception {
        Transactional keyword = CompetitorKeywordRefreshTransactionRunner.class
                .getMethod(
                        "runKeyword",
                        Long.class,
                        Long.class,
                        CompetitorWatchProductRow.class,
                        CompetitorKeywordRow.class,
                        Long.class
                )
                .getAnnotation(Transactional.class);
        Transactional detail = CompetitorProductDetailWriteGuard.class
                .getMethod(
                        "write",
                        Long.class,
                        Long.class,
                        CompetitorWatchProductRow.class,
                        CompetitorProductRow.class,
                        CompetitorProductInsertCommand.class,
                        NoonProductDetail.class,
                        Long.class
                )
                .getAnnotation(Transactional.class);
        Transactional detailWithCheckpoint =
                CompetitorProductDetailWriteGuard.class
                        .getMethod(
                                "write",
                                Long.class,
                                Long.class,
                                CompetitorWatchProductRow.class,
                                CompetitorProductRow.class,
                                CompetitorProductInsertCommand.class,
                                NoonProductDetail.class,
                                Long.class,
                                String.class
                        )
                        .getAnnotation(Transactional.class);
        Transactional terminal = CompetitorRefreshExecutionFinalizer.class
                .getMethod(
                        "fail",
                        Long.class,
                        Long.class,
                        Long.class,
                        String.class,
                        String.class,
                        Long.class
                )
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, keyword.propagation());
        assertEquals(Propagation.REQUIRES_NEW, detail.propagation());
        assertEquals(
                Propagation.REQUIRES_NEW,
                detailWithCheckpoint.propagation()
        );
        assertTrue(terminal != null);
    }

    @Test
    void enabledLeaseGuardFailsClosedOutsideTransaction() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorRefreshLeaseGuard guard =
                new CompetitorRefreshLeaseGuard(mapper, Clock.systemUTC(), true);
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThrows(
                IllegalStateException.class,
                () -> guard.acquire(150001L, 220001L, 180001L)
        );
        verifyNoInteractions(mapper);
    }

    private static String selectSql(Method method) {
        return normalize(String.join(" ", method.getAnnotation(Select.class).value()));
    }

    private static String updateSql(Method method) {
        return normalize(String.join(" ", method.getAnnotation(Update.class).value()));
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
