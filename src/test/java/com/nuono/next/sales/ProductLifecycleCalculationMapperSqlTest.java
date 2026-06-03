package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProductLifecycleCalculationMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductLifecycleCalculationMapperSqlTest {

    @Test
    void productScopeSkuShouldPreferExistingSalesFactSkuForSamePartnerSku() {
        Method method = Arrays.stream(ProductLifecycleCalculationMapper.class.getDeclaredMethods())
                .filter((candidate) -> "selectProductScopes".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("FROM daily_sales_fact dsf_scope"));
        assertTrue(sql.contains("dsf_scope.partner_sku = pv.partner_sku"));
        assertTrue(sql.contains("ORDER BY MAX(dsf_scope.fact_date) DESC, COUNT(*) DESC"));
        assertTrue(sql.indexOf("FROM daily_sales_fact dsf_scope") < sql.indexOf("NULLIF(pv.child_sku, '')"));
        assertTrue(sql.indexOf("NULLIF(pv.child_sku, '')") < sql.indexOf("NULLIF(pso.offer_code, '')"));
    }
}
