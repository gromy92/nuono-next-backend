package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityRow;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** DP-10 canonical child identity, compatibility lookup, and fenced set finalization. */
public interface Ali1688Dp10FactLookupMapper {
    String DP10_CHILD_FINALIZE_GUARD =
            "SELECT 1 FROM dp_pull_task task "
            + "JOIN dp_pull_dp10_stage_item stage ON stage.task_id = task.id "
            + "WHERE task.id = #{task.id} "
            + "AND task.operation_code = 'DP10' "
            + "AND task.owner_user_id = #{task.ownerUserId} "
            + "AND BINARY task.account_key = BINARY #{task.accountKey} "
            + "AND BINARY task.scope_key = BINARY #{task.scopeKey} "
            + "AND task.state = 'RUNNING' "
            + "AND BINARY task.lease_owner = BINARY #{task.leaseOwner} "
            + "AND task.lease_until > UTC_TIMESTAMP(6) "
            + "AND task.fence_epoch = #{task.fenceEpoch} "
            + "AND stage.generation_no = #{slice.generationNo} "
            + "AND stage.scan_pass = 2 "
            + "AND BINARY stage.partition_name = BINARY #{slice.partition} "
            + "AND stage.page_no = #{slice.pageNo} "
            + "AND stage.item_ordinal = #{slice.itemOrdinal} "
            + "AND BINARY stage.provider_order_no = BINARY #{slice.order.providerOrderNo} "
            + "AND stage.verification_state = 'VERIFIED' "
            + "AND stage.apply_state = 'READY' "
            + "AND stage.apply_item_cursor = #{slice.itemCursor}";

