package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/** Bounded retention for two-pass page fences and fingerprint counts. */
public interface SnapshotTwoPassRetentionMapper {
    String SUPERSEDED = " JOIN dp_pull_snapshot_apply a ON a.task_id=x.task_id"
            + " JOIN dp_pull_task t ON t.id=x.task_id"
            + " JOIN dp_pull_snapshot_current_head h ON h.operation_code=a.operation_code"
            + " AND BINARY h.scope_key=BINARY a.scope_key AND h.task_id<>x.task_id"
            + " WHERE a.operation_code IN ('DP04','DP07A')"
            + " AND t.state='SUCCEEDED' AND t.finished_at<#{cutoffUtc}"
            + SnapshotStageRetentionMapper.NO_ACTIVE_CARRY;

    @Delete({
            "DELETE FROM dp_pull_snapshot_verify_page",
            "WHERE (task_id,page_no) IN (SELECT task_id,page_no FROM (",
            " SELECT x.task_id,x.page_no FROM dp_pull_snapshot_verify_page x",
            SUPERSEDED,
            " ORDER BY x.task_id,x.page_no LIMIT #{limit}",
            ") bounded)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededVerifyPages(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_verify_page",
            "WHERE (task_id,page_no) IN (SELECT task_id,page_no FROM (",
            " SELECT x.task_id,x.page_no FROM dp_pull_snapshot_verify_page x",
            " JOIN dp_pull_task t ON t.id=x.task_id WHERE",
            SnapshotStageRetentionMapper.ABANDONED_TASK,
            " ORDER BY x.task_id,x.page_no LIMIT #{limit}",
            ") bounded)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedVerifyPages(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_fingerprint_count",
            "WHERE (task_id,content_fingerprint) IN (",
            " SELECT task_id,content_fingerprint FROM (",
            "  SELECT x.task_id,x.content_fingerprint",
            "  FROM dp_pull_snapshot_fingerprint_count x",
            SUPERSEDED,
            "  ORDER BY x.task_id,x.content_fingerprint LIMIT #{limit}",
            " ) bounded)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededFingerprintCounts(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_fingerprint_count",
            "WHERE (task_id,content_fingerprint) IN (",
            " SELECT task_id,content_fingerprint FROM (",
            "  SELECT x.task_id,x.content_fingerprint",
            "  FROM dp_pull_snapshot_fingerprint_count x",
            "  JOIN dp_pull_task t ON t.id=x.task_id WHERE",
            SnapshotStageRetentionMapper.ABANDONED_TASK,
            "  ORDER BY x.task_id,x.content_fingerprint LIMIT #{limit}",
            " ) bounded)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedFingerprintCounts(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );
}
