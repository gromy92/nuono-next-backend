package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitPluginSyncRecords.EtBoxSyncStateRow;
import java.util.List;
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
