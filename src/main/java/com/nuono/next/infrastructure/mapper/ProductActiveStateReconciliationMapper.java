package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductActiveStateReconciliationCandidate;
import com.nuono.next.product.ProductActiveStateReconciliationScope;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductActiveStateReconciliationMapper {

    String TASK_TYPE = "product.active-state-reconciliation";

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode,",
            "  COUNT(*) AS unknownCount,",
            "  SUM(CASE WHEN NOT EXISTS (",
            "    SELECT 1 FROM operational_task attempt",
            "    WHERE attempt.task_type = '" + TASK_TYPE + "'",
            "      AND attempt.is_deleted = b'0'",
            "      AND BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|siteOffer:', pso.id",
            "      )",
            "  ) THEN 1 ELSE 0 END) AS neverAttemptedCount,",
            "  MIN((",
            "    SELECT MAX(attempt.gmt_updated) FROM operational_task attempt",
            "    WHERE attempt.task_type = '" + TASK_TYPE + "'",
            "      AND attempt.is_deleted = b'0'",
            "      AND BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|siteOffer:', pso.id",
            "      )",
            "  )) AS oldestAttemptAt",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "WHERE ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "  AND UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')",
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(lss.site_enabled, b'1') = b'1'",
            "  AND pso.maintenance_enabled = b'1'",
            "  AND pso.is_active IS NULL",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL",
            "  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL",
            "  AND NOT EXISTS (",
            "    SELECT 1 FROM operational_task active_scope",
            "    WHERE active_scope.task_type = '" + TASK_TYPE + "'",
            "      AND active_scope.is_deleted = b'0'",
            "      AND active_scope.status IN ('QUEUED', 'RUNNING')",
            "      AND active_scope.owner_user_id = ls.owner_user_id",
            "      AND UPPER(active_scope.store_code) = UPPER(lss.store_code)",
            "      AND UPPER(active_scope.site_code) = UPPER(lss.site)",
            "  )",
            "GROUP BY ls.owner_user_id, lss.store_code, lss.site",
            "ORDER BY",
            "  CASE WHEN neverAttemptedCount > 0 THEN 0 ELSE 1 END ASC,",
            "  oldestAttemptAt ASC,",
            "  COUNT(*) DESC,",
            "  ls.owner_user_id ASC, lss.store_code ASC, lss.site ASC",
            "LIMIT #{limit}"
    })
    List<ProductActiveStateReconciliationScope> listUnknownScopes(@Param("limit") int limit);

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  pm.id AS productMasterId,",
            "  pv.id AS variantId,",
            "  pso.id AS siteOfferId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  pv.partner_sku AS partnerSku,",
            "  pso.psku_code AS pskuCode,",
            "  (",
            "    SELECT MAX(attempt.gmt_updated)",
            "    FROM operational_task attempt",
            "    WHERE attempt.task_type = '" + TASK_TYPE + "'",
            "      AND attempt.is_deleted = b'0'",
            "      AND BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|siteOffer:', pso.id",
            "      )",
            "  ) AS lastAttemptAt",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "  AND UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')",
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(lss.site_enabled, b'1') = b'1'",
            "  AND pso.maintenance_enabled = b'1'",
            "  AND pso.is_active IS NULL",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL",
            "  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM operational_task attempt",
            "    WHERE attempt.task_type = '" + TASK_TYPE + "'",
            "      AND attempt.is_deleted = b'0'",
            "      AND attempt.status IN ('QUEUED', 'RUNNING')",
            "      AND BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|siteOffer:', pso.id",
            "      )",
            "  )",
            "ORDER BY",
            "  CASE WHEN lastAttemptAt IS NULL THEN 0 ELSE 1 END ASC,",
            "  lastAttemptAt ASC,",
            "  pso.id ASC",
            "LIMIT #{limit}"
    })
    List<ProductActiveStateReconciliationCandidate> listUnknownCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("limit") int limit
    );

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id AND lss.logical_store_id = ls.id",
            "SET pso.is_active = #{isActive},",
            "    pso.active_state_source = #{source},",
            "    pso.active_state_synced_at = #{syncedAt},",
            "    pso.updated_by = #{ownerUserId},",
            "    pso.gmt_updated = #{syncedAt}",
            "WHERE pso.id = #{siteOfferId}",
            "  AND ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND BINARY pv.partner_sku = BINARY #{partnerSku}",
            "  AND pso.is_deleted = b'0'",
            "  AND pso.maintenance_enabled = b'1'",
            "  AND pso.is_active IS NULL"
    })
    int resolveUnknownActiveState(
            @Param("siteOfferId") Long siteOfferId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("partnerSku") String partnerSku,
            @Param("isActive") Boolean isActive,
            @Param("source") String source,
            @Param("syncedAt") LocalDateTime syncedAt
    );
}
