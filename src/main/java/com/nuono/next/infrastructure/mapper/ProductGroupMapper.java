package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductGroupMemberGuardRecord;
import com.nuono.next.product.ProductGroupMemberProjectionRecord;
import com.nuono.next.product.ProductGroupProjectionRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductGroupMapper {

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
    int allocateProductManagementId(IdSequenceCommand command);

    default Long nextProductManagementId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateProductManagementId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("商品管理 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextProductGroupId() {
        return nextProductManagementId("product_group", 60000L);
    }

    default Long nextProductGroupMemberId() {
        return nextProductManagementId("product_group_member", 61000L);
    }

    @Insert({
            "INSERT INTO product_group (",
            "  id, logical_store_id, sku_group, group_ref, group_ref_canonical, group_name, brand, product_fulltype,",
            "  axes_json, conditions_json, member_count, source_snapshot_id, sync_status, last_synced_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{logicalStoreId}, #{skuGroup}, #{groupRef}, #{groupRefCanonical}, #{groupName}, #{brand}, #{productFulltype},",
            "  #{axesJson}, #{conditionsJson}, #{memberCount}, #{sourceSnapshotId}, #{syncStatus}, #{lastSyncedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  group_ref = VALUES(group_ref),",
            "  group_ref_canonical = VALUES(group_ref_canonical),",
            "  group_name = VALUES(group_name),",
            "  brand = VALUES(brand),",
            "  product_fulltype = VALUES(product_fulltype),",
            "  axes_json = VALUES(axes_json),",
            "  conditions_json = VALUES(conditions_json),",
            "  member_count = VALUES(member_count),",
            "  source_snapshot_id = VALUES(source_snapshot_id),",
            "  sync_status = VALUES(sync_status),",
            "  last_synced_at = VALUES(last_synced_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductGroup(
            @Param("id") Long id,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuGroup") String skuGroup,
            @Param("groupRef") String groupRef,
            @Param("groupRefCanonical") String groupRefCanonical,
            @Param("groupName") String groupName,
            @Param("brand") String brand,
            @Param("productFulltype") String productFulltype,
            @Param("axesJson") String axesJson,
            @Param("conditionsJson") String conditionsJson,
            @Param("memberCount") Integer memberCount,
            @Param("sourceSnapshotId") Long sourceSnapshotId,
            @Param("syncStatus") String syncStatus,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_group",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND sku_group = #{skuGroup}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductGroupId(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuGroup") String skuGroup
    );

    @Select({
            "SELECT id",
            "FROM product_master",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND sku_parent = #{skuParent}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductMasterId(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuParent") String skuParent
    );

    @Insert({
            "INSERT INTO product_group_member (",
            "  id, product_group_id, product_master_id, sku_parent, member_sku, child_sku, partner_sku,",
            "  axis_values_json, sort_ix, member_status, source_snapshot_id, last_synced_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{productGroupId}, #{productMasterId}, #{skuParent}, #{memberSku}, #{childSku}, #{partnerSku},",
            "  #{axisValuesJson}, #{sortIx}, #{memberStatus}, #{sourceSnapshotId}, #{lastSyncedAt},",
            "  0, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_master_id = VALUES(product_master_id),",
            "  member_sku = VALUES(member_sku),",
            "  child_sku = VALUES(child_sku),",
            "  partner_sku = VALUES(partner_sku),",
            "  axis_values_json = VALUES(axis_values_json),",
            "  sort_ix = VALUES(sort_ix),",
            "  member_status = VALUES(member_status),",
            "  source_snapshot_id = VALUES(source_snapshot_id),",
            "  last_synced_at = VALUES(last_synced_at),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertProductGroupMember(
            @Param("id") Long id,
            @Param("productGroupId") Long productGroupId,
            @Param("productMasterId") Long productMasterId,
            @Param("skuParent") String skuParent,
            @Param("memberSku") String memberSku,
            @Param("childSku") String childSku,
            @Param("partnerSku") String partnerSku,
            @Param("axisValuesJson") String axisValuesJson,
            @Param("sortIx") Integer sortIx,
            @Param("memberStatus") String memberStatus,
            @Param("sourceSnapshotId") Long sourceSnapshotId,
            @Param("lastSyncedAt") LocalDateTime lastSyncedAt,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM product_group_member",
            "WHERE product_group_id = #{productGroupId}",
            "  AND sku_parent = #{skuParent}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    Long selectProductGroupMemberId(
            @Param("productGroupId") Long productGroupId,
            @Param("skuParent") String skuParent
    );

    @Update({
            "<script>",
            "UPDATE product_group_member",
            "SET is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_group_id = #{productGroupId}",
            "  AND is_deleted = 0",
            "  <if test='skuParents != null and skuParents.size() > 0'>",
            "    AND sku_parent NOT IN",
            "    <foreach collection='skuParents' item='skuParent' open='(' separator=',' close=')'>",
            "      #{skuParent}",
            "    </foreach>",
            "  </if>",
            "</script>"
    })
    int markStaleProductGroupMembersDeleted(
            @Param("productGroupId") Long productGroupId,
            @Param("skuParents") List<String> skuParents,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master pm",
            "JOIN product_group pg",
            "  ON pg.logical_store_id = pm.logical_store_id",
            " AND pg.id = #{productGroupId}",
            " AND pg.is_deleted = 0",
            "JOIN product_group_member pgm",
            "  ON pgm.product_group_id = pg.id",
            " AND pgm.sku_parent = pm.sku_parent",
            " AND pgm.member_status = 'active'",
            " AND pgm.is_deleted = 0",
            "SET pm.sku_group = #{skuGroup},",
            "    pm.group_ref = #{groupRef},",
            "    pm.group_name_cache = #{groupName},",
            "    pm.group_member_count = #{memberCount},",
            "    pm.updated_by = #{updatedBy},",
            "    pm.gmt_updated = NOW()",
            "WHERE pm.is_deleted = 0"
    })
    int syncProductMasterGroupFieldsForActiveMembers(
            @Param("productGroupId") Long productGroupId,
            @Param("skuGroup") String skuGroup,
            @Param("groupRef") String groupRef,
            @Param("groupName") String groupName,
            @Param("memberCount") Integer memberCount,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "<script>",
            "UPDATE product_master pm",
            "SET pm.sku_group = NULL,",
            "    pm.group_ref = NULL,",
            "    pm.group_name_cache = NULL,",
            "    pm.group_member_count = NULL,",
            "    pm.updated_by = #{updatedBy},",
            "    pm.gmt_updated = NOW()",
            "WHERE pm.logical_store_id = #{logicalStoreId}",
            "  AND pm.is_deleted = 0",
            "  AND (",
            "    pm.sku_group = #{skuGroup}",
            "    OR pm.group_ref = #{groupRef}",
            "    OR pm.group_ref = #{groupRefCanonical}",
            "  )",
            "  <if test='activeSkuParents != null and activeSkuParents.size() > 0'>",
            "    AND pm.sku_parent NOT IN",
            "    <foreach collection='activeSkuParents' item='skuParent' open='(' separator=',' close=')'>",
            "      #{skuParent}",
            "    </foreach>",
            "  </if>",
            "</script>"
    })
    int clearProductMasterGroupFieldsForInactiveMembers(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuGroup") String skuGroup,
            @Param("groupRef") String groupRef,
            @Param("groupRefCanonical") String groupRefCanonical,
            @Param("activeSkuParents") List<String> activeSkuParents,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_group_member",
            "SET member_status = 'deleted',",
            "    is_deleted = 1,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND member_status = 'active'",
            "  AND is_deleted = 0"
    })
    int markActiveGroupMembersDeletedByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_group pg",
            "SET member_count = (",
            "      SELECT COUNT(1)",
            "      FROM product_group_member pgm",
            "      WHERE pgm.product_group_id = pg.id",
            "        AND pgm.member_status = 'active'",
            "        AND pgm.is_deleted = 0",
            "    ),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE EXISTS (",
            "  SELECT 1",
            "  FROM product_group_member pgm2",
            "  WHERE pgm2.product_group_id = pg.id",
            "    AND pgm2.product_master_id = #{productMasterId}",
            ")"
    })
    int refreshProductGroupMemberCountsByProductMasterId(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_master",
            "SET sku_group = NULL,",
            "    group_ref = NULL,",
            "    group_name_cache = NULL,",
            "    group_member_count = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{productMasterId}",
            "  AND is_deleted = 0"
    })
    int clearProductMasterGroupFieldsById(
            @Param("productMasterId") Long productMasterId,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  pm.sku_parent AS skuParent,",
            "  MAX(pg.group_ref) AS groupRef",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "LEFT JOIN product_group_member pgm",
            "  ON pgm.product_master_id = pm.id",
            " AND pgm.member_status = 'active'",
            " AND pgm.is_deleted = 0",
            "LEFT JOIN product_group pg",
            "  ON pg.id = pgm.product_group_id",
            " AND pg.logical_store_id = ls.id",
            " AND pg.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "GROUP BY pm.sku_parent",
            "LIMIT 1"
    })
    ProductGroupMemberGuardRecord selectGroupMemberGuardBySkuParent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  pg.id AS productGroupId,",
            "  pm.id AS productMasterId,",
            "  pg.sku_group AS skuGroup,",
            "  pg.group_ref AS groupRef,",
            "  pg.group_ref_canonical AS groupRefCanonical,",
            "  pg.group_name AS groupName,",
            "  pg.brand AS brand,",
            "  pg.product_fulltype AS productFulltype,",
            "  pg.axes_json AS axesJson,",
            "  pg.conditions_json AS conditionsJson,",
            "  pg.member_count AS memberCount",
            "FROM logical_store ls",
            "JOIN logical_store_site anchor",
            "  ON anchor.logical_store_id = ls.id",
            " AND anchor.store_code = #{storeCode}",
            " AND anchor.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "JOIN product_group_member pgm",
            "  ON pgm.sku_parent = pm.sku_parent",
            " AND pgm.member_status = 'active'",
            " AND pgm.is_deleted = 0",
            "JOIN product_group pg",
            "  ON pg.id = pgm.product_group_id",
            " AND pg.logical_store_id = ls.id",
            " AND pg.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    ProductGroupProjectionRecord selectCurrentProductGroupProjection(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  pgm.sku_parent AS skuParent,",
            "  pgm.member_sku AS memberSku,",
            "  pgm.child_sku AS childSku,",
            "  pgm.partner_sku AS partnerSku,",
            "  pgm.axis_values_json AS axisValuesJson,",
            "  pgm.sort_ix AS sortIx,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS title,",
            "  pm.cover_image_url AS imageUrl",
            "FROM product_group_member pgm",
            "JOIN product_group pg",
            "  ON pg.id = pgm.product_group_id",
            " AND pg.id = #{productGroupId}",
            " AND pg.is_deleted = 0",
            "LEFT JOIN product_master pm",
            "  ON pm.logical_store_id = pg.logical_store_id",
            " AND pm.sku_parent = pgm.sku_parent",
            " AND pm.is_deleted = 0",
            "WHERE pgm.product_group_id = #{productGroupId}",
            "  AND pgm.member_status = 'active'",
            "  AND pgm.is_deleted = 0",
            "ORDER BY COALESCE(pgm.sort_ix, 999999), pgm.id"
    })
    List<ProductGroupMemberProjectionRecord> selectActiveProductGroupMembers(
            @Param("productGroupId") Long productGroupId
    );
}
