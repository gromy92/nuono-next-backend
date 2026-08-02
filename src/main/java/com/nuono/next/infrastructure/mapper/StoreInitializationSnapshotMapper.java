package com.nuono.next.infrastructure.mapper;

import com.nuono.next.store.StoreInitializationSnapshotRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface StoreInitializationSnapshotMapper {

    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id,",
            "  store_code,",
            "  project_code,",
            "  project_name,",
            "  status,",
            "  last_initialized_at,",
            "  snapshot_json",
            "FROM store_initialization_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    StoreInitializationSnapshotRecord selectByOwnerAndStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT id, owner_user_id, store_code, project_code, project_name, status,",
            "       last_initialized_at, snapshot_json",
            "FROM store_initialization_snapshot",
            "WHERE id = #{id} AND is_deleted = 0"
    })
    StoreInitializationSnapshotRecord selectById(@Param("id") Long id);

    @Select({
            "SELECT id, owner_user_id, store_code, project_code, project_name, status,",
            "       last_initialized_at, snapshot_json",
            "FROM store_initialization_snapshot",
            "WHERE status = 'QUEUED' AND is_deleted = 0",
            "ORDER BY gmt_updated ASC, id ASC",
            "LIMIT #{limit}"
    })
    List<StoreInitializationSnapshotRecord> selectQueued(@Param("limit") int limit);

    @Update({
            "UPDATE store_initialization_snapshot",
            "SET status = 'RUNNING',",
            "    snapshot_json = JSON_SET(snapshot_json, '$.status', 'RUNNING',",
            "      '$.phaseLabel', '恢复初始化', '$.message', '授权已恢复，正在继续原初始化任务。'),",
            "    gmt_updated = NOW()",
            "WHERE id = #{id} AND status = 'QUEUED' AND is_deleted = 0"
    })
    int claimQueued(@Param("id") Long id);

    @Update({
            "UPDATE store_initialization_snapshot snapshot",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = snapshot.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET snapshot.status = 'QUEUED',",
            "    snapshot.snapshot_json = JSON_SET(snapshot.snapshot_json, '$.status', 'QUEUED',",
            "      '$.phaseLabel', '等待继续', '$.message', 'Noon Project 授权已恢复，原初始化任务将自动继续。'),",
            "    snapshot.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'STORE_INITIALIZATION'",
            "  AND item.resume_policy = 'AUTO_RESUME'",
            "  AND item.status = 'PENDING'",
            "  AND snapshot.status = 'WAITING_AUTHORIZATION'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int resumeAfterAuthorization(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("now") LocalDateTime now
    );

    @Update({
            "UPDATE store_initialization_snapshot snapshot",
            "JOIN noon_auth_identity_recovery_item item ON item.source_task_id = snapshot.id",
            "JOIN noon_auth_identity_recovery recovery ON recovery.id = item.recovery_id",
            "SET snapshot.status = 'FAILED',",
            "    snapshot.snapshot_json = JSON_SET(snapshot.snapshot_json, '$.status', 'FAILED',",
            "      '$.phaseLabel', '授权恢复失败', '$.message', #{diagnostic}),",
            "    snapshot.gmt_updated = #{now}",
            "WHERE item.id = #{itemId}",
            "  AND item.recovery_id = #{recoveryId}",
            "  AND item.source_domain = 'STORE_INITIALIZATION'",
            "  AND item.status = 'PENDING'",
            "  AND snapshot.status = 'WAITING_AUTHORIZATION'",
            "  AND recovery.status = #{expectedRecoveryStatus}",
            "  AND recovery.version_no = #{expectedRecoveryVersion}",
            "  AND recovery.lease_token = #{expectedLeaseToken}",
            "  AND recovery.lease_until > #{now}",
            "  AND recovery.active_identity_slot IS NOT NULL"
    })
    int failAuthorizationRecovery(
            @Param("itemId") Long itemId,
            @Param("recoveryId") Long recoveryId,
            @Param("expectedRecoveryStatus") NoonAuthRecoveryStatus expectedRecoveryStatus,
            @Param("expectedRecoveryVersion") Long expectedRecoveryVersion,
            @Param("expectedLeaseToken") String expectedLeaseToken,
            @Param("diagnostic") String diagnostic,
            @Param("now") LocalDateTime now
    );

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {
            "SELECT LAST_INSERT_ID()"
            },
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateStoreInitializationSnapshotId(IdSequenceCommand command);

    default Long nextId() {
        IdSequenceCommand command = new IdSequenceCommand("store_initialization_snapshot", 40000L);
        allocateStoreInitializationSnapshotId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("店铺初始化快照 ID 序列分配失败");
        }
        return id;
    }

    @Insert({
            "INSERT INTO store_initialization_snapshot (",
            "  id, owner_user_id, store_code, project_code, project_name, status, last_initialized_at, snapshot_json,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{projectCode}, #{projectName}, #{status}, #{lastInitializedAt}, #{snapshotJson},",
            "  0, #{ownerUserId}, #{ownerUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  project_code = VALUES(project_code),",
            "  project_name = VALUES(project_name),",
            "  status = VALUES(status),",
            "  last_initialized_at = VALUES(last_initialized_at),",
            "  snapshot_json = VALUES(snapshot_json),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsert(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("status") String status,
            @Param("lastInitializedAt") LocalDateTime lastInitializedAt,
            @Param("snapshotJson") String snapshotJson
    );
}
