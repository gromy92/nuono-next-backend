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
            "CASE WHEN COUNT(*) = COUNT(pm.id) AND COUNT(DISTINCT pm.id) = 1 THEN MIN(pm.id) END AS matched_product_id,",
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
            PRODUCT_MASTER_JOIN,
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
            "CASE WHEN COUNT(DISTINCT title_match.product_master_id) = 1 THEN MIN(title_match.product_master_id) END AS matched_product_id,",
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
            "SELECT DISTINCT title_pb.barcode AS barcode, title_pm.id AS product_master_id",
            "FROM logical_store title_ls",
            "JOIN product_master title_pm ON title_pm.logical_store_id = title_ls.id",
            "AND title_pm.is_deleted = b'0'",
            "JOIN product_barcode title_pb ON title_pb.product_master_id = title_pm.id",
            "AND title_pb.logical_store_id = title_pm.logical_store_id",
            "AND BINARY title_pb.partner_sku = BINARY title_pm.partner_sku",
            "AND title_pb.is_deleted = b'0'",
            "AND COALESCE(title_pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'",
            "WHERE title_ls.owner_user_id = #{query.ownerUserId} AND title_ls.is_deleted = b'0'",
            "<if test='query.projectCode != null and query.projectCode != \"\"'>",
            "AND title_ls.project_code = #{query.projectCode}",
            "</if>",
            "AND (title_pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')",
            "OR title_pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%'))",
            "AND 1 = (",
            "SELECT COUNT(DISTINCT identity_pb.logical_store_id, BINARY identity_pb.partner_sku)",
            "FROM product_barcode identity_pb",
            "JOIN product_master identity_pm ON identity_pm.id = identity_pb.product_master_id",
            "AND identity_pm.logical_store_id = identity_pb.logical_store_id",
            "AND BINARY identity_pm.partner_sku = BINARY identity_pb.partner_sku",
            "AND identity_pm.is_deleted = b'0'",
            "JOIN logical_store identity_ls ON identity_ls.id = identity_pb.logical_store_id",
            "AND identity_ls.owner_user_id = #{query.ownerUserId} AND identity_ls.is_deleted = b'0'",
            "WHERE identity_pb.barcode = title_pb.barcode",
            "AND BINARY identity_pb.barcode = BINARY title_pb.barcode",
            "AND identity_pb.is_deleted = b'0'",
            "AND COALESCE(identity_pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'",
            ")",
            "LIMIT 500",
            ") title_match",
            "JOIN in_transit_goods_line line ON line.owner_user_id = #{query.ownerUserId}",
            "AND line.is_deleted = b'0'",
            "AND line.sku = title_match.barcode",
            "AND BINARY line.sku = BINARY title_match.barcode",
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

    String PRODUCT_MASTER_JOIN = ""
            + "LEFT JOIN product_master pm ON pm.id = ( "
            + "SELECT MIN(exact_pb.product_master_id) "
            + "FROM product_barcode exact_pb "
            + "JOIN product_master exact_pm ON exact_pm.id = exact_pb.product_master_id "
            + "AND exact_pm.logical_store_id = exact_pb.logical_store_id "
            + "AND BINARY exact_pm.partner_sku = BINARY exact_pb.partner_sku "
            + "AND exact_pm.is_deleted = b'0' "
            + "JOIN logical_store exact_ls ON exact_ls.id = exact_pb.logical_store_id "
            + "AND exact_ls.owner_user_id = line.owner_user_id AND exact_ls.is_deleted = b'0' "
            + "<if test='query.projectCode != null and query.projectCode != \"\"'> "
            + "AND exact_ls.project_code = #{query.projectCode} "
            + "</if> "
            + "WHERE exact_pb.barcode = line.sku "
            + "AND BINARY exact_pb.barcode = BINARY line.sku "
            + "AND exact_pb.is_deleted = b'0' "
            + "AND COALESCE(exact_pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS' "
            + "HAVING COUNT(DISTINCT exact_pb.logical_store_id, BINARY exact_pb.partner_sku) = 1) "
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
            + "LEFT JOIN product_master pm ON pm.id = matched.matched_product_id "
            + "AND pm.is_deleted = b'0' ";
}
