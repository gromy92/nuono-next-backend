package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.SnapshotCarryMode;
import com.nuono.next.datapull.snapshot.SnapshotApplyProgressRow;
import com.nuono.next.datapull.snapshot.SnapshotApplyMarkerRow;
import com.nuono.next.datapull.snapshot.SnapshotApplyTaskRow;
import com.nuono.next.datapull.snapshot.SnapshotCurrentHeadRow;
import com.nuono.next.datapull.snapshot.SnapshotStageItemRow;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Fenced idempotency SQL for complete snapshot fact replacement. */
public interface SnapshotFactApplyMapper {

    @Select({
            "SELECT id AS taskId, operation_code AS operationCode, scope_key AS scopeKey,",
            "  business_window_key AS businessWindowKey, fence_epoch AS fenceEpoch,",
            "  state, lease_owner AS leaseOwner, lease_until AS leaseUntil",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "FOR UPDATE"
    })
    SnapshotApplyTaskRow selectTaskForUpdate(@Param("taskId") long taskId);

    @Select({
            "SELECT task_id AS taskId, operation_code AS operationCode, scope_key AS scopeKey,",
            "  business_window_key AS businessWindowKey, applied_fence_epoch AS appliedFenceEpoch,",
            "  authority_kind AS authorityKind, authority_token_sha256 AS authorityTokenSha256,",
            "  snapshot_as_of_utc AS snapshotAsOfUtc, declared_collection_count AS declaredCollectionCount,",
            "  source_item_count AS sourceItemCount, applied_item_count AS appliedItemCount,",
            "  identity_skipped_item_count AS identitySkippedItemCount,",
            "  business_skipped_item_count AS businessSkippedItemCount, last_page AS lastPage,",
            "  effective_item_count AS effectiveItemCount, carry_mode AS carryMode,",
            "  carried_from_task_id AS carriedFromTaskId",
            "FROM dp_pull_snapshot_apply",
            "WHERE task_id = #{taskId}",
            "LIMIT 1"
    })
    SnapshotApplyMarkerRow selectMarker(@Param("taskId") long taskId);

