package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.sales.NoonSalesCsvImportResult;
import com.nuono.next.sales.SalesSyncTaskCommand;
import com.nuono.next.sales.SalesSyncTaskRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Persistence owned only by the durable sales-sync task and its auth-wait transitions. */
public interface SalesSyncTaskMapper {

    @Insert({
            "INSERT INTO sales_sync_task (",
            "  id, owner_user_id, logical_store_id, store_code, site_code, date_from, date_to,",
            "  requested_by, trigger_type, listing_coverage_mode, status, queued_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{command.ownerUserId}, #{command.logicalStoreId}, #{command.storeCode},",
            "  #{command.siteCode}, #{command.dateFrom}, #{command.dateTo}, #{command.requestedBy},",
            "  #{command.triggerType}, #{command.listingCoverageMode}, 'queued', NOW(), NOW(), NOW()",
            ")"
    })
    int insert(@Param("id") Long id, @Param("command") SalesSyncTaskCommand command);

    @Update({
            "UPDATE sales_sync_task SET status = 'running',",
            "started_at = COALESCE(started_at, NOW()), failure_reason = NULL,",
            "auth_recovery_id = NULL, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND status = 'queued'"
    })
    int claimRunning(@Param("taskId") Long taskId);

    @Update({
            "UPDATE sales_sync_task SET status = #{result.taskStatus},",
            "source_batch_id = #{result.sourceBatchId}, total_rows = #{result.totalRows},",
            "success_rows = #{result.successRows}, failure_rows = #{result.failureRows},",
            "latest_fact_date = #{result.reportDateTo}, failure_reason = #{result.taskFailureReason},",
            "auth_recovery_id = NULL, finished_at = NOW(), gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSucceeded(@Param("taskId") Long taskId, @Param("result") NoonSalesCsvImportResult result);

    @Update({
            "UPDATE sales_sync_task SET status = 'failed', failure_reason = #{failureReason},",
            "auth_recovery_id = NULL, finished_at = NOW(), gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markFailed(@Param("taskId") Long taskId, @Param("failureReason") String failureReason);

    @Update({
            "UPDATE sales_sync_task SET status = 'waiting_authorization',",
            "auth_recovery_id = #{recoveryId},",
            "failure_reason = 'Noon Project 授权恢复中，恢复后将自动继续原销量同步任务。',",
            "finished_at = NULL, gmt_updated = NOW()",
            "WHERE id = #{taskId} AND status = 'running'"
    })
    int markWaitingAuthorization(@Param("taskId") Long taskId, @Param("recoveryId") Long recoveryId);

    @Update({
            "UPDATE sales_sync_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.status = 'queued', task.auth_recovery_id = NULL, task.failure_reason = NULL,",
            "task.queued_at = #{now}, task.started_at = NULL, task.finished_at = NULL, task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId} AND item.recovery_id = #{recoveryId}",
            "AND item.source_domain = 'SALES_SYNC' AND item.resume_policy = 'AUTO_RESUME'",
            "AND item.status = 'PENDING' AND task.status = 'waiting_authorization'",
            "AND task.auth_recovery_id = item.recovery_id AND recovery.status = #{expectedRecoveryStatus}",
            "AND recovery.version_no = #{expectedRecoveryVersion}",
            "AND recovery.lease_token = #{expectedLeaseToken} AND recovery.lease_until > #{now}",
            "AND recovery.active_identity_slot IS NOT NULL"
    })
    int resumeAfterAuthorization(
            @Param("itemId") Long itemId, @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken, @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE sales_sync_task task",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = task.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET task.status = 'failed', task.auth_recovery_id = NULL, task.failure_reason = #{diagnostic},",
            "task.finished_at = #{now}, task.gmt_updated = #{now}",
            "WHERE item.id = #{itemId} AND item.recovery_id = #{recoveryId}",
            "AND item.source_domain = 'SALES_SYNC' AND item.status = 'PENDING'",
            "AND task.status = 'waiting_authorization' AND task.auth_recovery_id = item.recovery_id",
            "AND recovery.status = #{expectedRecoveryStatus} AND recovery.version_no = #{expectedRecoveryVersion}",
            "AND recovery.lease_token = #{expectedLeaseToken} AND recovery.lease_until > #{now}",
            "AND recovery.active_identity_slot IS NOT NULL"
    })
    int failAuthorizationRecovery(
            @Param("itemId") Long itemId, @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("diagnostic") String diagnostic, @Param("now") LocalDateTime now
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "requestedBy", javaType = Long.class),
            @Arg(column = "triggerType", javaType = String.class),
            @Arg(column = "listingCoverageMode", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "sourceBatchId", javaType = Long.class),
            @Arg(column = "totalRows", javaType = Integer.class),
            @Arg(column = "successRows", javaType = Integer.class),
            @Arg(column = "failureRows", javaType = Integer.class),
            @Arg(column = "latestFactDate", javaType = LocalDate.class),
            @Arg(column = "failureReason", javaType = String.class),
            @Arg(column = "authRecoveryId", javaType = Long.class)
    })
    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "store_code AS storeCode, site_code AS siteCode, date_from AS dateFrom, date_to AS dateTo,",
            "requested_by AS requestedBy, trigger_type AS triggerType,",
            "listing_coverage_mode AS listingCoverageMode, status, source_batch_id AS sourceBatchId,",
            "total_rows AS totalRows, success_rows AS successRows, failure_rows AS failureRows,",
            "latest_fact_date AS latestFactDate, failure_reason AS failureReason,",
            "auth_recovery_id AS authRecoveryId FROM sales_sync_task WHERE id = #{taskId}"
    })
    SalesSyncTaskRecord selectById(@Param("taskId") Long taskId);

    @Select({
            "SELECT id FROM sales_sync_task WHERE status = 'queued'",
            "ORDER BY queued_at ASC, id ASC LIMIT #{limit}"
    })
    List<Long> selectQueuedIds(@Param("limit") int limit);
}
