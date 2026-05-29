package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderMapperSqlTest {

    @Test
    void excelImportRowSqlQuotesMysqlReservedRowNumberColumn() throws Exception {
        String insertSql = annotationSql(
                Ali1688HistoricalOrderMapper.class.getMethod("insertExcelImportRow", Ali1688HistoricalOrderExcelImportRow.class)
                        .getAnnotation(Insert.class)
                        .value()
        );
        Method listRowsMethod = Ali1688HistoricalOrderMapper.class.getMethod("listExcelImportRows", Long.class, Long.class);
        String listRowsSql = annotationSql(listRowsMethod.getAnnotation(Select.class).value());

        assertThat(insertSql).contains("`row_number`");
        assertThat(listRowsSql).contains("`row_number`").contains("ORDER BY `row_number` ASC");
    }

    @Test
    void listOrdersDefaultsToNewestOrderTimeFirst() throws Exception {
        Method listOrdersMethod = Ali1688HistoricalOrderMapper.class.getMethod(
                "listOrders",
                Long.class,
                java.util.List.class,
                Ali1688HistoricalOrderQuery.class
        );
        String listOrdersSql = annotationSql(listOrdersMethod.getAnnotation(Select.class).value());

        assertThat(listOrdersSql)
                .contains("ORDER BY order_time IS NULL ASC, order_time DESC, id DESC")
                .doesNotContain("ORDER BY gmt_updated DESC, id DESC");
    }

    @Test
    void assignmentSummarySqlScopesOwnerAndActiveFacts() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listOrderItemAssignmentSummaries",
                Long.class,
                java.util.List.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_assignment")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("status = 'active'")
                .contains("is_deleted = b'0'")
                .contains("COALESCE(SUM(grouped.target_quantity), 0) AS assigned_quantity")
                .contains("consumable_assignment_count")
                .contains("store_site_assignment_count")
                .contains("assignment_breakdown_text")
                .contains("target_type")
                .contains("WHEN target_type = 'CONSUMABLE' THEN '耗材'")
                .contains("target_site_code")
                .contains("GROUP BY item_id, target_type, target_store_code, target_site_code")
                .contains("GROUP BY grouped.item_id");
    }

    @Test
    void listOrdersAndCountOrdersSupportAssignmentFilters() throws Exception {
        Method listOrdersMethod = Ali1688HistoricalOrderMapper.class.getMethod(
                "listOrders",
                Long.class,
                java.util.List.class,
                Ali1688HistoricalOrderQuery.class
        );
        Method countOrdersMethod = Ali1688HistoricalOrderMapper.class.getMethod(
                "countOrders",
                Long.class,
                java.util.List.class,
                Ali1688HistoricalOrderQuery.class
        );
        String listOrdersSql = annotationSql(listOrdersMethod.getAnnotation(Select.class).value());
        String countOrdersSql = annotationSql(countOrdersMethod.getAnnotation(Select.class).value());

        assertThat(listOrdersSql)
                .contains("query.assignmentState == 'unassigned'")
                .contains("query.assignmentState == 'consumable'")
                .contains("consumable_filter_assignment.target_type = 'CONSUMABLE'")
                .contains("COALESCE(SUM(unassigned_filter_assignment.assigned_quantity), 0) = 0")
                .contains("query.assignmentTargetStoreCode != null")
                .contains("assignment_target_filter.target_type = 'STORE_SITE'")
                .contains("assignment_target_filter.target_store_code = #{query.assignmentTargetStoreCode}")
                .contains("query.assignmentTargetSiteCode != null")
                .contains("assignment_target_filter.target_site_code = #{query.assignmentTargetSiteCode}");
        assertThat(countOrdersSql)
                .contains("query.assignmentState == 'unassigned'")
                .contains("query.assignmentState == 'consumable'")
                .contains("consumable_filter_assignment.target_type = 'CONSUMABLE'")
                .contains("query.assignmentTargetStoreCode != null")
                .contains("assignment_target_filter.target_type = 'STORE_SITE'")
                .contains("assignment_target_filter.target_store_code = #{query.assignmentTargetStoreCode}")
                .contains("query.assignmentTargetSiteCode != null")
                .contains("assignment_target_filter.target_site_code = #{query.assignmentTargetSiteCode}");
    }

    @Test
    void orderCleanupSqlSoftDeletesFactsAndPreservesAuditColumns() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "softDeleteOrderHeader",
                Long.class,
                Long.class,
                Long.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Update.class).value());

        assertThat(sql)
                .contains("UPDATE procurement_ali1688_order_header")
                .contains("is_deleted = b'1'")
                .contains("deleted_by = #{operatorUserId}")
                .contains("deleted_at = NOW()")
                .contains("delete_reason = #{deleteReason}")
                .contains("owner_user_id = #{ownerUserId}");
    }

    @Test
    void activeAssignmentGuardCountsOnlyActiveNonDeletedRows() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "countActiveOrderAssignments",
                Long.class,
                Long.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_assignment")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("order_id = #{orderId}")
                .contains("status = 'active'")
                .contains("is_deleted = b'0'");
    }

    @Test
    void skuPurchaseHistorySqlUsesOnlyActiveStoreSkuFacts() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listSkuPurchaseHistoryRows",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_product_link link")
                .contains("assignment.target_type = 'STORE_SITE'")
                .contains("assignment.status = 'active'")
                .contains("assignment.is_deleted = b'0'")
                .contains("header.is_deleted = b'0'")
                .contains("item.is_deleted = b'0'")
                .contains("link.status = 'active'")
                .contains("link.is_deleted = b'0'")
                .contains("link.target_store_code = #{storeCode}")
                .contains("link.target_site_code = #{siteCode}")
                .contains("link.sku_parent LIKE CONCAT('%', #{keyword}, '%')")
                .contains("header.order_time &gt;= CONCAT(#{purchaseTimeFrom}, ' 00:00:00')")
                .contains("header.order_time &lt;= CONCAT(#{purchaseTimeTo}, ' 23:59:59')");
    }

    @Test
    void skuPurchaseHistorySqlIsValidMybatisXmlScript() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listSkuPurchaseHistoryRows",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Object.class);
    }

    @Test
    void unlinkedAssignedLineCountSqlScopesActiveUnlinkedStoreAssignments() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "countUnlinkedAssignedStoreSiteLines",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_assignment assignment")
                .contains("assignment.target_type = 'STORE_SITE'")
                .contains("assignment.status = 'active'")
                .contains("assignment.target_store_code = #{storeCode}")
                .contains("link.id IS NULL")
                .contains("COUNT(DISTINCT assignment.id)");
        new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Object.class);
    }

    @Test
    void unlinkedSkuPurchaseHistoryRowsSqlReturnsAssignedLinesWithoutActiveProductLink() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listUnlinkedSkuPurchaseHistoryRows",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM procurement_ali1688_order_item_assignment assignment")
                .contains("assignment.target_type = 'STORE_SITE'")
                .contains("assignment.status = 'active'")
                .contains("assignment.target_store_code = #{storeCode}")
                .contains("item.title AS productTitle")
                .contains("item.image_url AS productImageUrl")
                .contains("item.offer_id AS sourceOfferId")
                .contains("link.id IS NULL")
                .contains("ORDER BY header.order_time IS NULL ASC, header.order_time DESC, assignment.id DESC");
        new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Object.class);
    }

    @Test
    void skuPurchaseHistoryProductsSqlReadsCurrentStoreProducts() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listSkuPurchaseHistoryProducts",
                Long.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM logical_store ls")
                .contains("JOIN product_master pm")
                .contains("LEFT JOIN product_variant pv")
                .contains("LEFT JOIN product_site_offer pso")
                .contains("ls.owner_user_id = #{ownerUserId}")
                .contains("ls.project_code = #{storeCode}")
                .contains("pm.sku_parent AS skuParent")
                .contains("pm.title_cache AS productTitle")
                .contains("productTitleCn")
                .contains("$.content.titleCn");
        new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Object.class);
    }

    @Test
    void productLinkCandidatesSqlFiltersLinkedAndUnlinkedProductsByActiveLinks() throws Exception {
        Method method = Ali1688HistoricalOrderMapper.class.getMethod(
                "listOrderItemProductLinkCandidates",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        String sql = annotationSql(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("FROM logical_store ls")
                .contains("JOIN product_master pm")
                .contains("LEFT JOIN procurement_ali1688_order_item_product_link link")
                .contains("link.status = 'active'")
                .contains("link.is_deleted = b'0'")
                .contains("CASE WHEN COUNT(DISTINCT link.id) > 0 THEN 'linked' ELSE 'unlinked' END AS linkStatus")
                .contains("<when test='linkStatus == \"linked\"'>")
                .contains("HAVING COUNT(DISTINCT link.id) > 0")
                .contains("<when test='linkStatus == \"unlinked\"'>")
                .contains("HAVING COUNT(DISTINCT link.id) = 0");
        new XMLLanguageDriver().createSqlSource(new Configuration(), sql, Object.class);
    }

    private static String annotationSql(String[] fragments) {
        return String.join(" ", fragments);
    }
}
