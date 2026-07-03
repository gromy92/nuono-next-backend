package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductLogisticsCostMapperSqlTest {

    @Test
    void mapperExposesReadOperationsAndManualCurrentQuoteWritesOnly() {
        String methodNames = Arrays.stream(ProductLogisticsCostMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.joining(","));

        assertThat(methodNames).contains("listCurrentCosts");
        assertThat(methodNames).contains("listHistory");
        assertThat(methodNames).contains("listOpenExceptions");
        assertThat(methodNames).contains("selectProductMatches");
        assertThat(methodNames).contains("insertCostHistory");
        assertThat(methodNames).contains("upsertCurrentCost");
        assertThat(methodNames).doesNotContain("softDeleteCurrentCostsByActualBill");
    }

    @Test
    void currentAndHistoryReadsCanFilterByStableStoreAndPartnerSkuIdentity() throws Exception {
        String currentSql = mapperSql(
                "listCurrentCosts",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class
        );
        String historySql = mapperSql(
                "listHistory",
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class
        );

        assertThat(currentSql).contains("FROM product_logistics_current_cost");
        assertThat(currentSql).contains("logical_store_id = #{logicalStoreId}");
        assertThat(currentSql).contains("partner_sku = #{partnerSku}");
        assertThat(currentSql).contains("site_code = #{siteCode}");
        assertThat(currentSql).contains("cargo_category_code AS cargoCategoryCode");
        assertThat(currentSql).contains("cargo_category_name AS cargoCategoryName");
        assertThat(historySql).contains("FROM product_logistics_cost_history");
        assertThat(historySql).contains("logical_store_id = #{logicalStoreId}");
        assertThat(historySql).contains("partner_sku = #{partnerSku}");
        assertThat(historySql).contains("site_code = #{siteCode}");
        assertThat(historySql).contains("cargo_category_code AS cargoCategoryCode");
        assertThat(historySql).contains("cargo_category_name AS cargoCategoryName");
        assertThat(historySql).doesNotContain("product_site_offer");
        assertThat(historySql).doesNotContain("psku_code");
    }

    @Test
    void openExceptionReadIsDisplayOnly() throws Exception {
        String sql = mapperSql("listOpenExceptions", Long.class, Integer.class);

        assertThat(sql).contains("FROM product_logistics_cost_exception");
        assertThat(sql).contains("resolution_status = 'OPEN'");
        assertThat(sql).contains("store_code AS storeCode");
        assertThat(sql).contains("partner_sku AS partnerSku");
        assertThat(sql).contains("LIMIT #{limit}");
    }

    @Test
    void manualCurrentQuoteResolvesCurrentProductByStoreAndPartnerSku() throws Exception {
        String sql = mapperSql("selectProductMatches", Long.class, String.class, String.class, String.class);

        assertThat(sql).contains("JOIN logical_store_site anchor ON anchor.logical_store_id = ls.id");
        assertThat(sql).contains("UPPER(anchor.store_code) = UPPER(#{storeCode})");
        assertThat(sql).contains("JOIN product_variant pv ON pv.product_master_id = pm.id");
        assertThat(sql).contains("LEFT JOIN product_barcode matched_barcode");
        assertThat(sql).contains("BINARY matched_barcode.barcode = BINARY #{partnerSku}");
        assertThat(sql).contains("BINARY pv.partner_sku = BINARY #{partnerSku}");
        assertThat(sql).contains("matched_barcode.id IS NOT NULL");
        assertThat(sql).contains("NOT LIKE '待补资料商品 %'");
        assertThat(sql).contains("anchor.site = #{siteCode}");
        assertThat(sql).doesNotContain("SELECT DISTINCT");
        assertThat(sql).contains("LIMIT 2");
        assertThat(sql).doesNotContain("product_site_offer");
        assertThat(sql).doesNotContain("psku_code");
    }

    @Test
    void manualCurrentQuotePersistsHistoryAndCurrentProjectionWithCargoCategory() throws Exception {
        String historySql = mapperSql("insertCostHistory", CostHistoryRow.class, Long.class);
        String currentSql = mapperSql("upsertCurrentCost", CurrentCostRow.class, Long.class);

        assertThat(historySql).contains("INSERT INTO product_logistics_cost_history");
        assertThat(historySql).contains("cargo_category_code");
        assertThat(historySql).contains("cargo_category_name");
        assertThat(historySql).contains("idempotency_key");
        assertThat(currentSql).contains("INSERT INTO product_logistics_current_cost");
        assertThat(currentSql).contains("LAST_INSERT_ID(#{row.id})");
        assertThat(currentSql).contains("id = LAST_INSERT_ID(id)");
        assertThat(currentSql).contains("current_history_id");
        assertThat(currentSql).contains("ON DUPLICATE KEY UPDATE");
        assertThat(currentSql).contains("cargo_category_code = VALUES(cargo_category_code)");
        assertThat(currentSql).contains("cargo_category_name = VALUES(cargo_category_name)");
        assertThat(currentSql).contains("unit_cost_cny = VALUES(unit_cost_cny)");
    }

    private static String mapperSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ProductLogisticsCostMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return normalizeSql(String.join(" ", select.value()));
        }
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return normalizeSql(String.join(" ", insert.value()));
        }
        throw new IllegalArgumentException("No SQL annotation found on " + methodName);
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
