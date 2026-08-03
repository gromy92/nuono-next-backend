package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductProjectionIdentityRecoveryMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProductProjectionIdentityRecoveryMapperTest {

    @Test
    void resolverCanOnlyClaimAProductWithoutPartnerIdentity() throws Exception {
        Method method = ProductProjectionIdentityRecoveryMapper.class.getMethod(
                "selectUnclaimedProductMasterIdBySkuParent",
                Long.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("logical_store_id = #{logicalStoreId}")
                .contains("sku_parent = #{skuParent}")
                .contains("NULLIF(TRIM(partner_sku), '') IS NULL")
                .contains("is_deleted = 0");
    }
}
