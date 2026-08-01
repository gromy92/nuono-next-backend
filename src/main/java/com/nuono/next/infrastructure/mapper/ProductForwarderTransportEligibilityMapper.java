package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurementorder.ProductForwarderEligibilityScopeAnchorRecord;
import com.nuono.next.procurementorder.ProductForwarderTransportEligibilityRecord;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductForwarderTransportEligibilityMapper {

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "       eligibility_status AS eligibilityStatus, effective_from AS effectiveFrom,",
            "       effective_to AS effectiveTo, version,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM product_forwarder_transport_eligibility",
            "WHERE",
            "  <foreach collection='scopes' item='scope' open='(' separator=' OR ' close=')'>",
            "    (owner_user_id = #{scope.ownerUserId}",
            "     AND logical_store_id = #{scope.logicalStoreId}",
            "     AND partner_sku = #{scope.partnerSkuNormalized})",
            "  </foreach>",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY owner_user_id ASC, logical_store_id ASC, partner_sku ASC,",
            "         site_code ASC, forwarder_code ASC, transport_mode ASC, version DESC, id DESC",
            "</script>"
    })
    List<ProductForwarderTransportEligibilityRecord> listCurrentProductForwarderTransportEligibilities(
            @Param("scopes") List<ProductForwarderEligibilityScopeAnchorRecord> scopes
    );

    @Select({
            "<script>",
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "       eligibility_status AS eligibilityStatus, effective_from AS effectiveFrom,",
            "       effective_to AS effectiveTo, version,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i') AS createdAt,",
            "       DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i') AS updatedAt",
            "FROM product_forwarder_transport_eligibility",
            "WHERE",
            "  <foreach collection='scopes' item='scope' open='(' separator=' OR ' close=')'>",
            "    (owner_user_id = #{scope.ownerUserId}",
            "     AND logical_store_id = #{scope.logicalStoreId}",
            "     AND partner_sku = #{scope.partnerSkuNormalized})",
            "  </foreach>",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY owner_user_id ASC, logical_store_id ASC, partner_sku ASC,",
            "         site_code ASC, forwarder_code ASC, transport_mode ASC, version DESC, id DESC",
            "FOR UPDATE",
            "</script>"
    })
    List<ProductForwarderTransportEligibilityRecord> listCurrentProductForwarderTransportEligibilitiesForUpdate(
            @Param("scopes") List<ProductForwarderEligibilityScopeAnchorRecord> scopes
    );

    @Select({
            "SELECT id, owner_user_id AS ownerUserId, product_master_id AS productMasterId,",
            "       product_variant_id AS productVariantId, logical_store_id AS logicalStoreId,",
            "       source_store_code AS sourceStoreCode, partner_sku AS partnerSku,",
            "       site_code AS siteCode, forwarder_code AS forwarderCode, transport_mode AS transportMode,",
            "       eligibility_status AS eligibilityStatus, effective_from AS effectiveFrom,",
            "       effective_to AS effectiveTo, version",
            "FROM product_forwarder_transport_eligibility",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSkuNormalized}",
            "  AND site_code = #{siteCode}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND transport_mode = #{transportMode}",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'",
            "ORDER BY version DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    ProductForwarderTransportEligibilityRecord selectActiveProductForwarderTransportEligibilityForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSkuNormalized") String partnerSkuNormalized,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Select({
            "SELECT version FROM product_forwarder_transport_eligibility",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND logical_store_id = #{logicalStoreId}",
            "  AND partner_sku = #{partnerSkuNormalized}",
            "  AND site_code = #{siteCode}",
            "  AND forwarder_code = #{forwarderCode}",
            "  AND transport_mode = #{transportMode}",
            "ORDER BY version DESC, id DESC",
            "LIMIT 1 FOR UPDATE"
    })
    Integer selectLatestProductForwarderTransportEligibilityVersionForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("partnerSkuNormalized") String partnerSkuNormalized,
            @Param("siteCode") String siteCode,
            @Param("forwarderCode") String forwarderCode,
            @Param("transportMode") String transportMode
    );

    @Update({
            "UPDATE product_forwarder_transport_eligibility",
            "SET effective_to = #{effectiveTo}, updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND version = #{version}",
            "  AND effective_to IS NULL",
            "  AND is_deleted = b'0'"
    })
    int closeProductForwarderTransportEligibility(
            @Param("id") Long id,
            @Param("version") Integer version,
            @Param("effectiveTo") LocalDate effectiveTo,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO product_forwarder_transport_eligibility (",
            "id, owner_user_id, product_master_id, product_variant_id, logical_store_id,",
            "source_store_code, partner_sku, site_code, forwarder_code, transport_mode,",
            "eligibility_status, effective_from, effective_to, version,",
            "is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.productMasterId}, #{row.productVariantId}, #{row.logicalStoreId},",
            "#{row.sourceStoreCode}, #{row.partnerSku}, #{row.siteCode}, #{row.forwarderCode}, #{row.transportMode},",
            "#{row.eligibilityStatus}, #{row.effectiveFrom}, NULL, #{row.version},",
            "b'0', #{operatorUserId}, #{operatorUserId}, NOW(), NOW())"
    })
    int insertProductForwarderTransportEligibility(
            @Param("row") ProductForwarderTransportEligibilityRecord row,
            @Param("operatorUserId") Long operatorUserId
    );
}
