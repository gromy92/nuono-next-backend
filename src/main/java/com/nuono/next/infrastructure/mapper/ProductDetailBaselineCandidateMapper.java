package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductDetailBaselineCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductDetailBaselineCandidateMapper {

    @Select({
            "SELECT",
            "  pm.id AS productMasterId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  MIN(NULLIF(TRIM(pv.partner_sku), '')) AS partnerSku,",
            "  MIN(NULLIF(TRIM(pso.psku_code), '')) AS pskuCode,",
            "  MAX(attempt.gmt_updated) AS lastAttemptAt",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "LEFT JOIN operational_task attempt",
            "  ON attempt.task_type = 'product.detail-baseline-backfill'",
            " AND attempt.is_deleted = b'0'",
            " AND (",
            "      BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|logicalStore:', ls.id, '|skuParent:', TRIM(pm.sku_parent)",
            "      )",
            "   OR BINARY attempt.natural_key = BINARY CONCAT(",
            "          'owner:', ls.owner_user_id, '|store:', TRIM(lss.store_code), '|skuParent:', TRIM(pm.sku_parent)",
            "      )",
            " )",
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
            "  AND COALESCE(pso.maintenance_enabled, b'1') = b'1'",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL",
            "  AND NOT EXISTS (",
            "      SELECT 1",
            "      FROM product_master_snapshot baseline",
            "      WHERE baseline.product_master_id = pm.id",
            "        AND baseline.snapshot_type = 'baseline'",
            "        AND baseline.is_deleted = b'0'",
            "  )",
            "GROUP BY pm.id, ls.id, lss.store_code, lss.site, pm.sku_parent",
            "HAVING SUM(CASE WHEN attempt.status IN ('QUEUED', 'RUNNING') THEN 1 ELSE 0 END) = 0",
            "ORDER BY",
            "  CASE WHEN MAX(attempt.gmt_updated) IS NULL THEN 0 ELSE 1 END ASC,",
            "  MAX(attempt.gmt_updated) ASC,",
            "  pm.id ASC"
    })
    List<ProductDetailBaselineCandidate> listMissingMaintainedCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );
}
