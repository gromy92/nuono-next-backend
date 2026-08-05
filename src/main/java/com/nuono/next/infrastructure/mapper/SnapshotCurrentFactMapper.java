package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.snapshot.SnapshotCurrentHeadRow;
import com.nuono.next.datapull.snapshot.SnapshotStageItemRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/** Read-only mapper for the atomically sealed DP-owned current snapshot. */
public interface SnapshotCurrentFactMapper {
    @Select({
            "SELECT head.operation_code AS operationCode, head.scope_key AS scopeKey,",
            "  head.task_id AS taskId,",
            "  head.business_window_key AS businessWindowKey,",
            "  head.schedule_slot AS scheduleSlot, head.retire_missing AS retireMissing,",
            "  head.version_no AS versionNo",
            "FROM dp_pull_snapshot_current_head head",
            "JOIN dp_pull_snapshot_apply_progress progress",
            "  ON progress.task_id=head.task_id AND progress.state='SEALED'",
            "JOIN dp_pull_snapshot_apply applied",
            "  ON applied.task_id=head.task_id",
            "  AND applied.operation_code=head.operation_code",
            "  AND BINARY applied.scope_key=BINARY head.scope_key",
            "  AND BINARY applied.business_window_key=BINARY head.business_window_key",
            "  AND applied.applied_fence_epoch=progress.active_fence_epoch",
            "  AND applied.effective_item_count=progress.effective_item_count",
            "WHERE head.operation_code=#{operationCode}",
            "  AND BINARY head.scope_key=BINARY #{scopeKey}"
    })
    SnapshotCurrentHeadRow selectHead(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey
    );

    @Select({
            "SELECT item.task_id AS taskId, item.source_page_no AS pageNo,",
            "  item.source_item_ordinal AS itemOrdinal, item.stable_identity AS stableIdentity,",
            "  item.content_fingerprint AS contentFingerprint, item.payload,",
            "  b'1' AS validatedIdentityCandidate, b'1' AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_current_head head",
            "JOIN dp_pull_snapshot_apply_progress progress",
            "  ON progress.task_id=head.task_id AND progress.state='SEALED'",
            "JOIN dp_pull_snapshot_apply applied",
            "  ON applied.task_id=head.task_id",
            "  AND applied.operation_code=head.operation_code",
            "  AND BINARY applied.scope_key=BINARY head.scope_key",
            "  AND BINARY applied.business_window_key=BINARY head.business_window_key",
            "  AND applied.applied_fence_epoch=progress.active_fence_epoch",
            "  AND applied.effective_item_count=progress.effective_item_count",
            "JOIN dp_pull_snapshot_effective_item item ON item.task_id=head.task_id",
            "WHERE head.operation_code=#{operationCode} AND BINARY head.scope_key=BINARY #{scopeKey}",
            "  AND head.task_id=#{headTaskId}",
            "  AND item.stable_identity>COALESCE(#{afterStableIdentity},'')",
            "ORDER BY item.stable_identity ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<SnapshotStageItemRow> selectCurrentChunk(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKey") String scopeKey,
            @Param("headTaskId") long headTaskId,
            @Param("afterStableIdentity") String afterStableIdentity,
            @Param("limit") int limit
    );
}
