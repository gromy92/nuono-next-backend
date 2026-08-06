package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/** Bounded deletion of superseded heads and quiescent abandoned snapshot stages. */
public interface SnapshotStageRetentionMapper {
    String NO_ACTIVE_CARRY = " AND NOT EXISTS (SELECT 1"
            + " FROM dp_pull_snapshot_apply_progress active_carry"
            + " JOIN dp_pull_task carry_task ON carry_task.id=active_carry.task_id"
            + " WHERE active_carry.carry_source_task_id=t.id"
            + " AND carry_task.state NOT IN ('SUCCEEDED','FAILED','SUPERSEDED'))";
    String ABANDONED_TASK = " t.operation_code IN ('DP04','DP06','DP07A')"
            + " AND t.state IN ('FAILED','SUPERSEDED')"
            + " AND t.finished_at<#{cutoffUtc}"
            + " AND t.lease_owner IS NULL AND t.lease_until IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_current_head active_head"
            + " WHERE active_head.task_id=t.id)"
            + NO_ACTIVE_CARRY;

    @Delete({
            "DELETE FROM dp_pull_snapshot_effective_item",
            "WHERE (task_id, stable_identity) IN (",
            "  SELECT task_id, stable_identity FROM (",
            "    SELECT item.task_id, item.stable_identity",
            "    FROM dp_pull_snapshot_effective_item item",
            "    JOIN dp_pull_task t ON t.id=item.task_id",
            "    WHERE", ABANDONED_TASK,
            "    ORDER BY item.task_id ASC, item.stable_identity ASC LIMIT #{limit}",
            "  ) bounded_abandoned_effective_items",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedEffectiveItemsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_effective_item",
            "WHERE (task_id, stable_identity) IN (",
            "  SELECT task_id, stable_identity FROM (",
            "    SELECT item.task_id, item.stable_identity",
            "    FROM dp_pull_snapshot_effective_item item",
            "    JOIN dp_pull_snapshot_apply a ON a.task_id=item.task_id",
            "    JOIN dp_pull_task t ON t.id=item.task_id",
            "    JOIN dp_pull_snapshot_current_head h",
            "      ON h.operation_code=a.operation_code",
            "      AND BINARY h.scope_key=BINARY a.scope_key",
            "      AND h.task_id<>item.task_id",
            "    WHERE a.operation_code='DP04'",
            "      AND t.state='SUCCEEDED' AND t.finished_at<#{cutoffUtc}",
            NO_ACTIVE_CARRY,
            "    ORDER BY item.task_id ASC, item.stable_identity ASC LIMIT #{limit}",
            "  ) bounded_effective_items",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededEffectiveItemsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_item",
            "WHERE (task_id, page_no, item_ordinal) IN (",
            "  SELECT task_id, page_no, item_ordinal FROM (",
            "    SELECT i.task_id, i.page_no, i.item_ordinal",
            "    FROM dp_pull_snapshot_stage_item i",
            "    JOIN dp_pull_snapshot_apply a ON a.task_id=i.task_id",
            "    JOIN dp_pull_task t ON t.id=i.task_id",
            "    JOIN dp_pull_snapshot_current_head h",
            "      ON h.operation_code=a.operation_code",
            "      AND BINARY h.scope_key=BINARY a.scope_key AND h.task_id<>i.task_id",
            "    WHERE a.operation_code IN ('DP04','DP07A')",
            "      AND t.state='SUCCEEDED' AND t.finished_at<#{cutoffUtc}",
            "    ORDER BY i.task_id ASC, i.page_no ASC, i.item_ordinal ASC LIMIT #{limit}",
            "  ) bounded_snapshot_items",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededItemsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_item",
            "WHERE (task_id, page_no, item_ordinal) IN (",
            "  SELECT task_id, page_no, item_ordinal FROM (",
            "    SELECT i.task_id, i.page_no, i.item_ordinal",
            "    FROM dp_pull_snapshot_stage_item i",
            "    JOIN dp_pull_task t ON t.id=i.task_id",
            "    WHERE", ABANDONED_TASK,
            "    ORDER BY i.task_id ASC, i.page_no ASC, i.item_ordinal ASC LIMIT #{limit}",
            "  ) bounded_abandoned_snapshot_items",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedItemsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_page",
            "WHERE (task_id, page_no) IN (",
            "  SELECT task_id, page_no FROM (",
            "    SELECT p.task_id, p.page_no",
            "    FROM dp_pull_snapshot_stage_page p",
            "    JOIN dp_pull_snapshot_apply a ON a.task_id=p.task_id",
            "    JOIN dp_pull_task t ON t.id=p.task_id",
            "    JOIN dp_pull_snapshot_current_head h",
            "      ON h.operation_code=a.operation_code",
            "      AND BINARY h.scope_key=BINARY a.scope_key AND h.task_id<>p.task_id",
            "    WHERE a.operation_code IN ('DP04','DP07A')",
            "      AND t.state='SUCCEEDED' AND t.finished_at<#{cutoffUtc}",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item i",
            "        WHERE i.task_id=p.task_id AND i.page_no=p.page_no)",
            "    ORDER BY p.task_id ASC, p.page_no ASC LIMIT #{limit}",
            "  ) bounded_snapshot_pages",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededPagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_page",
            "WHERE (task_id, page_no) IN (",
            "  SELECT task_id, page_no FROM (",
            "    SELECT p.task_id, p.page_no",
            "    FROM dp_pull_snapshot_stage_page p",
            "    JOIN dp_pull_task t ON t.id=p.task_id",
            "    WHERE", ABANDONED_TASK,
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item i",
            "        WHERE i.task_id=p.task_id AND i.page_no=p.page_no)",
            "    ORDER BY p.task_id ASC, p.page_no ASC LIMIT #{limit}",
            "  ) bounded_abandoned_snapshot_pages",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedPagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage",
            "WHERE task_id IN (",
            "  SELECT task_id FROM (",
            "    SELECT s.task_id",
            "    FROM dp_pull_snapshot_stage s",
            "    JOIN dp_pull_snapshot_apply a ON a.task_id=s.task_id",
            "    JOIN dp_pull_task t ON t.id=s.task_id",
            "    JOIN dp_pull_snapshot_current_head h",
            "      ON h.operation_code=a.operation_code",
            "      AND BINARY h.scope_key=BINARY a.scope_key AND h.task_id<>s.task_id",
            "    WHERE a.operation_code IN ('DP04','DP07A')",
            "      AND t.state='SUCCEEDED' AND t.finished_at<#{cutoffUtc}",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_page p",
            "        WHERE p.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item i",
            "        WHERE i.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_verify_page v",
            "        WHERE v.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_fingerprint_count f",
            "        WHERE f.task_id=s.task_id)",
            "    ORDER BY s.task_id ASC LIMIT #{limit}",
            "  ) bounded_snapshot_stages",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteSupersededStagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage",
            "WHERE task_id IN (",
            "  SELECT task_id FROM (",
            "    SELECT s.task_id",
            "    FROM dp_pull_snapshot_stage s",
            "    JOIN dp_pull_task t ON t.id=s.task_id",
            "    WHERE", ABANDONED_TASK,
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_page p",
            "        WHERE p.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item i",
            "        WHERE i.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_verify_page v",
            "        WHERE v.task_id=s.task_id)",
            "      AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_fingerprint_count f",
            "        WHERE f.task_id=s.task_id)",
            "    ORDER BY s.task_id ASC LIMIT #{limit}",
            "  ) bounded_abandoned_snapshot_stages",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedStagesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );
}
