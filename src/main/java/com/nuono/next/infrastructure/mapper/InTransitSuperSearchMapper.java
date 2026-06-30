package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitSuperSearchCommands.InTransitSuperSearchQuery;
import com.nuono.next.intransit.InTransitSuperSearchRecords.SuperSearchItemRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InTransitSuperSearchMapper {

    String ACTIVE_BATCH_FILTER = "AND batch.batch_status NOT IN ('draft', 'warehouse_received', 'completed', 'cancelled')";

    @Select({
            "<script>",
            "SELECT",
            "line.psku AS psku,",
            "MAX(NULLIF(TRIM(line.product_name), '')) AS product_name,",
            "MAX(NULLIF(TRIM(pm.title_cache), '')) AS product_title,",
            "MAX(NULLIF(TRIM(pm.title_cn_cache), '')) AS product_title_cn,",
            "MAX(NULLIF(TRIM(pm.cover_image_url), '')) AS product_image_url,",
            "batch.id AS batch_id,",
            "MAX(batch.batch_reference_no) AS batch_reference_no,",
            "MAX(batch.raw_forwarder_name) AS raw_forwarder_name,",
            "MAX(forwarder.forwarder_name) AS standard_forwarder_name,",
            "MAX(batch.transport_mode) AS transport_mode,",
            "MAX(batch.batch_status) AS batch_status,",
            "MAX(batch.target_store_code) AS target_store_code,",
            "MAX(batch.target_site_code) AS target_site_code,",
            "MAX(batch.target_warehouse_name) AS target_warehouse_name,",
            "MAX(batch.source_created_at) AS source_created_at,",
            "(SELECT domestic.node_happened_at FROM in_transit_logistics_node domestic",
            "WHERE domestic.owner_user_id = batch.owner_user_id AND domestic.batch_id = batch.id",
            "AND domestic.node_status = 'handed_to_forwarder' AND domestic.description = '国内收货'",
            "AND domestic.is_deleted = b'0'",
            "ORDER BY domestic.node_happened_at ASC, domestic.id ASC LIMIT 1) AS domestic_received_at,",
            "MAX(batch.latest_node_happened_at) AS latest_node_happened_at,",
            "MAX(batch.latest_node_status) AS latest_node_status,",
            "MAX(batch.latest_node_description) AS latest_node_description,",
            "MAX(batch.eta_date) AS eta_date,",
            "COUNT(DISTINCT NULLIF(TRIM(line.box_no), '')) AS box_count,",
            "SUM(line.shipped_quantity) AS shipped_quantity_total,",
            "SUM(line.received_quantity) AS received_quantity_total,",
            "SUM(line.remaining_quantity) AS remaining_quantity_total",
            "FROM in_transit_goods_line line",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id",
            "AND batch.id = line.batch_id AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder forwarder ON forwarder.id = batch.standard_forwarder_id",
            "AND forwarder.owner_user_id = batch.owner_user_id AND forwarder.is_deleted = b'0'",
            PRODUCT_MASTER_JOIN,
            "WHERE line.owner_user_id = #{query.ownerUserId}",
            "AND line.is_deleted = b'0'",
            "<if test='query.includeHistory == false'>",
            ACTIVE_BATCH_FILTER,
            "</if>",
            ACCESS_SCOPE_CONDITIONS,
            PROJECT_CODE_CONDITIONS,
            "AND (line.psku LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR line.product_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%'))",
            "GROUP BY batch.id, line.psku",
            "ORDER BY COALESCE(MAX(batch.source_created_at), MAX(batch.gmt_create)) DESC, batch.id DESC, line.psku ASC",
            "LIMIT #{query.limit}",
            "</script>"
    })
    List<SuperSearchItemRow> searchInTransitProducts(@Param("query") InTransitSuperSearchQuery query);

    @Select({
            "<script>",
            "SELECT",
            "matched.psku AS psku,",
            "matched.product_name AS product_name,",
            "NULLIF(TRIM(pm.title_cache), '') AS product_title,",
            "NULLIF(TRIM(pm.title_cn_cache), '') AS product_title_cn,",
            "NULLIF(TRIM(pm.cover_image_url), '') AS product_image_url,",
            "matched.batch_id AS batch_id,",
            "matched.batch_reference_no AS batch_reference_no,",
            "matched.raw_forwarder_name AS raw_forwarder_name,",
            "matched.standard_forwarder_name AS standard_forwarder_name,",
            "matched.transport_mode AS transport_mode,",
            "matched.batch_status AS batch_status,",
            "matched.target_store_code AS target_store_code,",
            "matched.target_site_code AS target_site_code,",
            "matched.target_warehouse_name AS target_warehouse_name,",
            "matched.source_created_at AS source_created_at,",
            "(SELECT domestic.node_happened_at FROM in_transit_logistics_node domestic",
            "WHERE domestic.owner_user_id = matched.owner_user_id AND domestic.batch_id = matched.batch_id",
            "AND domestic.node_status = 'handed_to_forwarder' AND domestic.description = '国内收货'",
            "AND domestic.is_deleted = b'0'",
            "ORDER BY domestic.node_happened_at ASC, domestic.id ASC LIMIT 1) AS domestic_received_at,",
            "matched.latest_node_happened_at AS latest_node_happened_at,",
            "matched.latest_node_status AS latest_node_status,",
            "matched.latest_node_description AS latest_node_description,",
            "matched.eta_date AS eta_date,",
            "matched.box_count AS box_count,",
            "matched.shipped_quantity_total AS shipped_quantity_total,",
            "matched.received_quantity_total AS received_quantity_total,",
            "matched.remaining_quantity_total AS remaining_quantity_total",
            "FROM (",
            "SELECT",
            "line.owner_user_id AS owner_user_id,",
            "batch.id AS batch_id,",
            "line.psku AS psku,",
            "MAX(NULLIF(TRIM(line.product_name), '')) AS product_name,",
            "MAX(line.store_code) AS store_code,",
            "MAX(line.site_code) AS site_code,",
            "MAX(batch.batch_reference_no) AS batch_reference_no,",
            "MAX(batch.raw_forwarder_name) AS raw_forwarder_name,",
            "MAX(forwarder.forwarder_name) AS standard_forwarder_name,",
            "MAX(batch.transport_mode) AS transport_mode,",
            "MAX(batch.batch_status) AS batch_status,",
            "MAX(batch.target_store_code) AS target_store_code,",
            "MAX(batch.target_site_code) AS target_site_code,",
            "MAX(batch.target_warehouse_name) AS target_warehouse_name,",
            "MAX(batch.source_created_at) AS source_created_at,",
            "MAX(batch.gmt_create) AS gmt_create,",
            "MAX(batch.latest_node_happened_at) AS latest_node_happened_at,",
            "MAX(batch.latest_node_status) AS latest_node_status,",
            "MAX(batch.latest_node_description) AS latest_node_description,",
            "MAX(batch.eta_date) AS eta_date,",
            "COUNT(DISTINCT NULLIF(TRIM(line.box_no), '')) AS box_count,",
            "SUM(line.shipped_quantity) AS shipped_quantity_total,",
            "SUM(line.received_quantity) AS received_quantity_total,",
            "SUM(line.remaining_quantity) AS remaining_quantity_total",
            "FROM in_transit_goods_line line",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id",
            "AND batch.id = line.batch_id AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder forwarder ON forwarder.id = batch.standard_forwarder_id",
            "AND forwarder.owner_user_id = batch.owner_user_id AND forwarder.is_deleted = b'0'",
            "WHERE line.owner_user_id = #{query.ownerUserId}",
            "AND line.is_deleted = b'0'",
            "<if test='query.includeHistory == false'>",
            ACTIVE_BATCH_FILTER,
            "</if>",
            ACCESS_SCOPE_CONDITIONS,
            LINE_PROJECT_CODE_CONDITIONS,
            "AND (line.psku LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR line.product_name LIKE CONCAT('%', #{query.keyword}, '%'))",
            "GROUP BY batch.id, line.psku",
            "ORDER BY COALESCE(MAX(batch.source_created_at), MAX(batch.gmt_create)) DESC, batch.id DESC, line.psku ASC",
            "<choose>",
            "<when test='query.projectCode != null and query.projectCode != \"\"'>",
            "LIMIT 500",
            "</when>",
            "<otherwise>",
            "LIMIT #{query.limit}",
            "</otherwise>",
            "</choose>",
            ") matched",
            MATCHED_PRODUCT_MASTER_JOIN,
            MATCHED_PROJECT_CODE_CONDITIONS,
            "ORDER BY COALESCE(matched.source_created_at, matched.gmt_create) DESC, matched.batch_id DESC, matched.psku ASC",
            "LIMIT #{query.limit}",
            "</script>"
    })
    List<SuperSearchItemRow> searchLineMatchedInTransitProducts(@Param("query") InTransitSuperSearchQuery query);

    @Select({
            "<script>",
            "SELECT",
            "matched.psku AS psku,",
            "matched.product_name AS product_name,",
            "NULLIF(TRIM(pm.title_cache), '') AS product_title,",
            "NULLIF(TRIM(pm.title_cn_cache), '') AS product_title_cn,",
            "NULLIF(TRIM(pm.cover_image_url), '') AS product_image_url,",
            "matched.batch_id AS batch_id,",
            "matched.batch_reference_no AS batch_reference_no,",
            "matched.raw_forwarder_name AS raw_forwarder_name,",
            "matched.standard_forwarder_name AS standard_forwarder_name,",
            "matched.transport_mode AS transport_mode,",
            "matched.batch_status AS batch_status,",
            "matched.target_store_code AS target_store_code,",
            "matched.target_site_code AS target_site_code,",
            "matched.target_warehouse_name AS target_warehouse_name,",
            "matched.source_created_at AS source_created_at,",
            "(SELECT domestic.node_happened_at FROM in_transit_logistics_node domestic",
            "WHERE domestic.owner_user_id = matched.owner_user_id AND domestic.batch_id = matched.batch_id",
            "AND domestic.node_status = 'handed_to_forwarder' AND domestic.description = '国内收货'",
            "AND domestic.is_deleted = b'0'",
            "ORDER BY domestic.node_happened_at ASC, domestic.id ASC LIMIT 1) AS domestic_received_at,",
            "matched.latest_node_happened_at AS latest_node_happened_at,",
            "matched.latest_node_status AS latest_node_status,",
            "matched.latest_node_description AS latest_node_description,",
            "matched.eta_date AS eta_date,",
            "matched.box_count AS box_count,",
            "matched.shipped_quantity_total AS shipped_quantity_total,",
            "matched.received_quantity_total AS received_quantity_total,",
            "matched.remaining_quantity_total AS remaining_quantity_total",
            "FROM (",
            "SELECT",
            "line.owner_user_id AS owner_user_id,",
            "batch.id AS batch_id,",
            "line.psku AS psku,",
            "MAX(NULLIF(TRIM(line.product_name), '')) AS product_name,",
            "MAX(line.store_code) AS store_code,",
            "MAX(line.site_code) AS site_code,",
            "MAX(batch.batch_reference_no) AS batch_reference_no,",
            "MAX(batch.raw_forwarder_name) AS raw_forwarder_name,",
            "MAX(forwarder.forwarder_name) AS standard_forwarder_name,",
            "MAX(batch.transport_mode) AS transport_mode,",
            "MAX(batch.batch_status) AS batch_status,",
            "MAX(batch.target_store_code) AS target_store_code,",
            "MAX(batch.target_site_code) AS target_site_code,",
            "MAX(batch.target_warehouse_name) AS target_warehouse_name,",
            "MAX(batch.source_created_at) AS source_created_at,",
            "MAX(batch.gmt_create) AS gmt_create,",
            "MAX(batch.latest_node_happened_at) AS latest_node_happened_at,",
            "MAX(batch.latest_node_status) AS latest_node_status,",
            "MAX(batch.latest_node_description) AS latest_node_description,",
            "MAX(batch.eta_date) AS eta_date,",
            "COUNT(DISTINCT NULLIF(TRIM(line.box_no), '')) AS box_count,",
            "SUM(line.shipped_quantity) AS shipped_quantity_total,",
            "SUM(line.received_quantity) AS received_quantity_total,",
            "SUM(line.remaining_quantity) AS remaining_quantity_total",
            "FROM (",
            "SELECT pv.partner_sku AS partner_sku,",
            TITLE_MATCH_LEGACY_LINE_PSKU + " AS legacy_line_psku",
            "FROM logical_store title_ls",
            "JOIN product_master title_pm ON title_pm.logical_store_id = title_ls.id",
            "AND title_pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = title_pm.id AND pv.is_deleted = b'0'",
            "WHERE title_ls.owner_user_id = #{query.ownerUserId} AND title_ls.is_deleted = b'0'",
            "<if test='query.projectCode != null and query.projectCode != \"\"'>",
            "AND title_ls.project_code = #{query.projectCode}",
            "</if>",
            "AND (title_pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR title_pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%'))",
            "GROUP BY pv.partner_sku",
            "LIMIT 500",
            ") title_match",
            "JOIN in_transit_goods_line line ON line.owner_user_id = #{query.ownerUserId}",
            "AND line.is_deleted = b'0'",
            "AND (line.psku = title_match.partner_sku OR line.psku = title_match.legacy_line_psku)",
            "JOIN in_transit_batch batch ON batch.owner_user_id = line.owner_user_id",
            "AND batch.id = line.batch_id AND batch.is_deleted = b'0'",
            "LEFT JOIN in_transit_forwarder forwarder ON forwarder.id = batch.standard_forwarder_id",
            "AND forwarder.owner_user_id = batch.owner_user_id AND forwarder.is_deleted = b'0'",
            "WHERE line.owner_user_id = #{query.ownerUserId}",
            "<if test='query.includeHistory == false'>",
            ACTIVE_BATCH_FILTER,
            "</if>",
            ACCESS_SCOPE_CONDITIONS,
            LINE_PROJECT_CODE_CONDITIONS,
            "GROUP BY batch.id, line.psku",
            "ORDER BY COALESCE(MAX(batch.source_created_at), MAX(batch.gmt_create)) DESC, batch.id DESC, line.psku ASC",
            "LIMIT #{query.limit}",
            ") matched",
            MATCHED_PRODUCT_MASTER_JOIN,
            MATCHED_PROJECT_CODE_CONDITIONS,
            "ORDER BY COALESCE(matched.source_created_at, matched.gmt_create) DESC, matched.batch_id DESC, matched.psku ASC",
            "LIMIT #{query.limit}",
            "</script>"
    })
    List<SuperSearchItemRow> searchTitleMatchedInTransitProducts(@Param("query") InTransitSuperSearchQuery query);

    String ACCESS_SCOPE_CONDITIONS = ""
            + "<if test='query.accessScopeRestricted'> "
            + "AND ( "
            + "(batch.target_store_code IS NULL OR batch.target_store_code = '' OR batch.target_site_code IS NULL OR batch.target_site_code = '') "
            + "<if test='query.allowedStoreSites != null and query.allowedStoreSites.size() > 0'> "
            + "OR "
            + "<foreach collection='query.allowedStoreSites' item='scope' separator=' OR '> "
            + "(batch.target_store_code = #{scope.storeCode} AND batch.target_site_code = #{scope.siteCode}) "
            + "</foreach> "
            + "</if> "
            + ") "
            + "</if> ";

    String PROJECT_CODE_CONDITIONS = ""
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND (pm.id IS NOT NULL OR EXISTS ( "
            + "SELECT 1 FROM logical_store_site filter_lss "
            + "JOIN logical_store filter_ls ON filter_ls.id = filter_lss.logical_store_id "
            + "AND filter_ls.owner_user_id = line.owner_user_id AND filter_ls.is_deleted = b'0' "
            + "WHERE filter_ls.project_code = #{query.projectCode} "
            + "AND filter_lss.store_code = line.store_code AND filter_lss.is_deleted = b'0' "
            + "AND (line.site_code IS NULL OR line.site_code = '' OR filter_lss.site = line.site_code) "
            + "LIMIT 1)) "
            + "</if> ";

    String LINE_PROJECT_CODE_CONDITIONS = ""
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND EXISTS ( "
            + "SELECT 1 FROM logical_store_site filter_lss "
            + "JOIN logical_store filter_ls ON filter_ls.id = filter_lss.logical_store_id "
            + "AND filter_ls.owner_user_id = line.owner_user_id AND filter_ls.is_deleted = b'0' "
            + "WHERE filter_ls.project_code = #{query.projectCode} "
            + "AND filter_lss.store_code = line.store_code AND filter_lss.is_deleted = b'0' "
            + "AND (line.site_code IS NULL OR line.site_code = '' OR filter_lss.site = line.site_code) "
            + "LIMIT 1) "
            + "</if> ";

    String LINE_LEGACY_PSKU_ALIAS = "CASE WHEN line.psku REGEXP '^[A-Za-z]+B[0-9]+$' "
            + "THEN CONCAT(REGEXP_REPLACE(line.psku, 'B[0-9]+$', '', 1, 1, 'c'), "
            + "REGEXP_SUBSTR(line.psku, '[0-9]+$')) ELSE line.psku END";

    String MATCHED_LEGACY_PSKU_ALIAS = "CASE WHEN matched.psku REGEXP '^[A-Za-z]+B[0-9]+$' "
            + "THEN CONCAT(REGEXP_REPLACE(matched.psku, 'B[0-9]+$', '', 1, 1, 'c'), "
            + "REGEXP_SUBSTR(matched.psku, '[0-9]+$')) ELSE matched.psku END";

    String TITLE_MATCH_LEGACY_LINE_PSKU = "CASE WHEN pv.partner_sku REGEXP '^[A-Za-z]+[0-9]+$' "
            + "THEN CONCAT(REGEXP_REPLACE(pv.partner_sku, '[0-9]+$', '', 1, 1, 'c'), "
            + "'B', REGEXP_SUBSTR(pv.partner_sku, '[0-9]+$')) ELSE pv.partner_sku END";

    String PRODUCT_MASTER_JOIN = ""
            + "LEFT JOIN product_master pm ON pm.id = COALESCE( "
            + "(SELECT exact_pm.id "
            + "FROM logical_store_site exact_lss "
            + "JOIN logical_store exact_ls ON exact_ls.id = exact_lss.logical_store_id "
            + "AND exact_ls.owner_user_id = line.owner_user_id AND exact_ls.is_deleted = b'0' "
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND exact_ls.project_code = #{query.projectCode} "
            + "</if> "
            + "JOIN product_site_offer exact_pso ON exact_pso.site_id = exact_lss.id "
            + "AND exact_pso.is_deleted = b'0' "
            + "JOIN product_variant exact_pv ON exact_pv.id = exact_pso.variant_id "
            + "AND exact_pv.partner_sku IN (line.psku, " + LINE_LEGACY_PSKU_ALIAS + ") "
            + "AND exact_pv.is_deleted = b'0' "
            + "JOIN product_master exact_pm ON exact_pm.id = exact_pv.product_master_id "
            + "AND exact_pm.logical_store_id = exact_ls.id AND exact_pm.is_deleted = b'0' "
            + "WHERE exact_lss.store_code = line.store_code "
            + "AND exact_lss.site = line.site_code AND exact_lss.is_deleted = b'0' "
            + "ORDER BY exact_pm.cover_image_url IS NULL ASC, exact_pm.gmt_updated DESC, exact_pm.id DESC "
            + "LIMIT 1), "
            + "(SELECT fallback_pm.id "
            + "FROM logical_store fallback_ls "
            + "JOIN product_master fallback_pm ON fallback_pm.logical_store_id = fallback_ls.id "
            + "AND fallback_pm.is_deleted = b'0' "
            + "JOIN product_variant fallback_pv ON fallback_pv.product_master_id = fallback_pm.id "
            + "AND fallback_pv.partner_sku IN (line.psku, " + LINE_LEGACY_PSKU_ALIAS + ") "
            + "AND fallback_pv.is_deleted = b'0' "
            + "WHERE fallback_ls.owner_user_id = line.owner_user_id AND fallback_ls.is_deleted = b'0' "
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND fallback_ls.project_code = #{query.projectCode} "
            + "</if> "
            + "ORDER BY CASE WHEN fallback_pv.partner_sku = line.psku THEN 0 ELSE 1 END, "
            + "fallback_pm.cover_image_url IS NULL ASC, fallback_pm.gmt_updated DESC, fallback_pm.id DESC "
            + "LIMIT 1)) "
            + "AND pm.is_deleted = b'0' ";

    String MATCHED_PROJECT_CODE_CONDITIONS = ""
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "WHERE (pm.id IS NOT NULL OR EXISTS ( "
            + "SELECT 1 FROM logical_store_site filter_lss "
            + "JOIN logical_store filter_ls ON filter_ls.id = filter_lss.logical_store_id "
            + "AND filter_ls.owner_user_id = matched.owner_user_id AND filter_ls.is_deleted = b'0' "
            + "WHERE filter_ls.project_code = #{query.projectCode} "
            + "AND filter_lss.store_code = matched.store_code AND filter_lss.is_deleted = b'0' "
            + "AND (matched.site_code IS NULL OR matched.site_code = '' OR filter_lss.site = matched.site_code) "
            + "LIMIT 1)) "
            + "</if> ";

    String MATCHED_PRODUCT_MASTER_JOIN = ""
            + "LEFT JOIN product_master pm ON pm.id = COALESCE( "
            + "(SELECT exact_pm.id "
            + "FROM logical_store_site exact_lss "
            + "JOIN logical_store exact_ls ON exact_ls.id = exact_lss.logical_store_id "
            + "AND exact_ls.owner_user_id = matched.owner_user_id AND exact_ls.is_deleted = b'0' "
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND exact_ls.project_code = #{query.projectCode} "
            + "</if> "
            + "JOIN product_site_offer exact_pso ON exact_pso.site_id = exact_lss.id "
            + "AND exact_pso.is_deleted = b'0' "
            + "JOIN product_variant exact_pv ON exact_pv.id = exact_pso.variant_id "
            + "AND exact_pv.partner_sku IN (matched.psku, " + MATCHED_LEGACY_PSKU_ALIAS + ") "
            + "AND exact_pv.is_deleted = b'0' "
            + "JOIN product_master exact_pm ON exact_pm.id = exact_pv.product_master_id "
            + "AND exact_pm.logical_store_id = exact_ls.id AND exact_pm.is_deleted = b'0' "
            + "WHERE exact_lss.store_code = matched.store_code "
            + "AND exact_lss.site = matched.site_code AND exact_lss.is_deleted = b'0' "
            + "ORDER BY exact_pm.cover_image_url IS NULL ASC, exact_pm.gmt_updated DESC, exact_pm.id DESC "
            + "LIMIT 1), "
            + "(SELECT fallback_pm.id "
            + "FROM logical_store fallback_ls "
            + "JOIN product_master fallback_pm ON fallback_pm.logical_store_id = fallback_ls.id "
            + "AND fallback_pm.is_deleted = b'0' "
            + "JOIN product_variant fallback_pv ON fallback_pv.product_master_id = fallback_pm.id "
            + "AND fallback_pv.partner_sku IN (matched.psku, " + MATCHED_LEGACY_PSKU_ALIAS + ") "
            + "AND fallback_pv.is_deleted = b'0' "
            + "WHERE fallback_ls.owner_user_id = matched.owner_user_id AND fallback_ls.is_deleted = b'0' "
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND fallback_ls.project_code = #{query.projectCode} "
            + "</if> "
            + "ORDER BY CASE WHEN fallback_pv.partner_sku = matched.psku THEN 0 ELSE 1 END, "
            + "fallback_pm.cover_image_url IS NULL ASC, fallback_pm.gmt_updated DESC, fallback_pm.id DESC "
            + "LIMIT 1)) "
            + "AND pm.is_deleted = b'0' ";
}
