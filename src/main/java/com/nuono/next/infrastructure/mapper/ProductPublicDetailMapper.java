package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailScope;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductPublicDetailMapper {
    String SYNCABLE_SITE_STATUS_CONDITION =
            "  AND UPPER(COALESCE(lss.site_status, 'ACTIVE')) IN ('ACTIVE', 'LOCAL_READY')";

    @Insert({
            "INSERT INTO product_public_detail_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateProductPublicDetailId(IdSequenceCommand command);

    default Long nextSnapshotId() {
        IdSequenceCommand command = new IdSequenceCommand("product_public_detail_snapshot", 300000L);
        allocateProductPublicDetailId(command);
        return command.getAllocatedId();
    }

    @Select({
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = #{storeCode}",
            "  AND UPPER(lss.site) = #{siteCode}",
            "  AND ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            SYNCABLE_SITE_STATUS_CONDITION,
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "LIMIT 1"
    })
    ProductPublicDetailScope selectActiveScope(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "<script>",
            "SELECT",
            "  ls.owner_user_id AS ownerUserId,",
            "  ls.id AS logicalStoreId,",
            "  lss.store_code AS storeCode,",
            "  UPPER(lss.site) AS siteCode",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "LEFT JOIN product_public_detail_snapshot latest",
            "  ON latest.product_master_id = pm.id",
            " AND latest.product_variant_id = pv.id",
            " AND UPPER(latest.site_code) = UPPER(lss.site)",
            " AND latest.source_platform = 'NOON'",
            " AND latest.is_latest = b'1'",
            " AND latest.is_deleted = b'0'",
            "WHERE ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            SYNCABLE_SITE_STATUS_CONDITION,
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(pso.is_active, b'0') = b'1'",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL",
            "  AND (latest.id IS NULL OR latest.fetched_at &lt; DATE_SUB(NOW(), INTERVAL #{staleDays} DAY))",
            "  AND NOT EXISTS (",
            "      SELECT 1 FROM product_public_detail_snapshot fail",
            "      WHERE fail.product_master_id = pm.id",
            "        AND fail.product_variant_id = pv.id",
            "        AND UPPER(fail.site_code) = UPPER(lss.site)",
            "        AND fail.source_platform = 'NOON'",
            "        AND fail.sync_status IN ('FAILED', 'NOT_FOUND')",
            "        AND fail.fetched_at &gt;= DATE_SUB(NOW(), INTERVAL #{failureCooldownHours} HOUR)",
            "        AND fail.is_deleted = b'0'",
            "  )",
            "GROUP BY ls.owner_user_id, ls.id, lss.store_code, lss.site",
            "ORDER BY MIN(COALESCE(latest.fetched_at, '1970-01-01')) ASC, lss.store_code ASC, lss.site ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductPublicDetailScope> listDueScopes(
            @Param("limit") int limit,
            @Param("staleDays") int staleDays,
            @Param("failureCooldownHours") int failureCooldownHours
    );

    @Select({
            "<script>",
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
            "LEFT JOIN product_public_detail_snapshot latest",
            "  ON latest.product_master_id = pm.id",
            " AND latest.product_variant_id = pv.id",
            " AND UPPER(latest.site_code) = UPPER(lss.site)",
            " AND latest.source_platform = 'NOON'",
            " AND latest.is_latest = b'1'",
            " AND latest.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = #{storeCode}",
            "  AND UPPER(lss.site) = #{siteCode}",
            "  AND ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            SYNCABLE_SITE_STATUS_CONDITION,
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(pso.is_active, b'0') = b'1'",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL",
            "  <if test='onlyDue'>",
            "  AND (latest.id IS NULL OR latest.fetched_at &lt; DATE_SUB(NOW(), INTERVAL #{staleDays} DAY))",
            "  AND NOT EXISTS (",
            "      SELECT 1 FROM product_public_detail_snapshot fail",
            "      WHERE fail.product_master_id = pm.id",
            "        AND fail.product_variant_id = pv.id",
            "        AND UPPER(fail.site_code) = UPPER(lss.site)",
            "        AND fail.source_platform = 'NOON'",
            "        AND fail.sync_status IN ('FAILED', 'NOT_FOUND')",
            "        AND fail.fetched_at &gt;= DATE_SUB(NOW(), INTERVAL #{failureCooldownHours} HOUR)",
            "        AND fail.is_deleted = b'0'",
            "  )",
            "  </if>",
            "ORDER BY COALESCE(latest.fetched_at, '1970-01-01') ASC, pm.id ASC, pv.id ASC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<ProductPublicDetailCandidate> listCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("limit") int limit,
            @Param("staleDays") int staleDays,
            @Param("failureCooldownHours") int failureCooldownHours,
            @Param("onlyDue") boolean onlyDue
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM logical_store ls",
            "JOIN logical_store_site lss ON lss.logical_store_id = ls.id",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "JOIN product_variant pv ON pv.product_master_id = pm.id",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id AND pso.site_id = lss.id",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND UPPER(lss.store_code) = #{storeCode}",
            "  AND UPPER(lss.site) = #{siteCode}",
            "  AND ls.is_deleted = b'0'",
            "  AND lss.is_deleted = b'0'",
            "  AND pm.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pso.is_deleted = b'0'",
            "  AND UPPER(COALESCE(ls.status, 'ACTIVE')) = 'ACTIVE'",
            SYNCABLE_SITE_STATUS_CONDITION,
            "  AND COALESCE(lss.is_mounted, b'1') = b'1'",
            "  AND COALESCE(pso.is_active, b'0') = b'1'",
            "  AND NULLIF(TRIM(pm.sku_parent), '') IS NOT NULL"
    })
    int countCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_public_detail_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND UPPER(store_code) = #{storeCode}",
            "  AND UPPER(site_code) = #{siteCode}",
            "  AND source_platform = 'NOON'",
            "  AND sync_status = #{status}",
            "  AND is_latest = b'1'",
            "  AND is_deleted = b'0'"
    })
    int countLatestByStatus(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("status") ProductPublicDetailSyncStatus status
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_public_detail_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND UPPER(store_code) = #{storeCode}",
            "  AND UPPER(site_code) = #{siteCode}",
            "  AND source_platform = 'NOON'",
            "  AND sync_status = #{status}",
            "  AND fact_date = #{factDate}",
            "  AND is_deleted = b'0'"
    })
    int countTodayByStatus(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("status") ProductPublicDetailSyncStatus status,
            @Param("factDate") LocalDate factDate
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode, site_code AS siteCode,",
            "  product_master_id AS productMasterId, product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  partner_sku AS partnerSku, sku_parent AS skuParent, noon_product_code AS noonProductCode, code_type AS codeType, source_platform AS sourcePlatform,",
            "  title_en AS titleEn, title_ar AS titleAr, brand, category_path AS categoryPath, price_amount AS priceAmount, currency_code AS currencyCode,",
            "  rating, review_count AS reviewCount, availability_text AS availabilityText, main_image_url AS mainImageUrl, detail_url AS detailUrl,",
            "  raw_payload_json AS rawPayloadJson, snapshot_hash AS snapshotHash, provider_http_status AS providerHttpStatus, provider_source_url AS providerSourceUrl,",
            "  provider_response_hash AS providerResponseHash, provider_parser_version AS providerParserVersion, sync_status AS syncStatus, failure_code AS failureCode,",
            "  failure_message AS failureMessage, fact_date AS factDate, fetched_at AS fetchedAt, is_latest AS latest, created_by AS createdBy, updated_by AS updatedBy,",
            "  gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM product_public_detail_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND UPPER(store_code) = #{storeCode}",
            "  AND UPPER(site_code) = #{siteCode}",
            "  AND product_master_id = #{productMasterId}",
            "  AND product_variant_id = #{productVariantId}",
            "  AND source_platform = 'NOON'",
            "  AND is_latest = b'1'",
            "  AND is_deleted = b'0'",
            "ORDER BY fact_date DESC, id DESC",
            "LIMIT 1"
    })
    ProductPublicDetailSnapshot selectLatestSnapshot(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("productMasterId") Long productMasterId,
            @Param("productVariantId") Long productVariantId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode, site_code AS siteCode,",
            "  product_master_id AS productMasterId, product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  partner_sku AS partnerSku, sku_parent AS skuParent, noon_product_code AS noonProductCode, code_type AS codeType, source_platform AS sourcePlatform,",
            "  title_en AS titleEn, title_ar AS titleAr, brand, category_path AS categoryPath, price_amount AS priceAmount, currency_code AS currencyCode,",
            "  rating, review_count AS reviewCount, availability_text AS availabilityText, main_image_url AS mainImageUrl, detail_url AS detailUrl,",
            "  raw_payload_json AS rawPayloadJson, snapshot_hash AS snapshotHash, provider_http_status AS providerHttpStatus, provider_source_url AS providerSourceUrl,",
            "  provider_response_hash AS providerResponseHash, provider_parser_version AS providerParserVersion, sync_status AS syncStatus, failure_code AS failureCode,",
            "  failure_message AS failureMessage, fact_date AS factDate, fetched_at AS fetchedAt, is_latest AS latest, created_by AS createdBy, updated_by AS updatedBy,",
            "  gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM product_public_detail_snapshot",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND UPPER(store_code) = UPPER(#{storeCode})",
            "  AND (partner_sku = #{skuParent} OR sku_parent = #{skuParent} OR noon_product_code = #{skuParent})",
            "  AND source_platform = 'NOON'",
            "  AND sync_status IN ('SUCCEEDED', 'PARTIAL')",
            "  AND is_latest = b'1'",
            "  AND is_deleted = b'0'",
            "ORDER BY CASE WHEN sync_status = 'SUCCEEDED' THEN 0 ELSE 1 END, fetched_at DESC, fact_date DESC, id DESC",
            "LIMIT 1"
    })
    ProductPublicDetailSnapshot selectLatestUsableSnapshotBySkuParent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode, site_code AS siteCode,",
            "  product_master_id AS productMasterId, product_variant_id AS productVariantId, product_site_offer_id AS productSiteOfferId,",
            "  partner_sku AS partnerSku, sku_parent AS skuParent, noon_product_code AS noonProductCode, code_type AS codeType, source_platform AS sourcePlatform,",
            "  title_en AS titleEn, title_ar AS titleAr, brand, category_path AS categoryPath, price_amount AS priceAmount, currency_code AS currencyCode,",
            "  rating, review_count AS reviewCount, availability_text AS availabilityText, main_image_url AS mainImageUrl, detail_url AS detailUrl,",
            "  raw_payload_json AS rawPayloadJson, snapshot_hash AS snapshotHash, provider_http_status AS providerHttpStatus, provider_source_url AS providerSourceUrl,",
            "  provider_response_hash AS providerResponseHash, provider_parser_version AS providerParserVersion, sync_status AS syncStatus, failure_code AS failureCode,",
            "  failure_message AS failureMessage, fact_date AS factDate, fetched_at AS fetchedAt, is_latest AS latest, created_by AS createdBy, updated_by AS updatedBy,",
            "  gmt_create AS createdAt, gmt_updated AS updatedAt",
            "FROM product_public_detail_snapshot",
            "WHERE product_master_id = #{productMasterId}",
            "  AND product_variant_id = #{productVariantId}",
            "  AND UPPER(site_code) = #{siteCode}",
            "  AND source_platform = #{sourcePlatform}",
            "  AND fact_date = #{factDate}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductPublicDetailSnapshot selectDailySnapshot(
            @Param("productMasterId") Long productMasterId,
            @Param("productVariantId") Long productVariantId,
            @Param("siteCode") String siteCode,
            @Param("sourcePlatform") String sourcePlatform,
            @Param("factDate") LocalDate factDate
    );

    @Insert({
            "INSERT INTO product_public_detail_snapshot (",
            "  id, owner_user_id, logical_store_id, store_code, site_code, product_master_id, product_variant_id, product_site_offer_id,",
            "  partner_sku, sku_parent, noon_product_code, code_type, source_platform, title_en, title_ar, brand, category_path,",
            "  price_amount, currency_code, rating, review_count, availability_text, main_image_url, detail_url, raw_payload_json, snapshot_hash,",
            "  provider_http_status, provider_source_url, provider_response_hash, provider_parser_version, sync_status, failure_code, failure_message,",
            "  fact_date, fetched_at, is_latest, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{logicalStoreId}, #{storeCode}, #{siteCode}, #{productMasterId}, #{productVariantId}, #{productSiteOfferId},",
            "  #{partnerSku}, #{skuParent}, #{noonProductCode}, #{codeType}, #{sourcePlatform}, #{titleEn}, #{titleAr}, #{brand}, #{categoryPath},",
            "  #{priceAmount}, #{currencyCode}, #{rating}, #{reviewCount}, #{availabilityText}, #{mainImageUrl}, #{detailUrl}, #{rawPayloadJson}, #{snapshotHash},",
            "  #{providerHttpStatus}, #{providerSourceUrl}, #{providerResponseHash}, #{providerParserVersion}, #{syncStatus}, #{failureCode}, #{failureMessage},",
            "  #{factDate}, #{fetchedAt}, #{latest}, b'0', #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    void insertSnapshot(ProductPublicDetailSnapshot snapshot);

    @Update({
            "UPDATE product_public_detail_snapshot",
            "SET owner_user_id = #{ownerUserId},",
            "  logical_store_id = #{logicalStoreId},",
            "  store_code = #{storeCode},",
            "  site_code = #{siteCode},",
            "  product_site_offer_id = #{productSiteOfferId},",
            "  partner_sku = #{partnerSku},",
            "  sku_parent = #{skuParent},",
            "  noon_product_code = #{noonProductCode},",
            "  code_type = CASE WHEN NULLIF(TRIM(#{codeType}), '') IS NOT NULL THEN #{codeType} ELSE code_type END,",
            "  source_platform = #{sourcePlatform},",
            "  title_en = CASE WHEN NULLIF(TRIM(#{titleEn}), '') IS NOT NULL THEN #{titleEn} ELSE title_en END,",
            "  title_ar = CASE WHEN NULLIF(TRIM(#{titleAr}), '') IS NOT NULL THEN #{titleAr} ELSE title_ar END,",
            "  brand = CASE WHEN NULLIF(TRIM(#{brand}), '') IS NOT NULL THEN #{brand} ELSE brand END,",
            "  category_path = CASE WHEN NULLIF(TRIM(#{categoryPath}), '') IS NOT NULL THEN #{categoryPath} ELSE category_path END,",
            "  price_amount = COALESCE(#{priceAmount}, price_amount),",
            "  currency_code = CASE WHEN NULLIF(TRIM(#{currencyCode}), '') IS NOT NULL THEN #{currencyCode} ELSE currency_code END,",
            "  rating = COALESCE(#{rating}, rating),",
            "  review_count = COALESCE(#{reviewCount}, review_count),",
            "  availability_text = CASE WHEN NULLIF(TRIM(#{availabilityText}), '') IS NOT NULL THEN #{availabilityText} ELSE availability_text END,",
            "  main_image_url = CASE WHEN NULLIF(TRIM(#{mainImageUrl}), '') IS NOT NULL THEN #{mainImageUrl} ELSE main_image_url END,",
            "  detail_url = CASE WHEN NULLIF(TRIM(#{detailUrl}), '') IS NOT NULL THEN #{detailUrl} ELSE detail_url END,",
            "  raw_payload_json = CASE WHEN NULLIF(TRIM(#{rawPayloadJson}), '') IS NOT NULL THEN #{rawPayloadJson} ELSE raw_payload_json END,",
            "  snapshot_hash = CASE WHEN NULLIF(TRIM(#{snapshotHash}), '') IS NOT NULL THEN #{snapshotHash} ELSE snapshot_hash END,",
            "  provider_http_status = #{providerHttpStatus},",
            "  provider_source_url = #{providerSourceUrl},",
            "  provider_response_hash = #{providerResponseHash},",
            "  provider_parser_version = #{providerParserVersion},",
            "  sync_status = #{syncStatus},",
            "  failure_code = #{failureCode},",
            "  failure_message = #{failureMessage},",
            "  fetched_at = #{fetchedAt},",
            "  updated_by = #{updatedBy},",
            "  gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    void updateSnapshotPreservingTrustedData(ProductPublicDetailSnapshot snapshot);

    @Update({
            "UPDATE product_public_detail_snapshot",
            "SET is_latest = b'0', updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND product_variant_id = #{productVariantId}",
            "  AND UPPER(site_code) = #{siteCode}",
            "  AND source_platform = #{sourcePlatform}",
            "  AND id <> #{currentId}",
            "  AND is_latest = b'1'",
            "  AND is_deleted = b'0'"
    })
    void clearLatestForProduct(
            @Param("productMasterId") Long productMasterId,
            @Param("productVariantId") Long productVariantId,
            @Param("siteCode") String siteCode,
            @Param("sourcePlatform") String sourcePlatform,
            @Param("currentId") Long currentId,
            @Param("actorUserId") Long actorUserId
    );

    @Update({
            "UPDATE product_public_detail_snapshot",
            "SET is_latest = b'1', updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    void markLatest(@Param("id") Long id, @Param("actorUserId") Long actorUserId);
}
