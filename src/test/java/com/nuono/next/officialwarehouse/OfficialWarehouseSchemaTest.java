package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.permission.access.BusinessCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OfficialWarehouseSchemaTest {

    @Test
    void officialWarehouseSchemaIsIncludedInLocalDbBootstrapList() throws Exception {
        String java = Files.readString(Path.of("src/main/java/com/nuono/next/system/LocalDbBootstrapStatusService.java"));

        assertThat(java).contains("classpath:db/init/134_official_warehouse_asn.sql");
        assertThat(java).contains("classpath:db/init/135_product_variant_spec_source_noon_partner_psku.sql");
        assertThat(java).contains("classpath:db/init/136_official_warehouse_appointment.sql");
        assertThat(java).contains("classpath:db/init/144_official_warehouse_asn_shipping_batch_link.sql");
    }

    @Test
    void officialWarehouseSchemaProvidesAsnLinesAndGenericNoonCallLog() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/134_official_warehouse_asn.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_asn`")
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_asn_line`")
                .contains("CREATE TABLE IF NOT EXISTS `noon_http_call_log`")
                .contains("`routing_response_json` LONGTEXT DEFAULT NULL")
                .contains("`psku_code` VARCHAR(100) NOT NULL")
                .contains("`storage_type_code` VARCHAR(60) NOT NULL DEFAULT 'standard'")
                .contains("`cubic_feet` DECIMAL(12,5) DEFAULT NULL")
                .contains("`request_summary_json` LONGTEXT DEFAULT NULL")
                .contains("`response_summary_json` LONGTEXT DEFAULT NULL")
                .contains("`operation` VARCHAR(80) NOT NULL")
                .contains("`business_ref` VARCHAR(120) DEFAULT NULL");

        assertThat(normalized).doesNotContain("authorization");
        assertThat(normalized).doesNotContain("`cookie`");
    }

    @Test
    void officialWarehouseAppointmentSchemaKeepsAppointmentStateSeparateFromHttpLogs() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/136_official_warehouse_appointment.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_appointment`")
                .contains("`asn_id` BIGINT NOT NULL")
                .contains("`noon_asn_nr` VARCHAR(120) NOT NULL")
                .contains("`warehouse_from` VARCHAR(120) NOT NULL")
                .contains("`warehouse_to_partner_code` VARCHAR(80) NOT NULL")
                .contains("`ap_start_date` DATE NOT NULL")
                .contains("`ap_end_date` DATE NOT NULL")
                .contains("`appointment_slot_id` INT DEFAULT NULL")
                .contains("`attempt_count` INT NOT NULL DEFAULT 0")
                .contains("KEY `idx_official_warehouse_appointment_status`");

        assertThat(normalized).doesNotContain("authorization");
        assertThat(normalized).doesNotContain("`cookie`");
    }

    @Test
    void officialWarehouseAsnShippingBatchLinkSchemaKeepsExplicitSourceRelations() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/init/144_official_warehouse_asn_shipping_batch_link.sql"));
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_asn_shipping_batch_link`")
                .contains("`asn_line_id` BIGINT NOT NULL")
                .contains("`shipping_batch_id` BIGINT DEFAULT NULL")
                .contains("`shipping_batch_source_id` BIGINT DEFAULT NULL")
                .contains("`in_transit_batch_id` BIGINT DEFAULT NULL")
                .contains("`batch_reference_no` VARCHAR(160) DEFAULT NULL")
                .contains("`tracking_no` VARCHAR(160) DEFAULT NULL")
                .contains("`in_transit_goods_line_id` BIGINT DEFAULT NULL")
                .contains("`purchase_order_id` BIGINT DEFAULT NULL")
                .contains("`quantity` INT NOT NULL DEFAULT 0")
                .contains("`relation_basis` VARCHAR(80) NOT NULL DEFAULT 'ASN_CREATE_SELECTED_BATCH'")
                .contains("KEY `idx_official_warehouse_asn_in_transit_batch`")
                .contains("KEY `idx_official_warehouse_asn_shipping_product`")
                .contains("official_warehouse_asn_shipping_batch_link");

        assertThat(normalized).doesNotContain("authorization");
        assertThat(normalized).doesNotContain("`cookie`");
    }

    @Test
    void officialWarehouseShippingBatchCandidatesUseRemainingUnlinkedQuantity() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));

        assertThat(mapper)
                .contains("official_warehouse_asn_shipping_batch_link")
                .contains("remainingQuantity")
                .contains("HAVING remainingQuantity &gt; 0");
    }

    @Test
    void officialWarehouseProductCandidatesCanBeScopedBySelectedShippingBatches() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseController.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));
        String views = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseViews.java"));

        assertThat(controller).contains("@RequestParam(required = false) List<String> shippingBatchIds");
        assertThat(service)
                .contains("listProductCandidatesFromShippingBatches")
                .contains("listShippingBatchSourceAllocations(")
                .contains("batchAvailableQuantity");
        assertThat(views).contains("public Integer batchAvailableQuantity;");
    }

    @Test
    void officialWarehouseProductCandidatesPreferRenderableProductImageAsset() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));

        assertThat(mapper)
                .contains("COALESCE(imageAsset.url, pm.cover_image_url) AS imageUrlCache")
                .contains("LEFT JOIN product_image_asset imageAsset")
                .contains("imageAsset.asset_status = 'synced'");
    }

    @Test
    void officialWarehouseProductCandidatesUseSiteOfferPskuCodeAsAsnPskuFallback() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));

        assertThat(mapper)
                .contains("COALESCE(NULLIF(TRIM(official.noon_partner_psku_code), ''), NULLIF(TRIM(pso.psku_code), '')) AS pskuCode");
    }

    @Test
    void officialWarehouseViewsNormalizeNoonImageUrlsBeforeReturningRows() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(service)
                .contains("import com.nuono.next.productselection.NoonImageUrlNormalizer;")
                .contains("lineRow.imageUrlCache = NoonImageUrlNormalizer.normalize(candidate.imageUrlCache);")
                .contains("view.imageUrl = NoonImageUrlNormalizer.normalize(row.imageUrlCache);");
    }

    @Test
    void officialWarehouseHasOwnBusinessCapability() {
        assertThat(BusinessCapability.OFFICIAL_WAREHOUSE.getMenuPathPrefixes())
                .contains("/warehouse/official-warehouse", "/api/warehouse/official-warehouse");
    }
}
