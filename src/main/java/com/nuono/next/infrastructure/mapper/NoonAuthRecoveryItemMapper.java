package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthIdentityRecoveryRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryItemStatus;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonProjectAuthStateRecord;
import com.nuono.next.noonauth.NoonProjectAuthStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonAuthRecoveryItemMapper extends NoonAuthRecoveryMapperColumns {
    @Insert({
            "INSERT INTO noon_auth_identity_recovery_item (",
            "  recovery_id, owner_user_id, project_code, store_code, site_code, source_task_id,",
            "  source_domain, source_checkpoint, resume_policy, expected_auth_version, status,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{recoveryId}, #{ownerUserId}, #{projectCode}, #{storeCode}, #{siteCode}, #{sourceTaskId},",
            "  #{sourceDomain}, #{sourceCheckpoint}, #{resumePolicy}, #{expectedAuthVersion}, 'PENDING',",
            "  #{createdAt}, #{createdAt}",
            ") ON DUPLICATE KEY UPDATE",
            "  id = LAST_INSERT_ID(id),",
            "  source_checkpoint = VALUES(source_checkpoint),",
            "  resume_policy = VALUES(resume_policy),",
            "  gmt_updated = VALUES(gmt_updated)"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "id",
            before = false,
            resultType = Long.class
    )
    int coalesceRecoveryItem(NoonAuthRecoveryItemRecord item);

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    NoonAuthRecoveryItemRecord selectProjectRecoveryItem(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM noon_auth_identity_recovery_item item",
            "WHERE item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND UPPER(item.source_domain) = UPPER(#{sourceDomain})",
            "  AND item.source_task_id = #{sourceTaskId}",
            "  AND item.status = 'RECOVERED'",
            "  AND item.expected_auth_version + 1 = #{currentAuthVersion}"
    })
    int countRecoveredSourceTaskAtCurrentAuthVersion(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("sourceDomain") String sourceDomain,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("currentAuthVersion") Long currentAuthVersion
    );

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND status = 'PENDING'",
            "ORDER BY id ASC",
            "LIMIT #{limit}"
    })
    List<NoonAuthRecoveryItemRecord> listPendingItems(
            @Param("recoveryId") Long recoveryId,
            @Param("limit") int limit
    );

    @Select({
            "SELECT", ITEM_COLUMNS,
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "ORDER BY id ASC"
    })
    List<NoonAuthRecoveryItemRecord> listRecoveryItems(@Param("recoveryId") Long recoveryId);

    @Select({
            "SELECT COUNT(1)",
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND status IN ('PENDING', 'VALIDATING')"
    })
    int countPendingItems(@Param("recoveryId") Long recoveryId);

    @Update({
            "UPDATE noon_pull_task task",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = task.auth_recovery_id",
            "SET task.status = 'FAILED',",
            "    task.failure_type = 'auth_required',",
            "    task.retry_action = 'MANUAL_ACTION',",
            "    task.retryable = b'0',",
            "    task.requires_manual_action = b'1',",
            "    task.diagnostic_summary = #{diagnosticSummary},",
            "    task.readiness_state = 'auth_recovery_failed',",
            "    task.locked_by = NULL,",
            "    task.finished_at = #{now},",
            "    task.gmt_updated = #{now}",
            "WHERE task.id = #{taskId}",
            "  AND task.status = 'BLOCKED_AUTH'",
            "  AND task.auth_recovery_id = #{recoveryId}",
            "  AND task.is_deleted = b'0'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int failBlockedTaskAfterRecovery(
            @Param("taskId") Long taskId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = #{targetStatus},",
            "    item.failure_code = #{failureCode},",
            "    item.diagnostic_summary = #{diagnosticSummary},",
            "    item.recovered_at = #{recoveredAt},",
            "    item.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.status = #{expectedStatus}",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int transitionRecoveryItem(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryItemStatus expectedStatus,
            @Param("targetStatus") NoonAuthRecoveryItemStatus targetStatus,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery_item item",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET item.status = #{targetStatus},",
            "    item.failure_code = #{failureCode},",
            "    item.diagnostic_summary = #{diagnosticSummary},",
            "    item.recovered_at = #{recoveredAt},",
            "    item.gmt_updated = #{now}",
            "WHERE item.recovery_id = #{recoveryId}",
            "  AND item.owner_user_id = #{ownerUserId}",
            "  AND BINARY item.project_code = BINARY #{projectCode}",
            "  AND item.expected_auth_version = #{expectedAuthVersion}",
            "  AND item.status IN ('PENDING', 'VALIDATING')",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int transitionProjectItems(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("expectedAuthVersion") Long expectedAuthVersion,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("targetStatus") NoonAuthRecoveryItemStatus targetStatus,
            @Param("failureCode") String failureCode,
            @Param("diagnosticSummary") String diagnosticSummary,
            @Param("recoveredAt") LocalDateTime recoveredAt,
            @Param("now") LocalDateTime now
    );
}
