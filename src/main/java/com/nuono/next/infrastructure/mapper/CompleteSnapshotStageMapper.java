package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.snapshot.SnapshotStageAggregateRow;
import com.nuono.next.datapull.snapshot.SnapshotStageItemRow;
import com.nuono.next.datapull.snapshot.SnapshotStagePageRow;
import com.nuono.next.datapull.snapshot.SnapshotStageTaskRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Independent MyBatis Adapter statements for durable complete-snapshot staging. */
public interface CompleteSnapshotStageMapper extends SnapshotStageProofMapper {

    @Select({
            "SELECT id AS taskId, fence_epoch AS fenceEpoch, state,",
            "       (lease_until IS NOT NULL AND lease_until > UTC_TIMESTAMP(3)) AS leaseValid",
            "FROM dp_pull_task",
            "WHERE id = #{taskId}",
            "FOR UPDATE"
    })
    SnapshotStageTaskRow selectTaskForUpdate(@Param("taskId") long taskId);

    @Insert({
            "INSERT IGNORE INTO dp_pull_snapshot_stage (",
            "  task_id, active_fence_epoch, declared_total_pages, known_last_page, poison_code,",
            "  authority_kind, authority_token_sha256, snapshot_as_of_utc,",
            "  declared_collection_count,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{taskId}, #{fenceEpoch}, NULL, NULL, NULL, NULL, NULL, NULL, NULL,",
            "  UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)",
            ")"
    })
    int insertAggregateIfAbsent(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Select({
            "SELECT task_id AS taskId, active_fence_epoch AS activeFenceEpoch,",
            "       declared_total_pages AS declaredTotalPages,",
            "       known_last_page AS knownLastPage, poison_code AS poisonCode,",
            "       authority_kind AS authorityKind,",
            "       authority_token_sha256 AS authorityTokenSha256,",
            "       snapshot_as_of_utc AS snapshotAsOfUtc,",
            "       declared_collection_count AS declaredCollectionCount,",
            "       collection_mode AS collectionMode, verification_state AS verificationState,",
            "       pass_one_page_count AS passOnePageCount,",
            "       pass_one_source_item_count AS passOneSourceItemCount,",
            "       verification_next_page AS verificationNextPage,",
            "       verification_page_count AS verificationPageCount,",
            "       verification_source_item_count AS verificationSourceItemCount,",
            "       comparison_after_fingerprint AS comparisonAfterFingerprint,",
            "       comparison_digest_sha256 AS comparisonDigestSha256,",
            "       comparison_key_count AS comparisonKeyCount,",
            "       comparison_source_item_count AS comparisonSourceItemCount",
            "FROM dp_pull_snapshot_stage",
            "WHERE task_id = #{taskId}",
            "FOR UPDATE"
    })
    SnapshotStageAggregateRow selectAggregateForUpdate(@Param("taskId") long taskId);

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET active_fence_epoch = #{fenceEpoch}, version_no = version_no + 1,",
            "    gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId}",
            "  AND active_fence_epoch < #{fenceEpoch}"
    })
    int adoptFence(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET poison_code = COALESCE(poison_code, #{poisonCode}),",
            "    version_no = version_no + 1, gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId}",
            "  AND active_fence_epoch = #{fenceEpoch}",
            "  AND poison_code IS NULL"
    })
    int poison(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("poisonCode") String poisonCode
    );

    @Select({
            "SELECT task_id AS taskId, page_no AS pageNo, next_page AS nextPage,",
            "       is_last_page AS lastPage, total_pages AS totalPages, item_count AS itemCount,",
            "       source_item_count AS sourceItemCount,",
            "       business_skipped_item_count AS businessSkippedItemCount",
            "FROM dp_pull_snapshot_stage_page",
            "WHERE task_id = #{taskId} AND page_no = #{pageNo}"
    })
    SnapshotStagePageRow selectPage(
            @Param("taskId") long taskId,
            @Param("pageNo") int pageNo
    );

    @Select({
            "SELECT MAX(page_no)",
            "FROM dp_pull_snapshot_stage_page",
            "WHERE task_id = #{taskId}"
    })
    Integer selectMaxPageNo(@Param("taskId") long taskId);

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET declared_total_pages = #{declaredTotalPages},",
            "    known_last_page = #{knownLastPage},",
            "    authority_kind = #{authorityKind},",
            "    authority_token_sha256 = #{authorityTokenSha256},",
            "    snapshot_as_of_utc = #{snapshotAsOfUtc},",
            "    declared_collection_count = #{declaredCollectionCount},",
            "    version_no = version_no + 1,",
            "    gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE task_id = #{taskId}",
            "  AND active_fence_epoch = #{fenceEpoch}",
            "  AND poison_code IS NULL"
    })
    int updateMetadata(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("declaredTotalPages") Integer declaredTotalPages,
            @Param("knownLastPage") Integer knownLastPage,
            @Param("authorityKind") String authorityKind,
            @Param("authorityTokenSha256") String authorityTokenSha256,
            @Param("snapshotAsOfUtc") LocalDateTime snapshotAsOfUtc,
            @Param("declaredCollectionCount") Long declaredCollectionCount
    );

    @Insert({
            "INSERT INTO dp_pull_snapshot_stage_page (",
            "  task_id, page_no, next_page, is_last_page, total_pages, item_count,",
            "  source_item_count, business_skipped_item_count,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.taskId}, #{row.pageNo}, #{row.nextPage}, #{row.lastPage},",
            "  #{row.totalPages}, #{row.itemCount}, #{row.sourceItemCount},",
            "  #{row.businessSkippedItemCount}, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)",
            ")"
    })
    int insertPage(@Param("row") SnapshotStagePageRow row);

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_snapshot_stage_item (",
            "  task_id, page_no, item_ordinal, stable_identity, content_fingerprint, payload,",
            "  validated_identity_candidate, absence_reconciliation_safe,",
            "  gmt_create",
            ") VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            " (#{row.taskId}, #{row.pageNo}, #{row.itemOrdinal}, #{row.stableIdentity},",
            "  #{row.contentFingerprint}, #{row.payload}, #{row.validatedIdentityCandidate},",
            "  #{row.absenceReconciliationSafe}, UTC_TIMESTAMP(3))",
            "</foreach>",
            "</script>"
    })
    int insertItems(@Param("rows") List<SnapshotStageItemRow> rows);

    @Update({
            "UPDATE dp_pull_snapshot_stage_page",
            "SET total_pages=#{totalPageCount},",
            " next_page=CASE WHEN page_no=#{sourcePageCount} THEN #{sourcePageCount}+1",
            "   ELSE next_page END,",
            " is_last_page=CASE WHEN page_no=#{sourcePageCount} THEN b'0'",
            "   ELSE is_last_page END,",
            " gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND total_pages=#{sourcePageCount}",
            " AND page_no BETWEEN 1 AND #{sourcePageCount}"
    })
    int extendVerifiedSourcePages(
            @Param("taskId") long taskId,
            @Param("sourcePageCount") int sourcePageCount,
            @Param("totalPageCount") int totalPageCount
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET declared_total_pages=#{totalPageCount},known_last_page=#{totalPageCount},",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND declared_total_pages=#{sourcePageCount}",
            " AND known_last_page=#{sourcePageCount}",
            " AND collection_mode='TWO_PASS_REQUIRED'",
            " AND verification_state='VERIFIED'",
            " AND authority_kind='TWO_PASS_OBSERVATION'",
            " AND authority_token_sha256 REGEXP '^[0-9a-f]{64}$'",
            " AND declared_collection_count>=0 AND poison_code IS NULL"
    })
    int promoteVerifiedTwoPass(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("sourcePageCount") int sourcePageCount,
            @Param("totalPageCount") int totalPageCount
    );

    @Select({
            "SELECT task_id AS taskId, page_no AS pageNo, item_ordinal AS itemOrdinal,",
            "       stable_identity AS stableIdentity, content_fingerprint AS contentFingerprint,",
            "       payload, validated_identity_candidate AS validatedIdentityCandidate,",
            "       absence_reconciliation_safe AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_stage_item",
            "WHERE task_id = #{taskId} AND page_no = #{pageNo}",
            "ORDER BY item_ordinal ASC"
    })
    List<SnapshotStageItemRow> selectPageItems(
            @Param("taskId") long taskId,
            @Param("pageNo") int pageNo
    );

    @Select({
            "SELECT task_id AS taskId, page_no AS pageNo, next_page AS nextPage,",
            "       is_last_page AS lastPage, total_pages AS totalPages, item_count AS itemCount,",
            "       source_item_count AS sourceItemCount,",
            "       business_skipped_item_count AS businessSkippedItemCount",
            "FROM dp_pull_snapshot_stage_page",
            "WHERE task_id = #{taskId}",
            "ORDER BY page_no ASC"
    })
    List<SnapshotStagePageRow> selectPages(@Param("taskId") long taskId);

    @Select({
            "SELECT task_id AS taskId, page_no AS pageNo, item_ordinal AS itemOrdinal,",
            "       stable_identity AS stableIdentity, content_fingerprint AS contentFingerprint,",
            "       payload, validated_identity_candidate AS validatedIdentityCandidate,",
            "       absence_reconciliation_safe AS absenceReconciliationSafe",
            "FROM dp_pull_snapshot_stage_item",
            "WHERE task_id = #{taskId}",
            "ORDER BY page_no ASC, item_ordinal ASC"
    })
    List<SnapshotStageItemRow> selectItems(@Param("taskId") long taskId);

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_item",
            "WHERE task_id = #{taskId}",
            "ORDER BY page_no ASC, item_ordinal ASC",
            "LIMIT #{batchSize}"
    })
    @Options(timeout = 10)
    int deleteStageItemsBounded(
            @Param("taskId") long taskId,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_page",
            "WHERE task_id = #{taskId}",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item item",
            "    WHERE item.task_id = dp_pull_snapshot_stage_page.task_id",
            "      AND item.page_no = dp_pull_snapshot_stage_page.page_no)",
            "ORDER BY page_no ASC",
            "LIMIT #{batchSize}"
    })
    @Options(timeout = 10)
    int deleteEmptyStagePagesBounded(
            @Param("taskId") long taskId,
            @Param("batchSize") int batchSize
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage",
            "WHERE task_id = #{taskId}",
            "  AND active_fence_epoch <= #{fenceEpoch}",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_apply_progress progress",
            "    WHERE progress.task_id=#{taskId}",
            "      AND (progress.prepared_item_count>0 OR progress.target_ref_id IS NOT NULL",
            "        OR progress.target_ref_type IS NOT NULL OR progress.state='SEALED'))"
    })
    @Options(timeout = 10)
    int deleteAggregate(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch
    );
}
