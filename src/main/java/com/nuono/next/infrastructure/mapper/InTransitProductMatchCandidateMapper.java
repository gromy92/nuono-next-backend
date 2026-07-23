package com.nuono.next.infrastructure.mapper;

import com.nuono.next.intransit.InTransitProductMatchCandidate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InTransitProductMatchCandidateMapper extends InTransitGoodsSequenceMapper {
    String CANDIDATE_COLUMNS = ""
            + "id, owner_user_id AS ownerUserId, batch_id AS batchId, package_id AS packageId, "
            + "box_no AS boxNo, source_barcode AS sourceBarcode, source_psku AS sourcePsku, source_msku AS sourceMsku, "
            + "product_name AS productName, store_code AS storeCode, site_code AS siteCode, "
            + "shipped_quantity AS shippedQuantity, received_quantity AS receivedQuantity, "
            + "carton_count AS cartonCount, units_per_carton AS unitsPerCarton, "
            + "carton_weight_kg AS cartonWeightKg, carton_volume_cbm AS cartonVolumeCbm, "
            + "match_status AS matchStatus, match_message AS matchMessage, "
            + "created_by AS createdBy, updated_by AS updatedBy";

    default Long nextProductMatchCandidateId() {
        return nextId("in_transit_product_match_candidate", 59000L);
    }

    @Select({
            "SELECT", CANDIDATE_COLUMNS,
            "FROM in_transit_product_match_candidate",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND match_status = 'UNMATCHED' AND is_deleted = b'0'",
            "ORDER BY box_no, id"
    })
    List<InTransitProductMatchCandidate> listProductMatchCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "SELECT", CANDIDATE_COLUMNS,
            "FROM in_transit_product_match_candidate",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND box_no = #{boxNo} AND source_barcode = #{sourceBarcode}",
            "AND is_deleted = b'0' LIMIT 1"
    })
    InTransitProductMatchCandidate selectProductMatchCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo,
            @Param("sourceBarcode") String sourceBarcode
    );

    @Select({
            "SELECT COUNT(*) FROM in_transit_product_match_candidate",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND match_status = 'UNMATCHED' AND is_deleted = b'0'"
    })
    int countProductMatchCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "SELECT DISTINCT batch_id FROM in_transit_product_match_candidate",
            "WHERE owner_user_id = #{ownerUserId} AND UPPER(store_code) = UPPER(#{storeCode})",
            "AND UPPER(site_code) = UPPER(#{siteCode})",
            "AND match_status = 'UNMATCHED' AND is_deleted = b'0'",
            "ORDER BY batch_id"
    })
    List<Long> listProductLandingBatchIds(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO in_transit_product_match_candidate (",
            "id, owner_user_id, batch_id, package_id, box_no, source_barcode, source_psku, source_msku, product_name,",
            "store_code, site_code, shipped_quantity, received_quantity, carton_count, units_per_carton,",
            "carton_weight_kg, carton_volume_cbm,",
            "match_status, match_message, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "#{row.id}, #{row.ownerUserId}, #{row.batchId}, #{row.packageId}, #{row.boxNo},",
            "#{row.sourceBarcode}, #{row.sourcePsku}, #{row.sourceMsku}, #{row.productName},",
            "#{row.storeCode}, #{row.siteCode},",
            "#{row.shippedQuantity}, #{row.receivedQuantity}, #{row.cartonCount}, #{row.unitsPerCarton},",
            "#{row.cartonWeightKg}, #{row.cartonVolumeCbm},",
            "'UNMATCHED', #{row.matchMessage}, b'0', #{row.createdBy}, #{row.updatedBy}, NOW(), NOW())"
    })
    int insertProductMatchCandidate(@Param("row") InTransitProductMatchCandidate row);

    @Update({
            "UPDATE in_transit_product_match_candidate",
            "SET package_id = #{row.packageId}, source_psku = #{row.sourcePsku}, source_msku = #{row.sourceMsku},",
            "product_name = #{row.productName},",
            "store_code = #{row.storeCode}, site_code = #{row.siteCode},",
            "shipped_quantity = #{row.shippedQuantity}, received_quantity = #{row.receivedQuantity},",
            "carton_count = #{row.cartonCount}, units_per_carton = #{row.unitsPerCarton},",
            "carton_weight_kg = #{row.cartonWeightKg}, carton_volume_cbm = #{row.cartonVolumeCbm},",
            "match_status = #{row.matchStatus}, match_message = #{row.matchMessage},",
            "updated_by = #{row.updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{row.id} AND owner_user_id = #{row.ownerUserId} AND is_deleted = b'0'"
    })
    int updateProductMatchCandidate(@Param("row") InTransitProductMatchCandidate row);

    @Update({
            "UPDATE in_transit_product_match_candidate",
            "SET match_status = 'MATCHED', match_message = NULL,",
            "updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId} AND batch_id = #{batchId}",
            "AND box_no = #{boxNo} AND source_barcode = #{sourceBarcode} AND is_deleted = b'0'"
    })
    int resolveProductMatchCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("boxNo") String boxNo,
            @Param("sourceBarcode") String sourceBarcode,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE in_transit_product_match_candidate",
            "SET match_status = 'UNMATCHED', match_message = #{matchMessage},",
            "updated_by = #{operatorUserId}, gmt_updated = NOW()",
            "WHERE id = #{id} AND owner_user_id = #{ownerUserId} AND is_deleted = b'0'"
    })
    int markProductMatchCandidateUnmatched(
            @Param("ownerUserId") Long ownerUserId,
            @Param("id") Long id,
            @Param("matchMessage") String matchMessage,
            @Param("operatorUserId") Long operatorUserId
    );
}
