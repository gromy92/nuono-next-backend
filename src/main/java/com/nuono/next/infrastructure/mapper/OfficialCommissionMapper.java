package com.nuono.next.infrastructure.mapper;

import com.nuono.next.commission.OfficialCommissionCalculationView;
import com.nuono.next.commission.OfficialCommissionProductContext;
import com.nuono.next.commission.OfficialCommissionRule;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface OfficialCommissionMapper {
    @Insert({
            "INSERT INTO official_commission_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    default Long nextCalculationFactId() {
        IdSequenceCommand command = new IdSequenceCommand("commission_calculation_fact", 740000L);
        nextId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Official commission calculation fact ID allocation failed.");
        }
        return id;
    }

    @Select({
            "SELECT",
            "  pv.id AS variantId,",
            "  CASE",
            "    WHEN BINARY pv.partner_sku = BINARY #{skuId} THEN pv.partner_sku",
            "    WHEN BINARY pv.child_sku = BINARY #{skuId} THEN pv.child_sku",
            "    ELSE COALESCE(NULLIF(pv.partner_sku, ''), NULLIF(pv.child_sku, ''), #{skuId})",
            "  END AS skuId,",
            "  pv.partner_sku AS partnerSku,",
            "  pv.child_sku AS childSku,",
            "  pm.sku_parent AS skuParent,",
            "  lss.site AS site,",
            "  pm.brand_cache AS brand,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  COALESCE(pso.final_price, pso.sale_price, pso.price, pso.price_min, pso.price_max) AS storedSalePrice,",
            "  CASE",
            "    WHEN UPPER(lss.site) IN ('SA', 'KSA') THEN 'SAR'",
            "    WHEN UPPER(lss.site) IN ('AE', 'UAE') THEN 'AED'",
            "    ELSE NULL",
            "  END AS marketCurrency",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.site_id = lss.id",
            " AND pso.variant_id = pv.id",
            " AND pso.is_deleted = b'0'",
            "WHERE lss.store_code = #{storeCode}",
            "  AND UPPER(TRIM(lss.site)) = UPPER(TRIM(#{site}))",
            "  AND lss.is_deleted = b'0'",
            "  AND (",
            "    BINARY pv.partner_sku = BINARY #{skuId}",
            "    OR BINARY pv.child_sku = BINARY #{skuId}",
            "    OR BINARY pm.sku_parent = BINARY #{skuId}",
            "    OR BINARY pso.psku_code = BINARY #{skuId}",
            "    OR BINARY pso.offer_code = BINARY #{skuId}",
            "  )",
            "ORDER BY CASE WHEN BINARY pv.partner_sku = BINARY #{skuId} THEN 0 WHEN BINARY pv.child_sku = BINARY #{skuId} THEN 1 ELSE 2 END, pv.id ASC",
            "LIMIT 1"
    })
    OfficialCommissionProductContext selectProductContext(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("skuId") String skuId
    );

    @Select({
            "SELECT",
            "  vi.natural_key AS naturalKey,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country')) AS country,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform')) AS platform,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType')) AS fulfillmentType,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.parentCategoryName')) AS parentCategoryName,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.categoryName')) AS categoryName,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.categoryPath')) AS categoryPath,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.brandRestriction')) AS brandRestriction,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountRangeLabel')) AS amountRangeLabel,",
            "  CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMin')), ''), 'null') AS DECIMAL(18,6)) AS amountMin,",
            "  CASE",
            "    WHEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMinInclusive'))) IN ('true', '1', 'yes') THEN TRUE",
            "    WHEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMinInclusive'))) IN ('false', '0', 'no') THEN FALSE",
            "    ELSE NULL",
            "  END AS amountMinInclusive,",
            "  CAST(NULLIF(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMax')), ''), 'null') AS DECIMAL(18,6)) AS amountMax,",
            "  CASE",
            "    WHEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMaxInclusive'))) IN ('true', '1', 'yes') THEN TRUE",
            "    WHEN LOWER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountMaxInclusive'))) IN ('false', '0', 'no') THEN FALSE",
            "    ELSE NULL",
            "  END AS amountMaxInclusive,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.amountCurrency')) AS amountCurrency,",
            "  JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.commissionRate')) AS commissionRate,",
            "  STR_TO_DATE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), ''), '%Y-%m-%d') AS effectiveDate,",
            "  vi.version_id AS sourceVersionId,",
            "  vi.id AS sourceVersionItemId",
            "FROM file_mgmt_parse_active_version av",
            "JOIN file_mgmt_parse_version v",
            "  ON v.id = av.version_id",
            " AND v.is_deleted = b'0'",
            " AND v.version_status = 'active'",
            "JOIN file_mgmt_parse_version_item vi",
            "  ON vi.version_id = av.version_id",
            " AND vi.target_plan_id = av.target_plan_id",
            " AND vi.is_deleted = b'0'",
            "WHERE av.is_deleted = b'0'",
            "  AND vi.item_type = 'commission_rule'",
            "  AND (",
            "    UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) = UPPER(#{country})",
            "    OR (UPPER(#{country}) IN ('KSA', 'SA') AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) IN ('KSA', 'SA'))",
            "    OR (UPPER(#{country}) IN ('UAE', 'AE') AND UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.country'))) IN ('UAE', 'AE'))",
            "  )",
            "  AND (NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform'))), '') IS NULL",
            "       OR UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.platform'))) = UPPER(#{platform}))",
            "  AND (NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType'))), '') IS NULL",
            "       OR UPPER(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.fulfillmentType'))) = UPPER(#{fulfillmentType}))",
            "  AND (#{calculationDate} IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') IS NULL",
            "       OR NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), '') <= DATE_FORMAT(#{calculationDate}, '%Y-%m-%d'))",
            "ORDER BY COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(vi.version_payload_json, '$.effectiveDate')), ''), '1970-01-01') DESC, vi.id ASC"
    })
    List<OfficialCommissionRule> selectActiveCommissionRules(
            @Param("country") String country,
            @Param("platform") String platform,
            @Param("fulfillmentType") String fulfillmentType,
            @Param("calculationDate") LocalDate calculationDate
    );

    @Insert({
            "INSERT INTO official_commission_calculation_fact (",
            "  id, owner_user_id, store_code, site, country, platform, fulfillment_type, variant_id,",
            "  sku_id, partner_sku, child_sku, brand, product_fulltype, sale_price, market_currency, calculation_date,",
            "  category_path, category_name, brand_restriction, amount_range_label, amount_min, amount_max, amount_currency,",
            "  commission_rate, commission_amount, currency, tax_multiplier, tax_included_commission_amount,",
            "  matched_rule_natural_key, source_version_id, evidence_json, status, failure_code, message,",
            "  calculated_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.ownerUserId}, #{fact.storeCode}, #{fact.site}, #{fact.country}, #{fact.platform}, #{fact.fulfillmentType}, #{fact.variantId},",
            "  #{fact.skuId}, #{fact.partnerSku}, #{fact.childSku}, #{fact.brand}, #{fact.productFulltype}, #{fact.salePrice}, #{fact.marketCurrency}, #{calculationDate},",
            "  #{fact.categoryPath}, #{fact.categoryName}, #{fact.brandRestriction}, #{fact.amountRangeLabel}, #{fact.amountMin}, #{fact.amountMax}, #{fact.amountCurrency},",
            "  #{fact.commissionRate}, #{fact.commissionAmount}, #{fact.currency}, #{fact.taxMultiplier}, #{fact.taxIncludedCommissionAmount},",
            "  #{fact.matchedRuleNaturalKey}, #{fact.sourceVersionId}, #{fact.evidenceJson}, #{fact.status}, #{fact.failureCode}, #{fact.message},",
            "  NOW(), #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ")"
    })
    int insertCalculationFact(
            @Param("id") Long id,
            @Param("fact") OfficialCommissionCalculationView fact,
            @Param("calculationDate") LocalDate calculationDate,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "<script>",
            "SELECT",
            "  latest.id AS calculationFactId,",
            "  latest.owner_user_id AS ownerUserId,",
            "  latest.store_code AS storeCode,",
            "  COALESCE(latest.sku_id, sku_match.skuId) AS skuId,",
            "  latest.variant_id AS variantId,",
            "  latest.partner_sku AS partnerSku,",
            "  latest.child_sku AS childSku,",
            "  latest.site,",
            "  latest.country,",
            "  latest.platform,",
            "  latest.fulfillment_type AS fulfillmentType,",
            "  latest.sale_price AS salePrice,",
            "  latest.market_currency AS marketCurrency,",
            "  latest.brand,",
            "  latest.product_fulltype AS productFulltype,",
            "  latest.category_path AS categoryPath,",
            "  latest.category_name AS categoryName,",
            "  latest.brand_restriction AS brandRestriction,",
            "  latest.amount_range_label AS amountRangeLabel,",
            "  latest.amount_min AS amountMin,",
            "  latest.amount_max AS amountMax,",
            "  latest.amount_currency AS amountCurrency,",
            "  latest.commission_rate AS commissionRate,",
            "  latest.commission_amount AS commissionAmount,",
            "  latest.currency,",
            "  latest.tax_multiplier AS taxMultiplier,",
            "  latest.tax_included_commission_amount AS taxIncludedCommissionAmount,",
            "  latest.matched_rule_natural_key AS matchedRuleNaturalKey,",
            "  latest.source_version_id AS sourceVersionId,",
            "  latest.status,",
            "  latest.failure_code AS failureCode,",
            "  latest.message",
            "FROM (",
            "  SELECT DISTINCT",
            "    pv.id AS variantId,",
            "    CASE WHEN BINARY pv.partner_sku = BINARY requested.skuId THEN pv.partner_sku ELSE COALESCE(NULLIF(pv.child_sku, ''), requested.skuId) END AS skuId",
            "  FROM (",
            "    <foreach collection='skuIds' item='skuId' separator=' UNION ALL '>",
            "      SELECT #{skuId} AS skuId",
            "    </foreach>",
            "  ) requested",
            "  JOIN product_variant pv",
            "    ON pv.is_deleted = b'0'",
            "   AND (BINARY pv.partner_sku = BINARY requested.skuId OR BINARY pv.child_sku = BINARY requested.skuId)",
            "  JOIN product_master pm",
            "    ON pm.id = pv.product_master_id",
            "   AND pm.is_deleted = b'0'",
            "  JOIN logical_store ls",
            "    ON ls.id = pm.logical_store_id",
            "   AND ls.owner_user_id = #{ownerUserId}",
            "   AND ls.is_deleted = b'0'",
            "  JOIN logical_store_site lss",
            "    ON lss.logical_store_id = ls.id",
            "   AND lss.store_code = #{storeCode}",
            "   AND lss.site = #{site}",
            "   AND lss.is_deleted = b'0'",
            ") sku_match",
            "JOIN official_commission_calculation_fact latest",
            "  ON latest.variant_id = sku_match.variantId",
            " AND latest.owner_user_id = #{ownerUserId}",
            " AND latest.store_code = #{storeCode}",
            " AND latest.site = #{site}",
            "JOIN (",
            "  SELECT variant_id, MAX(id) AS latestId",
            "  FROM official_commission_calculation_fact",
            "  WHERE owner_user_id = #{ownerUserId}",
            "    AND store_code = #{storeCode}",
            "    AND site = #{site}",
            "  GROUP BY variant_id",
            ") marker",
            "  ON marker.variant_id = latest.variant_id",
            " AND marker.latestId = latest.id",
            "ORDER BY sku_match.skuId ASC",
            "</script>"
    })
    List<OfficialCommissionCalculationView> selectLatestCalculationViewsBySkuIds(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("skuIds") List<String> skuIds
    );
}
