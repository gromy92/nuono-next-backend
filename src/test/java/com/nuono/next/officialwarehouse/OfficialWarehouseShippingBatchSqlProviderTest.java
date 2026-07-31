package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchSqlProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OfficialWarehouseShippingBatchSqlProviderTest {

    @Test
    void matchesEligibleLinesThroughDeduplicatedIdentifiersInsteadOfCorrelatedProductScans() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        assertThat(sql)
                .contains("WITH storeScope AS")
                .contains("productIdentifiers AS")
                .contains("lineIdentifiers AS")
                .contains("matchedProductCandidates AS")
                .contains("matchedProducts AS")
                .contains("GROUP BY identifier.lineId, product.productVariantId")
                .contains("MIN(productVariantId) AS matchedProductVariantId")
                .contains("JOIN matchedProductCandidates identity ON identity.lineId = line.id")
                .contains("identity.productVariantId = COALESCE(line.allocatedProductVariantId, matched.matchedProductVariantId)")
                .contains("COALESCE(line.allocatedProductVariantId, matched.matchedProductVariantId)")
                .doesNotContain("SELECT MIN(pvScope.id)")
                .doesNotContain("UPPER(COALESCE(line.psku")
                .doesNotContain("UPPER(COALESCE(line.sku")
                .doesNotContain("UPPER(COALESCE(line.msku");
    }

    @Test
    void keepsExactBarcodeScopeWhilePrefilteringWithStoreIndexes() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        assertThat(sql)
                .contains("scopeBarcode.logical_store_id = scope.logicalStoreId")
                .contains("scopeBarcode.barcode = line.sku")
                .contains("BINARY scopeBarcode.barcode = BINARY line.sku")
                .contains("scopeBarcode.partner_sku = line.psku")
                .contains("BINARY scopeBarcode.partner_sku = BINARY line.psku")
                .contains("COALESCE(scopeBarcode.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'");
    }

    @Test
    void keepsScopeKeywordQuantityAppointmentAndOrderingContracts() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        assertThat(sql)
                .contains("owner_user_id = #{ownerUserId}")
                .contains("target_store_code = #{storeCode} AND target_site_code = #{siteCode}")
                .contains("<if test='keywordLike != null and keywordLike != \"\"'>")
                .contains("allocation.scopedQuantity")
                .contains("official_warehouse_asn_shipping_batch_link link")
                .contains("official_warehouse_appointment scheduledAppointment")
                .contains("scheduledAppointment.status = 'SCHEDULED'")
                .contains("AS remainingQuantity")
                .contains("AS scheduledAppointmentQuantity")
                .contains("AS alreadyAppointed")
                .contains("AS batchUsedByAsn")
                .contains("AS batchUsageLabel")
                .contains("ORDER BY batchUsedByAsn ASC, batch.gmt_updated DESC, batch.id DESC")
                .contains("LIMIT #{limit}")
                .doesNotContain("HAVING remainingQuantity");
    }

    @Test
    void rendersAsMyBatisDynamicSqlWithAndWithoutKeyword() {
        XMLLanguageDriver driver = new XMLLanguageDriver();
        Configuration configuration = new Configuration();
        String providerSql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        String unfilteredSql = driver.createSqlSource(configuration, providerSql, Map.class)
                .getBoundSql(Map.of(
                        "ownerUserId", 307L,
                        "storeCode", "STR69486-NSA",
                        "siteCode", "SA",
                        "limit", 100
                ))
                .getSql();
        String filteredSql = driver.createSqlSource(configuration, providerSql, Map.class)
                .getBoundSql(Map.of(
                        "ownerUserId", 307L,
                        "storeCode", "STR69486-NSA",
                        "siteCode", "SA",
                        "keywordLike", "%YT2605793678%",
                        "limit", 100
                ))
                .getSql();

        assertThat(unfilteredSql)
                .contains("WITH storeScope AS")
                .doesNotContain("batch_reference_no LIKE ?");
        assertThat(filteredSql)
                .contains("batch_reference_no LIKE ?")
                .doesNotContain("<if", "#{");
    }

    @Test
    void keepsBatchUsageFieldsMappedThroughRecordsViewsAndService() throws Exception {
        String records = Files.readString(Path.of(
                "src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseRecords.java"
        ));
        String views = Files.readString(Path.of(
                "src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseViews.java"
        ));
        String service = Files.readString(Path.of(
                "src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"
        ));

        assertThat(records)
                .contains("public Boolean alreadyAppointed;")
                .contains("public Integer scheduledAppointmentQuantity;")
                .contains("public Boolean batchUsedByAsn;")
                .contains("public String batchUsageLabel;");
        assertThat(views)
                .contains("public Boolean alreadyAppointed;")
                .contains("public Integer scheduledAppointmentQuantity;")
                .contains("public Boolean batchUsedByAsn;")
                .contains("public String batchUsageLabel;");
        assertThat(service)
                .contains("view.alreadyAppointed = row.alreadyAppointed")
                .contains("view.batchUsedByAsn = row.batchUsedByAsn")
                .contains("view.scheduledAppointmentQuantity = row.scheduledAppointmentQuantity;")
                .contains("view.batchUsageLabel = firstNonBlank(")
                .contains("row.batchUsageLabel");
    }
}
