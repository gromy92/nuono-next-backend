package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingReauthenticationAttemptRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductListingReauthenticationAttemptMapper {

    @Select({
            "SELECT",
            "  id AS recoveryItemId,",
            "  recovery_id AS recoveryId,",
            "  owner_user_id AS ownerUserId,",
            "  project_code AS projectCode,",
            "  expected_auth_version AS requestedAuthVersion,",
            "  status AS recoveryItemStatus,",
            "  failure_code AS recoveryItemFailureCode,",
            "  recovered_at AS recoveryItemRecoveredAt",
            "FROM noon_auth_identity_recovery_item",
            "WHERE recovery_id = #{recoveryId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND source_task_id IS NULL",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    ProductListingReauthenticationAttemptRecord selectSourceLessRecoveryItem(
            @Param("recoveryId") Long recoveryId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    @Insert({
            "INSERT INTO product_listing_reauthentication_attempt (",
            "  real_run_task_id, owner_user_id, draft_id, project_id,",
            "  project_code, store_code, recovery_id, recovery_item_id,",
            "  requested_auth_version, resume_action, status, version_no, failure_code,",
            "  requested_at, completed_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{realRunTaskId}, #{ownerUserId}, #{draftId}, #{projectId},",
            "  #{projectCode}, #{storeCode}, #{recoveryId}, #{recoveryItemId},",
            "  #{requestedAuthVersion}, #{resumeAction}, 'PENDING', 0, NULL,",
            "  NOW(), NULL, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  real_run_task_id = VALUES(real_run_task_id)"
    })
    int insertPendingAttempt(
            ProductListingReauthenticationAttemptRecord attempt
    );

    @Update({
            "UPDATE product_listing_reauthentication_attempt",
            "SET draft_id = #{replacement.draftId},",
            "    project_id = #{replacement.projectId},",
            "    project_code = #{replacement.projectCode},",
            "    store_code = #{replacement.storeCode},",
            "    recovery_id = #{replacement.recoveryId},",
            "    recovery_item_id = #{replacement.recoveryItemId},",
            "    requested_auth_version = #{replacement.requestedAuthVersion},",
            "    resume_action = #{replacement.resumeAction},",
            "    status = 'PENDING',",
            "    version_no = version_no + 1,",
            "    failure_code = NULL,",
            "    requested_at = NOW(),",
            "    completed_at = NULL,",
            "    gmt_updated = NOW()",
            "WHERE real_run_task_id = #{replacement.realRunTaskId}",
            "  AND owner_user_id = #{replacement.ownerUserId}",
            "  AND recovery_id = #{expectedRecoveryId}",
            "  AND recovery_item_id = #{expectedRecoveryItemId}",
            "  AND version_no = #{expectedVersionNo}",
            "  AND status IN ('FAILED', 'COMPLETED')"
    })
    int rebindTerminalAttemptCas(
            @Param("replacement")
            ProductListingReauthenticationAttemptRecord replacement,
            @Param("expectedRecoveryId") Long expectedRecoveryId,
            @Param("expectedRecoveryItemId") Long expectedRecoveryItemId,
            @Param("expectedVersionNo") Long expectedVersionNo
    );

    @Select({
            "SELECT",
            "  real_run_task_id AS realRunTaskId,",
            "  owner_user_id AS ownerUserId,",
            "  draft_id AS draftId,",
            "  project_id AS projectId,",
            "  project_code AS projectCode,",
            "  store_code AS storeCode,",
            "  recovery_id AS recoveryId,",
            "  recovery_item_id AS recoveryItemId,",
            "  requested_auth_version AS requestedAuthVersion,",
            "  resume_action AS resumeAction,",
            "  status, version_no AS versionNo, failure_code AS failureCode",
            "FROM product_listing_reauthentication_attempt",
            "WHERE real_run_task_id = #{realRunTaskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "LIMIT 1 FOR UPDATE"
    })
    ProductListingReauthenticationAttemptRecord selectAttemptForUpdate(
            @Param("realRunTaskId") Long realRunTaskId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  attempt.real_run_task_id AS realRunTaskId,",
            "  attempt.owner_user_id AS ownerUserId,",
            "  attempt.draft_id AS draftId,",
            "  attempt.project_id AS projectId,",
            "  attempt.project_code AS projectCode,",
            "  attempt.store_code AS storeCode,",
            "  attempt.recovery_id AS recoveryId,",
            "  attempt.recovery_item_id AS recoveryItemId,",
            "  attempt.requested_auth_version AS requestedAuthVersion,",
            "  attempt.resume_action AS resumeAction,",
            "  attempt.status,",
            "  attempt.version_no AS versionNo,",
            "  attempt.failure_code AS failureCode,",
            "  attempt.requested_at AS requestedAt,",
            "  attempt.completed_at AS completedAt,",
            "  item.status AS recoveryItemStatus,",
            "  item.failure_code AS recoveryItemFailureCode,",
            "  item.recovered_at AS recoveryItemRecoveredAt,",
            "  recovery.status AS recoveryStatus,",
            "  recovery.failure_code AS recoveryFailureCode,",
            "  project_state.status AS projectAuthStatus,",
            "  project_state.auth_version AS currentAuthVersion,",
            "  project_state.active_recovery_id AS activeRecoveryId",
            "FROM product_listing_reauthentication_attempt attempt",
            "LEFT JOIN noon_auth_identity_recovery_item item",
            "  ON item.id = attempt.recovery_item_id",
            " AND item.recovery_id = attempt.recovery_id",
            " AND item.owner_user_id = attempt.owner_user_id",
            " AND BINARY item.project_code = BINARY attempt.project_code",
            " AND item.expected_auth_version = attempt.requested_auth_version",
            " AND item.source_task_id IS NULL",
            "LEFT JOIN noon_auth_identity_recovery recovery",
            "  ON recovery.id = attempt.recovery_id",
            "LEFT JOIN noon_project_auth_state project_state",
            "  ON project_state.owner_user_id = attempt.owner_user_id",
            " AND BINARY project_state.project_code = BINARY attempt.project_code",
            "WHERE attempt.real_run_task_id = #{realRunTaskId}",
            "  AND attempt.owner_user_id = #{ownerUserId}",
            "LIMIT 1"
    })
    ProductListingReauthenticationAttemptRecord selectAttemptState(
            @Param("realRunTaskId") Long realRunTaskId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Update({
            "UPDATE product_listing_reauthentication_attempt attempt",
            "JOIN noon_auth_identity_recovery_item item",
            "  ON item.id = attempt.recovery_item_id",
            " AND item.recovery_id = attempt.recovery_id",
            " AND item.owner_user_id = attempt.owner_user_id",
            " AND BINARY item.project_code = BINARY attempt.project_code",
            " AND item.expected_auth_version = attempt.requested_auth_version",
            "JOIN noon_project_auth_state project_state",
            "  ON project_state.owner_user_id = attempt.owner_user_id",
            " AND BINARY project_state.project_code = BINARY attempt.project_code",
            "SET attempt.status = 'VERIFYING',",
            "    attempt.version_no = attempt.version_no + 1,",
            "    attempt.gmt_updated = NOW()",
            "WHERE attempt.real_run_task_id = #{realRunTaskId}",
            "  AND attempt.owner_user_id = #{ownerUserId}",
            "  AND attempt.recovery_id = #{recoveryId}",
            "  AND attempt.recovery_item_id = #{recoveryItemId}",
            "  AND attempt.version_no = #{expectedVersionNo}",
            "  AND attempt.status = 'PENDING'",
            "  AND item.source_task_id IS NULL",
            "  AND item.status = 'RECOVERED'",
            "  AND item.recovered_at IS NOT NULL",
            "  AND project_state.status = 'HEALTHY'",
            "  AND project_state.active_recovery_id IS NULL",
            "  AND project_state.auth_version = attempt.requested_auth_version + 1"
    })
    int claimRecoveredAttempt(
            @Param("realRunTaskId") Long realRunTaskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("recoveryId") Long recoveryId,
            @Param("recoveryItemId") Long recoveryItemId,
            @Param("expectedVersionNo") Long expectedVersionNo
    );

    @Update({
            "UPDATE product_listing_reauthentication_attempt attempt",
            "JOIN noon_auth_identity_recovery_item item",
            "  ON item.id = attempt.recovery_item_id",
            " AND item.recovery_id = attempt.recovery_id",
            " AND item.owner_user_id = attempt.owner_user_id",
            " AND BINARY item.project_code = BINARY attempt.project_code",
            " AND item.expected_auth_version = attempt.requested_auth_version",
            "JOIN noon_project_auth_state project_state",
            "  ON project_state.owner_user_id = attempt.owner_user_id",
            " AND BINARY project_state.project_code = BINARY attempt.project_code",
            "SET attempt.status = 'COMPLETED',",
            "    attempt.version_no = attempt.version_no + 1,",
            "    attempt.failure_code = NULL,",
            "    attempt.completed_at = NOW(),",
            "    attempt.gmt_updated = NOW()",
            "WHERE attempt.real_run_task_id = #{realRunTaskId}",
            "  AND attempt.owner_user_id = #{ownerUserId}",
            "  AND attempt.recovery_id = #{recoveryId}",
            "  AND attempt.recovery_item_id = #{recoveryItemId}",
            "  AND attempt.version_no = #{expectedVersionNo}",
            "  AND attempt.status = 'VERIFYING'",
            "  AND item.source_task_id IS NULL",
            "  AND item.status = 'RECOVERED'",
            "  AND item.recovered_at IS NOT NULL",
            "  AND project_state.status = 'HEALTHY'",
            "  AND project_state.active_recovery_id IS NULL",
            "  AND project_state.auth_version = attempt.requested_auth_version + 1"
    })
    int completeClaimedAttempt(
            @Param("realRunTaskId") Long realRunTaskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("recoveryId") Long recoveryId,
            @Param("recoveryItemId") Long recoveryItemId,
            @Param("expectedVersionNo") Long expectedVersionNo
    );

    @Update({
            "UPDATE product_listing_reauthentication_attempt",
            "SET status = 'FAILED',",
            "    version_no = version_no + 1,",
            "    failure_code = #{failureCode},",
            "    completed_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE real_run_task_id = #{realRunTaskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND recovery_id = #{recoveryId}",
            "  AND recovery_item_id = #{recoveryItemId}",
            "  AND version_no = #{expectedVersionNo}",
            "  AND status = 'PENDING'"
    })
    int markAttemptFailed(
            @Param("realRunTaskId") Long realRunTaskId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("recoveryId") Long recoveryId,
            @Param("recoveryItemId") Long recoveryItemId,
            @Param("expectedVersionNo") Long expectedVersionNo,
            @Param("failureCode") String failureCode
    );
}
