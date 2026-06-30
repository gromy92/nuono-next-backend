package com.nuono.next.infrastructure.mapper;

import com.nuono.next.outboundfee.OfficialOutboundFeeProductSpecRecord;
import com.nuono.next.outboundfee.OfficialOutboundFeeProductSpecSourceCommand;
import com.nuono.next.outboundfee.OfficialOutboundFeeProductSpecSourceView;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OfficialOutboundFeeProductMapper {

    @Select({
            "SELECT COALESCE(MAX(id), 120000) + 1",
            "FROM product_variant_spec_source"
    })
    Long nextProductVariantSpecSourceId();

    @Select({
            "SELECT pv.id",
            "FROM product_variant pv",
            "JOIN product_master pm",
            "  ON pm.id = pv.product_master_id",
            " AND pm.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = pm.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "WHERE pv.is_deleted = 0",
            "  AND (",
            "    BINARY pv.child_sku = BINARY #{skuId}",
            "    OR BINARY pv.partner_sku = BINARY #{skuId}",
            "    OR (#{variantIdCandidate} IS NOT NULL AND pv.id = #{variantIdCandidate})",
            "  )",
            "ORDER BY",
            "  CASE WHEN BINARY pv.child_sku = BINARY #{skuId} THEN 0 ELSE 1 END,",
            "  CASE WHEN BINARY pv.partner_sku = BINARY #{skuId} THEN 0 ELSE 1 END,",
            "  pv.id",
            "LIMIT 1"
    })
    Long selectProductVariantIdForOfficialOutboundFee(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuId") String skuId,
            @Param("variantIdCandidate") Long variantIdCandidate
    );

    @Select({
            "SELECT COALESCE(pso.sale_price, pso.price)",
            "FROM product_site_offer pso",
            "JOIN logical_store_site lss",
            "  ON lss.id = pso.site_id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND UPPER(lss.site) = UPPER(#{site})",
            " AND lss.is_deleted = 0",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = 0",
            "WHERE pso.variant_id = #{variantId}",
            "  AND pso.is_deleted = 0",
            "ORDER BY pso.id DESC",
            "LIMIT 1"
    })
    BigDecimal selectProductSiteSalePriceForOfficialOutboundFee(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId,
            @Param("site") String site
    );

    @Select({
            "SELECT",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  pm.title_cache AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    OfficialOutboundFeeProductSpecRecord selectProductVariantIdentityForOfficialOutboundFee(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId
    );

    @Select({
            "SELECT",
            "  pvs.id AS spec_id,",
            "  pvs.effective_source_id,",
            "  pvs.effective_source_type,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  pm.title_cache AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.id AS variant_id,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar,",
            "  pvss.product_length_cm,",
            "  pvss.product_width_cm,",
            "  pvss.product_height_cm,",
            "  pvss.product_weight_g,",
            "  pvss.carton_length_cm,",
            "  pvss.carton_width_cm,",
            "  pvss.carton_height_cm,",
            "  pvss.carton_weight_kg,",
            "  pvss.carton_quantity,",
            "  pvss.carton_source_type,",
            "  pvss.battery_magnetic_type,",
            "  pvss.liquid_powder_type,",
            "  pvss.source_type,",
            "  pvss.confirmed_at,",
            "  pvss.confirmed_by",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "JOIN product_variant_spec pvs",
            "  ON pvs.variant_id = pv.id",
            " AND pvs.is_deleted = 0",
            "JOIN product_variant_spec_source pvss",
            "  ON pvss.id = pvs.effective_source_id",
            " AND pvss.variant_id = pv.id",
            " AND pvss.is_deleted = 0",
            " AND pvss.source_type IN ('ali1688', 'warehouse')",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    OfficialOutboundFeeProductSpecRecord selectProductVariantEffectiveSpecForOfficialOutboundFee(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId
    );

    @Select({
            "<script>",
            "SELECT",
            "  pv.id AS variant_id,",
            "  lss.store_code,",
            "  pm.sku_parent,",
            "  pm.title_cache AS title,",
            "  pm.cover_image_url AS image_url,",
            "  pv.partner_sku,",
            "  pv.child_sku,",
            "  pv.size_en,",
            "  pv.size_ar",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (pv.partner_sku IS NOT NULL OR pv.child_sku IS NOT NULL)",
            "  AND (#{keyword} IS NULL OR #{keyword} = ''",
            "       OR pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pv.child_sku LIKE CONCAT('%', #{keyword}, '%')",
            "       OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%'))",
            "<if test='skuIds != null and skuIds.size() &gt; 0'>",
            "  AND (",
            "    pv.child_sku IN",
            "    <foreach collection='skuIds' item='skuId' open='(' separator=',' close=')'>#{skuId}</foreach>",
            "    OR pv.partner_sku IN",
            "    <foreach collection='skuIds' item='skuId' open='(' separator=',' close=')'>#{skuId}</foreach>",
            "    OR CAST(pv.id AS CHAR) IN",
            "    <foreach collection='skuIds' item='skuId' open='(' separator=',' close=')'>#{skuId}</foreach>",
            "  )",
            "</if>",
            "<if test='includeTestSkus == null or includeTestSkus == false'>",
            "  AND LOWER(COALESCE(pv.partner_sku, '')) NOT LIKE '%test%'",
            "  AND LOWER(COALESCE(pv.child_sku, '')) NOT LIKE '%test%'",
            "  AND COALESCE(pv.partner_sku, '') NOT LIKE 'PSKU\\_%' ESCAPE '\\\\'",
            "</if>",
            "ORDER BY pm.sku_parent, COALESCE(pv.variant_ix, 999999), pv.partner_sku",
            "<if test='limit != null and limit &gt; 0'>LIMIT #{limit}</if>",
            "</script>"
    })
    List<OfficialOutboundFeeProductSpecRecord> selectNoonOfficialSpecSyncCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword,
            @Param("skuIds") List<String> skuIds,
            @Param("includeTestSkus") Boolean includeTestSkus,
            @Param("limit") Integer limit
    );

    @Insert({
            "INSERT INTO product_variant_spec_source (",
            "  id, variant_id, source_type,",
            "  product_length_cm, product_width_cm, product_height_cm, product_weight_g,",
            "  carton_length_cm, carton_width_cm, carton_height_cm, carton_weight_kg, carton_quantity,",
            "  carton_source_type, battery_magnetic_type, liquid_powder_type, source_recorded_at,",
            "  confirmed_at, confirmed_by, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{variantId}, #{sourceType},",
            "  #{productLengthCm}, #{productWidthCm}, #{productHeightCm}, #{productWeightG},",
            "  #{cartonLengthCm}, #{cartonWidthCm}, #{cartonHeightCm}, #{cartonWeightKg}, #{cartonQuantity},",
            "  COALESCE(#{cartonSourceType}, 'none'), COALESCE(#{batteryMagneticType}, 'unknown'), COALESCE(#{liquidPowderType}, 'unknown'), COALESCE(#{sourceRecordedAt}, NOW()),",
            "  NOW(), #{operatorUserId}, 0, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  product_length_cm = VALUES(product_length_cm),",
            "  product_width_cm = VALUES(product_width_cm),",
            "  product_height_cm = VALUES(product_height_cm),",
            "  product_weight_g = VALUES(product_weight_g),",
            "  source_recorded_at = VALUES(source_recorded_at),",
            "  confirmed_at = VALUES(confirmed_at),",
            "  confirmed_by = VALUES(confirmed_by),",
            "  is_deleted = 0,",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertNoonOfficialProductSpecSource(OfficialOutboundFeeProductSpecSourceCommand command);

    @Select({
            "SELECT",
            "  pvss.id AS source_id,",
            "  pvss.variant_id,",
            "  pvss.source_type,",
            "  pvss.product_length_cm,",
            "  pvss.product_width_cm,",
            "  pvss.product_height_cm,",
            "  pvss.product_weight_g,",
            "  pvss.source_recorded_at,",
            "  pvss.confirmed_at,",
            "  pvss.confirmed_by",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND BINARY lss.store_code = BINARY #{storeCode}",
            " AND lss.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = 0",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.id = #{variantId}",
            " AND pv.is_deleted = 0",
            "JOIN product_variant_spec_source pvss",
            "  ON pvss.variant_id = pv.id",
            " AND pvss.source_type = #{sourceType}",
            " AND pvss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "LIMIT 1"
    })
    OfficialOutboundFeeProductSpecSourceView selectProductVariantSpecSourceForOfficialOutboundFee(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("variantId") Long variantId,
            @Param("sourceType") String sourceType
    );
}
