package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderSyncTaskRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Legacy-only sync-task evidence. Runtime DP-10 never consumes this mapper. */
@Mapper
public interface LegacyAli1688HistoricalOrderSyncMapper {

    @Select({
            "SELECT id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at,",
            "  created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE provider_code = #{providerCode}",
            "  AND is_deleted = b'0' AND status = 'authorized'",
            "ORDER BY gmt_updated ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<Ali1688HistoricalOrderAuthorizationRow> listScheduledOpenApiAuthorizations(
            @Param("providerCode") String providerCode,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT COUNT(*) FROM procurement_ali1688_order_sync_task",
            "WHERE owner_user_id = #{ownerUserId} AND authorization_id = #{authorizationId}",
            "  AND status = 'running' AND is_deleted = b'0'",
            "  AND gmt_updated >= DATE_SUB(NOW(), INTERVAL #{staleMinutes} MINUTE)"
    })
    int countRecentRunningSyncTasks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId,
            @Param("staleMinutes") Integer staleMinutes
    );

    @Select({
            "SELECT COUNT(*) FROM procurement_ali1688_order_sync_task",
            "WHERE owner_user_id = #{ownerUserId} AND authorization_id = #{authorizationId}",
            "  AND task_type = #{taskType} AND is_deleted = b'0'",
            "  AND gmt_create >= DATE_SUB(NOW(), INTERVAL #{recentDays} DAY)"
    })
    int countRecentSyncTasksByType(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId,
            @Param("taskType") String taskType,
            @Param("recentDays") Integer recentDays
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_sync_task (",
            "  id, owner_user_id, authorization_id, task_type, status, processed_count,",
            "  imported_count, failed_count, progress_percent, checkpoint_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{taskType}, #{status},",
            "  #{processedCount}, #{importedCount}, #{failedCount}, #{progressPercent},",
            "  #{checkpointJson}, #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSyncTask(Ali1688HistoricalOrderSyncTaskRow row);

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET checkpoint_json = #{checkpointJson}, progress_percent = #{progressPercent},",
            "  processed_count = #{processedCount}, imported_count = #{importedCount},",
            "  failed_count = #{failedCount}, status = 'running', gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int updateSyncTaskCheckpoint(
            @Param("taskId") Long taskId,
            @Param("checkpointJson") String checkpointJson,
            @Param("progressPercent") Integer progressPercent,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'success', processed_count = #{processedCount},",
            "  imported_count = #{importedCount}, failed_count = #{failedCount},",
            "  progress_percent = 100, checkpoint_json = #{checkpointJson},",
            "  failure_code = NULL, failure_message = NULL, retryable = b'0',",
            "  requires_manual_action = b'0', finished_at = NOW(), gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskSuccess(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("checkpointJson") String checkpointJson
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'partial_success', processed_count = #{processedCount},",
            "  imported_count = #{importedCount}, failed_count = #{failedCount},",
            "  progress_percent = 100, failure_code = #{failureCode},",
            "  failure_message = #{failureMessage}, checkpoint_json = #{checkpointJson},",
            "  retryable = #{retryable}, requires_manual_action = #{requiresManualAction},",
            "  finished_at = NOW(), gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskPartialSuccess(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("checkpointJson") String checkpointJson,
            @Param("retryable") Boolean retryable,
            @Param("requiresManualAction") Boolean requiresManualAction
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'failed', processed_count = #{processedCount},",
            "  imported_count = #{importedCount}, failed_count = #{failedCount},",
            "  progress_percent = 100, failure_code = #{failureCode},",
            "  failure_message = #{failureMessage}, checkpoint_json = #{checkpointJson},",
            "  retryable = #{retryable}, requires_manual_action = #{requiresManualAction},",
            "  finished_at = NOW(), gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskFailed(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("checkpointJson") String checkpointJson,
            @Param("retryable") Boolean retryable,
            @Param("requiresManualAction") Boolean requiresManualAction
    );
}
