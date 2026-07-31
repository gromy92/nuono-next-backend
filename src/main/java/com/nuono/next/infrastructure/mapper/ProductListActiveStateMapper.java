package com.nuono.next.infrastructure.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductListActiveStateMapper {

    @Update({
            "<script>",
            "UPDATE product_site_offer pso",
            "JOIN product_variant pv",
            "  ON pv.id = pso.variant_id",
            " AND pv.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.is_deleted = b'0'",
            "JOIN logical_store ls",
            "  ON ls.id = pm.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "SET pso.is_active = b'0',",
            "    pso.active_state_source = #{activeStateSource},",
            "    pso.active_state_synced_at = #{activeStateSyncedAt},",
            "    pso.updated_by = #{updatedBy},",
            "    pso.gmt_updated = NOW()",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = UPPER(#{storeCode})",
            "  AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  AND pso.is_deleted = b'0'",
            "  AND pso.maintenance_enabled = b'1'",
            "  AND NULLIF(TRIM(pv.partner_sku), '') IS NOT NULL",
            "  AND UPPER(TRIM(pv.partner_sku)) NOT IN",
            "  <foreach collection='presentPartnerSkus' item='partnerSku' open='(' separator=',' close=')'>",
            "    #{partnerSku}",
            "  </foreach>",
            "</script>"
    })
    int markProductOffersMissingFromCompleteListInactive(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("presentPartnerSkus") List<String> presentPartnerSkus,
            @Param("activeStateSource") String activeStateSource,
            @Param("activeStateSyncedAt") LocalDateTime activeStateSyncedAt,
            @Param("updatedBy") Long updatedBy
    );
}
