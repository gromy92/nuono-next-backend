package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.BarcodeProductIdentity;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncStateRow;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitGoodsLineMapper extends InTransitGoodsSequenceMapper {

    @Select({
            LINE_SELECT,
            "WHERE line.owner_user_id = #{ownerUserId} AND line.batch_id = #{batchId} AND line.is_deleted = b'0'",
            "ORDER BY line.box_no IS NULL ASC, line.box_no ASC, line.id ASC"
    })
    List<LineRow> listLines(@Param("ownerUserId") Long ownerUserId, @Param("batchId") Long batchId);

    @Select({
            LINE_SELECT,
            "WHERE line.owner_user_id = #{ownerUserId} AND line.batch_id = #{batchId} AND line.id = #{lineId} AND line.is_deleted = b'0' LIMIT 1"
    })
    LineRow selectLineById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("lineId") Long lineId
    );

    @Select({
            LINE_SELECT,
            "WHERE line.owner_user_id = #{ownerUserId}",
            "AND line.batch_id = #{batchId}",
            "AND line.box_no = #{boxNo}",
            "AND line.psku = #{psku}",
            "AND line.is_deleted = b'0'",
            "ORDER BY line.gmt_updated DESC, line.id DESC",
            "LIMIT 1"
    })
    LineRow selectLineByBoxNoAndPsku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo,
            @Param("psku") String psku
    );

    @ConstructorArgs({
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "partnerSku", javaType = String.class)
    })
    @Select({
            "SELECT pb.logical_store_id AS logicalStoreId, pb.partner_sku AS partnerSku",
            "FROM product_barcode pb",
            "JOIN product_master pm",
            "  ON pm.id = pb.product_master_id",
            " AND pm.logical_store_id = pb.logical_store_id",
            " AND BINARY pm.partner_sku = BINARY pb.partner_sku",
            " AND pm.is_deleted = b'0'",
            "JOIN logical_store ls",
            "  ON ls.id = pb.logical_store_id",
            " AND ls.owner_user_id = #{ownerUserId}",
            " AND ls.is_deleted = b'0'",
            "WHERE pb.barcode = #{barcode}",
            "  AND pb.is_deleted = b'0'",
            "  AND pb.logical_store_id IS NOT NULL",
            "  AND NULLIF(TRIM(pb.partner_sku), '') IS NOT NULL",
            "  AND COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'",
            "LIMIT 1"
    })
    BarcodeProductIdentity selectProductIdentityByBarcode(
            @Param("ownerUserId") Long ownerUserId,
            @Param("barcode") String barcode
    );

    @Select({
            PACKAGE_SELECT,
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND box_no = #{boxNo} AND is_deleted = b'0' LIMIT 1"
    })
    PackageRow selectPackageByBoxNo(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo
    );

    @Select({
            PACKAGE_SELECT,
            "WHERE owner_user_id = #{ownerUserId}",
            "AND batch_id = #{batchId}",
            "AND external_box_no = #{externalBoxNo}",
            "AND is_deleted = b'0'",
            "ORDER BY CASE WHEN box_no <> external_box_no THEN 0 ELSE 1 END, gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    PackageRow selectPackageByExternalBoxNoForMerge(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("externalBoxNo") String externalBoxNo
    );

    @Select({
            "SELECT",
            "CASE WHEN pkg.id IS NULL THEN 0 ELSE 1 END AS package_exists,",
            "CASE WHEN pkg.id IS NOT NULL AND (",
            "pkg.weight_kg IS NOT NULL OR pkg.length_cm IS NOT NULL OR pkg.width_cm IS NOT NULL OR pkg.height_cm IS NOT NULL",
            "OR pkg.volume_cbm IS NOT NULL OR pkg.volume_weight_kg IS NOT NULL OR pkg.chargeable_weight_kg IS NOT NULL",
            "OR pkg.measured_weight_kg IS NOT NULL OR pkg.measured_length_cm IS NOT NULL OR pkg.measured_width_cm IS NOT NULL",
            "OR pkg.measured_height_cm IS NOT NULL OR pkg.measured_volume_cbm IS NOT NULL",
            ") THEN 1 ELSE 0 END AS package_spec_complete,",
            "COALESCE((",
            "SELECT COUNT(*) FROM in_transit_goods_line line",
            "WHERE line.owner_user_id = pkg.owner_user_id AND line.batch_id = pkg.batch_id AND line.package_id = pkg.id",
            "AND line.is_deleted = b'0' AND NULLIF(TRIM(line.psku), '') IS NOT NULL AND line.shipped_quantity IS NOT NULL",
            "), 0) AS line_count",
            "FROM in_transit_package pkg",
            "WHERE pkg.owner_user_id = #{ownerUserId}",
            "AND pkg.batch_id = #{batchId}",
            "AND (",
            "(#{externalBoxNo} IS NOT NULL AND #{externalBoxNo} != '' AND pkg.external_box_no = #{externalBoxNo})",
            "OR (#{boxNo} IS NOT NULL AND #{boxNo} != '' AND pkg.box_no = #{boxNo})",
            ")",
            "AND pkg.is_deleted = b'0'",
            "ORDER BY CASE",
            "WHEN #{boxNo} IS NOT NULL AND #{boxNo} != '' AND pkg.box_no = #{boxNo} THEN 0",
            "WHEN #{externalBoxNo} IS NOT NULL AND #{externalBoxNo} != '' AND pkg.external_box_no = #{externalBoxNo} THEN 1",
            "ELSE 2 END,",
            "pkg.gmt_updated DESC, pkg.id DESC",
            "LIMIT 1"
    })
    EtBoxSyncStateRow selectEtBoxSyncState(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("externalBoxNo") String externalBoxNo,
            @Param("boxNo") String boxNo
    );

    @Insert({
            "INSERT INTO in_transit_package (",
            "id, owner_user_id, batch_id, box_no, external_box_no, tracking_no,",
            "weight_kg, length_cm, width_cm, height_cm, volume_cbm, volume_weight_kg, chargeable_weight_kg,",
            "measured_weight_kg, measured_length_cm, measured_width_cm, measured_height_cm, measured_volume_cbm,",
            "package_status, logistics_status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.boxNo}, #{row.externalBoxNo}, #{row.trackingNo},",
            "#{row.weightKg}, #{row.lengthCm}, #{row.widthCm}, #{row.heightCm}, #{row.volumeCbm}, #{row.volumeWeightKg}, #{row.chargeableWeightKg},",
            "#{row.measuredWeightKg}, #{row.measuredLengthCm}, #{row.measuredWidthCm}, #{row.measuredHeightCm}, #{row.measuredVolumeCbm},",
            "#{row.packageStatus}, #{row.logisticsStatus}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertPackage(@Param("row") PackageRow row);

    @Update({
            "UPDATE in_transit_package",
            "SET external_box_no = #{row.externalBoxNo}, tracking_no = #{row.trackingNo},",
            "weight_kg = #{row.weightKg}, length_cm = #{row.lengthCm}, width_cm = #{row.widthCm}, height_cm = #{row.heightCm},",
            "volume_cbm = #{row.volumeCbm}, volume_weight_kg = #{row.volumeWeightKg}, chargeable_weight_kg = #{row.chargeableWeightKg},",
            "measured_weight_kg = #{row.measuredWeightKg},",
            "measured_length_cm = #{row.measuredLengthCm}, measured_width_cm = #{row.measuredWidthCm},",
            "measured_height_cm = #{row.measuredHeightCm}, measured_volume_cbm = #{row.measuredVolumeCbm},",
            "package_status = #{row.packageStatus}, logistics_status = #{row.logisticsStatus},",
            "updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND batch_id = #{row.batchId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updatePackage(@Param("row") PackageRow row);

    @Select({
            "SELECT COUNT(DISTINCT pkg.id)",
            "FROM in_transit_package pkg",
            "JOIN in_transit_goods_line line",
            "ON line.owner_user_id = pkg.owner_user_id",
            "AND line.batch_id = pkg.batch_id",
            "AND (line.package_id = pkg.id OR (line.package_id IS NULL AND line.box_no = pkg.box_no))",
            "AND line.is_deleted = b'0'",
            "AND NULLIF(TRIM(line.psku), '') IS NOT NULL",
            "WHERE pkg.owner_user_id = #{ownerUserId}",
            "AND pkg.batch_id = #{batchId}",
            "AND pkg.is_deleted = b'0'",
            "AND pkg.chargeable_weight_kg IS NULL"
    })
    int countPackagesWithGoodsLinesMissingChargeable(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Update({
            "<script>",
            "UPDATE in_transit_goods_line",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "AND (",
            "box_no NOT IN",
            "<foreach collection='boxNos' item='boxNo' open='(' separator=',' close=')'>#{boxNo}</foreach>",
            "OR CONCAT(box_no, '\n', psku) NOT IN",
            "<foreach collection='lineKeys' item='lineKey' open='(' separator=',' close=')'>#{lineKey}</foreach>",
            ")",
            "</script>"
    })
    int softDeleteLinesNotInSyncedDetails(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNos") List<String> boxNos,
            @Param("lineKeys") List<String> lineKeys,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "<script>",
            "UPDATE in_transit_package",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND is_deleted = b'0'",
            "AND box_no NOT IN",
            "<foreach collection='boxNos' item='boxNo' open='(' separator=',' close=')'>#{boxNo}</foreach>",
            "</script>"
    })
    int softDeletePackagesNotInSyncedBoxes(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNos") List<String> boxNos,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO in_transit_goods_line (",
            "id, owner_user_id, batch_id, package_id, box_no, sku, msku, psku, product_name, store_code, site_code,",
            "shipped_quantity, received_quantity, remaining_quantity, carton_count, units_per_carton,",
            "carton_weight_kg, carton_volume_cbm, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.packageId}, #{row.boxNo}, #{row.sku}, #{row.msku}, #{row.psku}, #{row.productName},",
            "#{row.storeCode}, #{row.siteCode}, #{row.shippedQuantity}, #{row.receivedQuantity}, #{row.remainingQuantity},",
            "#{row.cartonCount}, #{row.unitsPerCarton}, #{row.cartonWeightKg}, #{row.cartonVolumeCbm},",
            "b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertLine(@Param("row") LineRow row);

    @Update({
            "UPDATE in_transit_goods_line",
            "SET package_id = #{row.packageId}, box_no = #{row.boxNo}, sku = #{row.sku}, msku = #{row.msku}, psku = #{row.psku}, product_name = #{row.productName},",
            "store_code = #{row.storeCode}, site_code = #{row.siteCode}, shipped_quantity = #{row.shippedQuantity},",
            "received_quantity = #{row.receivedQuantity}, remaining_quantity = #{row.remainingQuantity},",
            "carton_count = #{row.cartonCount}, units_per_carton = #{row.unitsPerCarton},",
            "carton_weight_kg = #{row.cartonWeightKg}, carton_volume_cbm = #{row.cartonVolumeCbm},",
            "updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{row.ownerUserId} AND batch_id = #{row.batchId} AND id = #{row.id} AND is_deleted = b'0'"
    })
    int updateLine(@Param("row") LineRow row);

    @Update({
            "UPDATE product_site_offer pso",
            "JOIN (",
            "    SELECT matched_history.product_site_offer_id,",
            "           MIN(matched_history.first_flow_at) AS first_flow_at,",
            "           MAX(matched_history.last_flow_at) AS last_flow_at,",
            "           MAX(matched_history.operator_user_id) AS operator_user_id",
            "    FROM (",
            "        SELECT pso_match.id AS product_site_offer_id,",
            "               history.first_flow_at,",
            "               history.last_flow_at,",
            "               history.operator_user_id",
            "        FROM product_site_offer pso_match",
            "        JOIN (",
            "            SELECT source.logical_store_id,",
            "                   source.partner_sku_key,",
            "                   MIN(source.first_flow_at) AS first_flow_at,",
            "                   MAX(source.last_flow_at) AS last_flow_at,",
            "                   MAX(source.operator_user_id) AS operator_user_id",
            "            FROM (",
            "        SELECT lss.logical_store_id,",
            "               CONVERT(UPPER(TRIM(COALESCE(pv.partner_sku, NULLIF(TRIM(line.psku), ''), NULLIF(TRIM(line.sku), '')))) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,",
            "               COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,",
            "               COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at,",
            "               COALESCE(line.updated_by, line.created_by, batch.updated_by, batch.created_by) AS operator_user_id",
            "        FROM in_transit_goods_line line",
            "        JOIN in_transit_batch batch",
            "          ON batch.id = line.batch_id",
            "         AND batch.owner_user_id = line.owner_user_id",
            "         AND batch.is_deleted = b'0'",
            "         AND COALESCE(batch.batch_status, '') <> 'cancelled'",
            "        JOIN logical_store_site lss",
            "          ON lss.is_deleted = b'0'",
            "         AND CONVERT(UPPER(TRIM(lss.store_code)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "             = CONVERT(UPPER(TRIM(COALESCE(NULLIF(TRIM(line.store_code), ''), NULLIF(TRIM(batch.target_store_code), '')))) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "        JOIN logical_store ls",
            "          ON ls.id = lss.logical_store_id",
            "         AND ls.owner_user_id = line.owner_user_id",
            "         AND ls.is_deleted = b'0'",
            "        LEFT JOIN product_barcode pb",
            "          ON CONVERT(UPPER(TRIM(pb.barcode)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "             IN (",
            "                 CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci,",
            "                 CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "             )",
            "         AND pb.is_deleted = b'0'",
            "        LEFT JOIN product_variant pv",
            "          ON pv.id = pb.variant_id",
            "         AND pv.logical_store_id = lss.logical_store_id",
            "         AND pv.is_deleted = b'0'",
            "        WHERE line.owner_user_id = #{ownerUserId}",
            "          AND line.batch_id = #{batchId}",
            "          AND line.id = #{lineId}",
            "          AND line.is_deleted = b'0'",
            "          AND (",
            "              (line.psku IS NOT NULL AND TRIM(line.psku) <> '')",
            "              OR (line.sku IS NOT NULL AND TRIM(line.sku) <> '')",
            "          )",
            "          AND COALESCE(NULLIF(TRIM(line.store_code), ''), NULLIF(TRIM(batch.target_store_code), '')) IS NOT NULL",
            "        UNION ALL",
            "        SELECT owner_unique_product.logical_store_id,",
            "               raw_owner_in_transit.partner_sku_key,",
            "               raw_owner_in_transit.first_flow_at,",
            "               raw_owner_in_transit.last_flow_at,",
            "               raw_owner_in_transit.operator_user_id",
            "        FROM (",
            "            SELECT line.owner_user_id,",
            "                   CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,",
            "                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,",
            "                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at,",
            "                   COALESCE(line.updated_by, line.created_by, batch.updated_by, batch.created_by) AS operator_user_id",
            "            FROM in_transit_goods_line line",
            "            JOIN in_transit_batch batch",
            "              ON batch.id = line.batch_id",
            "             AND batch.owner_user_id = line.owner_user_id",
            "             AND batch.is_deleted = b'0'",
            "             AND COALESCE(batch.batch_status, '') <> 'cancelled'",
            "            WHERE line.owner_user_id = #{ownerUserId}",
            "              AND line.batch_id = #{batchId}",
            "              AND line.id = #{lineId}",
            "              AND line.is_deleted = b'0'",
            "              AND line.psku IS NOT NULL",
            "              AND TRIM(line.psku) <> ''",
            "            UNION ALL",
            "            SELECT line.owner_user_id,",
            "                   CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,",
            "                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,",
            "                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at,",
            "                   COALESCE(line.updated_by, line.created_by, batch.updated_by, batch.created_by) AS operator_user_id",
            "            FROM in_transit_goods_line line",
            "            JOIN in_transit_batch batch",
            "              ON batch.id = line.batch_id",
            "             AND batch.owner_user_id = line.owner_user_id",
            "             AND batch.is_deleted = b'0'",
            "             AND COALESCE(batch.batch_status, '') <> 'cancelled'",
            "            WHERE line.owner_user_id = #{ownerUserId}",
            "              AND line.batch_id = #{batchId}",
            "              AND line.id = #{lineId}",
            "              AND line.is_deleted = b'0'",
            "              AND line.sku IS NOT NULL",
            "              AND TRIM(line.sku) <> ''",
            "            UNION ALL",
            "            SELECT line.owner_user_id,",
            "                   CONVERT(UPPER(TRIM(pv.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key,",
            "                   COALESCE(batch.source_created_at, line.gmt_create, batch.gmt_create) AS first_flow_at,",
            "                   COALESCE(line.gmt_updated, batch.gmt_updated, NOW()) AS last_flow_at,",
            "                   COALESCE(line.updated_by, line.created_by, batch.updated_by, batch.created_by) AS operator_user_id",
            "            FROM in_transit_goods_line line",
            "            JOIN in_transit_batch batch",
            "              ON batch.id = line.batch_id",
            "             AND batch.owner_user_id = line.owner_user_id",
            "             AND batch.is_deleted = b'0'",
            "             AND COALESCE(batch.batch_status, '') <> 'cancelled'",
            "            JOIN product_barcode pb",
            "              ON pb.is_deleted = b'0'",
            "             AND CONVERT(UPPER(TRIM(pb.barcode)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "                 IN (",
            "                     CONVERT(UPPER(TRIM(line.psku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci,",
            "                     CONVERT(UPPER(TRIM(line.sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "                 )",
            "            JOIN product_variant pv",
            "              ON pv.id = pb.variant_id",
            "             AND pv.is_deleted = b'0'",
            "             AND pv.partner_sku IS NOT NULL",
            "             AND TRIM(pv.partner_sku) <> ''",
            "            JOIN logical_store pv_ls",
            "              ON pv_ls.id = pv.logical_store_id",
            "             AND pv_ls.owner_user_id = line.owner_user_id",
            "             AND pv_ls.is_deleted = b'0'",
            "            WHERE line.owner_user_id = #{ownerUserId}",
            "              AND line.batch_id = #{batchId}",
            "              AND line.id = #{lineId}",
            "              AND line.is_deleted = b'0'",
            "        ) raw_owner_in_transit",
            "        JOIN (",
            "            SELECT owner_product.owner_user_id,",
            "                   owner_product.partner_sku_key,",
            "                   MIN(owner_product.logical_store_id) AS logical_store_id",
            "            FROM (",
            "                SELECT ls.owner_user_id,",
            "                       pso.logical_store_id,",
            "                       CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key",
            "                FROM product_site_offer pso",
            "                JOIN logical_store ls",
            "                  ON ls.id = pso.logical_store_id",
            "                 AND ls.is_deleted = b'0'",
            "                WHERE pso.is_deleted = b'0'",
            "                  AND pso.partner_sku IS NOT NULL",
            "                  AND TRIM(pso.partner_sku) <> ''",
            "                UNION ALL",
            "                SELECT ls.owner_user_id,",
            "                       pso.logical_store_id,",
            "                       CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci AS partner_sku_key",
            "                FROM product_site_offer pso",
            "                JOIN logical_store ls",
            "                  ON ls.id = pso.logical_store_id",
            "                 AND ls.is_deleted = b'0'",
            "                WHERE pso.is_deleted = b'0'",
            "                  AND pso.partner_sku IS NOT NULL",
            "                  AND TRIM(pso.partner_sku) <> ''",
            "                  AND CONVERT(UPPER(TRIM(pso.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "            ) owner_product",
            "            GROUP BY owner_product.owner_user_id, owner_product.partner_sku_key",
            "            HAVING COUNT(DISTINCT owner_product.logical_store_id) = 1",
            "        ) owner_unique_product",
            "          ON owner_unique_product.owner_user_id = raw_owner_in_transit.owner_user_id",
            "         AND owner_unique_product.partner_sku_key = raw_owner_in_transit.partner_sku_key",
            "            ) source",
            "            GROUP BY source.logical_store_id, source.partner_sku_key",
            "        ) history",
            "          ON history.logical_store_id = pso_match.logical_store_id",
            "         AND (",
            "                history.partner_sku_key = CONVERT(UPPER(TRIM(pso_match.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "                OR (",
            "                    CONVERT(UPPER(TRIM(pso_match.partner_sku)) USING utf8mb4) COLLATE utf8mb4_unicode_ci REGEXP '[0-9]-[0-9]+$'",
            "                    AND history.partner_sku_key = CONVERT(REGEXP_REPLACE(UPPER(TRIM(pso_match.partner_sku)), '-[0-9]+$', '') USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            "                )",
            "             )",
            "        WHERE pso_match.is_deleted = b'0'",
            "          AND pso_match.partner_sku IS NOT NULL",
            "          AND TRIM(pso_match.partner_sku) <> ''",
            "    ) matched_history",
            "    GROUP BY matched_history.product_site_offer_id",
            ") history",
            "  ON history.product_site_offer_id = pso.id",
            "SET pso.logistics_has_history = b'1',",
            "    pso.logistics_first_flow_at = CASE",
            "        WHEN pso.logistics_first_flow_at IS NULL THEN COALESCE(history.first_flow_at, NOW())",
            "        WHEN history.first_flow_at IS NULL THEN pso.logistics_first_flow_at",
            "        WHEN pso.logistics_first_flow_at > history.first_flow_at THEN history.first_flow_at",
            "        ELSE pso.logistics_first_flow_at",
            "    END,",
            "    pso.logistics_last_flow_at = CASE",
            "        WHEN pso.logistics_last_flow_at IS NULL THEN COALESCE(history.last_flow_at, NOW())",
            "        WHEN history.last_flow_at IS NULL THEN pso.logistics_last_flow_at",
            "        WHEN pso.logistics_last_flow_at < history.last_flow_at THEN history.last_flow_at",
            "        ELSE pso.logistics_last_flow_at",
            "    END,",
            "    pso.logistics_history_source = 'IN_TRANSIT_GOODS_LINE',",
            "    pso.updated_by = COALESCE(#{operatorUserId}, history.operator_user_id),",
            "    pso.gmt_updated = NOW()",
            "WHERE pso.is_deleted = b'0'",
            "  AND pso.partner_sku IS NOT NULL",
            "  AND TRIM(pso.partner_sku) <> ''"
    })
    int markProductSiteOfferLogisticsHistoryByLine(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("lineId") Long lineId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE in_transit_goods_line",
            "SET is_deleted = b'1', updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId} AND id = #{lineId} AND is_deleted = b'0'"
    })
    int deleteLine(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("lineId") Long lineId,
            @Param("operatorUserId") Long operatorUserId
    );
}
