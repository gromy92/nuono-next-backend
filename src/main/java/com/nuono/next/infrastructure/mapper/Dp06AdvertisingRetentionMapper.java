package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/** Bounded deletion of sealed DP-06 generations no longer selected by the current head. */
public interface Dp06AdvertisingRetentionMapper {
    String ABANDONED_GENERATION = " g.state='PREPARING'"
            + " AND t.operation_code='DP06'"
            + " AND t.state IN ('FAILED','SUPERSEDED')"
            + " AND t.finished_at<#{cutoffUtc}"
            + " AND t.lease_owner IS NULL AND t.lease_until IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own"
            + " WHERE own.task_id=g.task_id)";

    @Delete({
            "DELETE FROM dp_pull_advertising_query_fact",
            "WHERE (task_id,page_no,item_ordinal) IN (SELECT task_id,page_no,item_ordinal FROM (",
            " SELECT q.task_id,q.page_no,q.item_ordinal",
            " FROM dp_pull_advertising_query_fact q",
            " JOIN dp_pull_advertising_generation g ON g.task_id=q.task_id",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE g.state='SEALED' AND t.operation_code='DP06'",
            "  AND t.state IN ('SUCCEEDED','SUPERSEDED') AND t.finished_at<#{cutoffUtc}",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own",
            "   WHERE own.task_id=g.task_id)",
            "  AND EXISTS (SELECT 1 FROM dp_pull_advertising_current_head h",
            "   WHERE h.owner_user_id=g.owner_user_id",
            "    AND BINARY h.project_code=BINARY g.project_code",
            "    AND BINARY h.store_code=BINARY g.store_code",
            "    AND BINARY h.site_code=BINARY g.site_code",
            "    AND h.report_date=g.report_date AND h.task_id<>g.task_id)",
            " ORDER BY q.task_id,q.page_no,q.item_ordinal LIMIT #{limit}",
            ") bounded_dp06_query)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteQueriesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_query_fact",
            "WHERE (task_id,page_no,item_ordinal) IN (SELECT task_id,page_no,item_ordinal FROM (",
            " SELECT q.task_id,q.page_no,q.item_ordinal",
            " FROM dp_pull_advertising_query_fact q",
            " JOIN dp_pull_advertising_generation g ON g.task_id=q.task_id",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE", ABANDONED_GENERATION,
            " ORDER BY q.task_id,q.page_no,q.item_ordinal LIMIT #{limit}",
            ") bounded_abandoned_dp06_query)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedQueriesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_campaign_fact",
            "WHERE (task_id,page_no,item_ordinal) IN (SELECT task_id,page_no,item_ordinal FROM (",
            " SELECT c.task_id,c.page_no,c.item_ordinal",
            " FROM dp_pull_advertising_campaign_fact c",
            " JOIN dp_pull_advertising_generation g ON g.task_id=c.task_id",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE g.state='SEALED' AND t.operation_code='DP06'",
            "  AND t.state IN ('SUCCEEDED','SUPERSEDED') AND t.finished_at<#{cutoffUtc}",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own",
            "   WHERE own.task_id=g.task_id)",
            "  AND EXISTS (SELECT 1 FROM dp_pull_advertising_current_head h",
            "   WHERE h.owner_user_id=g.owner_user_id",
            "    AND BINARY h.project_code=BINARY g.project_code",
            "    AND BINARY h.store_code=BINARY g.store_code",
            "    AND BINARY h.site_code=BINARY g.site_code",
            "    AND h.report_date=g.report_date AND h.task_id<>g.task_id)",
            " ORDER BY c.task_id,c.page_no,c.item_ordinal LIMIT #{limit}",
            ") bounded_dp06_campaign)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteCampaignsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_campaign_fact",
            "WHERE (task_id,page_no,item_ordinal) IN (SELECT task_id,page_no,item_ordinal FROM (",
            " SELECT c.task_id,c.page_no,c.item_ordinal",
            " FROM dp_pull_advertising_campaign_fact c",
            " JOIN dp_pull_advertising_generation g ON g.task_id=c.task_id",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE", ABANDONED_GENERATION,
            " ORDER BY c.task_id,c.page_no,c.item_ordinal LIMIT #{limit}",
            ") bounded_abandoned_dp06_campaign)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedCampaignsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_generation WHERE task_id IN (SELECT task_id FROM (",
            " SELECT g.task_id FROM dp_pull_advertising_generation g",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE g.state='SEALED' AND t.operation_code='DP06'",
            "  AND t.state IN ('SUCCEEDED','SUPERSEDED') AND t.finished_at<#{cutoffUtc}",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_current_head own",
            "   WHERE own.task_id=g.task_id)",
            "  AND EXISTS (SELECT 1 FROM dp_pull_advertising_current_head h",
            "   WHERE h.owner_user_id=g.owner_user_id",
            "    AND BINARY h.project_code=BINARY g.project_code",
            "    AND BINARY h.store_code=BINARY g.store_code",
            "    AND BINARY h.site_code=BINARY g.site_code",
            "    AND h.report_date=g.report_date AND h.task_id<>g.task_id)",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_query_fact q",
            "   WHERE q.task_id=g.task_id)",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_campaign_fact c",
            "   WHERE c.task_id=g.task_id)",
            " ORDER BY g.task_id LIMIT #{limit}",
            ") bounded_dp06_generation)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteGenerationsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_advertising_generation WHERE task_id IN (SELECT task_id FROM (",
            " SELECT g.task_id FROM dp_pull_advertising_generation g",
            " JOIN dp_pull_task t ON t.id=g.task_id",
            " WHERE", ABANDONED_GENERATION,
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_query_fact q",
            "   WHERE q.task_id=g.task_id)",
            "  AND NOT EXISTS (SELECT 1 FROM dp_pull_advertising_campaign_fact c",
            "   WHERE c.task_id=g.task_id)",
            " ORDER BY g.task_id LIMIT #{limit}",
            ") bounded_abandoned_dp06_generation)"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int deleteAbandonedGenerationsBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );
}
