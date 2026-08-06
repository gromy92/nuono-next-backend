package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.datapull.Dp05TaskFenceRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** DP-05 cursor and fact-apply fence persistence Adapter. */
public interface Dp05RuntimeMapper {

    @Select({
            "SELECT ls.owner_user_id AS ownerUserId, ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode, UPPER(lss.site) AS siteCode,",
            "  pm.id AS productMasterId, pv.id AS productVariantId,",
            "  pso.id AS productSiteOfferId, pv.partner_sku AS partnerSku,",
            "  pm.sku_parent AS skuParent, pm.sku_parent AS noonProductCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.id = #{logicalStoreId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND pso.id > #{afterOfferId}",
            "  AND ls.is_deleted = b'0' AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0' AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            "  AND UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')",
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(lss.site_enabled, b'1') = b'1'",
            "  AND COALESCE(pso.is_active, b'0') = b'1'",
            "  AND (NULLIF(TRIM(pm.sku_parent), '') IS NULL OR NOT EXISTS (",
            "      SELECT 1 FROM product_site_offer earlier_pso",
            "      JOIN product_variant earlier_pv ON earlier_pv.id = earlier_pso.variant_id",
            "      JOIN product_master earlier_pm ON earlier_pm.id = earlier_pv.product_master_id",
            "      WHERE earlier_pso.site_id = pso.site_id",
            "        AND earlier_pm.logical_store_id = pm.logical_store_id",
            "        AND earlier_pso.id < pso.id",
            "        AND earlier_pso.is_deleted = b'0'",
            "        AND earlier_pv.is_deleted = b'0'",
            "        AND earlier_pm.is_deleted = b'0'",
            "        AND COALESCE(earlier_pso.is_active, b'0') = b'1'",
            "        AND UPPER(TRIM(earlier_pm.sku_parent)) = UPPER(TRIM(pm.sku_parent))",
            "  ))",
            "ORDER BY pso.id ASC LIMIT 1"
    })
    ProductPublicDetailCandidate selectCandidateAfter(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("afterOfferId") long afterOfferId
    );

    @Select({
            "SELECT id AS taskId, operation_code AS operationCode, state,",
            "  fence_epoch AS fenceEpoch, lease_owner AS leaseOwner,",
            "  (lease_until IS NOT NULL AND lease_until > UTC_TIMESTAMP(3)) AS leaseValid",
            "FROM dp_pull_task WHERE id = #{taskId} FOR UPDATE"
    })
    Dp05TaskFenceRow selectTaskFenceForUpdate(@Param("taskId") long taskId);

    @Select({
            "SELECT COUNT(*) FROM dp_pull_task",
            "WHERE id = #{taskId} AND operation_code = 'DP05' AND state = 'RUNNING'",
            "  AND fence_epoch = #{fenceEpoch}",
            "  AND BINARY lease_owner = BINARY #{leaseOwner}",
            "  AND lease_until > UTC_TIMESTAMP(3)"
    })
    int countLiveTaskFence(
            @Param("taskId") long taskId,
            @Param("fenceEpoch") long fenceEpoch,
            @Param("leaseOwner") String leaseOwner
    );
}