    @Insert({
            "INSERT IGNORE INTO dp_pull_snapshot_apply_progress (",
            "  task_id, active_fence_epoch, cursor_page_no, cursor_item_ordinal,",
            "  prepared_item_count, absence_unsafe_item_count, effective_item_count,",
            "  target_ref_type, target_ref_id, carry_mode, carry_source_task_id,",
            "  carry_source_head_version, carry_cursor_identity,",
            "  state, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{snapshot.taskId}, #{snapshot.fenceEpoch}, 0, -1, 0, 0, 0, NULL, NULL,",
            "  'NONE', NULL, NULL, NULL, 'PREPARING', #{nowUtc}, #{nowUtc}",
            ")"
    })
    int insertProgressIfAbsent(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT task_id AS taskId, active_fence_epoch AS activeFenceEpoch,",
            "  cursor_page_no AS cursorPageNo, cursor_item_ordinal AS cursorItemOrdinal,",
            "  prepared_item_count AS preparedItemCount,",
            "  absence_unsafe_item_count AS absenceUnsafeItemCount,",
            "  effective_item_count AS effectiveItemCount,",
            "  target_ref_type AS targetRefType, target_ref_id AS targetRefId,",
            "  carry_mode AS carryMode, carry_source_task_id AS carrySourceTaskId,",
            "  carry_source_head_version AS carrySourceHeadVersion,",
            "  carry_cursor_identity AS carryCursorIdentity, state",
            "FROM dp_pull_snapshot_apply_progress WHERE task_id=#{taskId} FOR UPDATE"
    })
    SnapshotApplyProgressRow selectProgressForUpdate(@Param("taskId") long taskId);

    @Update({
            "UPDATE dp_pull_snapshot_apply_progress",
            "SET active_fence_epoch=#{fenceEpoch}, gmt_updated=#{nowUtc}",
            "WHERE task_id=#{taskId} AND state IN ('PREPARING','CARRYING')",
            "  AND active_fence_epoch<#{fenceEpoch}"
    })
    int adoptProgressFence(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT i.task_id AS taskId, i.page_no AS pageNo, i.item_ordinal AS itemOrdinal,",
            "  i.stable_identity AS stableIdentity, i.content_fingerprint AS contentFingerprint,",
            "  i.payload, i.validated_identity_candidate AS validatedIdentityCandidate,",
            "  i.absence_reconciliation_safe AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_stage_item i",
            "WHERE i.task_id=#{taskId}",
            "  AND (i.page_no>#{afterPageNo}",
            "    OR (i.page_no=#{afterPageNo} AND i.item_ordinal>#{afterItemOrdinal}))",
            "  AND (",
            "    (i.validated_identity_candidate=b'1' AND NOT EXISTS (",
            "      SELECT 1 FROM dp_pull_snapshot_stage_item earlier",
            "      WHERE earlier.task_id=i.task_id",
            "        AND earlier.stable_identity=i.stable_identity",
            "        AND earlier.validated_identity_candidate=b'1'",
            "        AND (earlier.page_no<i.page_no OR (earlier.page_no=i.page_no",
            "          AND earlier.item_ordinal<i.item_ordinal))",
            "    )) OR (i.validated_identity_candidate=b'0'",
            "      AND NOT EXISTS (",
            "        SELECT 1 FROM dp_pull_snapshot_stage_item valid_item",
            "        WHERE valid_item.task_id=i.task_id",
            "          AND valid_item.stable_identity=i.stable_identity",
            "          AND valid_item.validated_identity_candidate=b'1'",
            "      ) AND NOT EXISTS (",
            "        SELECT 1 FROM dp_pull_snapshot_stage_item earlier",
            "        WHERE earlier.task_id=i.task_id",
            "          AND earlier.stable_identity=i.stable_identity",
            "          AND (earlier.page_no<i.page_no OR (earlier.page_no=i.page_no",
            "            AND earlier.item_ordinal<i.item_ordinal))",
            "      )",
            "    )",
            "  )",
            "ORDER BY i.page_no ASC, i.item_ordinal ASC",
            "LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<SnapshotStageItemRow> selectCanonicalChunk(
            @Param("taskId") long taskId,
            @Param("afterPageNo") int afterPageNo,
            @Param("afterItemOrdinal") int afterItemOrdinal,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE dp_pull_snapshot_apply_progress progress",
            "JOIN dp_pull_task task ON task.id=progress.task_id",
            "SET progress.cursor_page_no=#{lastPageNo},",
            "  progress.cursor_item_ordinal=#{lastItemOrdinal},",
            "  progress.prepared_item_count=progress.prepared_item_count+#{preparedDelta},",
            "  progress.absence_unsafe_item_count=progress.absence_unsafe_item_count+#{absenceUnsafeDelta},",
            "  progress.effective_item_count=progress.effective_item_count+#{effectiveDelta},",
            "  progress.gmt_updated=#{nowUtc}",
            "WHERE progress.task_id=#{snapshot.taskId}",
            "  AND progress.active_fence_epoch=#{snapshot.fenceEpoch}",
            "  AND progress.state='PREPARING' AND progress.cursor_page_no=#{expectedPageNo}",
            "  AND progress.cursor_item_ordinal=#{expectedItemOrdinal}",
            "  AND task.state='RUNNING' AND task.fence_epoch=#{snapshot.fenceEpoch}",
            "  AND BINARY task.lease_owner=BINARY #{snapshot.leaseOwner}",
            "  AND task.lease_until>#{nowUtc}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int advanceProgress(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("expectedPageNo") int expectedPageNo,
            @Param("expectedItemOrdinal") int expectedItemOrdinal,
            @Param("lastPageNo") int lastPageNo,
            @Param("lastItemOrdinal") int lastItemOrdinal,
            @Param("preparedDelta") int preparedDelta,
            @Param("absenceUnsafeDelta") int absenceUnsafeDelta,
            @Param("effectiveDelta") int effectiveDelta,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT operation_code AS operationCode, scope_key AS scopeKey, task_id AS taskId,",
            "  business_window_key AS businessWindowKey, schedule_slot AS scheduleSlot,",
            "  retire_missing AS retireMissing, version_no AS versionNo",
            "FROM dp_pull_snapshot_current_head",
            "WHERE operation_code=#{snapshot.operationCode} AND BINARY scope_key=BINARY #{snapshot.scopeKey}",
            "FOR UPDATE"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    SnapshotCurrentHeadRow selectCurrentHeadForUpdate(
            @Param("snapshot") CompleteSnapshot<?> snapshot
    );

    @Insert({
            "INSERT INTO dp_pull_snapshot_current_head (",
            "  operation_code, scope_key, task_id, business_window_key, schedule_slot,",
            "  retire_missing, version_no, sealed_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{snapshot.operationCode}, #{snapshot.scopeKey}, #{snapshot.taskId},",
            "  #{snapshot.businessWindowKey}, #{snapshot.scheduleSlot}, #{retireMissing},",
            "  0, #{nowUtc}, #{nowUtc}, #{nowUtc}",
            ") AS incoming ON DUPLICATE KEY UPDATE",
            "  business_window_key=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.business_window_key, dp_pull_snapshot_current_head.business_window_key),",
            "  retire_missing=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.retire_missing, dp_pull_snapshot_current_head.retire_missing),",
            "  version_no=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    dp_pull_snapshot_current_head.version_no+1,",
            "    dp_pull_snapshot_current_head.version_no),",
            "  sealed_at=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.sealed_at, dp_pull_snapshot_current_head.sealed_at),",
            "  gmt_updated=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.gmt_updated, dp_pull_snapshot_current_head.gmt_updated),",
            "  task_id=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.task_id, dp_pull_snapshot_current_head.task_id),",
            "  schedule_slot=IF(",
            "    incoming.schedule_slot>dp_pull_snapshot_current_head.schedule_slot",
            "      OR (incoming.schedule_slot=dp_pull_snapshot_current_head.schedule_slot",
            "        AND incoming.task_id>dp_pull_snapshot_current_head.task_id),",
            "    incoming.schedule_slot, dp_pull_snapshot_current_head.schedule_slot)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int upsertCurrentHead(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("retireMissing") boolean retireMissing,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_snapshot_apply_progress",
            "SET state='SEALED', active_fence_epoch=#{fenceEpoch},",
            "  carry_mode=#{carryMode}, gmt_updated=#{nowUtc}",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            "  AND state IN ('PREPARING','CARRYING')",
            "  AND prepared_item_count=#{expectedItemCount}",
            "  AND effective_item_count=#{expectedEffectiveItemCount}"
    })
    int markProgressSealed(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("expectedItemCount") long expectedItemCount,
            @Param("expectedEffectiveItemCount") long expectedEffectiveItemCount,
            @Param("carryMode") SnapshotCarryMode carryMode,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Update({
            "UPDATE dp_pull_snapshot_apply_progress",
            "SET target_ref_type=#{targetRefType}, target_ref_id=#{targetRefId},",
            "  gmt_updated=#{nowUtc}",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            "  AND state='PREPARING' AND target_ref_type IS NULL AND target_ref_id IS NULL"
    })
    int bindTargetRef(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("targetRefType") String targetRefType,
            @Param("targetRefId") long targetRefId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Insert({
            "INSERT INTO dp_pull_snapshot_apply (",
            "  task_id, operation_code, scope_key, business_window_key, applied_fence_epoch,",
            "  authority_kind, authority_token_sha256, snapshot_as_of_utc,",
            "  declared_collection_count, source_item_count, applied_item_count,",
            "  identity_skipped_item_count, business_skipped_item_count, last_page,",
            "  effective_item_count, carry_mode, carried_from_task_id,",
            "  applied_at, gmt_create",
            ")",
            "SELECT task.id, task.operation_code, task.scope_key, task.business_window_key,",
            "  task.fence_epoch, #{snapshot.authority.kind},",
            "  #{snapshot.authority.generationTokenSha256}, #{snapshot.authority.providerAsOfUtc},",
            "  #{snapshot.authority.declaredCollectionCount}, #{snapshot.sourceItemCount},",
            "  #{snapshot.appliedItemCount}, #{snapshot.skippedIdentityCount},",
            "  #{snapshot.businessSkippedItemCount}, #{snapshot.lastPage},",
            "  #{effectiveItemCount}, #{carryMode}, #{carriedFromTaskId}, #{nowUtc}, #{nowUtc}",
            "FROM dp_pull_task task",
            "WHERE task.id = #{snapshot.taskId}",
            "  AND task.operation_code = #{snapshot.operationCode}",
            "  AND BINARY task.scope_key = BINARY #{snapshot.scopeKey}",
            "  AND BINARY task.business_window_key = BINARY #{snapshot.businessWindowKey}",
            "  AND task.state = 'RUNNING'",
            "  AND task.fence_epoch = #{snapshot.fenceEpoch}",
            "  AND BINARY task.lease_owner = BINARY #{snapshot.leaseOwner}",
            "  AND task.lease_until > #{nowUtc}"
    })
    int insertMarkerIfLive(
            @Param("snapshot") CompleteSnapshot<?> snapshot,
            @Param("effectiveItemCount") long effectiveItemCount,
            @Param("carryMode") SnapshotCarryMode carryMode,
            @Param("carriedFromTaskId") Long carriedFromTaskId,
            @Param("nowUtc") LocalDateTime nowUtc
    );
}
