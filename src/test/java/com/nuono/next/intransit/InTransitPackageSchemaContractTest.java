package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class InTransitPackageSchemaContractTest {

    private static final Path PACKAGE_MIGRATION = Path.of(
            "src", "main", "resources", "db", "init", "108_in_transit_package.sql"
    );
    private static final Path PACKAGE_CHARGEABLE_WEIGHT_MIGRATION = Path.of(
            "src", "main", "resources", "db", "init", "112_in_transit_package_chargeable_weight.sql"
    );

    @Test
    void batchAggregateUsesPackageLayerWeightAndVolumeWhenAvailable() throws NoSuchMethodException {
        Method aggregateLines = InTransitGoodsMapper.class.getMethod("aggregateLines", Long.class, Long.class);
        String aggregateSql = String.join(" ", aggregateLines.getAnnotation(Select.class).value());

        assertTrue(aggregateSql.contains("FROM in_transit_package pkg"));
        assertTrue(aggregateSql.contains("SUM(pkg.weight_kg)"));
        assertTrue(aggregateSql.contains("SUM(pkg.volume_cbm)"));
        assertTrue(aggregateSql.contains("COALESCE("));
    }

    @Test
    void batchAggregateRefreshPreservesExistingWeightAndVolumeWhenNoSourceSpecs() throws NoSuchMethodException {
        Method refreshBatchAggregate = InTransitGoodsMapper.class.getMethod(
                "refreshBatchAggregate",
                Long.class,
                Long.class,
                InTransitBatchRecords.BatchAggregateRow.class
        );
        String updateSql = String.join(" ", refreshBatchAggregate.getAnnotation(Update.class).value());

        assertTrue(updateSql.contains("total_weight_kg = COALESCE(#{aggregate.totalWeightKg}, total_weight_kg)"));
        assertTrue(updateSql.contains("total_volume_cbm = COALESCE(#{aggregate.totalVolumeCbm}, total_volume_cbm)"));
    }

    @Test
    void migrationDefinesPackageLayerBetweenBatchAndGoodsLinesWithoutPurchaseFeeOrInventoryFields() throws IOException {
        String sql = Files.readString(PACKAGE_MIGRATION);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `in_transit_package`"));
        assertTrue(sql.contains("`batch_id` BIGINT NOT NULL"));
        assertTrue(sql.contains("`box_no` VARCHAR(160) NOT NULL"));
        assertTrue(sql.contains("`external_box_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`tracking_no` VARCHAR(160) DEFAULT NULL"));
        assertTrue(sql.contains("`weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`length_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`width_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`height_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_length_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_width_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_height_cm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`measured_volume_cbm` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`package_status` VARCHAR(60) DEFAULT NULL"));
        assertTrue(sql.contains("`logistics_status` VARCHAR(60) DEFAULT NULL"));
        assertFalse(sql.contains("`remark`"));
        assertFalse(sql.contains("`merchant_box_no`"));
        assertTrue(sql.contains("KEY `idx_in_transit_package_external_box`"));
        assertTrue(sql.contains("KEY `idx_in_transit_package_batch`"));
        assertTrue(sql.contains("UNIQUE KEY `uk_in_transit_package_box`"));

        assertFalse(lower.contains("purchase_order"));
        assertFalse(lower.contains("procurement_order"));
        assertFalse(lower.contains("`fee"));
        assertFalse(lower.contains("_fee"));
        assertFalse(lower.contains("invoice"));
        assertFalse(lower.contains("settlement"));
        assertFalse(lower.contains("inventory"));
    }

    @Test
    void packageMapperSqlReadsAndWritesCanonicalPackageFields() throws NoSuchMethodException {
        String selectSql = InTransitGoodsMapper.PACKAGE_SELECT;
        assertTrue(selectSql.contains("external_box_no"));
        assertTrue(selectSql.contains("length_cm"));
        assertTrue(selectSql.contains("width_cm"));
        assertTrue(selectSql.contains("height_cm"));
        assertTrue(selectSql.contains("volume_weight_kg"));
        assertTrue(selectSql.contains("chargeable_weight_kg"));
        assertTrue(selectSql.contains("measured_weight_kg"));
        assertTrue(selectSql.contains("measured_length_cm"));
        assertTrue(selectSql.contains("measured_width_cm"));
        assertTrue(selectSql.contains("measured_height_cm"));
        assertTrue(selectSql.contains("measured_volume_cbm"));
        assertTrue(selectSql.contains("package_status"));
        assertTrue(selectSql.contains("logistics_status"));

        Method insertPackage = InTransitGoodsMapper.class.getMethod("insertPackage", InTransitBatchRecords.PackageRow.class);
        String insertSql = String.join(" ", insertPackage.getAnnotation(Insert.class).value());
        assertTrue(insertSql.contains("external_box_no"));
        assertTrue(insertSql.contains("length_cm"));
        assertTrue(insertSql.contains("width_cm"));
        assertTrue(insertSql.contains("height_cm"));
        assertTrue(insertSql.contains("volume_weight_kg"));
        assertTrue(insertSql.contains("chargeable_weight_kg"));
        assertTrue(insertSql.contains("measured_weight_kg"));
        assertTrue(insertSql.contains("measured_length_cm"));
        assertTrue(insertSql.contains("measured_width_cm"));
        assertTrue(insertSql.contains("measured_height_cm"));
        assertTrue(insertSql.contains("measured_volume_cbm"));
        assertTrue(insertSql.contains("package_status"));
        assertTrue(insertSql.contains("logistics_status"));
        assertFalse(insertSql.contains("merchant_box_no"));

        Method updatePackage = InTransitGoodsMapper.class.getMethod("updatePackage", InTransitBatchRecords.PackageRow.class);
        String updateSql = Arrays.stream(updatePackage.getAnnotation(Update.class).value())
                .reduce("", (left, right) -> left + " " + right);
        assertTrue(updateSql.contains("external_box_no"));
        assertTrue(updateSql.contains("length_cm"));
        assertTrue(updateSql.contains("width_cm"));
        assertTrue(updateSql.contains("height_cm"));
        assertTrue(updateSql.contains("volume_weight_kg"));
        assertTrue(updateSql.contains("chargeable_weight_kg"));
        assertTrue(updateSql.contains("measured_weight_kg"));
        assertTrue(updateSql.contains("measured_length_cm"));
        assertTrue(updateSql.contains("measured_width_cm"));
        assertTrue(updateSql.contains("measured_height_cm"));
        assertTrue(updateSql.contains("measured_volume_cbm"));
        assertTrue(updateSql.contains("package_status"));
        assertTrue(updateSql.contains("logistics_status"));
        assertFalse(updateSql.contains("merchant_box_no"));
    }

    @Test
    void packageChargeableWeightMigrationAddsAirFreightWeightFields() throws IOException {
        String sql = Files.readString(PACKAGE_CHARGEABLE_WEIGHT_MIGRATION);

        assertTrue(sql.contains("`volume_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertTrue(sql.contains("`chargeable_weight_kg` DECIMAL(18,6) DEFAULT NULL"));
        assertFalse(sql.toLowerCase(Locale.ROOT).contains("fee"));
    }
}
