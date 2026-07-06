package com.nuono.next.intransit.autosync;

import com.nuono.next.infrastructure.mapper.InTransitGoodsSequenceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LogisticsAutoSyncMapper extends InTransitGoodsSequenceMapper {
    String ACCOUNT_COLUMNS = ""
            + "id, owner_user_id AS ownerUserId, operator_user_id AS operatorUserId, "
            + "source_system AS sourceSystem, forwarder_name AS forwarderName, "
            + "login_account AS loginAccount, login_account_hash AS loginAccountHash, "
            + "password_cipher AS passwordCipher, enabled, schedule_enabled AS scheduleEnabled, "
            + "commit_enabled AS commitEnabled, schedule_window_start AS scheduleWindowStart, "
            + "schedule_window_end AS scheduleWindowEnd, min_interval_hours AS minIntervalHours, "
            + "verification_status AS verificationStatus, last_login_status AS lastLoginStatus, "
            + "last_preview_status AS lastPreviewStatus, last_sync_status AS lastSyncStatus, "
            + "last_task_id AS lastTaskId, last_synced_at AS lastSyncedAt, "
            + "next_eligible_at AS nextEligibleAt, cooldown_until AS cooldownUntil, "
            + "last_failure_code AS lastFailureCode, last_failure_message AS lastFailureMessage, "
            + "is_deleted AS deleted, created_by AS createdBy, updated_by AS updatedBy, "
            + "gmt_create AS createdAt, gmt_updated AS updatedAt";

    default Long nextAccountId() {
        return nextId("logistics_forwarder_account", 180000L);
    }

    @Select({
            "SELECT", ACCOUNT_COLUMNS,
            "FROM logistics_forwarder_account",
            "WHERE id = #{accountId} AND is_deleted = b'0'"
    })
    LogisticsAutoSyncAccount selectAccountById(@Param("accountId") Long accountId);

    @Select({
            "SELECT", ACCOUNT_COLUMNS,
            "FROM logistics_forwarder_account",
            "WHERE owner_user_id = #{ownerUserId} AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<LogisticsAutoSyncAccount> listAccounts(@Param("ownerUserId") Long ownerUserId);

    @Insert({
            "INSERT INTO logistics_forwarder_account (",
            "id, owner_user_id, operator_user_id, source_system, forwarder_name, login_account, login_account_hash,",
            "password_cipher, enabled, schedule_enabled, commit_enabled, schedule_window_start, schedule_window_end,",
            "min_interval_hours, verification_status, next_eligible_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.operatorUserId}, #{row.sourceSystem}, #{row.forwarderName},",
            "#{row.loginAccount}, #{row.loginAccountHash}, #{row.passwordCipher}, #{row.enabled}, #{row.scheduleEnabled},",
            "#{row.commitEnabled}, #{row.scheduleWindowStart}, #{row.scheduleWindowEnd}, #{row.minIntervalHours},",
            "#{row.verificationStatus}, #{row.nextEligibleAt}, #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertAccount(@Param("row") LogisticsAutoSyncAccount row);

    @Update({
            "UPDATE logistics_forwarder_account",
            "SET operator_user_id = #{row.operatorUserId}, source_system = #{row.sourceSystem},",
            "forwarder_name = #{row.forwarderName}, login_account = #{row.loginAccount},",
            "login_account_hash = #{row.loginAccountHash}, password_cipher = #{row.passwordCipher},",
            "enabled = #{row.enabled}, schedule_enabled = #{row.scheduleEnabled}, commit_enabled = #{row.commitEnabled},",
            "schedule_window_start = #{row.scheduleWindowStart}, schedule_window_end = #{row.scheduleWindowEnd},",
            "min_interval_hours = #{row.minIntervalHours}, verification_status = #{row.verificationStatus},",
            "updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{row.id} AND owner_user_id = #{row.ownerUserId} AND is_deleted = b'0'"
    })
    int updateAccount(@Param("row") LogisticsAutoSyncAccount row);

    @Select({
            "SELECT", ACCOUNT_COLUMNS,
            "FROM logistics_forwarder_account",
            "WHERE enabled = b'1' AND schedule_enabled = b'1' AND is_deleted = b'0'",
            "AND (cooldown_until IS NULL OR cooldown_until <= NOW())",
            "AND (next_eligible_at IS NULL OR next_eligible_at <= NOW())",
            "ORDER BY COALESCE(next_eligible_at, gmt_updated), id",
            "LIMIT #{limit}"
    })
    List<LogisticsAutoSyncAccount> listDueAccounts(@Param("limit") int limit);

    @Update({
            "UPDATE logistics_forwarder_account",
            "SET last_task_id = #{taskId}, last_login_status = #{loginStatus},",
            "last_preview_status = #{previewStatus}, last_sync_status = #{syncStatus},",
            "verification_status = #{verificationStatus}, last_synced_at = #{lastSyncedAt},",
            "next_eligible_at = #{nextEligibleAt}, cooldown_until = #{cooldownUntil},",
            "last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},",
            "updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{accountId} AND is_deleted = b'0'"
    })
    int updateAccountRunState(
            @Param("accountId") Long accountId,
            @Param("taskId") Long taskId,
            @Param("loginStatus") String loginStatus,
            @Param("previewStatus") String previewStatus,
            @Param("syncStatus") String syncStatus,
            @Param("verificationStatus") String verificationStatus,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("nextEligibleAt") LocalDateTime nextEligibleAt,
            @Param("cooldownUntil") LocalDateTime cooldownUntil,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("operatorUserId") Long operatorUserId
    );
}
