package com.nuono.next.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductForwarderEligibilityLockMapper {

    @Select({
            "<script>",
            "SELECT pv.id",
            "FROM product_variant pv",
            "JOIN logical_store store ON store.id = pv.logical_store_id",
            "WHERE store.owner_user_id = #{ownerUserId}",
            "  AND store.is_deleted = b'0'",
            "  AND pv.is_deleted = b'0'",
            "  AND pv.id IN",
            "  <foreach collection='productVariantIds' item='variantId' open='(' separator=',' close=')'>#{variantId}</foreach>",
            "ORDER BY pv.id ASC",
            "FOR UPDATE",
            "</script>"
    })
    List<Long> lockProductVariantsForForwarderEligibility(
            @Param("ownerUserId") Long ownerUserId,
            @Param("productVariantIds") List<Long> productVariantIds
    );
}
