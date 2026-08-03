package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductProjectionIdentityRecoveryMapper {

    @Select({
            "SELECT id",
            "FROM product_master",
            "WHERE logical_store_id = #{logicalStoreId}",
            "  AND sku_parent = #{skuParent}",
            "  AND NULLIF(TRIM(partner_sku), '') IS NULL",
            "  AND is_deleted = 0",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Long selectUnclaimedProductMasterIdBySkuParent(
            @Param("logicalStoreId") Long logicalStoreId,
            @Param("skuParent") String skuParent
    );
}
