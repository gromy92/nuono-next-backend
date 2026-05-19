package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productselection.ProductSelectionSourceCollectionRow;
import com.nuono.next.productselection.ProductSelectionStoreScope;
import com.nuono.next.productselection.ProductSelectionUserContext;
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

    @Insert({
            "INSERT INTO product_selection_source_collection (",
            "  id, owner_user_id, logical_store_id, collection_no, source_type, source_platform, source_url, page_url,",
            "  source_title, source_title_cn, source_title_ar, source_image_url, image_urls_json, price_summary, moq_hint, shipping_from,",
            "  brand_name, unit_count, color_name, spec_hints_json, spec_attribute_count, source_description_en, source_description_ar,",
            "  source_selling_points_en_json, source_selling_points_ar_json,",
            "  selected_text, selected_text_ar, notes, status, failure_code, failure_message, collected_at, is_deleted, created_by, updated_by,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.id}, #{row.ownerUserId}, #{row.logicalStoreId}, #{row.collectionNo}, #{row.sourceType}, #{row.sourcePlatform}, #{row.sourceUrl}, #{row.pageUrl},",
            "  #{row.sourceTitle}, #{row.sourceTitleCn}, #{row.sourceTitleAr}, #{row.sourceImageUrl}, #{row.imageUrlsJson}, #{row.priceSummary}, #{row.moqHint}, #{row.shippingFrom},",
            "  #{row.brandName}, #{row.unitCount}, #{row.colorName}, #{row.specHintsJson}, #{row.specAttributeCount}, #{row.sourceDescriptionEn}, #{row.sourceDescriptionAr},",
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
            "  source.collection_no,",
            "  source.source_type,",
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
            "  source.updated_by",
            "FROM product_selection_source_collection source",
            "WHERE source.status = 'running'",
            "  AND source.source_type = 'marketplace-url'",
            "  AND source.is_deleted = b'0'",
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
            "  source.collection_no,",
            "  source.source_type,",
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
            "    ORDER BY site.is_reference_site DESC, site.id ASC",
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
            "SET status = 'running',",
            "    failure_code = NULL,",
            "    failure_message = NULL,",
            "    locked_at = NULL,",
            "    locked_by = NULL,",
            "    attempt_count = 0,",
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
            "  source.collection_no,",
            "  source.source_type,",
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
            "    ORDER BY site.is_reference_site DESC, site.id ASC",
            "    LIMIT 1",
            "  ) AS store_code",
            "FROM product_selection_source_collection source",
            "LEFT JOIN logical_store store ON store.id = source.logical_store_id AND store.is_deleted = b'0'",
            "LEFT JOIN `user` operator ON operator.id = source.created_by AND operator.is_deleted = 0",
            "WHERE source.logical_store_id = #{logicalStoreId}",
            "  AND source.is_deleted = b'0'",
            "ORDER BY source.collected_at DESC, source.id DESC",
            "LIMIT #{limit}"
    })
    List<ProductSelectionSourceCollectionRow> listSourceCollections(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("limit") Integer limit
    );
}
