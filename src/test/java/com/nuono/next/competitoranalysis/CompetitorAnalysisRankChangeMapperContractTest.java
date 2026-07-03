package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisRankChangeMapperContractTest {
    private static final Path MAPPER_PATH = Path.of(
            "src",
            "main",
            "java",
            "com",
            "nuono",
            "next",
            "infrastructure",
            "mapper",
            "CompetitorAnalysisMapper.java"
    );

    @Test
    void dashboardRankChangesCompareWindowEndpointRankedFactsOnly() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertSelectContains(source, "selectlatestrankfactdate", "max(rf.fact_date)");
        assertSelectContains(source, "selectlatestrankfactdate", "rf.fact_date <= #{upperbounddate}");
        assertSelectContains(source, "selectlatestrankfactdate", "rf.rank_status = 'ranked'");
        assertTrue(source.contains("rf.fact_date in (#{fromdate}, #{todate})"), "rank change dashboard must compare only the requested endpoint dates");
        assertTrue(source.contains("rf.rank_status = 'ranked'"), "rank change dashboard must use only real ranked facts");
        assertTrue(source.contains("rf.rank_no is not null"), "rank change dashboard must ignore placeholder facts without rank numbers");
        assertTrue(source.contains("first_rank.first_rank_date = #{fromdate}"), "rank change dashboard must require a ranked fact at the start endpoint");
        assertTrue(source.contains("last_rank.last_rank_date = #{todate}"), "rank change dashboard must require a ranked fact at the end endpoint");
        assertFalse(source.contains("lag(rf.rank_status)"), "rank change dashboard must not count intermediate adjacent fluctuations");
        assertFalse(source.contains("else 101"), "rank change dashboard must not turn missing data into fake out-of-range ranks");
    }

    @Test
    void dashboardCompetitorRankChangesExposeCompetitorIdentity() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertSelectContains(source, "listrankchanges", "case when rf.tracked_product_type = 'self' then wp.partner_sku");
        assertSelectContains(source, "listrankchanges", "else coalesce(nullif(rf.noon_product_code, ''), nullif(cp.noon_product_code, ''), wp.partner_sku)");
        assertSelectContains(source, "listrankchanges", "case when rf.tracked_product_type = 'self' then coalesce(nullif(wp.title_snapshot, ''), wp.partner_sku)");
        assertSelectContains(source, "listrankchanges", "else coalesce(nullif(sr.title_snapshot, ''), nullif(cp.title_snapshot, ''), rf.noon_product_code)");
    }

    @Test
    void dashboardRankChangeDetailsExposeRangeChangeSummaries() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertSelectContains(source, "listrankchanges", "range_change_event as");
        assertSelectContains(source, "listrankchanges", "operations_competitor_product_change_event ev");
        assertSelectContains(source, "listrankchanges", "ev.fact_date between #{fromdate} and #{todate}");
        assertSelectContains(source, "listrankchanges", "ev.field_key in ('price', 'title')");
        assertSelectContains(source, "listrankchanges", "price_change_summary as pricechangesummary");
        assertSelectContains(source, "listrankchanges", "title_change_summary as titlechangesummary");
        assertSelectContains(source, "listrankchanges", "range_ad_change as");
        assertSelectContains(source, "listrankchanges", "rf.is_sponsored = b'1'");
        assertSelectContains(source, "listrankchanges", "date_format(rf.fact_date, '%m-%d')");
        assertSelectContains(source, "listrankchanges", "group_concat(distinct");
        assertSelectContains(source, "listrankchanges", "ad_change_summary as adchangesummary");
        assertFalse(source.contains("'自然 -> 广告'"), "rank change detail ad summary should list sponsored dates, not status transition text");
    }

    @Test
    void dashboardCompetitorDetailChangesIncludePriceAndTitleOnly() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertSelectContains(source, "countcompetitorchangeproducts", "changed.field_key in ('price', 'title')");
        assertSelectContains(source, "listchangetypedistribution", "ev.field_key in ('price', 'title')");
        assertSelectContains(source, "listchangedproducttop", "ev.field_key in ('price', 'title')");
        assertSelectContains(source, "listcompetitorattributechanges", "ev.field_key in ('price', 'title')");
        assertSelectContains(source, "listcompetitorattributechanges", "partition by ev.field_key order by ev.fact_date desc, ev.id desc");
        assertSelectContains(source, "listcompetitorattributechanges", "detail_change_row <= #{limit}");
        assertSelectContains(source, "listcompetitorattributechanges", "change_date_rank as");
        assertSelectContains(source, "listcompetitorattributechanges", "change_rank.rank_no as changedaterankno");
        assertSelectContains(source, "listcompetitorattributechanges", "change_rank.keyword_id = latest_rank.keyword_id");
        assertSelectContains(source, "listcompetitorattributechanges", "change_rank.fact_date = ev.fact_date");
    }

    @Test
    void dashboardCompetitorPriceChangesFallbackToSystemSelfPriceByPartnerSku() throws IOException {
        String source = Files.readString(MAPPER_PATH).toLowerCase(Locale.ROOT);

        assertSelectContains(source, "listcompetitorattributechanges", "latest_system_price as");
        assertSelectContains(source, "listcompetitorattributechanges", "pv.partner_sku collate utf8mb4_unicode_ci = wp_system.partner_sku collate utf8mb4_unicode_ci");
        assertSelectContains(source, "listcompetitorattributechanges", "upper(lss.store_code) collate utf8mb4_unicode_ci = upper(wp_system.store_code) collate utf8mb4_unicode_ci");
        assertSelectContains(source, "listcompetitorattributechanges", "upper(lss.site) collate utf8mb4_unicode_ci = upper(wp_system.site_code) collate utf8mb4_unicode_ci");
        assertSelectContains(source, "listcompetitorattributechanges", "coalesce(public_detail.price_amount, pso.final_price, pso.sale_price, pso.price)");
        assertSelectContains(source, "listcompetitorattributechanges", "coalesce(self_snapshot.self_latest_value, system_price.system_latest_value) as selflatestvalue");
        assertFalse(source.contains("public_detail.noon_product_code = wp_system.self_noon_product_code"),
                "system self price fallback must not bind by Noon Z/N code");
        assertFalse(source.contains("pso.psku_code = wp_system.partner_sku"),
                "Noon pskuCode must not be treated as system PSKU");
    }

    private static void assertSelectContains(String source, String methodName, String expectedSql) {
        int methodIndex = source.indexOf(methodName);
        assertTrue(methodIndex >= 0, "expected mapper method: " + methodName);
        int selectIndex = source.lastIndexOf("@select({", methodIndex);
        assertTrue(selectIndex >= 0, "expected @Select SQL before mapper method: " + methodName);
        String sql = source.substring(selectIndex, methodIndex);
        assertTrue(sql.contains(expectedSql), methodName + " must filter by supported competitor detail changes");
    }
}
