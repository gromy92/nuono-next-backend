package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.snapshot.SnapshotStageManifestRow;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Aggregate completeness proof SQL; it never materializes all staged payloads. */
public interface SnapshotStageProofMapper {
    @Select({
            "SELECT s.task_id AS taskId, s.active_fence_epoch AS activeFenceEpoch,",
            "  s.declared_total_pages AS declaredTotalPages, s.known_last_page AS knownLastPage,",
            "  s.poison_code AS poisonCode, s.authority_kind AS authorityKind,",
            "  s.authority_token_sha256 AS authorityTokenSha256,",
            "  s.snapshot_as_of_utc AS snapshotAsOfUtc,",
            "  s.declared_collection_count AS declaredCollectionCount,",
            "  (SELECT COUNT(*) FROM dp_pull_snapshot_stage_page p",
            "    WHERE p.task_id=s.task_id) AS pageCount,",
            "  (SELECT MIN(page_no) FROM dp_pull_snapshot_stage_page p",
            "    WHERE p.task_id=s.task_id) AS firstPage,",
            "  (SELECT MAX(page_no) FROM dp_pull_snapshot_stage_page p",
            "    WHERE p.task_id=s.task_id) AS lastPage,",
            "  (SELECT COUNT(*) FROM dp_pull_snapshot_stage_item i",
            "    WHERE i.task_id=s.task_id) AS stagedItemCount,",
            "  (SELECT COUNT(DISTINCT stable_identity) FROM dp_pull_snapshot_stage_item i",
            "    WHERE i.task_id=s.task_id) AS canonicalItemCount,",
            "  (SELECT COALESCE(SUM(source_item_count),0) FROM dp_pull_snapshot_stage_page p",
            "    WHERE p.task_id=s.task_id) AS sourceItemCount,",
            "  (SELECT COALESCE(SUM(business_skipped_item_count),0)",
            "    FROM dp_pull_snapshot_stage_page p",
            "    WHERE p.task_id=s.task_id) AS businessSkippedItemCount",
            "FROM dp_pull_snapshot_stage s WHERE s.task_id=#{taskId}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    SnapshotStageManifestRow selectManifest(@Param("taskId") long taskId);

    @Select({
            "SELECT COUNT(*) FROM (",
            "  SELECT p.task_id, p.page_no",
            "  FROM dp_pull_snapshot_stage_page p",
            "  JOIN dp_pull_snapshot_stage s ON s.task_id=p.task_id",
            "  LEFT JOIN dp_pull_snapshot_stage_item i",
            "    ON i.task_id=p.task_id AND i.page_no=p.page_no",
            "  WHERE p.task_id=#{taskId}",
            "  GROUP BY p.task_id, p.page_no, p.next_page, p.is_last_page, p.total_pages,",
            "    p.item_count, p.source_item_count, p.business_skipped_item_count,",
            "    s.declared_total_pages, s.known_last_page",
            "  HAVING p.page_no<1 OR p.item_count<0 OR p.source_item_count<0",
            "    OR p.business_skipped_item_count<0",
            "    OR (p.next_page IS NOT NULL AND p.next_page<>p.page_no+1)",
            "    OR (p.total_pages IS NOT NULL AND p.total_pages<p.page_no)",
            "    OR (p.total_pages IS NOT NULL AND (s.declared_total_pages IS NULL",
            "      OR p.total_pages<>s.declared_total_pages))",
            "    OR p.page_no>s.known_last_page",
            "    OR (p.next_page IS NOT NULL AND p.next_page>s.known_last_page)",
            "    OR (p.is_last_page=b'1' AND p.page_no<>s.known_last_page)",
            "    OR (p.is_last_page=b'0' AND p.page_no=s.known_last_page)",
            "    OR (p.is_last_page=b'1' AND (p.next_page IS NOT NULL",
            "      OR (p.total_pages IS NOT NULL AND p.total_pages<>p.page_no)))",
            "    OR (p.is_last_page=b'0' AND p.total_pages=p.page_no)",
            "    OR COUNT(i.item_ordinal)<>p.item_count",
            "    OR p.source_item_count<>p.item_count+p.business_skipped_item_count",
            "    OR (p.item_count=0 AND (MIN(i.item_ordinal) IS NOT NULL",
            "      OR MAX(i.item_ordinal) IS NOT NULL))",
            "    OR (p.item_count>0 AND (MIN(i.item_ordinal)<>0",
            "      OR MAX(i.item_ordinal)<>p.item_count-1))",
            ") invalid_page"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    long countInvalidPageShapes(@Param("taskId") long taskId);

    @Select({
            "SELECT COUNT(*) FROM dp_pull_snapshot_stage_item",
            "WHERE task_id=#{taskId}",
            "  AND (validated_identity_candidate IS NULL OR absence_reconciliation_safe IS NULL",
            "    OR stable_identity IS NULL OR stable_identity=''",
            "    OR stable_identity<>TRIM(stable_identity)",
            "    OR content_fingerprint NOT REGEXP '^[0-9a-f]{64}$'",
            "    OR LOWER(SHA2(payload,256))<>content_fingerprint)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    long countInvalidItems(@Param("taskId") long taskId);
}
