package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.ProductSelectionAnalysisItemRow;
import com.nuono.next.productselection.ProductSelectionGroupCompetitorRow;
import com.nuono.next.productselection.ProductSelectionGroupMaterialRow;
import com.nuono.next.productselection.ProductSelectionGroupProfitSnapshotRow;
import com.nuono.next.productselection.ProductSelectionGroupProcurementRow;
import com.nuono.next.productselection.ProductSelectionGroupRow;
import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import com.nuono.next.productselection.ProductSelectionStoreScope;
import com.nuono.next.productselection.ProductSelectionUserContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface ProductSelectionMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {
                    "SELECT LAST_INSERT_ID()"
            },
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateProductManagementId(IdSequenceCommand command);

    default Long nextProductManagementId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateProductManagementId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("商品共享店铺 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  COALESCE(NULLIF(TRIM(u.real_name), ''), u.account_no) AS real_name,",
            "  u.level,",
            "  u.status",
            "FROM `user` u",
            "WHERE u.id = #{userId}",
            "  AND u.is_deleted = 0",
            "LIMIT 1"
    })
    ProductSelectionUserContext selectUserContext(@Param("userId") Long userId);

    @Select({
            "SELECT",
            "  us.id AS user_store_id,",
            "  us.user_id AS operator_user_id,",
            "  COALESCE(ls.owner_user_id, us.owner_user_id) AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  us.project_code,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  us.is_authorized AS authorized",
            "FROM (",
            "  SELECT",
            "    owner_site.id,",
            "    #{operatorUserId} AS user_id,",
            "    owner_project.user_id AS owner_user_id,",
            "    owner_site.project_code,",
            "    owner_site.project_name,",
            "    owner_site.store_code,",
            "    owner_site.site,",
            "    owner_site.is_authorized,",
            "    owner_site.gmt_updated,",
            "    0 AS scope_sort",
            "  FROM user_project owner_project",
            "  JOIN user_store owner_site",
            "    ON owner_site.user_id = owner_project.user_id",
            "   AND owner_site.project_code = owner_project.project_code",
            "   AND owner_site.is_deleted = 0",
            "  WHERE owner_project.user_id = #{operatorUserId}",
            "    AND owner_project.is_deleted = 0",
            "  UNION ALL",
            "  SELECT",
            "    member_site.id,",
            "    #{operatorUserId} AS user_id,",
            "    member_project.user_id AS owner_user_id,",
            "    member_site.project_code,",
            "    member_site.project_name,",
            "    member_site.store_code,",
            "    member_site.site,",
            "    member_site.is_authorized,",
            "    member_site.gmt_updated,",
            "    1 AS scope_sort",
            "  FROM user_project_access access",
            "  JOIN user_project member_project",
            "    ON member_project.id = access.project_id",
            "   AND member_project.is_deleted = 0",
            "  JOIN user_store member_site",
            "    ON member_site.user_id = member_project.user_id",
            "   AND member_site.project_code = member_project.project_code",
            "   AND member_site.is_deleted = 0",
            "  WHERE access.user_id = #{operatorUserId}",
            "    AND access.is_deleted = 0",
            ") us",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.store_code COLLATE utf8mb4_unicode_ci = us.store_code COLLATE utf8mb4_unicode_ci",
            " AND lss.is_deleted = b'0'",
            "LEFT JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "WHERE us.store_code = #{storeCode}",
            "ORDER BY us.is_authorized DESC, us.scope_sort ASC, us.id ASC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectVisibleStoreScope(
            @Param("operatorUserId") Long operatorUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT",
            "  us.id AS user_store_id,",
            "  us.user_id AS operator_user_id,",
            "  COALESCE(ls.owner_user_id, us.owner_user_id) AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  us.project_code,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  us.is_authorized AS authorized",
            "FROM (",
            "  SELECT",
            "    owner_site.id,",
            "    #{operatorUserId} AS user_id,",
            "    owner_project.user_id AS owner_user_id,",
            "    owner_site.project_code,",
            "    owner_site.project_name,",
            "    owner_site.store_code,",
            "    owner_site.site,",
            "    owner_site.is_authorized,",
            "    owner_site.gmt_updated,",
            "    0 AS scope_sort",
            "  FROM user_project owner_project",
            "  JOIN user_store owner_site",
            "    ON owner_site.user_id = owner_project.user_id",
            "   AND owner_site.project_code = owner_project.project_code",
            "   AND owner_site.is_deleted = 0",
            "  WHERE owner_project.user_id = #{operatorUserId}",
            "    AND owner_project.is_deleted = 0",
            "  UNION ALL",
            "  SELECT",
            "    member_site.id,",
            "    #{operatorUserId} AS user_id,",
            "    member_project.user_id AS owner_user_id,",
            "    member_site.project_code,",
            "    member_site.project_name,",
            "    member_site.store_code,",
            "    member_site.site,",
            "    member_site.is_authorized,",
            "    member_site.gmt_updated,",
            "    1 AS scope_sort",
            "  FROM user_project_access access",
            "  JOIN user_project member_project",
            "    ON member_project.id = access.project_id",
            "   AND member_project.is_deleted = 0",
            "  JOIN user_store member_site",
            "    ON member_site.user_id = member_project.user_id",
            "   AND member_site.project_code = member_project.project_code",
            "   AND member_site.is_deleted = 0",
            "  WHERE access.user_id = #{operatorUserId}",
            "    AND access.is_deleted = 0",
            ") us",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.store_code COLLATE utf8mb4_unicode_ci = us.store_code COLLATE utf8mb4_unicode_ci",
            " AND lss.is_deleted = b'0'",
            "LEFT JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "ORDER BY us.is_authorized DESC, us.scope_sort ASC, us.gmt_updated DESC, us.id DESC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectFirstVisibleStoreScope(@Param("operatorUserId") Long operatorUserId);

    @Select({
            "SELECT",
            "  NULL AS user_store_id,",
            "  #{operatorUserId} AS operator_user_id,",
            "  ls.owner_user_id AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  ls.project_code,",
            "  ls.project_name,",
            "  lss.store_code,",
            "  lss.site,",
            "  1 AS authorized",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{operatorUserId}",
            "  AND lss.store_code = #{storeCode}",
            "  AND lss.is_deleted = b'0'",
            "ORDER BY ls.id ASC, lss.id ASC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectOwnedLogicalStoreScope(
            @Param("operatorUserId") Long operatorUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT",
            "  NULL AS user_store_id,",
            "  #{operatorUserId} AS operator_user_id,",
            "  ls.owner_user_id AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  ls.project_code,",
            "  ls.project_name,",
            "  lss.store_code,",
            "  lss.site,",
            "  1 AS authorized",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{operatorUserId}",
            "  AND ls.is_deleted = b'0'",
            "ORDER BY lss.is_reference_site DESC, ls.id ASC, lss.id ASC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectFirstOwnedLogicalStoreScope(@Param("operatorUserId") Long operatorUserId);

    @Select({
            "SELECT",
            "  us.id AS user_store_id,",
            "  us.user_id AS operator_user_id,",
            "  COALESCE(ls.owner_user_id, us.user_id) AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  us.project_code,",
            "  us.project_name,",
            "  us.store_code,",
            "  us.site,",
            "  us.is_authorized AS authorized",
            "FROM user_project up",
            "JOIN user_store us",
            "  ON us.user_id = up.user_id",
            " AND us.project_code = up.project_code",
            " AND us.is_deleted = 0",
            "LEFT JOIN logical_store_site lss",
            "  ON lss.store_code COLLATE utf8mb4_unicode_ci = us.store_code COLLATE utf8mb4_unicode_ci",
            " AND lss.is_deleted = b'0'",
            "LEFT JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "WHERE us.store_code = #{storeCode}",
            "  AND up.is_deleted = 0",
            "ORDER BY us.is_authorized DESC, up.user_id ASC, us.id ASC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectAnyStoreScope(@Param("storeCode") String storeCode);

    @Select({
            "SELECT",
            "  NULL AS user_store_id,",
            "  ls.owner_user_id AS operator_user_id,",
            "  ls.owner_user_id AS owner_user_id,",
            "  ls.id AS logical_store_id,",
            "  ls.project_code,",
            "  ls.project_name,",
            "  lss.store_code,",
            "  lss.site,",
            "  1 AS authorized",
            "FROM logical_store_site lss",
            "JOIN logical_store ls",
            "  ON ls.id = lss.logical_store_id",
            " AND ls.is_deleted = b'0'",
            "WHERE lss.store_code = #{storeCode}",
            "  AND lss.is_deleted = b'0'",
            "ORDER BY ls.owner_user_id ASC, ls.id ASC, lss.id ASC",
            "LIMIT 1"
    })
    ProductSelectionStoreScope selectLogicalStoreScope(@Param("storeCode") String storeCode);

    @Select({
            "SELECT COUNT(1)",
            "FROM logical_store_site lss",
            "WHERE lss.logical_store_id = #{logicalStoreId}",
            "  AND lss.is_deleted = b'0'",
            "  AND (",
            "    EXISTS (",
            "      SELECT 1",
            "      FROM logical_store owner_store",
            "      WHERE owner_store.id = lss.logical_store_id",
            "        AND owner_store.owner_user_id = #{operatorUserId}",
            "        AND owner_store.is_deleted = b'0'",
            "    )",
            "    OR EXISTS (",
            "      SELECT 1",
            "      FROM user_project owner_project",
            "      JOIN user_store owner_site",
            "        ON owner_site.user_id = owner_project.user_id",
            "       AND owner_site.project_code = owner_project.project_code",
            "       AND owner_site.is_deleted = 0",
            "      WHERE owner_project.user_id = #{operatorUserId}",
            "        AND owner_project.is_deleted = 0",
            "        AND owner_site.store_code COLLATE utf8mb4_unicode_ci = lss.store_code COLLATE utf8mb4_unicode_ci",
            "      UNION ALL",
            "      SELECT 1",
            "      FROM user_project_access access",
            "      JOIN user_project member_project",
            "        ON member_project.id = access.project_id",
            "       AND member_project.is_deleted = 0",
            "      JOIN user_store member_site",
            "        ON member_site.user_id = member_project.user_id",
            "       AND member_site.project_code = member_project.project_code",
            "       AND member_site.is_deleted = 0",
            "      WHERE access.user_id = #{operatorUserId}",
            "        AND access.is_deleted = 0",
            "        AND member_site.store_code COLLATE utf8mb4_unicode_ci = lss.store_code COLLATE utf8mb4_unicode_ci",
            "    )",
            "  )"
    })
    int countVisibleLogicalStoreSites(
            @Param("operatorUserId") Long operatorUserId,
            @Param("logicalStoreId") Long logicalStoreId
    );

    @Select({
            "SELECT id",
            "FROM logical_store",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND project_code = #{projectCode}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    Long selectLogicalStoreId(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );

    default Long nextLogicalStoreId() {
        return nextProductManagementId("logical_store", 50000L);
    }

    default Long nextLogicalStoreSiteId() {
        return nextProductManagementId("logical_store_site", 51000L);
    }

    @Insert({
            "INSERT INTO logical_store (",
            "  id, owner_user_id, manager_user_id, project_code, project_name, status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, NULL, #{projectCode}, #{projectName}, 'ACTIVE',",
            "  b'0', #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  project_name = VALUES(project_name),",
            "  status = 'ACTIVE',",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertLogicalStore(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode,
            @Param("projectName") String projectName,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO logical_store_site (",
            "  id, logical_store_id, store_code, site, is_reference_site, is_mounted, site_status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{logicalStoreId}, #{storeCode}, #{site}, b'1', b'1', 'ACTIVE',",
            "  b'0', #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  logical_store_id = VALUES(logical_store_id),",
            "  site = VALUES(site),",
            "  is_reference_site = VALUES(is_reference_site),",
            "  is_mounted = b'1',",
            "  site_status = 'ACTIVE',",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertLogicalStoreSite(
            @Param("id") Long id,
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("storeCode") String storeCode,
            @Param("site") String site,
            @Param("updatedBy") Long updatedBy
    );

    default Long nextSourceCollectionId() {
        return nextProductManagementId("product_selection_source_collection", 86000L);
    }

    default Long nextAnalysisItemId() {
        return nextProductManagementId("product_selection_analysis_item", 89000L);
    }

    default Long nextSelectionGroupId() {
        return nextProductManagementId("product_selection_group", 91000L);
    }

    default Long nextSelectionGroupMaterialId() {
        return nextProductManagementId("product_selection_group_material", 92000L);
    }

    default Long nextSelectionGroupCompetitorId() {
        return nextProductManagementId("product_selection_group_competitor", 93000L);
    }

    default Long nextSelectionGroupProfitSnapshotId() {
        return nextProductManagementId("product_selection_group_profit_snapshot", 94000L);
    }

    @Insert({
            "INSERT INTO product_selection_source_collection (",
            "  id, owner_user_id, logical_store_id, site_code, collection_no, source_type, collection_source, plugin_batch_id, plugin_item_key, extractor_version, source_platform, source_url, page_url,",
            "  source_title, source_title_cn, source_title_ar, source_image_url, image_urls_json, price_summary, moq_hint, shipping_from,",
            "  brand_name, unit_count, color_name, spec_hints_json, category_links_json, spec_attribute_count, source_description_en, source_description_ar,",
            "  source_selling_points_en_json, source_selling_points_ar_json,",
            "  selected_text, selected_text_ar, notes, status, failure_code, failure_message, collected_at, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.siteCode}, #{row.collectionNo}, #{row.sourceType}, #{row.collectionSource}, #{row.pluginBatchId}, #{row.pluginItemKey}, #{row.extractorVersion}, #{row.sourcePlatform}, #{row.sourceUrl}, #{row.pageUrl},",
            "  #{row.sourceTitle}, #{row.sourceTitleCn}, #{row.sourceTitleAr}, #{row.sourceImageUrl}, #{row.imageUrlsJson}, #{row.priceSummary}, #{row.moqHint}, #{row.shippingFrom},",
            "  #{row.brandName}, #{row.unitCount}, #{row.colorName}, #{row.specHintsJson}, #{row.categoryLinksJson}, #{row.specAttributeCount}, #{row.sourceDescriptionEn}, #{row.sourceDescriptionAr},",
            "  #{row.sourceSellingPointsEnJson}, #{row.sourceSellingPointsArJson},",
            "  #{row.selectedText}, #{row.selectedTextAr}, #{row.notes}, #{row.status}, #{row.failureCode}, #{row.failureMessage}, NOW(), b'0', #{row.createdBy}, #{row.updatedBy},",
            "  NOW(), NOW()",
            ")"
    })
    int insertSourceCollection(@Param("row") ProductSelectionSourceCollectionRow row);

    @Select({
            "SELECT",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  source.site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.plugin_batch_id,",
            "  source.plugin_item_key,",
            "  source.extractor_version,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  DATE_FORMAT(source.next_run_at, '%Y-%m-%d %H:%i') AS next_run_at,",
            "  source.created_by,",
            "  source.updated_by",
            "FROM product_selection_source_collection source",
            "WHERE source.status = 'running'",
            "  AND source.source_type = 'marketplace-url'",
            "  AND source.is_deleted = b'0'",
            "  AND (source.next_run_at IS NULL OR source.next_run_at <= NOW())",
            "  AND (source.locked_at IS NULL OR source.locked_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE))",
            "ORDER BY source.collected_at ASC, source.id ASC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionSourceCollectionRow> listRunningSourceCollections(@Param("limit") Integer limit);

    @Update({
            "UPDATE product_selection_source_collection",
            "SET locked_at = NOW(),",
            "    locked_by = #{lockedBy},",
            "    attempt_count = attempt_count + 1,",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = 'running'",
            "  AND source_type = 'marketplace-url'",
            "  AND is_deleted = b'0'",
            "  AND (next_run_at IS NULL OR next_run_at <= NOW())",
            "  AND (locked_at IS NULL OR locked_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE))"
    })
    int claimSourceCollection(
            @Param("id") Long id,
            @Param("lockedBy") String lockedBy
    );

    @Select({
            "SELECT",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  source.site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.plugin_batch_id,",
            "  source.plugin_item_key,",
            "  source.extractor_version,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  DATE_FORMAT(source.next_run_at, '%Y-%m-%d %H:%i') AS next_run_at,",
            "  source.created_by,",
            "  source.updated_by,",
            "  COALESCE(NULLIF(TRIM(operator.real_name), ''), operator.account_no, '') AS created_by_name,",
            "  COALESCE(store.project_name, '') AS store_name,",
            "  (",
            "    SELECT site.store_code",
            "    FROM logical_store_site site",
            "    WHERE site.logical_store_id = source.logical_store_id",
            "      AND site.is_deleted = b'0'",
            "      AND (source.site_code IS NULL OR source.site_code = '' OR site.site = source.site_code)",
            "    ORDER BY (site.site = source.site_code) DESC, site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_source_collection source",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE source.id = #{id}",
            "  AND source.is_deleted = b'0'"
    })
    ProductSelectionSourceCollectionRow selectSourceCollectionById(@Param("id") Long id);

    @Update({
            "UPDATE product_selection_source_collection",
            "SET source_platform = #{row.sourcePlatform},",
            "    source_url = #{row.sourceUrl},",
            "    page_url = #{row.pageUrl},",
            "    source_title = #{row.sourceTitle},",
            "    source_title_cn = #{row.sourceTitleCn},",
            "    source_title_ar = #{row.sourceTitleAr},",
            "    source_image_url = #{row.sourceImageUrl},",
            "    image_urls_json = #{row.imageUrlsJson},",
            "    price_summary = #{row.priceSummary},",
            "    moq_hint = #{row.moqHint},",
            "    shipping_from = #{row.shippingFrom},",
            "    brand_name = #{row.brandName},",
            "    unit_count = #{row.unitCount},",
            "    color_name = #{row.colorName},",
            "    spec_hints_json = #{row.specHintsJson},",
            "    category_links_json = #{row.categoryLinksJson},",
            "    spec_attribute_count = #{row.specAttributeCount},",
            "    source_description_en = #{row.sourceDescriptionEn},",
            "    source_description_ar = #{row.sourceDescriptionAr},",
            "    source_selling_points_en_json = #{row.sourceSellingPointsEnJson},",
            "    source_selling_points_ar_json = #{row.sourceSellingPointsArJson},",
            "    selected_text = #{row.selectedText},",
            "    selected_text_ar = #{row.selectedTextAr},",
            "    status = 'success',",
            "    failure_code = NULL,",
            "    failure_message = NULL,",
            "    locked_at = NULL,",
            "    locked_by = NULL,",
            "    next_run_at = NULL,",
            "    collected_at = NOW(),",
            "    updated_by = #{row.updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.id}",
            "  AND status = 'running'",
            "  AND locked_by = #{lockedBy}",
            "  AND is_deleted = b'0'"
    })
    int markSourceCollectionSuccess(
            @Param("row") ProductSelectionSourceCollectionRow row,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_source_collection",
            "SET status = 'failed',",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_at = NULL,",
            "    locked_by = NULL,",
            "    next_run_at = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = 'running'",
            "  AND locked_by = #{lockedBy}",
            "  AND is_deleted = b'0'"
    })
    int markSourceCollectionFailed(
            @Param("id") Long id,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy
    );

    @Update({
            "UPDATE product_selection_source_collection",
            "SET failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    locked_at = NULL,",
            "    locked_by = NULL,",
            "    next_run_at = #{nextRunAt},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND status = 'running'",
            "  AND locked_by = #{lockedBy}",
            "  AND is_deleted = b'0'"
    })
    int markSourceCollectionRiskBackoff(
            @Param("id") Long id,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("updatedBy") Long updatedBy,
            @Param("lockedBy") String lockedBy,
            @Param("nextRunAt") LocalDateTime nextRunAt
    );

    @Update({
            "UPDATE product_selection_source_collection",
            "SET status = 'running',",
            "    failure_code = NULL,",
            "    failure_message = NULL,",
            "    locked_at = NULL,",
            "    locked_by = NULL,",
            "    attempt_count = 0,",
            "    next_run_at = NULL,",
            "    collected_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int markSourceCollectionRunning(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    @Select({
            "SELECT",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  source.site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  source.created_by,",
            "  source.updated_by,",
            "  COALESCE(NULLIF(TRIM(operator.real_name), ''), operator.account_no, '') AS created_by_name,",
            "  COALESCE(store.project_name, '') AS store_name,",
            "  (",
            "    SELECT site.store_code",
            "    FROM logical_store_site site",
            "    WHERE site.logical_store_id = source.logical_store_id",
            "      AND site.is_deleted = b'0'",
            "      AND (#{siteCode} IS NULL OR #{siteCode} = '' OR site.site = source.site_code)",
            "    ORDER BY (site.site = source.site_code) DESC, site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_source_collection source",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE source.logical_store_id = #{logicalStoreId}",
            "  AND (#{siteCode} IS NULL OR #{siteCode} = '' OR source.site_code = #{siteCode})",
            "  AND source.is_deleted = b'0'",
            "  AND (source.source_type IS NULL OR source.source_type != 'purchase-order-product')",
            "ORDER BY source.collected_at DESC, source.id DESC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionSourceCollectionRow> listSourceCollections(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("siteCode") String siteCode,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT",
            "  grp.id AS group_id,",
            "  grp.group_no,",
            "  grp.owner_user_id,",
            "  grp.logical_store_id,",
            "  grp.site_code,",
            "  grp.group_name,",
            "  grp.status AS group_status,",
            "  COUNT(source.id) AS material_count,",
            "  grp.created_by,",
            "  grp.updated_by",
            "FROM product_selection_group grp",
            "LEFT JOIN product_selection_group_material material",
            "  ON material.group_id = grp.id",
            " AND material.is_deleted = b'0'",
            "LEFT JOIN product_selection_source_collection source",
            "  ON source.id = material.source_collection_id",
            " AND source.is_deleted = b'0'",
            "WHERE grp.id = #{groupId}",
            "  AND grp.is_deleted = b'0'",
            "GROUP BY grp.id, grp.group_no, grp.owner_user_id, grp.logical_store_id, grp.site_code,",
            "  grp.group_name, grp.status, grp.created_by, grp.updated_by",
            "LIMIT 1"
    })
    ProductSelectionGroupRow selectGroupById(@Param("groupId") Long groupId);

    @Select({
            "SELECT",
            "  grp.id AS group_id,",
            "  grp.group_no,",
            "  grp.owner_user_id,",
            "  grp.logical_store_id,",
            "  grp.site_code,",
            "  grp.group_name,",
            "  grp.status AS group_status,",
            "  COUNT(source.id) AS material_count,",
            "  grp.created_by,",
            "  grp.updated_by",
            "FROM product_selection_group grp",
            "LEFT JOIN product_selection_group_material material",
            "  ON material.group_id = grp.id",
            " AND material.is_deleted = b'0'",
            "LEFT JOIN product_selection_source_collection source",
            "  ON source.id = material.source_collection_id",
            " AND source.is_deleted = b'0'",
            "WHERE grp.logical_store_id = #{logicalStoreId}",
            "  AND (#{siteCode} IS NULL OR #{siteCode} = '' OR grp.site_code = #{siteCode})",
            "  AND grp.is_deleted = b'0'",
            "GROUP BY grp.id, grp.group_no, grp.owner_user_id, grp.logical_store_id, grp.site_code,",
            "  grp.group_name, grp.status, grp.created_by, grp.updated_by",
            "HAVING COUNT(source.id) > 0",
            "ORDER BY grp.gmt_create DESC, grp.id DESC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionGroupRow> listSelectionGroups(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("siteCode") String siteCode,
            @Param("limit") Integer limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  material.id AS material_id,",
            "  material.group_id,",
            "  material.source_collection_id,",
            "  material.status AS material_status,",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  source.site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  source.created_by,",
            "  source.updated_by,",
            "  COALESCE(NULLIF(TRIM(operator.real_name), ''), operator.account_no, '') AS created_by_name,",
            "  COALESCE(store.project_name, '') AS store_name,",
            "  (",
            "    SELECT site.store_code",
            "    FROM logical_store_site site",
            "    WHERE site.logical_store_id = source.logical_store_id",
            "      AND site.is_deleted = b'0'",
            "      AND (source.site_code IS NULL OR source.site_code = '' OR site.site = source.site_code)",
            "    ORDER BY (site.site = source.site_code) DESC, site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_group_material material",
            "JOIN product_selection_source_collection source",
            "  ON source.id = material.source_collection_id",
            " AND source.is_deleted = b'0'",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE material.is_deleted = b'0'",
            "  AND material.group_id IN",
            "  <foreach collection='groupIds' item='groupId' open='(' separator=',' close=')'>",
            "    #{groupId}",
            "  </foreach>",
            "ORDER BY material.group_id ASC, material.gmt_create ASC, material.id ASC",
            "</script>"
    })
    List<ProductSelectionGroupMaterialRow> listGroupMaterialsByGroupIds(@Param("groupIds") List<Long> groupIds);

    @Select({
            "<script>",
            "SELECT",
            "  competitor.id AS competitor_id,",
            "  competitor.group_id,",
            "  competitor.competitor_url,",
            "  competitor.note,",
            "  competitor.fetch_status,",
            "  competitor.fetched_payload_json,",
            "  DATE_FORMAT(competitor.fetched_at, '%Y-%m-%d %H:%i') AS fetched_at,",
            "  competitor.created_by,",
            "  competitor.updated_by",
            "FROM product_selection_group_competitor competitor",
            "WHERE competitor.is_deleted = b'0'",
            "  AND competitor.group_id IN",
            "  <foreach collection='groupIds' item='groupId' open='(' separator=',' close=')'>",
            "    #{groupId}",
            "  </foreach>",
            "ORDER BY competitor.group_id ASC, competitor.id ASC",
            "</script>"
    })
    List<ProductSelectionGroupCompetitorRow> listGroupCompetitorsByGroupIds(@Param("groupIds") List<Long> groupIds);

    @Select({
            "SELECT",
            "  competitor.id AS competitor_id,",
            "  competitor.group_id,",
            "  competitor.competitor_url,",
            "  competitor.note,",
            "  competitor.fetch_status,",
            "  competitor.fetched_payload_json,",
            "  DATE_FORMAT(competitor.fetched_at, '%Y-%m-%d %H:%i') AS fetched_at,",
            "  competitor.created_by,",
            "  competitor.updated_by",
            "FROM product_selection_group_competitor competitor",
            "WHERE competitor.is_deleted = b'0'",
            "  AND competitor.group_id = #{groupId}",
            "  AND competitor.id = #{competitorId}",
            "LIMIT 1"
    })
    ProductSelectionGroupCompetitorRow selectGroupCompetitorById(
            @Param("groupId") Long groupId,
            @Param("competitorId") Long competitorId
    );

    @Select({
            "SELECT",
            "  material.id AS material_id,",
            "  material.group_id,",
            "  material.source_collection_id,",
            "  material.owner_user_id,",
            "  material.logical_store_id,",
            "  material.site_code,",
            "  material.status AS material_status,",
            "  material.created_by,",
            "  material.updated_by",
            "FROM product_selection_group_material material",
            "WHERE material.source_collection_id = #{sourceCollectionId}",
            "  AND material.is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductSelectionGroupMaterialRow selectActiveGroupMaterialBySourceCollectionId(
            @Param("sourceCollectionId") Long sourceCollectionId
    );

    @Insert({
            "INSERT INTO product_selection_group (",
            "  id, owner_user_id, logical_store_id, site_code, group_no, group_name, status, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.groupId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.siteCode}, #{row.groupNo}, #{row.groupName}, #{row.groupStatus}, b'0',",
            "  #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSelectionGroup(@Param("row") ProductSelectionGroupRow row);

    @Insert({
            "INSERT INTO product_selection_group_material (",
            "  id, group_id, source_collection_id, owner_user_id, logical_store_id, site_code, status, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.materialId}, #{row.groupId}, #{row.sourceCollectionId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.siteCode}, #{row.materialStatus}, b'0',",
            "  #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSelectionGroupMaterial(@Param("row") ProductSelectionGroupMaterialRow row);

    @Update({
            "UPDATE product_selection_group",
            "SET group_name = #{groupName},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{groupId}",
            "  AND is_deleted = b'0'"
    })
    int updateSelectionGroupName(
            @Param("groupId") Long groupId,
            @Param("groupName") String groupName,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_group_competitor",
            "SET is_deleted = b'1',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE group_id = #{groupId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteSelectionGroupCompetitors(
            @Param("groupId") Long groupId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE product_selection_group_competitor",
            "SET is_deleted = b'1',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE group_id = #{groupId}",
            "  AND id = #{competitorId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteSelectionGroupCompetitor(
            @Param("groupId") Long groupId,
            @Param("competitorId") Long competitorId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO product_selection_group_competitor (",
            "  id, group_id, competitor_url, note, fetch_status, fetched_payload_json, fetched_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.competitorId}, #{row.groupId}, #{row.competitorUrl}, #{row.note}, #{row.fetchStatus}, #{row.fetchedPayloadJson},",
            "  CASE WHEN #{row.fetchedAt} IS NULL OR #{row.fetchedAt} = '' THEN NULL ELSE STR_TO_DATE(#{row.fetchedAt}, '%Y-%m-%d %H:%i') END,",
            "  b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSelectionGroupCompetitor(@Param("row") ProductSelectionGroupCompetitorRow row);

    @Update({
            "UPDATE product_selection_group_competitor",
            "SET competitor_url = #{row.competitorUrl},",
            "    note = #{row.note},",
            "    fetch_status = #{row.fetchStatus},",
            "    fetched_payload_json = #{row.fetchedPayloadJson},",
            "    fetched_at = CASE WHEN #{row.fetchedAt} IS NULL OR #{row.fetchedAt} = '' THEN NULL ELSE STR_TO_DATE(#{row.fetchedAt}, '%Y-%m-%d %H:%i') END,",
            "    updated_by = #{row.updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{row.competitorId}",
            "  AND group_id = #{row.groupId}",
            "  AND is_deleted = b'0'"
    })
    int updateSelectionGroupCompetitorSnapshot(@Param("row") ProductSelectionGroupCompetitorRow row);

    @Select({
            "SELECT",
            "  group_id,",
            "  ali1688_purchase_url,",
            "  purchase_price_rmb,",
            "  status AS procurement_status,",
            "  created_by,",
            "  updated_by",
            "FROM product_selection_group_procurement",
            "WHERE group_id = #{groupId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductSelectionGroupProcurementRow selectGroupProcurementByGroupId(@Param("groupId") Long groupId);

    @Insert({
            "INSERT INTO product_selection_group_procurement (",
            "  group_id, ali1688_purchase_url, purchase_price_rmb, status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{groupId}, #{ali1688PurchaseUrl}, #{purchasePrice}, 'active', b'0', #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  ali1688_purchase_url = VALUES(ali1688_purchase_url),",
            "  purchase_price_rmb = VALUES(purchase_price_rmb),",
            "  status = 'active',",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertSelectionGroupProcurement(
            @Param("groupId") Long groupId,
            @Param("ali1688PurchaseUrl") String ali1688PurchaseUrl,
            @Param("purchasePrice") BigDecimal purchasePrice,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  id AS snapshot_id,",
            "  group_id,",
            "  currency_code,",
            "  profit_amount,",
            "  profit_margin,",
            "  snapshot_json,",
            "  status,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  created_by,",
            "  updated_by",
            "FROM product_selection_group_profit_snapshot",
            "WHERE group_id = #{groupId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_create DESC, id DESC",
            "LIMIT 1"
    })
    ProductSelectionGroupProfitSnapshotRow selectLatestSelectionGroupProfitSnapshot(@Param("groupId") Long groupId);

    @Insert({
            "INSERT INTO product_selection_group_profit_snapshot (",
            "  id, group_id, currency_code, profit_amount, profit_margin, snapshot_json, status,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.snapshotId}, #{row.groupId}, #{row.currencyCode}, #{row.profitAmount}, #{row.profitMargin},",
            "  #{row.snapshotJson}, #{row.status},",
            "  b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSelectionGroupProfitSnapshot(@Param("row") ProductSelectionGroupProfitSnapshotRow row);

    @Select({
            "SELECT",
            "  item.id AS analysis_item_id,",
            "  COALESCE(item.project_id, item.id) AS project_id,",
            "  COALESCE(NULLIF(TRIM(item.project_name), ''), '') AS project_name,",
            "  item.source_collection_id,",
            "  item.owner_user_id,",
            "  item.logical_store_id,",
            "  item.site_code,",
            "  item.ali1688_purchase_url,",
            "  item.purchase_price_rmb,",
            "  item.status AS analysis_status,",
            "  item.created_by,",
            "  item.updated_by",
            "FROM product_selection_analysis_item item",
            "WHERE item.source_collection_id = #{sourceCollectionId}",
            "  AND item.is_deleted = b'0'",
            "LIMIT 1"
    })
    ProductSelectionAnalysisItemRow selectAnalysisItemBySourceCollectionId(
            @Param("sourceCollectionId") Long sourceCollectionId
    );

    @Insert({
            "INSERT INTO product_selection_analysis_item (",
            "  id, owner_user_id, logical_store_id, site_code, project_id, project_name, source_collection_id,",
            "  ali1688_purchase_url, purchase_price_rmb, status, is_deleted,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.analysisItemId}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.siteCode}, #{row.projectId}, #{row.projectName}, #{row.sourceCollectionId},",
            "  #{row.ali1688PurchaseUrl}, #{row.purchasePriceRmb}, #{row.analysisStatus}, b'0',",
            "  #{row.createdBy}, #{row.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertAnalysisItem(@Param("row") ProductSelectionAnalysisItemRow row);

    @Update({
            "UPDATE product_selection_analysis_item",
            "SET ali1688_purchase_url = #{ali1688PurchaseUrl},",
            "    purchase_price_rmb = #{purchasePrice},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE source_collection_id = #{sourceCollectionId}",
            "  AND is_deleted = b'0'"
    })
    int updateAnalysisItemProcurement(
            @Param("sourceCollectionId") Long sourceCollectionId,
            @Param("ali1688PurchaseUrl") String ali1688PurchaseUrl,
            @Param("purchasePrice") BigDecimal purchasePrice,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  item.id AS analysis_item_id,",
            "  COALESCE(item.project_id, item.id) AS project_id,",
            "  COALESCE(NULLIF(TRIM(item.project_name), ''), NULLIF(TRIM(source.source_title_cn), ''), NULLIF(TRIM(source.source_title), ''), CONCAT('选品项目 ', item.id)) AS project_name,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_selection_analysis_item peer",
            "    WHERE peer.is_deleted = b'0'",
            "      AND COALESCE(peer.project_id, peer.id) = COALESCE(item.project_id, item.id)",
            "  ) AS project_material_count,",
            "  item.source_collection_id,",
            "  item.ali1688_purchase_url,",
            "  item.purchase_price_rmb,",
            "  item.status AS analysis_status,",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  COALESCE(item.site_code, source.site_code) AS site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  source.created_by,",
            "  source.updated_by,",
            "  COALESCE(NULLIF(TRIM(operator.real_name), ''), operator.account_no, '') AS created_by_name,",
            "  COALESCE(store.project_name, '') AS store_name,",
            "  (",
            "    SELECT site.store_code",
            "    FROM logical_store_site site",
            "    WHERE site.logical_store_id = source.logical_store_id",
            "      AND site.is_deleted = b'0'",
            "      AND (#{siteCode} IS NULL OR #{siteCode} = '' OR site.site = COALESCE(item.site_code, source.site_code))",
            "    ORDER BY (site.site = COALESCE(item.site_code, source.site_code)) DESC, site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_analysis_item item",
            "JOIN product_selection_source_collection source ON source.id = item.source_collection_id AND source.is_deleted = b'0'",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE item.logical_store_id = #{logicalStoreId}",
            "  AND (#{siteCode} IS NULL OR #{siteCode} = '' OR COALESCE(item.site_code, source.site_code) = #{siteCode})",
            "  AND item.is_deleted = b'0'",
            "  AND (source.source_type IS NULL OR source.source_type != 'purchase-order-product')",
            "ORDER BY item.gmt_create DESC, item.id DESC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionAnalysisItemRow> listAnalysisItems(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("siteCode") String siteCode,
            @Param("limit") Integer limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  item.id AS analysis_item_id,",
            "  COALESCE(item.project_id, item.id) AS project_id,",
            "  COALESCE(NULLIF(TRIM(item.project_name), ''), NULLIF(TRIM(source.source_title_cn), ''), NULLIF(TRIM(source.source_title), ''), CONCAT('选品项目 ', item.id)) AS project_name,",
            "  (",
            "    SELECT COUNT(*)",
            "    FROM product_selection_analysis_item peer",
            "    WHERE peer.is_deleted = b'0'",
            "      AND COALESCE(peer.project_id, peer.id) = COALESCE(item.project_id, item.id)",
            "  ) AS project_material_count,",
            "  item.source_collection_id,",
            "  item.ali1688_purchase_url,",
            "  item.purchase_price_rmb,",
            "  item.status AS analysis_status,",
            "  source.id,",
            "  source.owner_user_id,",
            "  source.logical_store_id,",
            "  COALESCE(item.site_code, source.site_code) AS site_code,",
            "  source.collection_no,",
            "  source.source_type,",
            "  source.collection_source,",
            "  source.source_platform,",
            "  source.source_url,",
            "  source.page_url,",
            "  source.source_title,",
            "  source.source_title_cn,",
            "  source.source_title_ar,",
            "  source.source_image_url,",
            "  source.image_urls_json,",
            "  source.price_summary,",
            "  source.moq_hint,",
            "  source.shipping_from,",
            "  source.brand_name,",
            "  source.unit_count,",
            "  source.color_name,",
            "  source.spec_hints_json,",
            "  source.category_links_json,",
            "  source.spec_attribute_count,",
            "  source.source_description_en,",
            "  source.source_description_ar,",
            "  source.source_selling_points_en_json,",
            "  source.source_selling_points_ar_json,",
            "  source.selected_text,",
            "  source.selected_text_ar,",
            "  source.notes,",
            "  source.status,",
            "  source.failure_code,",
            "  source.failure_message,",
            "  DATE_FORMAT(source.collected_at, '%Y-%m-%d %H:%i') AS collected_at,",
            "  source.created_by,",
            "  source.updated_by,",
            "  COALESCE(NULLIF(TRIM(operator.real_name), ''), operator.account_no, '') AS created_by_name,",
            "  COALESCE(store.project_name, '') AS store_name,",
            "  (",
            "    SELECT site.store_code",
            "    FROM logical_store_site site",
            "    WHERE site.logical_store_id = source.logical_store_id",
            "      AND site.is_deleted = b'0'",
            "      AND (COALESCE(item.site_code, source.site_code) IS NULL OR COALESCE(item.site_code, source.site_code) = '' OR site.site = COALESCE(item.site_code, source.site_code))",
            "    ORDER BY (site.site = COALESCE(item.site_code, source.site_code)) DESC, site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_analysis_item item",
            "JOIN product_selection_source_collection source ON source.id = item.source_collection_id AND source.is_deleted = b'0'",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE item.is_deleted = b'0'",
            "  AND item.source_collection_id IN",
            "  <foreach collection='sourceCollectionIds' item='sourceCollectionId' open='(' separator=',' close=')'>",
            "    #{sourceCollectionId}",
            "  </foreach>",
            "ORDER BY item.gmt_create DESC, item.id DESC",
            "</script>"
    })
    List<ProductSelectionAnalysisItemRow> listAnalysisItemsBySourceCollectionIds(
            @Param("sourceCollectionIds") List<Long> sourceCollectionIds
    );
}
