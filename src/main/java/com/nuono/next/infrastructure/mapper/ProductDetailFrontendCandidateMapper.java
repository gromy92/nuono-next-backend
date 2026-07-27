package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductDetailFrontendCandidateMapper {

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode,",
            "  pm.id AS productMasterId,",
            "  pv.id AS productVariantId,",
            "  pso.id AS productSiteOfferId,",
            "  pv.partner_sku AS partnerSku,",
            "  pm.sku_parent AS skuParent,",
            "  pm.sku_parent AS noonProductCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND (",
            "       BINARY pm.sku_parent = BINARY #{skuParent}",
            "    OR (#{partnerSku} IS NOT NULL AND BINARY pv.partner_sku = BINARY #{partnerSku})",
            "    OR (#{pskuCode} IS NOT NULL AND BINARY pso.psku_code = BINARY #{pskuCode})",
            "  )",
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
            "ORDER BY",
            "  CASE WHEN BINARY pm.sku_parent = BINARY #{skuParent} THEN 0 ELSE 1 END,",
            "  CASE WHEN #{partnerSku} IS NOT NULL AND BINARY pv.partner_sku = BINARY #{partnerSku} THEN 0 ELSE 1 END,",
            "  CASE WHEN COALESCE(pso.is_active, b'0') = b'1' THEN 0 ELSE 1 END,",
            "  pv.id ASC",
            "LIMIT 1"
    })
    ProductPublicDetailCandidate selectCandidateByProductIdentity(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent,
            @Param("partnerSku") String partnerSku,
            @Param("pskuCode") String pskuCode
    );
}
