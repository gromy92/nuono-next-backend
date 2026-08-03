package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProductForwarderEligibilityScopeAnchorRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductForwarderEligibilityLockMapper {

    @Insert({
            "<script>",
            "INSERT INTO product_forwarder_eligibility_scope_anchor (",
            "  owner_user_id, logical_store_id, partner_sku_normalized, gmt_create, gmt_updated",
            ") VALUES",
            "<foreach collection='scopes' item='scope' separator=','>",
            "  (#{scope.ownerUserId}, #{scope.logicalStoreId}, #{scope.partnerSkuNormalized}, NOW(), NOW())",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE gmt_updated = gmt_updated",
            "</script>"
    })
    int ensureProductForwarderEligibilityScopeAnchors(
            @Param("scopes") List<ProductForwarderEligibilityScopeAnchorRecord> scopes
    );

    @Select({
            "<script>",
            "SELECT owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       partner_sku_normalized AS partnerSkuNormalized",
            "FROM product_forwarder_eligibility_scope_anchor",
            "WHERE",
            "<foreach collection='scopes' item='scope' open='(' separator=' OR ' close=')'>",
            "  (owner_user_id = #{scope.ownerUserId}",
            "   AND logical_store_id = #{scope.logicalStoreId}",
            "   AND partner_sku_normalized = #{scope.partnerSkuNormalized})",
            "</foreach>",
            "ORDER BY owner_user_id ASC, logical_store_id ASC, partner_sku_normalized ASC",
            "FOR UPDATE",
            "</script>"
    })
    List<ProductForwarderEligibilityScopeAnchorRecord> lockProductForwarderEligibilityScopeAnchors(
            @Param("scopes") List<ProductForwarderEligibilityScopeAnchorRecord> scopes
    );
}
