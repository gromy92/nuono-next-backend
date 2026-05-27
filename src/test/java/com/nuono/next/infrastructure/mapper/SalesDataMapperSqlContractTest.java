package com.nuono.next.infrastructure.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class SalesDataMapperSqlContractTest {

    @Test
    void nonScriptSelectAnnotationsDoNotUseXmlEscapedComparisonOperators() {
        for (Method method : SalesDataMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = String.join("\n", select.value());
            if (sql.contains("<script>")) {
                continue;
            }
            assertFalse(
                    sql.contains("&gt;") || sql.contains("&lt;"),
                    method.getName() + " must use raw SQL comparison operators in Java annotations"
            );
        }
    }

    @Test
    void salesImportExceptionRowNumberAliasIsQuotedForMysql8() throws Exception {
        Method method = SalesDataMapper.class.getDeclaredMethod("selectSalesImportExceptions", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertFalse(sql.contains(" AS rowNumber"), "rowNumber alias must be quoted to avoid MySQL 8 ROW_NUMBER parsing");
        assertFalse(sql.contains("  row_number AS"), "row_number column must be quoted to avoid MySQL 8 ROW_NUMBER parsing");
        assertFalse(sql.contains("ORDER BY row_number"), "row_number order column must be quoted to avoid MySQL 8 ROW_NUMBER parsing");
    }

    @Test
    void dailySalesFactReadDoesNotTruncateAnalyticsSourceRows() throws Exception {
        Method method = SalesDataMapper.class.getDeclaredMethod("selectDailySalesFacts", com.nuono.next.sales.SalesFactQuery.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertFalse(sql.contains("LIMIT 1000"), "analytics summary and export must not be capped at the first 1000 facts");
    }

    @Test
    void dailySalesFactReadSupportsExactBatchPartnerSkuFilter() throws Exception {
        Method method = SalesDataMapper.class.getDeclaredMethod("selectDailySalesFacts", com.nuono.next.sales.SalesFactQuery.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("query.partnerSkuList"), "sales analytics must support exact batch PSKU filters");
        assertTrue(sql.contains("partner_sku IN"), "batch PSKU filters must be exact matches, not fuzzy keyword search");
        assertTrue(sql.contains("<foreach"), "batch PSKU filters must bind values safely with foreach");
    }
}
