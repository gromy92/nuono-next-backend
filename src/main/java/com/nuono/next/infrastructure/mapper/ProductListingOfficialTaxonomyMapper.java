package com.nuono.next.infrastructure.mapper;

import com.nuono.next.productlisting.ProductListingOfficialTaxonomyRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductListingOfficialTaxonomyMapper {

    @Select({
            "SELECT",
            "  id_product_fulltype, product_fulltype_code,",
            "  family_name_en, product_type_name_en, product_subtype_name_en",
            "FROM cross_border_erp.goods_category",
            "WHERE COALESCE(is_deleted, 0) = 0",
            "  AND BINARY product_fulltype_code = BINARY #{productFulltype}",
            "  AND id_product_fulltype IS NOT NULL",
            "  AND NULLIF(TRIM(family_name_en), '') IS NOT NULL",
            "  AND NULLIF(TRIM(product_type_name_en), '') IS NOT NULL",
            "  AND NULLIF(TRIM(product_subtype_name_en), '') IS NOT NULL",
            "LIMIT 1"
    })
    ProductListingOfficialTaxonomyRecord selectOfficialNoonProductFulltype(
            @Param("productFulltype") String productFulltype
    );
}
