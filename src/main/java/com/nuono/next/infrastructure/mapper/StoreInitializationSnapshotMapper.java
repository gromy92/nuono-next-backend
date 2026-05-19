package com.nuono.next.infrastructure.mapper;

import com.nuono.next.store.StoreInitializationSnapshotRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

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
