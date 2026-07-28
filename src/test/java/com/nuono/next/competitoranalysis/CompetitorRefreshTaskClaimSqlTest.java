package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.OperationalTaskMapper;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class CompetitorRefreshTaskClaimSqlTest {

    @Test
    void operationalTaskClaimIsAtomicAndOnlyClaimsQueuedRows() throws Exception {
        Method method = OperationalTaskMapper.class.getMethod(
                "claimQueued",
                Long.class,
                String.class,
                LocalDateTime.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("status = 'RUNNING'"));
        assertTrue(sql.contains("status = 'QUEUED'"));
        assertTrue(sql.contains("WHERE id = #{taskId}"));
    }

    @Test
    void searchRunStartsOnlyAfterItsQueuedTaskIsClaimed() throws Exception {
        Method method = CompetitorAnalysisMapper.class.getMethod("markSearchRunRunning", Long.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());

        assertTrue(sql.contains("status = 'RUNNING'"));
        assertTrue(sql.contains("status = 'QUEUED'"));
        assertTrue(sql.contains("started_at = NOW()"));
    }

    @Test
    void checkpointAndStaleFailureAreRunningLeaseCasUpdates() throws Exception {
        Method checkpoint = OperationalTaskMapper.class.getMethod(
                "checkpointRunning",
                Long.class,
                String.class,
                int.class,
                String.class,
                LocalDateTime.class
        );
        Method stale = OperationalTaskMapper.class.getMethod(
                "failStaleRunning",
                Long.class,
                LocalDateTime.class,
                String.class,
                String.class,
                LocalDateTime.class
        );
        String checkpointSql = String.join(" ", checkpoint.getAnnotation(Update.class).value());
        String staleSql = String.join(" ", stale.getAnnotation(Update.class).value());

        assertTrue(checkpointSql.contains("AND status = 'RUNNING'"));
        assertTrue(staleSql.contains("AND status = 'RUNNING'"));
        assertTrue(staleSql.contains("COALESCE(gmt_updated, started_at) <= #{staleBefore}"));
    }

    @Test
    void activeRecoveryUsesTaskIdKeysetInsteadOfOffset() throws Exception {
        Method method = OperationalTaskMapper.class.getMethod(
                "listActiveByTaskTypeAfterId",
                String.class,
                Long.class,
                int.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("id > #{afterTaskId}"));
        assertTrue(sql.contains("ORDER BY id ASC"));
        assertTrue(!sql.toUpperCase().contains("OFFSET"));
    }
}
