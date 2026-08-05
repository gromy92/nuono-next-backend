package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.snapshot.SnapshotApplyItem;
import com.nuono.next.datapull.snapshot.SnapshotStageItemRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Bounded DP-04 effective-generation materialization SQL. */
public interface SnapshotEffectiveItemMapper {
    @Insert({
            "INSERT INTO dp_pull_snapshot_effective_item (",
            "  task_id, stable_identity, source_page_no, source_item_ordinal,",
            "  content_fingerprint, payload, gmt_create",
            ") VALUES (",
            "  #{taskId}, #{item.stableIdentity}, #{item.pageNo}, #{item.itemOrdinal},",
            "  #{item.contentFingerprint}, #{item.valuePayload}, NOW()",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertEffectiveItem(
            @Param("taskId") long taskId,
            @Param("item") EffectiveItemInsert item
    );

    @Select({
            "SELECT #{targetTaskId} AS taskId, old.source_page_no AS pageNo,",
            "  old.source_item_ordinal AS itemOrdinal, old.stable_identity AS stableIdentity,",
            "  old.content_fingerprint AS contentFingerprint, old.payload,",
            "  b'1' AS validatedIdentityCandidate, b'1' AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_effective_item old",
            "WHERE old.task_id=#{sourceTaskId}",
            "  AND old.stable_identity>COALESCE(#{afterStableIdentity},'')",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_effective_item current_item",
            "    WHERE current_item.task_id=#{targetTaskId}",
            "      AND current_item.stable_identity=old.stable_identity)",
            "ORDER BY old.stable_identity ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<SnapshotStageItemRow> selectFullCarryChunk(
            @Param("sourceTaskId") long sourceTaskId,
            @Param("targetTaskId") long targetTaskId,
            @Param("afterStableIdentity") String afterStableIdentity,
            @Param("limit") int limit
    );

    @Select({
            "SELECT #{targetTaskId} AS taskId, old.source_page_no AS pageNo,",
            "  old.source_item_ordinal AS itemOrdinal, old.stable_identity AS stableIdentity,",
            "  old.content_fingerprint AS contentFingerprint, old.payload,",
            "  b'1' AS validatedIdentityCandidate, b'1' AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_effective_item old",
            "WHERE old.task_id=#{sourceTaskId}",
            "  AND old.stable_identity>COALESCE(#{afterStableIdentity},'')",
            "  AND EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item problem",
            "    WHERE problem.task_id=#{targetTaskId}",
            "      AND problem.stable_identity=old.stable_identity",
            "      AND problem.validated_identity_candidate=b'0'",
            "      AND problem.absence_reconciliation_safe=b'1')",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_effective_item current_item",
            "    WHERE current_item.task_id=#{targetTaskId}",
            "      AND current_item.stable_identity=old.stable_identity)",
            "ORDER BY old.stable_identity ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<SnapshotStageItemRow> selectTargetedCarryChunk(
            @Param("sourceTaskId") long sourceTaskId,
            @Param("targetTaskId") long targetTaskId,
            @Param("afterStableIdentity") String afterStableIdentity,
            @Param("limit") int limit
    );

    /** Immutable insert values shared by fresh and carried rows. */
    final class EffectiveItemInsert {
        private final String stableIdentity;
        private final int pageNo;
        private final int itemOrdinal;
        private final String contentFingerprint;
        private final String valuePayload;

        public EffectiveItemInsert(SnapshotApplyItem<?> item, String payload) {
            stableIdentity = item.getStableIdentity();
            pageNo = item.getPageNo();
            itemOrdinal = item.getItemOrdinal();
            contentFingerprint = item.getContentFingerprint();
            valuePayload = payload;
        }

        public EffectiveItemInsert(SnapshotStageItemRow row) {
            stableIdentity = row.getStableIdentity();
            pageNo = row.getPageNo();
            itemOrdinal = row.getItemOrdinal();
            contentFingerprint = row.getContentFingerprint();
            valuePayload = row.getPayload();
        }

        public String getStableIdentity() { return stableIdentity; }
        public int getPageNo() { return pageNo; }
        public int getItemOrdinal() { return itemOrdinal; }
        public String getContentFingerprint() { return contentFingerprint; }
        public String getValuePayload() { return valuePayload; }
    }
}
