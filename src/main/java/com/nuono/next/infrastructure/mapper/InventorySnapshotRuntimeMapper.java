package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.datapull.InventorySnapshotIdBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

/** Bounded DP-07-A staging, sealing, and terminal-generation retirement SQL. */
public interface InventorySnapshotRuntimeMapper {
    String NO_ACTIVE_INVENTORY_CARRY = " AND NOT EXISTS (SELECT 1"
            + " FROM dp_pull_snapshot_apply_progress active_carry"
            + " JOIN dp_pull_task carry_task ON carry_task.id=active_carry.task_id"
            + " WHERE active_carry.carry_source_task_id=task.id"
            + " AND carry_task.state NOT IN ('SUCCEEDED','FAILED','SUPERSEDED'))";
    String ABANDONED_INVENTORY_TASK = " task.operation_code='DP07A'"
            + " AND task.state IN ('FAILED','SUPERSEDED')"
            + " AND task.finished_at<#{cutoffUtc}"
            + " AND task.lease_owner IS NULL AND task.lease_until IS NULL"
            + " AND NOT EXISTS (SELECT 1 FROM dp_pull_snapshot_current_head active_head"
            + " WHERE active_head.task_id=task.id)"
            + NO_ACTIVE_INVENTORY_CARRY;

    @Insert({
            "INSERT INTO product_management_id_sequence(sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES(#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastId",
            before = false, resultType = Long.class)
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    void reserveInventorySnapshotLineIds(InventorySnapshotIdBlock block);

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "  store_code AS storeCode, site_code AS siteCode, project_code AS projectCode,",
            "  partner_id AS partnerId, source_type AS sourceType, status,",
            "  total_pages AS totalPages, total_rows AS totalRows, valid_rows AS validRows,",
            "  error_rows AS errorRows",
            "FROM official_warehouse_inventory_sync_batch WHERE id=#{batchId}",
            "  AND is_deleted=b'0' FOR UPDATE"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    InventorySyncBatchInsertRecord selectInventorySyncBatchForUpdate(
            @Param("batchId") Long batchId
    );

    @Insert({
            "INSERT INTO official_warehouse_inventory_snapshot_line (",
            "id, sync_batch_id, snapshot_stable_identity, owner_user_id, logical_store_id, store_code, site_code, project_code, partner_id,",
            "product_master_id, product_variant_id, product_site_offer_id, partner_sku, psku_code, noon_sku, pbarcode, barcode,",
            "warehouse_code, country_code, inventory_type, reason_code, classification_code, stock_bucket, qty,",
            "inventory_snapshot_at, title_cache, brand_cache, match_status, match_message, raw_payload_json,",
            "is_current, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{id}, #{syncBatchId}, #{snapshotStableIdentity}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{projectCode}, #{partnerId},",
            "#{productMasterId}, #{productVariantId}, #{productSiteOfferId}, #{partnerSku}, #{pskuCode}, #{noonSku}, #{pbarcode}, #{barcode},",
            "#{warehouseCode}, #{countryCode}, #{inventoryType}, #{reasonCode}, #{classificationCode}, #{stockBucket}, #{quantity},",
            "#{inventorySnapshotAt}, #{titleCache}, #{brandCache}, #{matchStatus}, #{matchMessage}, #{rawPayloadJson},",
            "b'0', b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int insertStagedInventorySnapshotLine(InventorySnapshotLineInsertRecord record);

    @Select({
            "SELECT source_line.snapshot_stable_identity AS snapshotStableIdentity,",
            "  source_line.owner_user_id AS ownerUserId,",
            "  source_line.logical_store_id AS logicalStoreId, source_line.store_code AS storeCode,",
            "  source_line.site_code AS siteCode, source_line.project_code AS projectCode,",
            "  source_line.partner_id AS partnerId, source_line.partner_sku AS partnerSku,",
            "  source_line.noon_sku AS noonSku, source_line.pbarcode, source_line.barcode,",
            "  source_line.warehouse_code AS warehouseCode, source_line.country_code AS countryCode,",
            "  source_line.inventory_type AS inventoryType, source_line.reason_code AS reasonCode,",
            "  source_line.classification_code AS classificationCode,",
            "  source_line.stock_bucket AS stockBucket, source_line.qty AS quantity,",
            "  source_line.inventory_snapshot_at AS inventorySnapshotAt,",
            "  source_line.title_cache AS titleCache, source_line.brand_cache AS brandCache,",
            "  source_line.raw_payload_json AS rawPayloadJson",
            "FROM dp_pull_snapshot_apply_progress source_progress",
            "JOIN official_warehouse_inventory_snapshot_line source_line",
            "  ON source_line.sync_batch_id=source_progress.target_ref_id",
            "JOIN dp_pull_snapshot_apply_progress target_progress",
            "  ON target_progress.task_id=#{targetTaskId}",
            "WHERE source_progress.task_id=#{sourceTaskId}",
            "  AND source_progress.state='SEALED'",
            "  AND source_progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "  AND target_progress.state='CARRYING'",
            "  AND target_progress.carry_source_task_id=#{sourceTaskId}",
            "  AND target_progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "  AND source_line.is_deleted=b'0'",
            "  AND source_line.snapshot_stable_identity>COALESCE(#{afterStableIdentity},'')",
            "  AND NOT EXISTS (SELECT 1 FROM official_warehouse_inventory_snapshot_line target_line",
            "    WHERE target_line.sync_batch_id=target_progress.target_ref_id",
            "      AND target_line.snapshot_stable_identity=source_line.snapshot_stable_identity",
            "      AND target_line.is_deleted=b'0')",
            "ORDER BY source_line.snapshot_stable_identity ASC LIMIT #{limit}"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    List<InventorySnapshotLineInsertRecord> selectInventoryCarryChunk(
            @Param("sourceTaskId") long sourceTaskId,
            @Param("targetTaskId") long targetTaskId,
            @Param("afterStableIdentity") String afterStableIdentity,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE official_warehouse_inventory_sync_batch",
            "SET status='IMPORTED', total_rows=#{effectiveItemCount},",
            "  valid_rows=#{effectiveItemCount}, synced_at=NOW(),",
            "  updated_by=#{operatorUserId}, gmt_updated=NOW()",
            "WHERE id=#{batchId} AND is_deleted=b'0' AND status='STAGING'"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int markInventorySyncBatchImported(
            @Param("batchId") Long batchId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("effectiveItemCount") long effectiveItemCount
    );

    @Update({
            "UPDATE official_warehouse_inventory_snapshot_line line",
            "JOIN (SELECT id FROM (",
            "  SELECT old_line.id",
            "  FROM official_warehouse_inventory_snapshot_line old_line",
            "  JOIN dp_pull_snapshot_apply_progress progress",
            "    ON progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "    AND progress.target_ref_id=old_line.sync_batch_id",
            "    AND progress.state='SEALED'",
            "  JOIN dp_pull_snapshot_apply applied ON applied.task_id=progress.task_id",
            "  JOIN dp_pull_task task ON task.id=progress.task_id",
            "  JOIN dp_pull_snapshot_current_head current_head",
            "    ON current_head.operation_code=applied.operation_code",
            "    AND BINARY current_head.scope_key=BINARY applied.scope_key",
            "    AND current_head.task_id<>progress.task_id",
            "  WHERE applied.operation_code='DP07A'",
            "    AND task.state='SUCCEEDED' AND task.finished_at<#{cutoffUtc}",
            NO_ACTIVE_INVENTORY_CARRY,
            "    AND old_line.is_deleted=b'0'",
            "  ORDER BY old_line.sync_batch_id ASC, old_line.id ASC LIMIT #{limit}",
            ") bounded_inventory_lines) victim ON victim.id=line.id",
            "SET line.is_current=b'0', line.is_deleted=b'1', line.gmt_updated=NOW()"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int retireSupersededInventoryLinesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE official_warehouse_inventory_sync_batch batch",
            "JOIN (SELECT id FROM (",
            "  SELECT old_batch.id",
            "  FROM official_warehouse_inventory_sync_batch old_batch",
            "  JOIN dp_pull_snapshot_apply_progress progress",
            "    ON progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "    AND progress.target_ref_id=old_batch.id AND progress.state='SEALED'",
            "  JOIN dp_pull_snapshot_apply applied ON applied.task_id=progress.task_id",
            "  JOIN dp_pull_task task ON task.id=progress.task_id",
            "  JOIN dp_pull_snapshot_current_head current_head",
            "    ON current_head.operation_code=applied.operation_code",
            "    AND BINARY current_head.scope_key=BINARY applied.scope_key",
            "    AND current_head.task_id<>progress.task_id",
            "  WHERE applied.operation_code='DP07A'",
            "    AND task.state='SUCCEEDED' AND task.finished_at<#{cutoffUtc}",
            NO_ACTIVE_INVENTORY_CARRY,
            "    AND old_batch.is_deleted=b'0'",
            "    AND NOT EXISTS (SELECT 1 FROM official_warehouse_inventory_snapshot_line line",
            "      WHERE line.sync_batch_id=old_batch.id AND line.is_deleted=b'0')",
            "  ORDER BY old_batch.id ASC LIMIT #{limit}",
            ") bounded_inventory_batches) victim ON victim.id=batch.id",
            "SET batch.is_deleted=b'1', batch.gmt_updated=NOW()"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int retireSupersededInventoryBatchesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE official_warehouse_inventory_snapshot_line line",
            "JOIN (SELECT id FROM (",
            "  SELECT abandoned_line.id",
            "  FROM official_warehouse_inventory_snapshot_line abandoned_line",
            "  JOIN dp_pull_snapshot_apply_progress progress",
            "    ON progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "    AND progress.target_ref_id=abandoned_line.sync_batch_id",
            "  JOIN dp_pull_task task ON task.id=progress.task_id",
            "  WHERE", ABANDONED_INVENTORY_TASK,
            "    AND abandoned_line.is_deleted=b'0'",
            "  ORDER BY abandoned_line.sync_batch_id ASC, abandoned_line.id ASC",
            "  LIMIT #{limit}",
            ") bounded_abandoned_inventory_lines) victim ON victim.id=line.id",
            "SET line.is_current=b'0', line.is_deleted=b'1', line.gmt_updated=NOW()"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int retireAbandonedInventoryLinesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE official_warehouse_inventory_sync_batch batch",
            "JOIN (SELECT id FROM (",
            "  SELECT abandoned_batch.id",
            "  FROM official_warehouse_inventory_sync_batch abandoned_batch",
            "  JOIN dp_pull_snapshot_apply_progress progress",
            "    ON progress.target_ref_type='OFFICIAL_WAREHOUSE_INVENTORY_BATCH'",
            "    AND progress.target_ref_id=abandoned_batch.id",
            "  JOIN dp_pull_task task ON task.id=progress.task_id",
            "  WHERE", ABANDONED_INVENTORY_TASK,
            "    AND abandoned_batch.is_deleted=b'0'",
            "    AND NOT EXISTS (SELECT 1 FROM official_warehouse_inventory_snapshot_line line",
            "      WHERE line.sync_batch_id=abandoned_batch.id AND line.is_deleted=b'0')",
            "  ORDER BY abandoned_batch.id ASC LIMIT #{limit}",
            ") bounded_abandoned_inventory_batches) victim ON victim.id=batch.id",
            "SET batch.is_deleted=b'1', batch.gmt_updated=NOW()"
    })
    @Options(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    int retireAbandonedInventoryBatchesBatch(
            @Param("cutoffUtc") LocalDateTime cutoffUtc,
            @Param("limit") int limit
    );
}
