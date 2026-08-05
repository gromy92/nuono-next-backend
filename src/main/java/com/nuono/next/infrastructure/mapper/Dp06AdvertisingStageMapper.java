package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.advertising.AdvertisingRawStageRow;
import com.nuono.next.datapull.advertising.AdvertisingStageManifestRow;
import com.nuono.next.datapull.advertising.AdvertisingTaskFenceRow;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import java.util.List;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

/** Read-only DP-06 adapter over the generic acquisition stage and live task fence. */
public interface Dp06AdvertisingStageMapper {
    @Select({
            "SELECT id AS taskId, operation_code AS operationCode, owner_user_id AS ownerUserId,",
            " project_code AS projectCode, store_code AS storeCode, site_code AS siteCode,",
            " business_window_key AS businessWindowKey, schedule_slot AS scheduleSlot,",
            " fence_epoch AS fenceEpoch, state, lease_owner AS leaseOwner,",
            " (lease_until IS NOT NULL AND lease_until > UTC_TIMESTAMP(3)) AS leaseValid",
            "FROM dp_pull_task WHERE id=#{taskId} FOR UPDATE"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    AdvertisingTaskFenceRow selectTaskForUpdate(@Param("taskId") long taskId);

    @Select({
            "SELECT s.task_id AS taskId, s.active_fence_epoch AS activeFenceEpoch,",
            " s.declared_total_pages AS declaredTotalPages, s.known_last_page AS knownLastPage,",
            " s.poison_code AS poisonCode, s.authority_kind AS authorityKind,",
            " s.authority_token_sha256 AS authorityTokenSha256,",
            " s.snapshot_as_of_utc AS snapshotAsOfUtc,",
            " s.declared_collection_count AS declaredCampaignCount,",
            " COUNT(p.page_no) AS pageCount, MIN(p.page_no) AS firstPage,",
            " MAX(p.page_no) AS lastPage,",
            " MAX(CASE WHEN p.page_no=1 THEN p.item_count END) AS dashboardItemCount,",
            " MAX(CASE WHEN p.page_no=1 THEN p.source_item_count END)",
            "  AS dashboardSourceItemCount,",
            " MAX(CASE WHEN p.page_no=1 THEN p.business_skipped_item_count END)",
            "  AS dashboardBusinessSkippedItemCount,",
            " COALESCE(SUM(p.item_count),0) AS stagedItemCount,",
            " COALESCE(SUM(p.source_item_count),0) AS sourceItemCount,",
            " COALESCE(SUM(p.business_skipped_item_count),0) AS businessSkippedItemCount",
            "FROM dp_pull_snapshot_stage s",
            "LEFT JOIN dp_pull_snapshot_stage_page p ON p.task_id=s.task_id",
            "WHERE s.task_id=#{taskId}",
            "GROUP BY s.task_id, s.active_fence_epoch, s.declared_total_pages,",
            " s.known_last_page, s.poison_code, s.authority_kind,",
            " s.authority_token_sha256, s.snapshot_as_of_utc, s.declared_collection_count"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    AdvertisingStageManifestRow selectManifest(@Param("taskId") long taskId);

    @Select({
            "SELECT COUNT(*) FROM dp_pull_snapshot_stage_page p",
            "JOIN dp_pull_snapshot_stage s ON s.task_id=p.task_id",
            "WHERE p.task_id=#{taskId} AND (",
            " p.page_no<1 OR p.item_count<0 OR p.source_item_count<0",
            " OR p.business_skipped_item_count<0",
            " OR (p.page_no>1 AND p.item_count<1)",
            " OR p.source_item_count<>p.item_count+p.business_skipped_item_count",
            " OR p.total_pages IS NULL OR p.total_pages<>s.declared_total_pages",
            " OR p.is_last_page IS NULL",
            " OR p.page_no>s.known_last_page",
            " OR (p.page_no<s.known_last_page AND (p.is_last_page<>b'0'",
            "   OR p.next_page IS NULL OR p.next_page<>p.page_no+1))",
            " OR (p.page_no=s.known_last_page AND (p.is_last_page<>b'1'",
            "   OR p.next_page IS NOT NULL)))"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int countInvalidPageShapes(@Param("taskId") long taskId);

    @Select({
            "SELECT i.task_id AS taskId, i.page_no AS pageNo,",
            " i.item_ordinal AS itemOrdinal, p.item_count AS pageItemCount,",
            " i.stable_identity AS stableIdentity,",
            " i.content_fingerprint AS contentFingerprint, i.payload",
            "FROM dp_pull_snapshot_stage_item i",
            "JOIN dp_pull_snapshot_stage_page p",
            " ON p.task_id=i.task_id AND p.page_no=i.page_no",
            "WHERE i.task_id=#{taskId}",
            " AND (i.page_no>#{cursorPageNo}",
            "   OR (i.page_no=#{cursorPageNo} AND i.item_ordinal>#{cursorItemOrdinal}))",
            "ORDER BY i.page_no ASC, i.item_ordinal ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<AdvertisingRawStageRow> selectRawChunk(
            @Param("taskId") long taskId,
            @Param("cursorPageNo") int cursorPageNo,
            @Param("cursorItemOrdinal") int cursorItemOrdinal,
            @Param("limit") int limit
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_task",
            "WHERE id=#{taskId} AND operation_code='DP06' AND fence_epoch=#{fenceEpoch}",
            " AND state='RUNNING' AND BINARY lease_owner=BINARY #{leaseOwner}",
            " AND lease_until>UTC_TIMESTAMP(3)"
    })
    int countLiveFence(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("leaseOwner") String leaseOwner
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_item WHERE task_id=#{taskId}",
            "ORDER BY page_no ASC,item_ordinal ASC LIMIT #{limit}"
    })
    int deleteRawItemsBatch(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage_page WHERE task_id=#{taskId}",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_item i",
            "  WHERE i.task_id=dp_pull_snapshot_stage_page.task_id",
            "   AND i.page_no=dp_pull_snapshot_stage_page.page_no)",
            "ORDER BY page_no ASC LIMIT #{limit}"
    })
    int deleteRawPagesBatch(
            @Param("taskId") long taskId,
            @Param("limit") int limit
    );

    @Delete({
            "DELETE FROM dp_pull_snapshot_stage WHERE task_id=#{taskId}",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_page p",
            "  WHERE p.task_id=dp_pull_snapshot_stage.task_id)"
    })
    int deleteRawStageIfEmpty(@Param("taskId") long taskId);
}
