package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.OperationalTaskMapper;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
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
}
