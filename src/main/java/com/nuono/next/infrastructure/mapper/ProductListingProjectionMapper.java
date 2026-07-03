package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingStoreProjectionContext;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductListingProjectionMapper {

    @Select({
            "SELECT project_code, project_name, store_code, site",
            "FROM user_store",
            "WHERE user_id = #{ownerUserId}",
            "  AND BINARY store_code = BINARY #{storeCode}",
            "  AND is_deleted = 0",
            "LIMIT 1"
    })
    ProductListingStoreProjectionContext selectStoreContext(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode
    );

    @Select({
            "SELECT project_code, project_name, store_code, site",
            "FROM user_store",
            "WHERE user_id = #{ownerUserId}",
            "  AND BINARY project_code = BINARY #{projectCode}",
            "  AND is_deleted = 0",
            "ORDER BY store_code ASC"
    })
    List<ProductListingStoreProjectionContext> selectProjectStoreContexts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectCode") String projectCode
    );
}
