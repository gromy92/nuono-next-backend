package com.nuono.next.infrastructure.mapper;

import com.nuono.next.preorderprofit.PreOrderProfitCandidateRow;
import com.nuono.next.preorderprofit.PreOrderProfitCompetitorRow;
import com.nuono.next.preorderprofit.PreOrderProfitPurchaseOrderItemRow;
import com.nuono.next.preorderprofit.PreOrderProfitPurchaseOrderRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface PreOrderProfitMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {"SELECT LAST_INSERT_ID()"},
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateProductManagementId(IdSequenceCommand command);

    default Long nextId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateProductManagementId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("出单前利润 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    @Insert({
            "INSERT INTO pre_order_profit_candidate (",
            "  id, owner_user_id, store_code, site_code, title, sku_hint, purchase_url,",
            "  purchase_price_rmb, length_cm, width_cm, height_cm, actual_weight_kg,",
            "  category_id, category_label, logistics_carrier_id, logistics_carrier_label,",
            "  sale_price, target_margin_rate, candidate_status, notes,",
            "  latest_calculation_status, latest_calculation_json, created_by, updated_by",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{title}, #{skuHint}, #{purchaseUrl},",
            "  #{purchasePriceRmb}, #{lengthCm}, #{widthCm}, #{heightCm}, #{actualWeightKg},",
            "  #{categoryId}, #{categoryLabel}, #{logisticsCarrierId}, #{logisticsCarrierLabel},",
            "  #{salePrice}, #{targetMarginRate}, #{candidateStatus}, #{notes},",
            "  #{latestCalculationStatus}, #{latestCalculationJson}, #{createdBy}, #{updatedBy}",
            ")"
    })
    int insertCandidate(PreOrderProfitCandidateRow row);

    @Update({
            "UPDATE pre_order_profit_candidate",
            "SET store_code = #{storeCode},",
            "    site_code = #{siteCode},",
            "    title = #{title},",
            "    sku_hint = #{skuHint},",
            "    purchase_url = #{purchaseUrl},",
            "    purchase_price_rmb = #{purchasePriceRmb},",
            "    length_cm = #{lengthCm},",
            "    width_cm = #{widthCm},",
            "    height_cm = #{heightCm},",
            "    actual_weight_kg = #{actualWeightKg},",
            "    category_id = #{categoryId},",
            "    category_label = #{categoryLabel},",
            "    logistics_carrier_id = #{logisticsCarrierId},",
            "    logistics_carrier_label = #{logisticsCarrierLabel},",
            "    sale_price = #{salePrice},",
            "    target_margin_rate = #{targetMarginRate},",
            "    candidate_status = #{candidateStatus},",
            "    notes = #{notes},",
            "    latest_calculation_status = #{latestCalculationStatus},",
            "    latest_calculation_json = #{latestCalculationJson},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateCandidate(PreOrderProfitCandidateRow row);

    @Update({
            "UPDATE pre_order_profit_candidate",
            "SET is_deleted = b'1', updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{candidateId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId,
            @Param("actorUserId") Long actorUserId
    );

    @Select({
            "SELECT",
            "  c.*,",
            "  (SELECT COUNT(1) FROM pre_order_profit_competitor pc",
            "    WHERE pc.candidate_id = c.id AND pc.is_deleted = b'0') AS competitor_count,",
            "  (SELECT COUNT(1) FROM pre_order_profit_purchase_order_item poi",
            "    WHERE poi.candidate_id = c.id AND poi.is_deleted = b'0') AS purchase_order_count",
            "FROM pre_order_profit_candidate c",
            "WHERE c.owner_user_id = #{ownerUserId}",
            "  AND c.id = #{candidateId}",
            "  AND c.is_deleted = b'0'",
            "LIMIT 1"
    })
    PreOrderProfitCandidateRow selectCandidateById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId
    );

    @Select({
            "<script>",
            "SELECT",
            "  c.*,",
            "  (SELECT COUNT(1) FROM pre_order_profit_competitor pc",
            "    WHERE pc.candidate_id = c.id AND pc.is_deleted = b'0') AS competitor_count,",
            "  (SELECT COUNT(1) FROM pre_order_profit_purchase_order_item poi",
            "    WHERE poi.candidate_id = c.id AND poi.is_deleted = b'0') AS purchase_order_count",
            "FROM pre_order_profit_candidate c",
            "WHERE c.owner_user_id = #{ownerUserId}",
            "  AND c.store_code = #{storeCode}",
            "  AND c.is_deleted = b'0'",
            "  <if test='siteCode != null and siteCode != \"\"'>AND c.site_code = #{siteCode}</if>",
            "  <if test='calculationStatus != null and calculationStatus != \"\"'>AND c.latest_calculation_status = #{calculationStatus}</if>",
            "  <if test='categoryId != null and categoryId != \"\"'>AND c.category_id = #{categoryId}</if>",
            "  <if test='logisticsCarrierId != null and logisticsCarrierId != \"\"'>AND c.logistics_carrier_id = #{logisticsCarrierId}</if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (c.title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR c.sku_hint LIKE CONCAT('%', #{keyword}, '%')",
            "      OR c.purchase_url LIKE CONCAT('%', #{keyword}, '%'))",
            "  </if>",
            "ORDER BY c.gmt_updated DESC, c.id DESC",
            "</script>"
    })
    List<PreOrderProfitCandidateRow> selectCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("calculationStatus") String calculationStatus,
            @Param("categoryId") String categoryId,
            @Param("logisticsCarrierId") String logisticsCarrierId
    );

    @Insert({
            "INSERT INTO pre_order_profit_competitor (",
            "  id, candidate_id, owner_user_id, title, url, platform, site_code, price, currency,",
            "  seller_name, notes, created_by, updated_by",
            ") VALUES (",
            "  #{id}, #{candidateId}, #{ownerUserId}, #{title}, #{url}, #{platform}, #{siteCode}, #{price}, #{currency},",
            "  #{sellerName}, #{notes}, #{createdBy}, #{updatedBy}",
            ")"
    })
    int insertCompetitor(PreOrderProfitCompetitorRow row);

    @Update({
            "UPDATE pre_order_profit_competitor",
            "SET title = #{title},",
            "    url = #{url},",
            "    platform = #{platform},",
            "    site_code = #{siteCode},",
            "    price = #{price},",
            "    currency = #{currency},",
            "    seller_name = #{sellerName},",
            "    notes = #{notes},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND candidate_id = #{candidateId}",
            "  AND is_deleted = b'0'"
    })
    int updateCompetitor(PreOrderProfitCompetitorRow row);

    @Update({
            "UPDATE pre_order_profit_competitor",
            "SET is_deleted = b'1', updated_by = #{actorUserId}, gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND candidate_id = #{candidateId}",
            "  AND id = #{competitorId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteCompetitor(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId,
            @Param("competitorId") Long competitorId,
            @Param("actorUserId") Long actorUserId
    );

    @Select({
            "SELECT *",
            "FROM pre_order_profit_competitor",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND candidate_id = #{candidateId}",
            "  AND id = #{competitorId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PreOrderProfitCompetitorRow selectCompetitorById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId,
            @Param("competitorId") Long competitorId
    );

    @Select({
            "SELECT *",
            "FROM pre_order_profit_competitor",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND candidate_id = #{candidateId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<PreOrderProfitCompetitorRow> selectCompetitorsByCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId
    );

    @Insert({
            "INSERT INTO pre_order_profit_purchase_order (",
            "  id, owner_user_id, store_code, site_code, name, notes, created_by, updated_by",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{name}, #{notes}, #{createdBy}, #{updatedBy}",
            ")"
    })
    int insertPurchaseOrder(PreOrderProfitPurchaseOrderRow row);

    @Select({
            "SELECT",
            "  po.*,",
            "  (SELECT COUNT(1) FROM pre_order_profit_purchase_order_item poi",
            "    WHERE poi.purchase_order_id = po.id AND poi.is_deleted = b'0') AS item_count",
            "FROM pre_order_profit_purchase_order po",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND po.id = #{purchaseOrderId}",
            "  AND po.is_deleted = b'0'",
            "LIMIT 1"
    })
    PreOrderProfitPurchaseOrderRow selectPurchaseOrderById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("purchaseOrderId") Long purchaseOrderId
    );

    @Select({
            "<script>",
            "SELECT",
            "  po.*,",
            "  (SELECT COUNT(1) FROM pre_order_profit_purchase_order_item poi",
            "    WHERE poi.purchase_order_id = po.id AND poi.is_deleted = b'0') AS item_count",
            "FROM pre_order_profit_purchase_order po",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND po.store_code = #{storeCode}",
            "  AND po.is_deleted = b'0'",
            "  <if test='siteCode != null and siteCode != \"\"'>AND po.site_code = #{siteCode}</if>",
            "ORDER BY po.gmt_updated DESC, po.id DESC",
            "</script>"
    })
    List<PreOrderProfitPurchaseOrderRow> selectPurchaseOrders(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "SELECT *",
            "FROM pre_order_profit_purchase_order_item",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND purchase_order_id = #{purchaseOrderId}",
            "  AND candidate_id = #{candidateId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    PreOrderProfitPurchaseOrderItemRow selectPurchaseOrderItem(
            @Param("ownerUserId") Long ownerUserId,
            @Param("purchaseOrderId") Long purchaseOrderId,
            @Param("candidateId") Long candidateId
    );

    @Insert({
            "INSERT INTO pre_order_profit_purchase_order_item (",
            "  id, owner_user_id, purchase_order_id, candidate_id, store_code, site_code, created_by",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{purchaseOrderId}, #{candidateId}, #{storeCode}, #{siteCode}, #{createdBy}",
            ")"
    })
    int insertPurchaseOrderItem(PreOrderProfitPurchaseOrderItemRow row);

    @Select({
            "SELECT po.*",
            "FROM pre_order_profit_purchase_order_item poi",
            "JOIN pre_order_profit_purchase_order po",
            "  ON po.id = poi.purchase_order_id",
            " AND po.is_deleted = b'0'",
            "WHERE poi.owner_user_id = #{ownerUserId}",
            "  AND poi.candidate_id = #{candidateId}",
            "  AND poi.is_deleted = b'0'",
            "ORDER BY po.gmt_updated DESC, po.id DESC"
    })
    List<PreOrderProfitPurchaseOrderRow> selectPurchaseOrdersByCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("candidateId") Long candidateId
    );
}
