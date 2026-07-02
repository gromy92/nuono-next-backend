package com.nuono.next.noonads;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonAdvertisingMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class NoonAdvertisingMapperSqlTest {

    @Test
    void summarySqlUsesExactAdvertisingReportWindowAndNaturalSalesFacts() throws Exception {
        String adSummarySql = sqlFor("selectAdSummary");
        assertThat(adSummarySql)
                .contains("FROM noon_ad_campaign_fact")
                .contains("owner_user_id = #{query.ownerUserId}")
                .contains("project_code = #{query.projectCode}")
                .contains("store_code = #{query.storeCode}")
                .contains("site_code = #{query.siteCode}")
                .contains("report_date_from = #{query.dateFrom}")
                .contains("report_date_to = #{query.dateTo}")
                .contains("SUM(spend_amount)")
                .contains("SUM(ad_revenue)")
                .contains("zeroOrderSpendShare")
                .doesNotContain("report_date_from &lt;=");

        String salesSummarySql = sqlFor("selectSalesSummary");
        assertThat(salesSummarySql)
                .contains("FROM daily_sales_fact")
                .contains("fact_date &gt;= #{query.dateFrom}")
                .contains("fact_date &lt;= #{query.dateTo}")
                .contains("SUM(net_units)")
                .contains("SUM(revenue_shipped)");
    }

    @Test
    void queryQueuesSortWasteAndWinsFromQueryFactTable() throws Exception {
        String zeroOrderSql = sqlFor("selectZeroOrderQueryRows");
        assertThat(zeroOrderSql)
                .contains("FROM noon_ad_query_fact")
                .contains("store_code AS storeCode")
                .contains("site_code AS siteCode")
                .contains("ad_sku_code AS adSkuCode")
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) AS partnerSku")
                .contains("orders_count = 0")
                .contains("spend_amount &gt; 0")
                .contains("ORDER BY q.spend_amount DESC")
                .contains("LIMIT 5000");

        String winningSql = sqlFor("selectWinningQueryRows");
        assertThat(winningSql)
                .contains("FROM noon_ad_query_fact")
                .contains("store_code AS storeCode")
                .contains("site_code AS siteCode")
                .contains("ad_sku_code AS adSkuCode")
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) AS partnerSku")
                .contains("orders_count &gt; 0")
                .contains("spend_amount &gt; 0")
                .contains("ORDER BY q.roas DESC, q.ad_revenue DESC, q.orders_count DESC")
                .contains("LIMIT 5000");
    }

    @Test
    void campaignRowsUseAdvertisingCampaignFactTable() throws Exception {
        String sql = sqlFor("selectCampaignRows");

        assertThat(sql)
                .contains("FROM noon_ad_campaign_fact")
                .contains("campaign_code AS campaignCode")
                .contains("c.store_code AS storeCode")
                .contains("c.site_code AS siteCode")
                .contains("AS primaryAdSkuCode")
                .contains("AS primaryPartnerSku")
                .contains("FROM noon_ad_query_fact q")
                .contains("q.campaign_code = c.campaign_code")
                .contains("zero_order_spend_amount")
                .contains("AS zeroOrderSpendAmount")
                .contains("ORDER BY c.spend_amount DESC, c.orders_count DESC, c.campaign_code ASC")
                .contains("LIMIT 1000");
    }

    @Test
    void productRowsAggregateQueryFactsBySku() throws Exception {
        String sql = sqlFor("selectProductRows");

        assertThat(sql)
                .contains("FROM noon_ad_query_fact q")
                .contains("resolved.store_code AS storeCode")
                .contains("resolved.site_code AS siteCode")
                .contains("MIN(resolved.ad_sku_code) AS adSkuCode")
                .contains("MIN(NULLIF(resolved.partner_sku, '')) AS partnerSku")
                .contains("product_image.image_url AS imageUrl")
                .contains("pm.cover_image_url")
                .contains("pp.main_image_url")
                .contains("pms.snapshot_type = 'baseline'")
                .contains("product_image.store_code = product.storeCode")
                .contains("product_image.site_code = product.siteCode")
                .contains("product_image.partner_sku = product.partnerSku")
                .contains("COUNT(DISTINCT resolved.campaign_code) AS campaignCount")
                .contains("COUNT(1) AS queryCount")
                .contains("SUM(CASE WHEN resolved.orders_count = 0 THEN resolved.spend_amount ELSE 0 END)")
                .contains("GROUP BY resolved.store_code, resolved.site_code, COALESCE(NULLIF(resolved.partner_sku, ''), CONCAT('ADSKU:', resolved.ad_sku_code))")
                .contains("ORDER BY product.spendAmount DESC, product.ordersCount DESC, product.partnerSku ASC")
                .contains("LIMIT 1000")
                .doesNotContain("rank_no");
    }

    @Test
    void dashboardRowsResolveMissingPartnerSkuFromNoonProductSources() throws Exception {
        String productSql = sqlFor("selectProductRows");
        assertThat(productSql)
                .contains("official_warehouse_inventory_snapshot_line")
                .contains("product_public_detail_snapshot")
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku)")
                .contains("ow.noon_sku")
                .contains("SUBSTRING_INDEX(q.ad_sku_code, '-', 1)")
                .contains("pp.is_latest = b'1'")
                .contains("COALESCE(NULLIF(resolved.partner_sku, ''), CONCAT('ADSKU:', resolved.ad_sku_code))");

        String zeroOrderSql = sqlFor("selectZeroOrderQueryRows");
        assertThat(zeroOrderSql)
                .contains("official_warehouse_inventory_snapshot_line")
                .contains("product_public_detail_snapshot")
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) AS partnerSku")
                .contains("ow.store_code = q.store_code")
                .contains("ow.site_code = q.site_code")
                .contains("pp.store_code = q.store_code")
                .contains("pp.site_code = q.site_code");

        String winningSql = sqlFor("selectWinningQueryRows");
        assertThat(winningSql)
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku) AS partnerSku");

        String campaignSql = sqlFor("selectCampaignRows");
        assertThat(campaignSql)
                .contains("COALESCE(NULLIF(q.partner_sku, ''), ow.partner_sku, pp.partner_sku)")
                .contains("AS primaryPartnerSku");
    }

    @Test
    void importSqlAllocatesIdsAndUpsertsNormalizedFacts() throws Exception {
        String idSql = insertSqlFor("nextId", IdSequenceCommand.class);
        assertThat(idSql)
                .contains("noon_ad_id_sequence")
                .contains("LAST_INSERT_ID");

        String batchSql = insertSqlFor("insertReportBatch", NoonAdvertisingReportBatch.class);
        assertThat(batchSql)
                .contains("INSERT INTO noon_ad_report_batch")
                .contains("source_digest_sha256")
                .contains("campaign_row_count")
                .contains("query_row_count");

        String campaignSql = insertSqlFor("upsertCampaignFact", NoonAdvertisingCampaignFact.class);
        assertThat(campaignSql)
                .contains("INSERT INTO noon_ad_campaign_fact")
                .contains("campaign_code")
                .contains("zero_order_spend_amount")
                .contains("raw_payload_json")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("gmt_updated = NOW()");

        String querySql = insertSqlFor("upsertQueryFact", NoonAdvertisingQueryFact.class);
        assertThat(querySql)
                .contains("INSERT INTO noon_ad_query_fact")
                .contains("ad_sku_code, partner_sku, query_text")
                .contains("#{adSkuCode}, #{partnerSku}, #{queryText}")
                .contains("query_hash")
                .contains("query_kind")
                .contains("raw_payload_json")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("gmt_updated = NOW()")
                .doesNotContain("#{sku}");
    }

    @Test
    void dataStatusCountsCurrentFactRowsAndDeduplicatesDigestBatches() throws Exception {
        String statusSql = sqlFor("selectDataStatus");

        assertThat(statusSql)
                .contains("COUNT(DISTINCT COALESCE(source_digest_sha256")
                .contains("FROM noon_ad_campaign_fact c")
                .contains("FROM noon_ad_query_fact q")
                .contains("AS campaignRowCount")
                .contains("AS queryRowCount")
                .doesNotContain("SUM(campaign_row_count)")
                .doesNotContain("SUM(query_row_count)");
    }

    @Test
    void importSqlCanFindExistingDigestBatchForIdempotentReimports() throws Exception {
        String sql = sqlFor("findReportBatchId", NoonAdvertisingReportBatch.class);

        assertThat(sql)
                .contains("FROM noon_ad_report_batch")
                .contains("source_system = #{batch.sourceSystem}")
                .contains("source_digest_sha256 = #{batch.sourceDigestSha256}")
                .contains("ORDER BY id ASC")
                .contains("LIMIT 1");
    }

    @Test
    void latestReportWindowSqlPrefersLongestWindowWhenTrendWindowSharesSameEndDate() throws Exception {
        String sql = sqlFor("selectLatestReportWindow", NoonAdvertisingScopeQuery.class);

        assertThat(sql)
                .contains("FROM noon_ad_report_batch")
                .contains("owner_user_id = #{query.ownerUserId}")
                .contains("project_code = #{query.projectCode}")
                .contains("store_code = #{query.storeCode}")
                .contains("site_code = #{query.siteCode}")
                .contains("status = 'imported'")
                .contains("ORDER BY report_date_to DESC, DATEDIFF(report_date_to, report_date_from) DESC, id DESC")
                .contains("LIMIT 1");
    }

    private String sqlFor(String methodName) throws Exception {
        Method method = NoonAdvertisingMapper.class.getMethod(methodName, NoonAdvertisingDashboardQuery.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        String rawSql = String.join("\n", select.value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
        return rawSql.replaceAll("\\s+", " ");
    }

    private String sqlFor(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = NoonAdvertisingMapper.class.getMethod(methodName, parameterTypes);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        String rawSql = String.join("\n", select.value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
        return rawSql.replaceAll("\\s+", " ");
    }

    private String insertSqlFor(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = NoonAdvertisingMapper.class.getMethod(methodName, parameterTypes);
        Insert insert = method.getAnnotation(Insert.class);
        assertThat(insert).isNotNull();
        String rawSql = String.join("\n", insert.value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
        return rawSql.replaceAll("\\s+", " ");
    }
}
