package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class Dp08LegacyTaskReconciliationEvidenceTest {

    @Test
    void opensOnlyWhenNoLegacyDp08TaskOrRunRemainsActive() {
        assertTrue(evidence(0).verified());
        assertFalse(evidence(1).verified());
        assertFalse(evidence(731).verified());
    }

    @Test
    void databaseFailureFailsClosed() {
        assertFalse(new Dp08LegacyTaskReconciliationEvidence(() -> {
            throw new IllegalStateException("database unavailable");
        }).verified());
    }

    @Test
    void registersIndependentRequirementAndStableBlocker() {
        Dp08LegacyTaskReconciliationEvidence evidence = evidence(0);

        assertEquals(
                DataPullRuntimeReleaseRequirement.DP08_LEGACY_TASK_RECONCILIATION,
                evidence.requirement()
        );
        assertEquals(
                "DP08_LEGACY_TASK_RECONCILIATION_UNVERIFIED",
                evidence.requirement().blockerCode()
        );
    }

    @Test
    void mapperCountsAllActiveLegacyTasksAndRunsWithoutReadingPayloads() throws Exception {
        Method method = Dp08LegacyTaskReconciliationMapper.class
                .getMethod("countActiveRows");
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).toUpperCase(Locale.ROOT);

        assertTrue(sql.contains("FROM OPERATIONAL_TASK"));
        assertTrue(sql.contains("FROM OPERATIONS_COMPETITOR_SEARCH_RUN"));
        assertEquals(2, occurrences(sql, "STATUS IN ('QUEUED', 'RUNNING')"));
        assertFalse(sql.contains("PAYLOAD_JSON"));
        assertFalse(sql.contains("JSON_"));
        assertFalse(Pattern.compile(
                "\\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|CREATE|TRUNCATE)\\b"
        ).matcher(sql).find());
    }

    private static Dp08LegacyTaskReconciliationEvidence evidence(int activeRows) {
        return new Dp08LegacyTaskReconciliationEvidence(() -> activeRows);
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
