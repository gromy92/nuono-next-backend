package com.nuono.next.officialwarehouse;

import static com.nuono.next.schema.DbInitScriptAssertions.assertInitScriptsInclude;
import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.permission.access.BusinessCapability;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class OfficialWarehouseSchemaTest {

    @Test
    void officialWarehouseSchemaIsIncludedInLocalDbBootstrapList() throws Exception {
        assertInitScriptsInclude(
                "classpath:db/init/134_official_warehouse_asn.sql",
                "classpath:db/init/135_product_variant_spec_source_noon_partner_psku.sql",
                "classpath:db/init/136_official_warehouse_appointment.sql",
                "classpath:db/init/144_official_warehouse_asn_shipping_batch_link.sql",
                "classpath:db/init/188_official_warehouse_asn_sync_throttle.sql"
        );
    }

    @Test
    void officialWarehouseManualAsnListSyncUsesPersistentStoreSiteThrottle() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/init/188_official_warehouse_asn_sync_throttle.sql"
        ));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"
        ));
        String service = Files.readString(Path.of(
                "src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"
        ));

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS `official_warehouse_asn_sync_throttle`")
                .contains("PRIMARY KEY (`owner_user_id`, `store_code`, `site_code`)")
                .contains("`last_started_at` DATETIME NOT NULL")
                .contains("`claim_token` VARCHAR(64) NOT NULL");
        assertThat(mapper)
                .contains("claimOfficialWarehouseAsnListSync")
                .contains("DATE_SUB(NOW(), INTERVAL 60 MINUTE)")
                .contains("selectOfficialWarehouseAsnListSyncThrottle");
        assertThat(service)
                .contains("OFFICIAL_WAREHOUSE_ASN_SYNC_RATE_LIMITED")
                .contains("HttpStatus.TOO_MANY_REQUESTS")
                .contains("claimOfficialWarehouseAsnListSync(")
                .contains("openNoonSession(ownerUserId, binding)");
        assertThat(service.indexOf("openNoonSession(ownerUserId, binding)"))
                .isLessThan(service.indexOf("claimOfficialWarehouseAsnListSync("));
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
    void officialWarehouseShippingBatchCandidatesKeepAppointedBatchesVisibleAfterAvailableBatches() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));
        String records = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseRecords.java"));
        String views = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseViews.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(mapper)
                .contains("official_warehouse_asn_shipping_batch_link")
                .contains("JOIN official_warehouse_asn linkedAsn")
                .contains("UPPER(COALESCE(linkedAsn.status, '')) NOT IN ('FAILED', 'CANCELED', 'CANCELLED')")
                .contains("scheduledAppointmentQuantity")
                .contains("official_warehouse_appointment scheduledAppointment")
                .contains("scheduledAppointment.status = 'SCHEDULED'")
                .contains("remainingQuantity")
                .contains("COALESCE(linked.scheduledAppointmentQuantity, 0), 0), 0)), 0) AS remainingQuantity")
                .contains("COUNT(DISTINCT COALESCE(NULLIF(line.psku, ''), NULLIF(line.sku, ''), NULLIF(line.msku, ''))) AS skuCount")
                .contains("alreadyAppointed")
                .contains("batchUsedByAsn")
                .contains("batchUsageLabel")
                .contains("ORDER BY batchUsedByAsn ASC, b.gmt_updated DESC, b.id DESC")
                .doesNotContain("HAVING remainingQuantity &gt; 0");
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
    void officialWarehouseBatchQuantitiesUseConfirmedStoreSiteAllocationsWithoutJoinFanout() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));

        assertThat(mapper)
                .contains("target_store_code")
                .contains("target_site_code")
                .contains("allocated_quantity")
                .contains("scopedQuantity")
                .contains("inTransitGoodsLineId")
                .doesNotContain("COALESCE(NULLIF(line.store_code, ''), #{storeCode}) AS sourceStoreCode");
    }

    @Test
    void officialWarehouseSupportsBatchPskuSearchAndNonBlockingCompletenessValidation() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseController.java"));
        String commands = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseCommands.java"));
        String views = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseViews.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(controller)
                .contains("@RequestParam(required = false) List<String> partnerSkus")
                .contains("@PostMapping(\"/asns/validate\")");
        assertThat(commands).contains("public Boolean partialBatchConfirmed;");
        assertThat(views)
                .contains("public static class AsnValidationView")
                .contains("public List<MissingBatchView> missingBatches");
        assertThat(service)
                .contains("OFFICIAL_WAREHOUSE_PARTIAL_BATCH_CONFIRM_REQUIRED")
                .contains("partialBatchConfirmationRequired(validation)");
    }

    @Test
    void officialWarehouseShippingBatchProductCandidatesRemainReusableAfterScheduledAppointment() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));
        String sourceAllocationQuery = mapper.substring(
                mapper.indexOf("\"SELECT NULL AS shippingBatchId,"),
                mapper.indexOf("List<ShippingBatchSourceAllocationRecord> listShippingBatchSourceAllocations")
        );

        assertThat(sourceAllocationQuery)
                .contains("allocationScope.scopedQuantity")
                .contains("ELSE GREATEST(COALESCE(line.shipped_quantity, 0), 0) END AS quantity")
                .doesNotContain("linked.scheduledAppointmentQuantity")
                .doesNotContain("official_warehouse_appointment scheduledAppointment")
                .doesNotContain("- GREATEST(COALESCE(linked.linkedQuantity, 0), 0)");
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
    void officialWarehouseProductCandidatesFallbackToProductSpecSourcesWhenOfficialDimensionsAreMissing() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));

        assertThat(mapper)
                .contains("LEFT JOIN product_variant_spec_source warehouseSpec")
                .contains("warehouseSpec.source_type = 'warehouse'")
                .contains("LEFT JOIN product_variant_spec_source ali1688Spec")
                .contains("ali1688Spec.source_type = 'ali1688'")
                .contains("COALESCE(official.product_length_cm, effective.product_length_cm, warehouseSpec.product_length_cm, ali1688Spec.product_length_cm, pvs.product_length_cm) AS productLengthCm")
                .contains("COALESCE(official.product_width_cm, effective.product_width_cm, warehouseSpec.product_width_cm, ali1688Spec.product_width_cm, pvs.product_width_cm) AS productWidthCm")
                .contains("COALESCE(official.product_height_cm, effective.product_height_cm, warehouseSpec.product_height_cm, ali1688Spec.product_height_cm, pvs.product_height_cm) AS productHeightCm");
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));
        assertThat(service)
                .contains("view.missingTags.add(\"缺尺寸\")")
                .doesNotContain("缺官方尺寸");
    }

    @Test
    void officialWarehouseViewsNormalizeNoonImageUrlsBeforeReturningRows() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(service)
                .contains("import com.nuono.next.product.ProductImageUrlSupport;")
                .contains("lineRow.imageUrlCache = ProductImageUrlSupport.normalize(candidate.imageUrlCache);")
                .contains("view.imageUrl = ProductImageUrlSupport.normalize(row.imageUrlCache);");
    }

    @Test
    void officialWarehouseHidesAndCleansPreSubmitAsnFailures() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));

        assertThat(mapper)
                .contains("AND NOT (status = 'FAILED' AND (noon_asn_nr IS NULL OR TRIM(noon_asn_nr) = ''))")
                .contains("int softDeleteAsnShippingBatchLinks(")
                .contains("int softDeleteAsnLines(")
                .contains("int softDeletePreSubmitAsn(")
                .contains("AND (noon_asn_nr IS NULL OR TRIM(noon_asn_nr) = '')");
        assertThat(service)
                .contains("boolean remoteAsnCreated = false;")
                .contains("remoteAsnCreated = true;")
                .contains("private void failAsnCreation(")
                .contains("if (!remoteAsnCreated)")
                .contains("mapper.softDeletePreSubmitAsn(asnId, operatorUserId);");
    }

    @Test
    void officialWarehouseNoonSyncCanPrefillRoutingSnapshotWithoutChangingAsnStatus() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));
        String client = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseNoonInboundClient.java"));
        String controller = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseController.java"));
        String commands = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseCommands.java"));
        int snapshotSqlStart = mapper.indexOf("\"SET routing_response_json = #{routingResponseJson}");
        int snapshotSqlEnd = mapper.indexOf("int updateAsnRoutingSnapshot", snapshotSqlStart);
        String snapshotSql = mapper.substring(snapshotSqlStart, snapshotSqlEnd);

        assertThat(mapper)
                .contains("int updateAsnRoutingSnapshot(")
                .contains("selected_warehouse_partner_code = #{selectedWarehousePartnerCode}");
        assertThat(snapshotSql).doesNotContain("status = 'ROUTED'");
        assertThat(service)
                .contains("syncNoonAsnListRow(result, ownerUserId, site, binding, session, remoteRow, false")
                .contains("syncNoonAsnListRow(result, ownerUserId, site, binding, session, remoteRow, true")
                .contains("prefillSyncedAsnRoutingWarehouses(")
                .contains("queryAsnDetailRow(session, binding, context, syncRecord.noonAsnNr)")
                .contains("mapper.updateAsnRoutingSnapshot(");
        assertThat(client)
                .contains("JsonNode queryAsnDetailRow(")
                .contains("routingLineRowsFromAsnDetail(JsonNode detail)");
        assertThat(commands).contains("public static class SyncNoonAsnNumbersCommand");
        assertThat(controller)
                .contains("@PostMapping(\"/asns/sync-noon-numbers\")")
                .contains("command.dryRun == null || command.dryRun")
                .contains("service().syncNoonAsnNumbers(");
    }

    @Test
    void successfulAppointmentWarehouseSelectionUpdatesAsnCurrentWarehouseProjection() throws Exception {
        String mapper = Files.readString(Path.of("src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"));
        String service = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"));
        String runner = Files.readString(Path.of("src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseAppointmentRunner.java"));

        assertThat(runner)
                .contains("client.onWarehousesSet(task);")
                .contains("default void onWarehousesSet(AppointmentTask task)");
        assertThat(mapper)
                .contains("int updateAsnCurrentWarehouse(")
                .contains("selected_warehouse_partner_code = #{warehouseToPartnerCode}")
                .contains("AND owner_user_id = #{ownerUserId}");
        assertThat(service)
                .contains("persistAsnCurrentWarehouse(")
                .contains("task.warehouseToCode = appointment.warehouseToCode")
                .contains("task.warehouseToCode = resolveAppointmentWarehouseToCode(");
    }

    @Test
    void officialWarehouseHasOwnBusinessCapability() {
        assertThat(BusinessCapability.OFFICIAL_WAREHOUSE.getMenuPathPrefixes())
                .contains("/warehouse/official-warehouse", "/api/warehouse/official-warehouse");
    }
}
