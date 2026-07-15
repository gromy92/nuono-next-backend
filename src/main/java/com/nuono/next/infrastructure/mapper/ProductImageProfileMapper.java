package com.nuono.next.infrastructure.mapper;

import com.nuono.next.product.ProductImageAssetStatus;
import com.nuono.next.product.ProductImageAssetMetadataView;
import com.nuono.next.product.ProductImageProductCandidateRecord;
import com.nuono.next.product.ProductImageProfileAssetRecord;
import com.nuono.next.product.ProductImageProfileAssetUsageRecord;
import com.nuono.next.product.ProductImageProfileRecord;
import com.nuono.next.product.ProductImageProfileSummaryRecord;
import com.nuono.next.product.ProductImageRole;
import com.nuono.next.product.ProductImageProcessingStatus;
import com.nuono.next.product.ProductImageSectionRecord;
import com.nuono.next.product.ProductImageSectionType;
import com.nuono.next.product.ProductImageSuiteAssetRecord;
import com.nuono.next.product.ProductImageSuiteAssetRole;
import com.nuono.next.product.ProductImageSuiteRecord;
import com.nuono.next.product.ProductImageSuiteStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductImageProfileMapper {

    @Results(id = "productImageProfileMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "owner_user_id", property = "ownerUserId"),
            @Result(column = "store_code", property = "storeCode"),
            @Result(column = "logical_store_id", property = "logicalStoreId"),
            @Result(column = "psku_code", property = "pskuCode"),
            @Result(column = "product_identity_key", property = "productIdentityKey"),
            @Result(column = "product_master_id", property = "productMasterId"),
            @Result(column = "product_variant_id", property = "productVariantId"),
            @Result(column = "product_title", property = "productTitle"),
            @Result(column = "brand", property = "brand"),
            @Result(column = "title_ar", property = "titleAr"),
            @Result(column = "title_en", property = "titleEn"),
            @Result(column = "spec_summary", property = "specSummary"),
            @Result(column = "product_fact_text", property = "productFactText"),
            @Result(column = "hero_selling_points_json", property = "heroSellingPointsJson"),
            @Result(column = "profile_status", property = "profileStatus"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT p.*",
            "FROM product_image_profile p",
            "WHERE p.id = #{id}",
            "  AND p.owner_user_id = #{ownerUserId}",
            "  AND p.deleted = b'0'",
            "  AND (",
            "    p.store_code = #{storeCode}",
            "    OR p.logical_store_id = (",
            "      SELECT lss.logical_store_id",
            "      FROM logical_store_site lss",
            "      JOIN logical_store ls",
            "        ON ls.id = lss.logical_store_id",
            "       AND ls.owner_user_id = #{ownerUserId}",
            "       AND ls.is_deleted = b'0'",
            "      WHERE lss.store_code = #{storeCode}",
            "        AND lss.is_deleted = b'0'",
            "      LIMIT 1",
            "    )",
            "  )",
            "LIMIT 1"
    })
    ProductImageProfileRecord selectProfileById(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @ResultMap("productImageProfileMap")
    @Select({
            "SELECT p.*",
            "FROM product_image_profile p",
            "LEFT JOIN (",
            "  SELECT profile_id, COUNT(1) AS asset_count",
            "  FROM product_image_profile_asset",
            "  WHERE asset_status = 'ACTIVE'",
            "  GROUP BY profile_id",
            ") ac ON ac.profile_id = p.id",
            "WHERE p.owner_user_id = #{ownerUserId}",
            "  AND p.psku_code = #{pskuCode}",
            "  AND p.product_identity_key = #{productIdentityKey}",
            "  AND p.deleted = b'0'",
            "  AND (",
            "    p.store_code = #{storeCode}",
            "    OR p.logical_store_id = (",
            "      SELECT lss.logical_store_id",
            "      FROM logical_store_site lss",
            "      JOIN logical_store ls",
            "        ON ls.id = lss.logical_store_id",
            "       AND ls.owner_user_id = #{ownerUserId}",
            "       AND ls.is_deleted = b'0'",
            "      WHERE lss.store_code = #{storeCode}",
            "        AND lss.is_deleted = b'0'",
            "      LIMIT 1",
            "    )",
            "  )",
            "ORDER BY COALESCE(ac.asset_count, 0) DESC, p.updated_at DESC, p.id ASC",
            "LIMIT 1"
    })
    ProductImageProfileRecord selectProfileByIdentity(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("pskuCode") String pskuCode,
            @Param("productIdentityKey") String productIdentityKey
    );

    @ResultMap("productImageProfileMap")
    @Select({
            "<script>",
            "SELECT p.*",
            "FROM product_image_profile p",
            "JOIN (",
            "  SELECT CAST(SUBSTRING_INDEX(GROUP_CONCAT(p2.id ORDER BY COALESCE(ac2.asset_count, 0) DESC, p2.updated_at DESC, p2.id ASC), ',', 1) AS UNSIGNED) AS canonical_id",
            "  FROM product_image_profile p2",
            "  LEFT JOIN (",
            "    SELECT profile_id, COUNT(1) AS asset_count",
            "    FROM product_image_profile_asset",
            "    WHERE asset_status = 'ACTIVE'",
            "    GROUP BY profile_id",
            "  ) ac2 ON ac2.profile_id = p2.id",
            "  WHERE p2.owner_user_id = #{ownerUserId}",
            "    AND p2.deleted = b'0'",
            "    AND (",
            "      p2.store_code = #{storeCode}",
            "      OR p2.logical_store_id = (",
            "        SELECT lss.logical_store_id",
            "        FROM logical_store_site lss",
            "        JOIN logical_store ls",
            "          ON ls.id = lss.logical_store_id",
            "         AND ls.owner_user_id = #{ownerUserId}",
            "         AND ls.is_deleted = b'0'",
            "        WHERE lss.store_code = #{storeCode}",
            "          AND lss.is_deleted = b'0'",
            "        LIMIT 1",
            "      )",
            "    )",
            "<if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      p2.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.product_title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.brand LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.title_en LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.title_ar LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "</if>",
            "  GROUP BY p2.owner_user_id, p2.psku_code, p2.product_identity_key",
            ") canonical ON canonical.canonical_id = p.id",
            "ORDER BY p.updated_at DESC, p.id DESC",
            "</script>"
    })
    List<ProductImageProfileRecord> selectProfilesForStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @Results(id = "productImageProfileSummaryMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "owner_user_id", property = "ownerUserId"),
            @Result(column = "store_code", property = "storeCode"),
            @Result(column = "psku_code", property = "pskuCode"),
            @Result(column = "product_identity_key", property = "productIdentityKey"),
            @Result(column = "product_master_id", property = "productMasterId"),
            @Result(column = "product_variant_id", property = "productVariantId"),
            @Result(column = "product_title", property = "productTitle"),
            @Result(column = "brand", property = "brand"),
            @Result(column = "title_ar", property = "titleAr"),
            @Result(column = "title_en", property = "titleEn"),
            @Result(column = "spec_summary", property = "specSummary"),
            @Result(column = "cover_image_url", property = "coverImageUrl"),
            @Result(column = "asset_count", property = "assetCount"),
            @Result(column = "suite_count", property = "suiteCount"),
            @Result(column = "has_adopted_suite", property = "hasAdoptedSuite"),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "<script>",
            "SELECT",
            "  p.id, p.owner_user_id, p.store_code, p.psku_code, p.product_identity_key,",
            "  p.product_master_id, p.product_variant_id, p.product_title, p.brand, p.title_ar, p.title_en,",
            "  p.spec_summary, p.updated_at,",
            "  COALESCE((",
            "    SELECT pia.image_url",
            "    FROM product_image_profile_asset pia",
            "    WHERE pia.profile_id = p.id",
            "      AND pia.asset_status = 'ACTIVE'",
            "    ORDER BY CASE pia.image_role WHEN 'MAIN' THEN 0 WHEN 'SIZE' THEN 1 ELSE 2 END, pia.sort_order ASC, pia.id ASC",
            "    LIMIT 1",
            "  ), (",
            "    SELECT pima.url",
            "    FROM product_image_asset pima",
            "    WHERE pima.product_master_id = p.product_master_id",
            "      AND pima.is_deleted = b'0'",
            "    ORDER BY pima.id ASC",
            "    LIMIT 1",
            "  )) AS cover_image_url,",
            "  COALESCE(pac.profile_asset_count, 0) + COALESCE(cac.current_asset_count, 0) AS asset_count,",
            "  COALESCE(sc.suite_count, 0) AS suite_count,",
            "  COALESCE(sc.has_adopted_suite, 0) AS has_adopted_suite",
            "FROM product_image_profile p",
            "JOIN (",
            "  SELECT CAST(SUBSTRING_INDEX(GROUP_CONCAT(p2.id ORDER BY COALESCE(ac2.asset_count, 0) DESC, p2.updated_at DESC, p2.id ASC), ',', 1) AS UNSIGNED) AS canonical_id",
            "  FROM product_image_profile p2",
            "  LEFT JOIN (",
            "    SELECT profile_id, COUNT(1) AS asset_count",
            "    FROM product_image_profile_asset",
            "    WHERE asset_status = 'ACTIVE'",
            "    GROUP BY profile_id",
            "  ) ac2 ON ac2.profile_id = p2.id",
            "  WHERE p2.owner_user_id = #{ownerUserId}",
            "    AND p2.deleted = b'0'",
            "    AND (",
            "      p2.store_code = #{storeCode}",
            "      OR p2.logical_store_id = (",
            "        SELECT lss.logical_store_id",
            "        FROM logical_store_site lss",
            "        JOIN logical_store ls",
            "          ON ls.id = lss.logical_store_id",
            "         AND ls.owner_user_id = #{ownerUserId}",
            "         AND ls.is_deleted = b'0'",
            "        WHERE lss.store_code = #{storeCode}",
            "          AND lss.is_deleted = b'0'",
            "        LIMIT 1",
            "      )",
            "    )",
            "<if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      p2.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.product_title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.brand LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.title_en LIKE CONCAT('%', #{keyword}, '%')",
            "      OR p2.title_ar LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "</if>",
            "  GROUP BY p2.owner_user_id, p2.psku_code, p2.product_identity_key",
            ") canonical ON canonical.canonical_id = p.id",
            "LEFT JOIN (",
            "  SELECT profile_id, COUNT(1) AS profile_asset_count",
            "  FROM product_image_profile_asset",
            "  WHERE asset_status = 'ACTIVE'",
            "  GROUP BY profile_id",
            ") pac ON pac.profile_id = p.id",
            "LEFT JOIN (",
            "  SELECT product_master_id, COUNT(1) AS current_asset_count",
            "  FROM product_image_asset",
            "  WHERE is_deleted = b'0'",
            "  GROUP BY product_master_id",
            ") cac ON cac.product_master_id = p.product_master_id",
            "LEFT JOIN (",
            "  SELECT profile_id, COUNT(1) AS suite_count, MAX(CASE WHEN suite_status = 'ADOPTED' THEN 1 ELSE 0 END) AS has_adopted_suite",
            "  FROM product_image_suite",
            "  WHERE deleted = b'0'",
            "    AND suite_status != 'DISCARDED'",
            "  GROUP BY profile_id",
            ") sc ON sc.profile_id = p.id",
            "ORDER BY p.updated_at DESC, p.id DESC",
            "</script>"
    })
    List<ProductImageProfileSummaryRecord> selectProfileSummariesForStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @Insert({
            "INSERT INTO product_image_profile (",
            "  owner_user_id, store_code, logical_store_id, psku_code, product_identity_key, product_master_id, product_variant_id,",
            "  product_title, brand, title_ar, title_en, spec_summary, product_fact_text, hero_selling_points_json, profile_status,",
            "  created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{ownerUserId}, #{storeCode}, COALESCE(#{logicalStoreId}, (",
            "    SELECT lss.logical_store_id",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls",
            "      ON ls.id = lss.logical_store_id",
            "     AND ls.owner_user_id = #{ownerUserId}",
            "     AND ls.is_deleted = b'0'",
            "    WHERE lss.store_code = #{storeCode}",
            "      AND lss.is_deleted = b'0'",
            "    LIMIT 1",
            "  )), #{pskuCode}, #{productIdentityKey}, #{productMasterId}, #{productVariantId},",
            "  #{productTitle}, #{brand}, #{titleAr}, #{titleEn}, #{specSummary}, #{productFactText}, #{heroSellingPointsJson}, #{profileStatus},",
            "  #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertProfile(ProductImageProfileRecord record);

    @Update({
            "UPDATE product_image_profile",
            "SET",
            "  logical_store_id = COALESCE(#{logicalStoreId}, logical_store_id, (",
            "    SELECT lss.logical_store_id",
            "    FROM logical_store_site lss",
            "    JOIN logical_store ls",
            "      ON ls.id = lss.logical_store_id",
            "     AND ls.owner_user_id = #{ownerUserId}",
            "     AND ls.is_deleted = b'0'",
            "    WHERE lss.store_code = #{storeCode}",
            "      AND lss.is_deleted = b'0'",
            "    LIMIT 1",
            "  )),",
            "  product_master_id = #{productMasterId},",
            "  product_variant_id = #{productVariantId},",
            "  product_title = #{productTitle},",
            "  brand = #{brand},",
            "  title_ar = #{titleAr},",
            "  title_en = #{titleEn},",
            "  spec_summary = #{specSummary},",
            "  product_fact_text = #{productFactText},",
            "  hero_selling_points_json = #{heroSellingPointsJson},",
            "  profile_status = #{profileStatus},",
            "  updated_by = #{updatedBy},",
            "  updated_at = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND deleted = b'0'"
    })
    int updateProfile(ProductImageProfileRecord record);

    @Results(id = "productImageProductCandidateMap", value = {
            @Result(column = "product_master_id", property = "productMasterId"),
            @Result(column = "product_variant_id", property = "productVariantId"),
            @Result(column = "psku_code", property = "pskuCode"),
            @Result(column = "product_identity_key", property = "productIdentityKey"),
            @Result(column = "product_title", property = "productTitle"),
            @Result(column = "brand", property = "brand"),
            @Result(column = "cover_image_url", property = "coverImageUrl")
    })
    @Select({
            "<script>",
            "SELECT",
            "  pm.id AS product_master_id,",
            "  NULL AS product_variant_id,",
            "  COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), '')) AS psku_code,",
            "  CONCAT('psku:', COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), ''))) AS product_identity_key,",
            "  pm.title_cache AS product_title,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS cover_image_url",
            "FROM logical_store_site request_lss",
            "JOIN logical_store ls",
            "  ON ls.id = request_lss.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.product_master_id = pm.id",
            " AND pso.logical_store_id = ls.id",
            " AND pso.is_deleted = b'0'",
            "WHERE request_lss.store_code = #{storeCode}",
            "  AND request_lss.is_deleted = b'0'",
            "<if test='keyword != null and keyword != \"\"'>",
            "  AND (",
            "    pm.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pm.current_z_code LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pm.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pso.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pso.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pm.title_cache LIKE CONCAT('%', #{keyword}, '%')",
            "    OR pm.brand_cache LIKE CONCAT('%', #{keyword}, '%')",
            "  )",
            "</if>",
            "GROUP BY pm.id, pm.partner_sku, pm.current_z_code, pm.sku_parent, pm.title_cache, pm.brand_cache, pm.cover_image_url, pm.gmt_updated",
            "HAVING psku_code IS NOT NULL",
            "ORDER BY pm.gmt_updated DESC, pm.id DESC",
            "LIMIT 200",
            "</script>"
    })
    List<ProductImageProductCandidateRecord> selectProductCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("keyword") String keyword
    );

    @ResultMap("productImageProductCandidateMap")
    @Select({
            "SELECT",
            "  pm.id AS product_master_id,",
            "  NULL AS product_variant_id,",
            "  COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), '')) AS psku_code,",
            "  CONCAT('psku:', COALESCE(NULLIF(pm.partner_sku, ''), NULLIF(MAX(pso.partner_sku), ''))) AS product_identity_key,",
            "  pm.title_cache AS product_title,",
            "  pm.brand_cache AS brand,",
            "  pm.cover_image_url AS cover_image_url",
            "FROM logical_store_site request_lss",
            "JOIN logical_store ls",
            "  ON ls.id = request_lss.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.product_master_id = pm.id",
            " AND pso.logical_store_id = ls.id",
            " AND pso.is_deleted = b'0'",
            "WHERE request_lss.store_code = #{storeCode}",
            "  AND request_lss.is_deleted = b'0'",
            "GROUP BY pm.id, pm.partner_sku, pm.current_z_code, pm.sku_parent, pm.title_cache, pm.brand_cache, pm.cover_image_url, pm.gmt_updated",
            "HAVING psku_code IS NOT NULL",
            "ORDER BY pm.gmt_updated DESC, pm.id DESC"
    })
    List<ProductImageProductCandidateRecord> selectAllProductCandidatesForStore(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Results(id = "productImageProfileAssetMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "content_type", property = "contentType"),
            @Result(column = "size_bytes", property = "sizeBytes"),
            @Result(column = "width_px", property = "widthPx"),
            @Result(column = "height_px", property = "heightPx"),
            @Result(column = "horizontal_ppi", property = "horizontalPpi"),
            @Result(column = "vertical_ppi", property = "verticalPpi"),
            @Result(column = "color_space", property = "colorSpace"),
            @Result(column = "source_store_code", property = "sourceStoreCode"),
            @Result(column = "source_site_code", property = "sourceSiteCode"),
            @Result(column = "source_snapshot_id", property = "sourceSnapshotId"),
            @Result(column = "source_field", property = "sourceField"),
            @Result(column = "source_kind", property = "sourceKind"),
            @Result(column = "image_role", property = "imageRole", javaType = ProductImageRole.class),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "asset_status", property = "assetStatus", javaType = ProductImageAssetStatus.class),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id, profile_id, image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  source_store_code, source_site_code, source_snapshot_id, source_field, source_kind,",
            "  image_role, sort_order, asset_status, created_by, updated_by, created_at, updated_at",
            "FROM product_image_profile_asset",
            "WHERE profile_id = #{profileId}",
            "  AND asset_status = 'ACTIVE'",
            "ORDER BY sort_order ASC, id ASC"
    })
    List<ProductImageProfileAssetRecord> selectAssets(@Param("profileId") Long profileId);

    @ResultMap("productImageProfileAssetMap")
    @Select({
            "SELECT",
            "  id, NULL AS profile_id, url AS image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  NULL AS source_store_code, NULL AS source_site_code, NULL AS source_snapshot_id, NULL AS source_field, NULL AS source_kind,",
            "  'MAIN' AS image_role, 0 AS sort_order,",
            "  'ACTIVE' AS asset_status, created_by, updated_by, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM product_image_asset",
            "WHERE product_master_id = #{productMasterId}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC",
            "LIMIT 20"
    })
    List<ProductImageProfileAssetRecord> selectCurrentProductImages(@Param("productMasterId") Long productMasterId);

    @ResultMap("productImageProfileAssetMap")
    @Select({
            "SELECT",
            "  id, NULL AS profile_id, url AS image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  NULL AS source_store_code, NULL AS source_site_code, NULL AS source_snapshot_id, NULL AS source_field, NULL AS source_kind,",
            "  'MAIN' AS image_role, 0 AS sort_order,",
            "  'ACTIVE' AS asset_status, created_by, updated_by, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM product_image_asset",
            "WHERE product_master_id = #{productMasterId}",
            "  AND url = #{imageUrl}",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    ProductImageProfileAssetRecord selectCurrentProductImageByUrl(
            @Param("productMasterId") Long productMasterId,
            @Param("imageUrl") String imageUrl
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM logical_store_site request_lss",
            "JOIN logical_store ls",
            "  ON ls.id = request_lss.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.id = #{productMasterId}",
            " AND pm.is_deleted = b'0'",
            "WHERE request_lss.store_code = #{storeCode}",
            "  AND request_lss.is_deleted = b'0'",
            "  AND (",
            "    pm.cover_image_url = #{imageUrl}",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM product_image_asset pia",
            "      WHERE pia.product_master_id = pm.id",
            "        AND pia.url = #{imageUrl}",
            "        AND pia.is_deleted = b'0'",
            "    )",
            "  )"
    })
    int countAccessibleProductImage(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("productMasterId") Long productMasterId,
            @Param("imageUrl") String imageUrl
    );

    @Update({
            "UPDATE product_image_asset",
            "SET content_type = COALESCE(content_type, #{metadata.contentType}),",
            "    size_bytes = COALESCE(size_bytes, #{metadata.sizeBytes}),",
            "    width_px = COALESCE(width_px, #{metadata.widthPx}),",
            "    height_px = COALESCE(height_px, #{metadata.heightPx}),",
            "    horizontal_ppi = COALESCE(horizontal_ppi, #{metadata.horizontalPpi}),",
            "    vertical_ppi = COALESCE(vertical_ppi, #{metadata.verticalPpi}),",
            "    color_space = COALESCE(color_space, #{metadata.colorSpace}),",
            "    gmt_updated = NOW()",
            "WHERE product_master_id = #{productMasterId}",
            "  AND url = #{imageUrl}",
            "  AND is_deleted = b'0'"
    })
    int updateCurrentProductImageMetadata(
            @Param("productMasterId") Long productMasterId,
            @Param("imageUrl") String imageUrl,
            @Param("metadata") ProductImageAssetMetadataView metadata
    );

    @Insert({
            "INSERT INTO product_image_profile_asset (",
            "  profile_id, image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  source_store_code, source_site_code, source_snapshot_id, source_field, source_kind,",
            "  image_role, sort_order, asset_status, created_by, updated_by, created_at, updated_at",
            ") VALUES (",
            "  #{profileId}, #{imageUrl}, #{contentType}, #{sizeBytes}, #{widthPx}, #{heightPx}, #{horizontalPpi}, #{verticalPpi}, #{colorSpace},",
            "  #{sourceStoreCode}, #{sourceSiteCode}, #{sourceSnapshotId}, #{sourceField}, #{sourceKind},",
            "  #{imageRole}, #{sortOrder}, #{assetStatus}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAsset(ProductImageProfileAssetRecord record);

    @ResultMap("productImageProfileAssetMap")
    @Select({
            "SELECT",
            "  id, profile_id, image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  source_store_code, source_site_code, source_snapshot_id, source_field, source_kind,",
            "  image_role, sort_order, asset_status, created_by, updated_by, created_at, updated_at",
            "FROM product_image_profile_asset",
            "WHERE profile_id = #{profileId}",
            "  AND id = #{assetId}",
            "  AND asset_status = 'ACTIVE'",
            "LIMIT 1"
    })
    ProductImageProfileAssetRecord selectAssetById(
            @Param("profileId") Long profileId,
            @Param("assetId") Long assetId
    );

    @ResultMap("productImageProfileAssetMap")
    @Select({
            "SELECT",
            "  id, profile_id, image_url, content_type, size_bytes, width_px, height_px, horizontal_ppi, vertical_ppi, color_space,",
            "  source_store_code, source_site_code, source_snapshot_id, source_field, source_kind,",
            "  image_role, sort_order, asset_status, created_by, updated_by, created_at, updated_at",
            "FROM product_image_profile_asset",
            "WHERE profile_id = #{profileId}",
            "  AND image_url = #{imageUrl}",
            "  AND asset_status = 'ACTIVE'",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    ProductImageProfileAssetRecord selectAssetByUrl(
            @Param("profileId") Long profileId,
            @Param("imageUrl") String imageUrl
    );

    @Results(id = "productImageProfileAssetUsageMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "asset_id", property = "assetId"),
            @Result(column = "image_role", property = "imageRole", javaType = ProductImageRole.class),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "processing_note", property = "processingNote"),
            @Result(column = "processing_status", property = "processingStatus", javaType = ProductImageProcessingStatus.class),
            @Result(column = "processed_by", property = "processedBy"),
            @Result(column = "processed_at", property = "processedAt", javaType = LocalDateTime.class),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT id, profile_id, asset_id, image_role, sort_order, processing_note, processing_status,",
            "       processed_by, processed_at, created_by, updated_by, created_at, updated_at, deleted",
            "FROM product_image_profile_asset_usage",
            "WHERE profile_id = #{profileId}",
            "  AND deleted = b'0'",
            "ORDER BY image_role ASC, sort_order ASC, id ASC"
    })
    List<ProductImageProfileAssetUsageRecord> selectAssetUsages(@Param("profileId") Long profileId);

    @ResultMap("productImageProfileAssetUsageMap")
    @Select({
            "SELECT id, profile_id, asset_id, image_role, sort_order, processing_note, processing_status,",
            "       processed_by, processed_at, created_by, updated_by, created_at, updated_at, deleted",
            "FROM product_image_profile_asset_usage",
            "WHERE profile_id = #{profileId}",
            "  AND id = #{usageId}",
            "  AND deleted = b'0'",
            "LIMIT 1"
    })
    ProductImageProfileAssetUsageRecord selectAssetUsageById(
            @Param("profileId") Long profileId,
            @Param("usageId") Long usageId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_image_profile_asset_usage",
            "WHERE profile_id = #{profileId}",
            "  AND asset_id = #{assetId}",
            "  AND image_role = #{imageRole}",
            "  AND deleted = b'0'"
    })
    int countActiveAssetUsage(
            @Param("profileId") Long profileId,
            @Param("assetId") Long assetId,
            @Param("imageRole") ProductImageRole imageRole
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_image_profile_asset_usage",
            "WHERE profile_id = #{profileId}",
            "  AND asset_id = #{assetId}"
    })
    int countAssetUsageHistory(
            @Param("profileId") Long profileId,
            @Param("assetId") Long assetId
    );

    @Insert({
            "INSERT INTO product_image_profile_asset_usage (",
            "  profile_id, asset_id, image_role, sort_order, processing_note, processing_status,",
            "  processed_by, processed_at, created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{profileId}, #{assetId}, #{imageRole}, #{sortOrder}, #{processingNote}, #{processingStatus},",
            "  #{processedBy}, #{processedAt}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, b'0'",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertAssetUsage(ProductImageProfileAssetUsageRecord record);

    @Update({
            "UPDATE product_image_profile_asset_usage",
            "SET image_role = #{imageRole},",
            "    processing_note = #{processingNote},",
            "    processing_status = #{processingStatus},",
            "    processed_by = #{processedBy},",
            "    processed_at = #{processedAt},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND id = #{id}",
            "  AND deleted = b'0'"
    })
    int updateAssetUsage(ProductImageProfileAssetUsageRecord record);

    @Update({
            "UPDATE product_image_profile_asset_usage",
            "SET deleted = b'1', updated_by = #{updatedBy}, updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND id = #{usageId}",
            "  AND deleted = b'0'"
    })
    int softDeleteAssetUsage(
            @Param("profileId") Long profileId,
            @Param("usageId") Long usageId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_profile_asset",
            "SET asset_status = #{status},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{assetId}",
            "  AND profile_id = #{profileId}",
            "  AND asset_status = 'ACTIVE'"
    })
    int updateAssetStatus(
            @Param("profileId") Long profileId,
            @Param("assetId") Long assetId,
            @Param("status") ProductImageAssetStatus status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_profile_asset",
            "SET image_role = #{imageRole},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{assetId}",
            "  AND profile_id = #{profileId}",
            "  AND asset_status = 'ACTIVE'"
    })
    int updateAssetRole(
            @Param("profileId") Long profileId,
            @Param("assetId") Long assetId,
            @Param("imageRole") ProductImageRole imageRole,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_profile_asset",
            "SET asset_status = #{status},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND image_url = #{imageUrl}",
            "  AND asset_status = 'ACTIVE'"
    })
    int updateAssetStatusByUrl(
            @Param("profileId") Long profileId,
            @Param("imageUrl") String imageUrl,
            @Param("status") ProductImageAssetStatus status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_profile_asset",
            "SET image_role = #{imageRole},",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND image_url = #{imageUrl}",
            "  AND asset_status = 'ACTIVE'"
    })
    int updateAssetRoleByUrl(
            @Param("profileId") Long profileId,
            @Param("imageUrl") String imageUrl,
            @Param("imageRole") ProductImageRole imageRole,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_profile_asset",
            "SET image_role = #{imageRole},",
            "    sort_order = #{sortOrder},",
            "    asset_status = 'ACTIVE',",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND image_url = #{imageUrl}",
            "  AND asset_status = 'ACTIVE'"
    })
    int updateAssetRoleAndSortOrderByUrl(
            @Param("profileId") Long profileId,
            @Param("imageUrl") String imageUrl,
            @Param("imageRole") ProductImageRole imageRole,
            @Param("sortOrder") Integer sortOrder,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_image_profile_asset",
            "WHERE profile_id = #{profileId}",
            "  AND image_url = #{imageUrl}"
    })
    int countProfileAssetByUrl(
            @Param("profileId") Long profileId,
            @Param("imageUrl") String imageUrl
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM product_image_asset",
            "WHERE product_master_id = #{productMasterId}",
            "  AND url = #{imageUrl}",
            "  AND is_deleted = b'0'"
    })
    int countCurrentProductImageByUrl(
            @Param("productMasterId") Long productMasterId,
            @Param("imageUrl") String imageUrl
    );

    @Select({
            "SELECT image_url",
            "FROM product_image_profile_asset",
            "WHERE profile_id = #{profileId}",
            "  AND asset_status = 'REMOVED'"
    })
    List<String> selectRemovedAssetUrls(@Param("profileId") Long profileId);

    @Results(id = "productImageSectionMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "section_type", property = "sectionType", javaType = ProductImageSectionType.class),
            @Result(column = "title_ar", property = "titleAr"),
            @Result(column = "title_en", property = "titleEn"),
            @Result(column = "description_ar", property = "descriptionAr"),
            @Result(column = "description_en", property = "descriptionEn"),
            @Result(column = "attributes_text", property = "attributesText"),
            @Result(column = "focus_part", property = "focusPart"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "enabled", property = "enabled", javaType = Boolean.class),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT *",
            "FROM product_image_section",
            "WHERE profile_id = #{profileId}",
            "  AND deleted = b'0'",
            "ORDER BY sort_order ASC, id ASC"
    })
    List<ProductImageSectionRecord> selectSections(@Param("profileId") Long profileId);

    @Update({
            "UPDATE product_image_section",
            "SET deleted = b'1',",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND deleted = b'0'"
    })
    int replaceSectionsAsDeleted(@Param("profileId") Long profileId);

    @Insert({
            "INSERT INTO product_image_section (",
            "  profile_id, section_type, title_ar, title_en, description_ar, description_en, attributes_text,",
            "  focus_part, sort_order, enabled, created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{profileId}, #{sectionType}, #{titleAr}, #{titleEn}, #{descriptionAr}, #{descriptionEn}, #{attributesText},",
            "  #{focusPart}, #{sortOrder}, #{enabled}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSection(ProductImageSectionRecord record);

    @Results(id = "productImageSuiteMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "profile_id", property = "profileId"),
            @Result(column = "suite_name", property = "suiteName"),
            @Result(column = "skin_id", property = "skinId"),
            @Result(column = "skin_name", property = "skinName"),
            @Result(column = "generation_task_id", property = "generationTaskId"),
            @Result(column = "draft_package_json", property = "draftPackageJson"),
            @Result(column = "draft_prompt_text", property = "draftPromptText"),
            @Result(column = "suite_status", property = "suiteStatus", javaType = ProductImageSuiteStatus.class),
            @Result(column = "adopted_at", property = "adoptedAt", javaType = LocalDateTime.class),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_by", property = "updatedBy"),
            @Result(column = "created_at", property = "createdAt", javaType = LocalDateTime.class),
            @Result(column = "updated_at", property = "updatedAt", javaType = LocalDateTime.class),
            @Result(column = "deleted", property = "deleted", javaType = Boolean.class)
    })
    @Select({
            "SELECT *",
            "FROM product_image_suite",
            "WHERE profile_id = #{profileId}",
            "  AND deleted = b'0'",
            "  AND suite_status <> 'DISCARDED'",
            "ORDER BY updated_at DESC, id DESC"
    })
    List<ProductImageSuiteRecord> selectSuites(@Param("profileId") Long profileId);

    @Insert({
            "INSERT INTO product_image_suite (",
            "  profile_id, suite_name, skin_id, skin_name, generation_task_id, draft_package_json, draft_prompt_text,",
            "  suite_status, adopted_at, created_by, updated_by, created_at, updated_at, deleted",
            ") VALUES (",
            "  #{profileId}, #{suiteName}, #{skinId}, #{skinName}, #{generationTaskId}, #{draftPackageJson}, #{draftPromptText},",
            "  #{suiteStatus}, #{adoptedAt}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{deleted}",
            ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSuite(ProductImageSuiteRecord record);

    @ResultMap("productImageSuiteMap")
    @Select({
            "SELECT *",
            "FROM product_image_suite",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND deleted = b'0'",
            "LIMIT 1"
    })
    ProductImageSuiteRecord selectSuiteById(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = 'HISTORICAL',",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE profile_id = #{profileId}",
            "  AND suite_status = 'ADOPTED'",
            "  AND deleted = b'0'"
    })
    int markAdoptedSuitesHistorical(
            @Param("profileId") Long profileId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET suite_status = #{status},",
            "    adopted_at = CASE WHEN #{status} = 'ADOPTED' THEN NOW() ELSE adopted_at END,",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND deleted = b'0'"
    })
    int updateSuiteStatus(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("status") ProductImageSuiteStatus status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET deleted = b'1',",
            "    updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND deleted = b'0'"
    })
    int softDeleteSuite(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_image_suite",
            "SET updated_by = #{updatedBy},",
            "    updated_at = NOW()",
            "WHERE id = #{suiteId}",
            "  AND profile_id = #{profileId}",
            "  AND deleted = b'0'"
    })
    int touchSuite(
            @Param("suiteId") Long suiteId,
            @Param("profileId") Long profileId,
            @Param("updatedBy") Long updatedBy
    );

    @Results(id = "productImageSuiteAssetMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "suite_id", property = "suiteId"),
            @Result(column = "image_role", property = "imageRole", javaType = ProductImageSuiteAssetRole.class),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "sort_order", property = "sortOrder")
    })
    @Select({
            "SELECT id, suite_id, image_role, image_url, sort_order",
            "FROM product_image_suite_asset",
            "WHERE suite_id = #{suiteId}",
            "ORDER BY sort_order ASC, id ASC"
    })
    List<ProductImageSuiteAssetRecord> selectSuiteAssets(@Param("suiteId") Long suiteId);

    @ResultMap("productImageSuiteAssetMap")
    @Select({
            "SELECT id, suite_id, image_role, image_url, sort_order",
            "FROM product_image_suite_asset",
            "WHERE id = #{assetId}",
            "  AND suite_id = #{suiteId}",
            "LIMIT 1"
    })
    ProductImageSuiteAssetRecord selectSuiteAssetById(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId
    );

    @Delete({
            "DELETE FROM product_image_suite_asset",
            "WHERE id = #{assetId}",
            "  AND suite_id = #{suiteId}"
    })
    int deleteSuiteAsset(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId
    );

    @Update({
            "UPDATE product_image_suite_asset",
            "SET suite_id = #{targetSuiteId},",
            "    sort_order = #{sortOrder}",
            "WHERE id = #{assetId}",
            "  AND suite_id = #{sourceSuiteId}"
    })
    int moveSuiteAssetToSuite(
            @Param("sourceSuiteId") Long sourceSuiteId,
            @Param("assetId") Long assetId,
            @Param("targetSuiteId") Long targetSuiteId,
            @Param("sortOrder") Integer sortOrder
    );

    @Update({
            "UPDATE product_image_suite_asset",
            "SET sort_order = #{sortOrder}",
            "WHERE id = #{assetId}",
            "  AND suite_id = #{suiteId}"
    })
    int updateSuiteAssetSortOrder(
            @Param("suiteId") Long suiteId,
            @Param("assetId") Long assetId,
            @Param("sortOrder") Integer sortOrder
    );

    @Select({
            "SELECT COALESCE(MAX(sort_order), 0)",
            "FROM product_image_suite_asset",
            "WHERE suite_id = #{suiteId}"
    })
    int selectMaxSuiteAssetSortOrder(@Param("suiteId") Long suiteId);
}
