package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonauth.NoonAuthRecoveryCheckpointRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface NoonAuthRecoveryCheckpointMapper {
    @Insert({
            "INSERT INTO noon_auth_recovery_checkpoint (",
            " recovery_id, generation_no, checkpoint_kind, key_version,",
            " initialization_vector, ciphertext, expires_at, version_no, gmt_create, gmt_updated",
            ") VALUES (#{recoveryId}, #{generationNo}, #{checkpointKind}, #{keyVersion},",
            " #{initializationVector}, #{ciphertext}, #{expiresAt}, 0, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            " generation_no = VALUES(generation_no), checkpoint_kind = VALUES(checkpoint_kind),",
            " key_version = VALUES(key_version), initialization_vector = VALUES(initialization_vector),",
            " ciphertext = VALUES(ciphertext), expires_at = VALUES(expires_at),",
            " version_no = version_no + 1, gmt_updated = NOW()"
    })
    int upsert(NoonAuthRecoveryCheckpointRecord record);

    @Select({
            "SELECT recovery_id AS recoveryId, generation_no AS generationNo,",
            " checkpoint_kind AS checkpointKind, key_version AS keyVersion,",
            " initialization_vector AS initializationVector, ciphertext,",
            " expires_at AS expiresAt, version_no AS versionNo",
            "FROM noon_auth_recovery_checkpoint WHERE recovery_id = #{recoveryId}"
    })
    NoonAuthRecoveryCheckpointRecord select(@Param("recoveryId") Long recoveryId);

    @Delete("DELETE FROM noon_auth_recovery_checkpoint WHERE recovery_id = #{recoveryId}")
    int delete(@Param("recoveryId") Long recoveryId);

    @Delete("DELETE FROM noon_auth_recovery_checkpoint WHERE expires_at <= #{now}")
    int deleteExpired(@Param("now") LocalDateTime now);
}
