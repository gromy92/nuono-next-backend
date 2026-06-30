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
    void productListsSupportExplicitSortOptions() throws NoSuchMethodException {
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

        for (String sql : java.util.List.of(productBaselinesSql, watchProductsSql)) {
            assertThat(sql)
                    .contains("query.sortby == \"candidate_count_asc\"")
                    .contains("query.sortby == \"monitored_count_desc\"")
                    .contains("query.sortby == \"monitored_count_asc\"")
                    .contains("query.sortby == \"recent_7d_change_count_desc\"")
                    .contains("query.sortby == \"recent_7d_change_count_asc\"")
                    .contains("order by confirmedcompetitorcount desc")
                    .contains("order by confirmedcompetitorcount asc")
                    .contains("order by recent7dcompetitorchangecount desc")
                    .contains("order by recent7dcompetitorchangecount asc");
        }
    }

    @Test
    void productListsCanFilterZeroPendingAndZeroConfirmedCounts() throws NoSuchMethodException {
        String productBaselinesSql = selectSql(
                "listProductBaselines",
                Long.class,
                String.class,
                String.class,
                CompetitorWatchProductQuery.class
        );
        String countProductBaselinesSql = selectSql(
                "countProductBaselines",
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
        String countWatchProductsSql = selectSql(
                "countWatchProducts",
                Long.class,
                java.util.List.class,
                CompetitorWatchProductQuery.class
        );

        for (String sql : java.util.List.of(
                productBaselinesSql,
                countProductBaselinesSql,
                watchProductsSql,
                countWatchProductsSql
        )) {
            assertThat(sql)
                    .contains("query.pendingcandidatecountzero")
                    .contains("query.confirmedcompetitorcountzero")
                    .contains("cp_zero.review_status = 'pending'")
                    .contains("cp_zero.review_status = 'confirmed'");
        }
    }

    @Test
    void productBaselineListReadsEnglishAndChineseTitlesSeparately() throws NoSuchMethodException {
        String productBaselinesSql = selectSql(
                "listProductBaselines",
                Long.class,
                String.class,
                String.class,
                CompetitorWatchProductQuery.class
        );

        assertThat(productBaselinesSql)
                .contains("$.content.titleen")
                .contains("$.content.titlecn")
                .contains("as titlesnapshot")
                .contains("as titlecnsnapshot");
    }

    @Test
    void watchProductNaturalKeyUsesStoreSitePartnerSkuAndSelfNoonCode() throws NoSuchMethodException {
        String sql = selectSql(
                "selectWatchProductByBusinessKey",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class
        );

        assertThat(sql)
                .contains("owner_user_id = #{owneruserid}")
                .contains("store_code = #{storecode}")
                .contains("upper(site_code) = upper(#{sitecode})")
                .contains("partner_sku = #{partnersku}")
                .contains("self_noon_product_code = #{selfnoonproductcode}")
                .doesNotContain("product_site_offer_id = #{productsiteofferid}");
    }

    @Test
    void productListsExposeRecentSevenDayCompetitorChangeCount() throws NoSuchMethodException {
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

        for (String sql : java.util.List.of(productBaselinesSql, watchProductsSql)) {
            assertThat(sql)
                    .contains("count(distinct ce.noon_product_code")
                    .contains("recent7dchangedcompetitorcount")
                    .contains("recent7dcompetitorchangecount")
                    .contains("operations_competitor_product_change_event ce")
                    .contains("ce.subject_type = 'competitor'")
                    .contains("ce.fact_date >= date_sub(current_date, interval 6 day)")
                    .contains("ce.is_deleted = b'0'");
        }
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
