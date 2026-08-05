package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.snapshot.SnapshotFingerprintCountRow;
import com.nuono.next.datapull.snapshot.SnapshotVerifyPageRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Bounded SQL seam for complete-snapshot two-pass observation. */
public interface SnapshotTwoPassMapper {
    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET declared_total_pages=#{declaredTotalPages},known_last_page=#{knownLastPage},",
            " authority_kind=#{authorityKind},authority_token_sha256=#{authorityDigest},",
            " snapshot_as_of_utc=#{snapshotAsOfUtc},declared_collection_count=#{declaredCount},",
            " collection_mode=COALESCE(collection_mode,#{mode}),",
            " verification_state=CASE WHEN #{mode}='TWO_PASS_REQUIRED'",
            "   THEN COALESCE(verification_state,'PASS_ONE') ELSE NULL END,",
            " pass_one_page_count=COALESCE(pass_one_page_count,0),",
            " pass_one_source_item_count=COALESCE(pass_one_source_item_count,0),",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND poison_code IS NULL",
            " AND (collection_mode IS NULL OR collection_mode=#{mode})"
    })
    int updateMetadataAndMode(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("declaredTotalPages") Integer declaredTotalPages,
            @Param("knownLastPage") Integer knownLastPage,
            @Param("authorityKind") String authorityKind,
            @Param("authorityDigest") String authorityDigest,
            @Param("snapshotAsOfUtc") LocalDateTime snapshotAsOfUtc,
            @Param("declaredCount") Long declaredCount,
            @Param("mode") String mode
    );

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_snapshot_fingerprint_count",
            " (task_id,content_fingerprint,pass_one_count,pass_two_count,gmt_create,gmt_updated)",
            " VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            " (#{taskId},#{row.contentFingerprint},#{row.passOneCount},0,",
            "  UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE pass_one_count=pass_one_count+VALUES(pass_one_count),",
            " gmt_updated=UTC_TIMESTAMP(3)",
            "</script>"
    })
    int upsertPassOneCounts(
            @Param("taskId") long taskId,
            @Param("rows") List<SnapshotFingerprintCountRow> rows
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET pass_one_page_count=pass_one_page_count+1,",
            " pass_one_source_item_count=pass_one_source_item_count+#{sourceCount},",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND collection_mode='TWO_PASS_REQUIRED' AND verification_state='PASS_ONE'",
            " AND poison_code IS NULL"
    })
    int recordPassOnePage(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("sourceCount") int sourceCount
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage s",
            "SET s.verification_state='VERIFYING',s.verification_next_page=1,",
            " s.verification_page_count=0,s.verification_source_item_count=0,",
            " s.comparison_after_fingerprint=NULL,s.comparison_digest_sha256=NULL,",
            " s.comparison_key_count=0,s.comparison_source_item_count=0,",
            " s.version_no=s.version_no+1,s.gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE s.task_id=#{taskId} AND s.active_fence_epoch=#{fenceEpoch}",
            " AND s.collection_mode='TWO_PASS_REQUIRED' AND s.verification_state='PASS_ONE'",
            " AND s.authority_kind IS NULL AND s.known_last_page=#{lastPage}",
            " AND s.pass_one_page_count=#{lastPage}",
            " AND s.poison_code IS NULL"
    })
    int beginVerification(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("lastPage") int lastPage
    );

    @Select({
            "SELECT task_id AS taskId,page_no AS pageNo,next_page AS nextPage,",
            " is_last_page AS lastPage,total_pages AS totalPages,",
            " source_item_count AS sourceItemCount,",
            " business_skipped_item_count AS businessSkippedItemCount,",
            " page_digest_sha256 AS pageDigestSha256",
            "FROM dp_pull_snapshot_verify_page",
            "WHERE task_id=#{taskId} AND page_no=#{pageNo}"
    })
    SnapshotVerifyPageRow selectVerifyPage(
            @Param("taskId") long taskId,
            @Param("pageNo") int pageNo
    );

    @Insert({
            "INSERT INTO dp_pull_snapshot_verify_page",
            " (task_id,page_no,next_page,is_last_page,total_pages,source_item_count,",
            "  business_skipped_item_count,page_digest_sha256,gmt_create)",
            "VALUES (#{row.taskId},#{row.pageNo},#{row.nextPage},#{row.lastPage},",
            " #{row.totalPages},#{row.sourceItemCount},#{row.businessSkippedItemCount},",
            " #{row.pageDigestSha256},UTC_TIMESTAMP(3))"
    })
    int insertVerifyPage(@Param("row") SnapshotVerifyPageRow row);

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_snapshot_fingerprint_count",
            " (task_id,content_fingerprint,pass_one_count,pass_two_count,gmt_create,gmt_updated)",
            " VALUES",
            "<foreach collection='rows' item='row' separator=','>",
            " (#{taskId},#{row.contentFingerprint},0,#{row.passOneCount},",
            "  UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE pass_two_count=pass_two_count+VALUES(pass_two_count),",
            " gmt_updated=UTC_TIMESTAMP(3)",
            "</script>"
    })
    int upsertPassTwoCounts(
            @Param("taskId") long taskId,
            @Param("rows") List<SnapshotFingerprintCountRow> rows
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET verification_next_page=#{nextPage},",
            " verification_page_count=verification_page_count+1,",
            " verification_source_item_count=verification_source_item_count+#{sourceCount},",
            " verification_state=IF(#{nextPage} IS NULL,'COMPARING','VERIFYING'),",
            " comparison_digest_sha256=IF(#{nextPage} IS NULL,#{initialDigest},NULL),",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND collection_mode='TWO_PASS_REQUIRED' AND verification_state='VERIFYING'",
            " AND verification_next_page=#{pageNo} AND poison_code IS NULL"
    })
    int advanceVerification(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("pageNo") int pageNo,
            @Param("nextPage") Integer nextPage,
            @Param("sourceCount") int sourceCount,
            @Param("initialDigest") String initialDigest
    );

    @Select({
            "SELECT content_fingerprint AS contentFingerprint,",
            " pass_one_count AS passOneCount,pass_two_count AS passTwoCount",
            "FROM dp_pull_snapshot_fingerprint_count",
            "WHERE task_id=#{taskId}",
            " AND (#{afterFingerprint} IS NULL OR content_fingerprint>#{afterFingerprint})",
            "ORDER BY content_fingerprint ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<SnapshotFingerprintCountRow> selectFingerprintCounts(
            @Param("taskId") long taskId,
            @Param("afterFingerprint") String afterFingerprint,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET comparison_after_fingerprint=#{nextAfter},",
            " comparison_digest_sha256=#{nextDigest},",
            " comparison_key_count=comparison_key_count+#{keyDelta},",
            " comparison_source_item_count=comparison_source_item_count+#{sourceDelta},",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND verification_state='COMPARING' AND poison_code IS NULL",
            " AND comparison_after_fingerprint <=> #{expectedAfter}",
            " AND comparison_digest_sha256=#{expectedDigest}"
    })
    int advanceComparison(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("expectedAfter") String expectedAfter,
            @Param("nextAfter") String nextAfter,
            @Param("expectedDigest") String expectedDigest,
            @Param("nextDigest") String nextDigest,
            @Param("keyDelta") int keyDelta,
            @Param("sourceDelta") long sourceDelta
    );

    @Update({
            "UPDATE dp_pull_snapshot_stage",
            "SET authority_kind='TWO_PASS_OBSERVATION',authority_token_sha256=#{digest},",
            " declared_collection_count=#{sourceCount},verification_state='VERIFIED',",
            " version_no=version_no+1,gmt_updated=UTC_TIMESTAMP(3)",
            "WHERE task_id=#{taskId} AND active_fence_epoch=#{fenceEpoch}",
            " AND verification_state='COMPARING' AND poison_code IS NULL",
            " AND comparison_after_fingerprint <=> #{afterFingerprint}",
            " AND comparison_digest_sha256=#{digest}",
            " AND comparison_source_item_count=#{sourceCount}",
            " AND pass_one_source_item_count=#{sourceCount}",
            " AND verification_source_item_count=#{sourceCount}"
    })
    int finalizeComparison(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("afterFingerprint") String afterFingerprint,
            @Param("digest") String digest,
            @Param("sourceCount") long sourceCount
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_verify_page WHERE task_id=#{taskId}",
            "ORDER BY page_no ASC LIMIT #{limit}"
    })
    @Options(timeout = 10)
    int deleteVerifyPagesBounded(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_fingerprint_count WHERE task_id=#{taskId}",
            "ORDER BY content_fingerprint ASC LIMIT #{limit}"
    })
    @Options(timeout = 10)
    int deleteFingerprintCountsBounded(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );
}
