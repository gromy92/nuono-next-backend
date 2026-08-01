package com.nuono.next.intransit;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.InTransitSuperSearchMapper;
import com.nuono.next.intransit.InTransitSuperSearchCommands.InTransitSuperSearchQuery;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class InTransitSuperSearchSchemaContractTest {

    @Test
    void scriptedSqlEscapesXmlInequalityOperators() {
        for (Method method : InTransitSuperSearchMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = String.join(" ", select.value());
            assertFalse(sql.contains(" <> "), method.getName() + " contains an unescaped XML operator");
        }
    }

    @Test
    void superSearchSqlMatchesPskuProductNameAndProductTitlesOnly() throws NoSuchMethodException {
        Method search = InTransitSuperSearchMapper.class.getMethod(
                "searchInTransitProducts",
                InTransitSuperSearchQuery.class
        );
        String sql = Arrays.stream(search.getAnnotation(Select.class).value())
                .reduce("", (left, right) -> left + " " + right);

        assertTrue(sql.contains("line.psku LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("line.product_name LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertFalse(sql.contains("line.msku LIKE"));
        assertTrue(sql.contains("batch.batch_status NOT IN ('draft', 'warehouse_received', 'completed', 'cancelled')"));
        assertTrue(sql.contains("query.projectCode != null and query.projectCode != \"\""));
        assertTrue(sql.contains("exact_ls.project_code = #{query.projectCode}"));
        assertTrue(sql.contains("filter_ls.project_code = #{query.projectCode}"));
        assertTrue(sql.contains("WHERE exact_pb.barcode = line.sku"));
        assertTrue(sql.contains("BINARY exact_pb.barcode = BINARY line.sku"));
        assertTrue(sql.contains("COALESCE(exact_pb.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'"));
        assertTrue(sql.contains("HAVING COUNT(DISTINCT exact_pb.logical_store_id, BINARY exact_pb.partner_sku) = 1"));
        assertFalse(sql.contains("partner_sku IN (line.psku"));
        assertFalse(sql.contains("fallback_pv"));
        assertTrue(sql.contains("LIMIT #{query.limit}"));
    }

    @Test
    void lineMatchedSuperSearchSqlDoesNotEvaluateProductTitlesInFilter() throws NoSuchMethodException {
        Method search = InTransitSuperSearchMapper.class.getMethod(
                "searchLineMatchedInTransitProducts",
                InTransitSuperSearchQuery.class
        );
        String sql = Arrays.stream(search.getAnnotation(Select.class).value())
                .reduce("", (left, right) -> left + " " + right);

        assertTrue(sql.contains("line.psku LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("line.product_name LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertFalse(sql.contains("OR pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertFalse(sql.contains("OR pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("FROM ( SELECT line.owner_user_id AS owner_user_id"));
        assertTrue(sql.contains(") matched LEFT JOIN product_master pm"));
        assertTrue(sql.contains("filter_ls.project_code = #{query.projectCode}"));
        assertTrue(sql.indexOf("filter_ls.project_code = #{query.projectCode}") < sql.indexOf("LIMIT 500"));
        assertTrue(sql.contains("CASE WHEN COUNT(*) = COUNT(pm.id) AND COUNT(DISTINCT pm.id) = 1"));
        assertTrue(sql.contains("WHERE exact_pb.barcode = line.sku"));
        assertTrue(sql.contains("LEFT JOIN product_master pm ON pm.id = matched.matched_product_id"));
        assertFalse(sql.contains("partner_sku IN (matched.psku"));
        assertTrue(sql.contains("WHERE (pm.id IS NOT NULL OR EXISTS"));
        assertTrue(sql.contains("ORDER BY COALESCE(matched.source_created_at, matched.gmt_create)"));
        assertTrue(sql.contains("LIMIT 500"));
        assertTrue(sql.contains("LIMIT #{query.limit}"));
    }

    @Test
    void titleMatchedSuperSearchSqlFindsProductsBeforeJoiningInTransitLines() throws NoSuchMethodException {
        Method search = InTransitSuperSearchMapper.class.getMethod(
                "searchTitleMatchedInTransitProducts",
                InTransitSuperSearchQuery.class
        );
        String sql = Arrays.stream(search.getAnnotation(Select.class).value())
                .reduce("", (left, right) -> left + " " + right);

        assertTrue(sql.contains("FROM ( SELECT DISTINCT title_pb.barcode AS barcode, title_pm.id AS product_master_id"));
        assertTrue(sql.contains("title_pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("title_pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertTrue(sql.contains("JOIN in_transit_goods_line line"));
        assertTrue(sql.contains("line.sku = title_match.barcode"));
        assertTrue(sql.contains("BINARY line.sku = BINARY title_match.barcode"));
        assertTrue(sql.contains("COUNT(DISTINCT identity_pb.logical_store_id, BINARY identity_pb.partner_sku)"));
        assertFalse(sql.contains("line.psku = title_match"));
        assertTrue(sql.contains("filter_ls.project_code = #{query.projectCode}"));
        assertTrue(sql.indexOf("filter_ls.project_code = #{query.projectCode}") < sql.indexOf("GROUP BY batch.id, line.psku"));
        assertFalse(sql.contains("OR pm.title_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
        assertFalse(sql.contains("OR pm.title_cn_cache LIKE CONCAT('%', #{query.keyword}, '%')"));
    }

    @Test
    void superSearchMigrationAddsPartnerSkuLookupIndex() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/140_in_transit_super_search_indexes.sql"));

        assertTrue(sql.contains("idx_product_variant_partner_sku_lookup"));
        assertTrue(sql.contains("(partner_sku, is_deleted, product_master_id)"));
        assertInitScriptsInclude("classpath:db/init/140_in_transit_super_search_indexes.sql");
    }

    @Test
    void inTransitGoodsProductJoinUsesExactUniqueBarcodeIdentityOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/InTransitGoodsSql.java"));

        assertTrue(source.contains("WHERE exact_pb.barcode = line.sku"));
        assertTrue(source.contains("BINARY exact_pb.barcode = BINARY line.sku"));
        assertTrue(source.contains("HAVING COUNT(DISTINCT exact_pb.logical_store_id, BINARY exact_pb.partner_sku) = 1"));
        assertFalse(source.contains("partner_sku IN (line.psku"));
        assertFalse(source.contains("LINE_LEGACY_PSKU_ALIAS"));
    }
}
