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

public interface NoonAuthRecoverySendLedgerMapper extends NoonAuthRecoveryMapperColumns {
    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET generation_no = generation_no + 1,",
            "    first_send_at = CASE WHEN send_attempt_count = 0 THEN #{sendIntentAt} ELSE first_send_at END,",
            "    second_send_at = CASE WHEN send_attempt_count = 1 THEN #{sendIntentAt} ELSE second_send_at END,",
            "    send_attempt_count = send_attempt_count + 1,",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}",
            "  AND send_attempt_count < 2",
            "  AND active_identity_slot IS NOT NULL"
    })
    int recordSendIntent(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("sendIntentAt") LocalDateTime sendIntentAt,
            @Param("now") LocalDateTime now
    );

    @Insert({
            "INSERT INTO noon_auth_identity_send_ledger (",
            "  identity_key, recovery_id, config_fingerprint, send_budget_epoch, generation_no,",
            "  send_intent_at, gmt_create",
            ")",
            "SELECT identity_key, id, config_fingerprint, send_budget_epoch, generation_no,",
            "  #{sendIntentAt}, #{now}",
            "FROM noon_auth_identity_recovery",
            "WHERE id = #{recoveryId}",
            "  AND config_fingerprint IS NOT NULL"
    })
    int insertIdentitySendLedger(
            @Param("recoveryId") Long recoveryId,
            @Param("sendIntentAt") LocalDateTime sendIntentAt,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE noon_auth_identity_recovery",
            "SET last_mail_uid_hash = #{mailUidHash},",
            "    last_message_id_hash = #{messageIdHash},",
            "    version_no = version_no + 1,",
            "    gmt_updated = #{now}",
            "WHERE id = #{recoveryId}",
            "  AND status = #{expectedStatus}",
            "  AND version_no = #{expectedVersion}",
            "  AND lease_token = #{expectedLeaseToken}",
            "  AND lease_until > #{now}"
    })
    int recordMailboxCorrelation(
            @Param("recoveryId") Long recoveryId,
            @Param("expectedStatus") NoonAuthRecoveryStatus expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("mailUidHash") String mailUidHash,
            @Param("messageIdHash") String messageIdHash,
            @Param("now") LocalDateTime now
    );

    @Select({
            "SELECT MAX(send_intent_at)",
            "FROM noon_auth_identity_send_ledger",
            "WHERE identity_key = #{identityKey}"
    })
    LocalDateTime selectLatestIdentitySendAt(@Param("identityKey") String identityKey);
}
