package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchSqlProvider;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OfficialWarehouseShippingBatchSqlProviderTest {

    @Test
    void resolvesEligibleLinesOnlyThroughUniqueExactBarcodeIdentity() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        assertThat(sql)
                .contains("WITH ownerBarcodeCandidates AS")
                .contains("ownerBarcodeIdentity AS")
                .contains("HAVING COUNT(DISTINCT logicalStoreId, BINARY partnerSku) = 1")
                .contains("JOIN ownerBarcodeIdentity identityScope ON identityScope.barcodeKey = BINARY pb.barcode")
                .contains("storeScope AS")
                .contains("barcodeProductCandidates AS")
                .contains("barcodeProducts AS")
                .contains("BINARY pb.barcode AS barcodeKey")
                .contains("HAVING COUNT(DISTINCT productMasterId, productVariantId, BINARY partnerSku) = 1")
                .contains("JOIN barcodeProducts product ON product.barcode = line.sku")
                .contains("BINARY product.barcode = BINARY line.sku")
                .contains("BINARY line.psku = BINARY product.partnerSku")
                .contains("allocation.allocatedProductVariantId = product.productVariantId")
                .contains("COUNT(DISTINCT BINARY line.sku) AS skuCount")
                .doesNotContain("productIdentifiers AS")
                .doesNotContain("lineIdentifiers AS")
                .doesNotContain("line.msku")
                .doesNotContain("scopeBarcode.partner_sku = line.psku");
    }

    @Test
    void rejectsPartnerSkuAndAllocationVariantAsReverseIdentityFallbacks() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchCandidates();

        assertThat(sql)
                .contains("COALESCE(pb.barcode_type, '') &lt;&gt; 'PARTNER_SKU_ALIAS'")
                .contains("allocation.allocatedVariantCount = 1")
                .doesNotContain("COALESCE(line.allocatedProductVariantId")
                .doesNotContain("pskuCode AS identifier")
                .doesNotContain("childSku AS identifier")
                .doesNotContain("skuParent AS identifier");
    }

    @Test
    void sourceAllocationsExposeTheMatchedLogisticsBarcodeAfterStrictResolution() {
        String sql = OfficialWarehouseShippingBatchSqlProvider.listShippingBatchSourceAllocations();

        assertThat(sql)
                .contains("line.sku AS sourceBarcode")
                .contains("JOIN barcodeProducts product ON product.barcode = line.sku")
                .contains("BINARY product.barcode = BINARY line.sku")
                .contains("BINARY line.psku = BINARY product.partnerSku")
                .contains("allocation.allocatedProductVariantId = product.productVariantId")
                .contains("OR product.partnerSku IN")
                .contains("OR product.productVariantId IN")
                .doesNotContain("UPPER(COALESCE(line.psku")
                .doesNotContain("UPPER(COALESCE(line.sku")
                .doesNotContain("line.msku");
    }

    @Test
    void productionSourceAllocationMapperUsesTheSameStrictIdentityBoundary() {
        Method sourceMethod = Arrays.stream(OfficialWarehouseMapper.class.getMethods())
                .filter(method -> "listShippingBatchSourceAllocations".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        String sql = String.join(" ", sourceMethod.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("line.sku AS sourceBarcode")
                .contains("pb.barcode = line.sku")
                .contains("BINARY pb.barcode = BINARY line.sku")
                .contains("COUNT(DISTINCT identityBarcode.logical_store_id, BINARY identityBarcode.partner_sku)")
                .contains("COUNT(DISTINCT identityVariant.id)")
                .contains("BINARY line.psku = BINARY pm.partner_sku")
                .doesNotContain("scopeBarcode.partner_sku = line.psku")
                .doesNotContain("line.msku");
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
                .contains("WITH ownerBarcodeCandidates AS")
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
