package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Auth-wait transitions for durable official-warehouse appointment tasks. */
public interface NoonAuthOfficialWarehouseTaskMapper {

    @Update({
            "UPDATE official_warehouse_appointment appointment",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = appointment.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET appointment.next_attempt_at = #{now}, appointment.error_stage = NULL,",
            "appointment.failure_type = NULL, appointment.error_message = NULL,",
            "appointment.execution_version = appointment.execution_version + 1,",
            "appointment.gmt_updated = #{now}",
            "WHERE item.id = #{itemId} AND item.recovery_id = #{recoveryId}",
            "AND item.source_domain = 'OFFICIAL_WAREHOUSE_APPOINTMENT'",
            "AND item.resume_policy = 'AUTO_RESUME' AND item.status = 'PENDING'",
            "AND appointment.status = 'PENDING' AND appointment.error_stage = 'AUTH_RECOVERY'",
            "AND appointment.failure_type = 'AUTH_RECOVERY_PENDING' AND appointment.is_deleted = b'0'",
            "AND recovery.status = #{expectedRecoveryStatus} AND recovery.version_no = #{expectedRecoveryVersion}",
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
            "UPDATE official_warehouse_appointment appointment",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = appointment.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET appointment.status = 'FAILED', appointment.next_attempt_at = NULL,",
            "appointment.error_stage = 'AUTH_RECOVERY', appointment.failure_type = 'AUTH_RECOVERY_FAILED',",
            "appointment.error_message = #{diagnostic},",
            "appointment.execution_version = appointment.execution_version + 1,",
            "appointment.gmt_updated = #{now}",
            "WHERE item.id = #{itemId} AND item.recovery_id = #{recoveryId}",
            "AND item.source_domain = 'OFFICIAL_WAREHOUSE_APPOINTMENT' AND item.status = 'PENDING'",
            "AND appointment.status = 'PENDING' AND appointment.error_stage = 'AUTH_RECOVERY'",
            "AND appointment.failure_type = 'AUTH_RECOVERY_PENDING' AND appointment.is_deleted = b'0'",
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
}
