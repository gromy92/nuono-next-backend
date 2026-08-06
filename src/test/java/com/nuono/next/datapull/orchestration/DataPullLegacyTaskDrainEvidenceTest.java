package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.DataPullLegacyCutoverMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataPullLegacyTaskDrainEvidenceTest {

    @Test
    void exactZeroCohortVerifies() {
        assertTrue(evidence(rows(0L, 0L)).verified());
    }

    @Test
    void anyActiveOrSupersedableLegacyTaskBlocksStartup() {
        List<DataPullLegacyCutoverRow> active = rows(0L, 0L);
        active.set(0, row("NOON_PULL", 1L, 1L));
        assertFalse(evidence(active).verified());

        List<DataPullLegacyCutoverRow> impossible = rows(0L, 0L);
        impossible.set(0, row("NOON_PULL", 0L, 1L));
        assertFalse(evidence(impossible).verified());
    }

    @Test
    void missingDuplicateUnknownAndUnavailableEvidenceFailClosed() {
        List<DataPullLegacyCutoverRow> missing = rows(0L, 0L);
        missing.remove(0);
        assertFalse(evidence(missing).verified());

        List<DataPullLegacyCutoverRow> duplicate = rows(0L, 0L);
        duplicate.set(0, row("LEGACY_AUTH_WAIT", 0L, 0L));
        assertFalse(evidence(duplicate).verified());
        assertFalse(evidence(List.of(row("UNKNOWN", 0L, 0L))).verified());
        assertFalse(evidence(null).verified());
    }

    @Test
    void mapperClassifiesOnlyZeroFactScheduledNoonTasksAsSupersedable() {
        String sql = mapperSql();
        assertContains(sql,
                "status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH')",
                "trigger_mode = 'SCHEDULED_DAILY'",
                "data_domain IN ('PRODUCT', 'SALES', 'ORDER', 'FINANCE_TRANSACTION'",
                "status = 'BLOCKED_AUTH' AND retry_action = 'WAIT_FOR_AUTH'",
                "status = 'QUEUED' AND started_at IS NULL",
                "status = 'RUNNING' AND started_at IS NOT NULL",
                "checkpoint_cursor IS NULL",
                "next_resume_position IS NULL",
                "last_safe_response_summary IS NULL",
                "COALESCE(processed_item_count, 0) = 0",
                "COALESCE(request_count, 0) = 0",
                "COALESCE(report_total_rows, 0) = 0",
                "finished_at IS NULL"
        );
    }

    @Test
    void mapperIncludesEveryRetiredWriterAndLegacyAuthorizationWait() {
        String sql = mapperSql();
        assertContains(sql,
                "status IN ('QUEUED', 'RUNNING', 'BLOCKED_AUTH')",
                "task_type = 'PRODUCT_PUBLIC_DETAIL_SYNC'",
                "FROM procurement_ali1688_order_sync_task",
                "status = 'running'",
                "FROM sales_sync_task",
                "status IN ('queued', 'running', 'waiting_authorization')",
                "status IN ('PENDING', 'VALIDATING')",
                "source_task_id IS NOT NULL",
                "source_domain IS NULL",
                "'NOON_PULL', 'PRODUCT', 'SALES', 'SALES_SYNC', 'ORDER'",
                "'OFFICIAL_WAREHOUSE_ASN', 'OFFICIAL_WAREHOUSE_INVENTORY'",
                "'OFFICIAL_WAREHOUSE_FBN_RECEIVED'"
        );
    }

    private DataPullLegacyTaskDrainEvidence evidence(
            List<DataPullLegacyCutoverRow> rows
    ) {
        DataPullLegacyCutoverMapper mapper = Mockito.mock(
                DataPullLegacyCutoverMapper.class
        );
        when(mapper.selectActiveCohort()).thenReturn(rows);
        return new DataPullLegacyTaskDrainEvidence(mapper);
    }

    private List<DataPullLegacyCutoverRow> rows(long active, long supersedable) {
        List<DataPullLegacyCutoverRow> rows = new ArrayList<>();
        for (DataPullLegacyTaskDrainEvidence.Kind kind
                : DataPullLegacyTaskDrainEvidence.Kind.values()) {
            rows.add(row(kind.name(), active, supersedable));
        }
        return rows;
    }

    private DataPullLegacyCutoverRow row(String kind, Long active, Long supersedable) {
        DataPullLegacyCutoverRow row = new DataPullLegacyCutoverRow();
        row.setRecordKind(kind);
        row.setActiveCount(active);
        row.setSupersedableSnapshotCount(supersedable);
        return row;
    }

    private String mapperSql() {
        Method method = DataPullLegacyCutoverMapper.class.getDeclaredMethods()[0];
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private void assertContains(String actual, String... expected) {
        for (String fragment : expected) assertTrue(actual.contains(fragment), fragment);
    }
}