    @Select({
            "SELECT oh.id, oh.authorization_id AS authorizationId,",
            " oh.order_natural_key AS orderNaturalKey,",
            " auth.provider_code AS providerCode,",
            " auth.provider_account_id AS providerAccountId,",
            " oh.provider_order_no AS providerOrderNo,",
            " oh.is_deleted AS deleted",
            "FROM procurement_ali1688_order_header oh",
            "LEFT JOIN procurement_ali1688_order_authorization auth",
            " ON auth.id = oh.authorization_id",
            " AND auth.owner_user_id = oh.owner_user_id",
            "WHERE oh.owner_user_id = #{ownerUserId}",
            " AND (BINARY oh.order_natural_key = BINARY #{orderNaturalKey}",
            "   OR (BINARY auth.provider_code = BINARY #{providerCode}",
            "     AND BINARY auth.provider_account_id = BINARY #{providerAccountId}",
            "     AND BINARY oh.provider_order_no = BINARY #{providerOrderNo}))",
            "ORDER BY CASE WHEN BINARY oh.order_natural_key = BINARY #{orderNaturalKey}",
            " THEN 0 ELSE 1 END, oh.id ASC",
            "LIMIT 2 FOR UPDATE"
    })
    List<Ali1688Dp10OrderHeaderIdentityRow> selectCanonicalOrderHeadersForUpdate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("providerCode") String providerCode,
            @Param("providerAccountId") String providerAccountId,
            @Param("providerOrderNo") String providerOrderNo,
            @Param("orderNaturalKey") String orderNaturalKey
    );

    @Select({
            "SELECT id FROM procurement_ali1688_order_item",
            "WHERE order_id = #{orderId}",
            " AND item_natural_key = #{naturalKey}",
            "ORDER BY id ASC LIMIT 1"
    })
    Long selectAnyCanonicalItemIdByNaturalKey(
            @Param("orderId") Long orderId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT id FROM procurement_ali1688_order_item",
            "WHERE order_id = #{orderId} AND is_deleted = b'0'",
            " AND BINARY COALESCE(NULLIF(TRIM(offer_id), ''), '') = BINARY #{offerId}",
            " AND BINARY COALESCE(NULLIF(TRIM(sku_id), ''), '') = BINARY #{skuId}",
            " AND BINARY COALESCE(NULLIF(TRIM(product_code), ''), '') = BINARY #{productCode}",
            " AND BINARY COALESCE(NULLIF(TRIM(single_product_code), ''), '') = BINARY #{singleProductCode}",
            "ORDER BY id ASC LIMIT #{occurrenceOffset}, 1"
    })
    Long selectCanonicalItemIdByStableTuple(
            @Param("orderId") Long orderId,
            @Param("offerId") String offerId,
            @Param("skuId") String skuId,
            @Param("productCode") String productCode,
            @Param("singleProductCode") String singleProductCode,
            @Param("occurrenceOffset") int occurrenceOffset
    );

    @Update({
            "UPDATE procurement_ali1688_order_item",
            "SET item_natural_key = #{naturalKey}, is_deleted = b'0',",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE id = #{itemId} AND order_id = #{orderId}"
    })
    int activateCanonicalItemIdentity(
            @Param("itemId") Long itemId,
            @Param("orderId") Long orderId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT id FROM procurement_ali1688_order_logistics",
            "WHERE order_id = #{orderId}",
            " AND logistics_natural_key = #{naturalKey}",
            "ORDER BY id ASC LIMIT 1"
    })
    Long selectAnyCanonicalLogisticsIdByNaturalKey(
            @Param("orderId") Long orderId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT id FROM procurement_ali1688_order_logistics",
            "WHERE order_id = #{orderId} AND item_id = #{itemId} AND is_deleted = b'0'",
            "ORDER BY id ASC LIMIT 1"
    })
    Long selectCanonicalLogisticsId(
            @Param("orderId") Long orderId,
            @Param("itemId") Long itemId
    );

    @Update({
            "UPDATE procurement_ali1688_order_logistics",
            "SET logistics_natural_key = #{naturalKey}, is_deleted = b'0',",
            " gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE id = #{logisticsId} AND order_id = #{orderId} AND item_id = #{itemId}"
    })
    int activateCanonicalLogisticsIdentity(
            @Param("logisticsId") Long logisticsId,
            @Param("orderId") Long orderId,
            @Param("itemId") Long itemId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT COUNT(*) FROM (",
            DP10_CHILD_FINALIZE_GUARD,
            ") dp10_child_finalize_guard"
    })
    int countDp10ChildFinalizeFence(
            @Param("task") DataPullTask task,
            @Param("slice") Ali1688Dp10ApplySlice slice
    );

    @Update({
            "<script>",
            "UPDATE procurement_ali1688_order_logistics logistics",
            "SET logistics.is_deleted = b'1', logistics.gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE logistics.order_id = #{orderId} AND logistics.is_deleted = b'0'",
            "<if test='authoritativeNaturalKeys != null and authoritativeNaturalKeys.size() > 0'>",
            " AND logistics.logistics_natural_key NOT IN",
            " <foreach collection='authoritativeNaturalKeys' item='naturalKey' open='(' separator=',' close=')'>",
            "   #{naturalKey}",
            " </foreach>",
            "</if>",
            " AND EXISTS (" + DP10_CHILD_FINALIZE_GUARD + ")",
            "</script>"
    })
    int softRetireDp10LogisticsMissingFromAuthoritativeSet(
            @Param("task") DataPullTask task,
            @Param("slice") Ali1688Dp10ApplySlice slice,
            @Param("orderId") Long orderId,
            @Param("authoritativeNaturalKeys") List<String> authoritativeNaturalKeys
    );

    @Update({
            "<script>",
            "UPDATE procurement_ali1688_order_item item",
            "SET item.is_deleted = b'1', item.gmt_updated = UTC_TIMESTAMP(3)",
            "WHERE item.order_id = #{orderId} AND item.is_deleted = b'0'",
            "<if test='authoritativeNaturalKeys != null and authoritativeNaturalKeys.size() > 0'>",
            " AND item.item_natural_key NOT IN",
            " <foreach collection='authoritativeNaturalKeys' item='naturalKey' open='(' separator=',' close=')'>",
            "   #{naturalKey}",
            " </foreach>",
            "</if>",
            " AND EXISTS (" + DP10_CHILD_FINALIZE_GUARD + ")",
            "</script>"
    })
    int softRetireDp10ItemsMissingFromAuthoritativeSet(
            @Param("task") DataPullTask task,
            @Param("slice") Ali1688Dp10ApplySlice slice,
            @Param("orderId") Long orderId,
            @Param("authoritativeNaturalKeys") List<String> authoritativeNaturalKeys
    );
}
