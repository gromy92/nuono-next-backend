package com.nuono.next.competitoranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisMapperSqlTest {

    @Test
    void productListsSortByCandidateCountThenMonitoredCountBeforeStableKeys() throws NoSuchMethodException {
        String productBaselinesSql = selectSql(
                "listProductBaselines",
                Long.class,
                String.class,
                String.class,
                CompetitorWatchProductQuery.class
        );
        String watchProductsSql = selectSql(
                "listWatchProducts",
                Long.class,
                java.util.List.class,
                CompetitorWatchProductQuery.class
        );

        assertThat(productBaselinesSql).contains(
                "order by pendingcandidatecount desc, confirmedcompetitorcount desc, pm.sku_parent asc, pv.partner_sku asc, pso.id asc"
        );
        assertThat(watchProductsSql).contains(
                "order by pendingcandidatecount desc, confirmedcompetitorcount desc, wp.gmt_updated desc, wp.id desc"
        );
    }

    @Test
    void productBaselineSelfNoonCodeComesFromSkuParent() throws NoSuchMethodException {
        String productBaselinesSql = selectSql(
                "listProductBaselines",
                Long.class,
                String.class,
                String.class,
                CompetitorWatchProductQuery.class
        );
        String productOptionsSql = selectSql(
                "listProductOptions",
                Long.class,
                String.class,
                String.class,
                String.class,
                int.class
        );

        assertThat(productBaselinesSql).contains("pm.sku_parent as selfnoonproductcode");
        assertThat(productBaselinesSql).doesNotContain("pso.psku_code as selfnoonproductcode");
        assertThat(productOptionsSql).contains("upper(pm.sku_parent) like 'z%'");
        assertThat(productOptionsSql).doesNotContain("upper(pso.psku_code) like 'z%'");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = CompetitorAnalysisMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        return Arrays.stream(select.value())
                .collect(java.util.stream.Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
