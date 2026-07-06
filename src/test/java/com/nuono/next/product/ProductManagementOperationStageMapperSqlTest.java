package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ProductManagementOperationStageMapperSqlTest {

    @Test
    void productListProjectionReadsOperationStageFromCurrentStoreSiteOffer() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductListProjection",
                Long.class,
                String.class
        );

        String sql = compactSql(method);

        assertThat(sql)
                .contains("MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_code END)")
                .contains("operationStageCode")
                .contains("operationStageUpdatedAt")
                .contains("operationStageUpdatedBy")
                .doesNotContain("pm.operation_stage_code");
    }

    @Test
    void productListProjectionByProductMasterIdReadsOperationStageFromCurrentStoreSiteOffer() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "selectProductListProjectionByProductMasterId",
                Long.class,
                String.class,
                Long.class
        );

        String sql = compactSql(method);

        assertThat(sql)
                .contains("MAX(CASE WHEN lss.store_code = #{storeCode} THEN pso.operation_stage_code END)")
                .contains("operationStageCode")
                .contains("operationStageUpdatedAt")
                .contains("operationStageUpdatedBy")
                .doesNotContain("pm.operation_stage_code");
    }

    @Test
    void updateOperationStageWritesCurrentStoreSiteOfferOnly() throws Exception {
        Method method = ProductManagementMapper.class.getMethod(
                "updateProductSiteOfferOperationStage",
                Long.class,
                String.class,
                String.class,
                String.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("JOIN product_variant pv")
                .contains("JOIN logical_store ls")
                .contains("JOIN logical_store_site lss")
                .contains("lss.store_code = #{storeCode}")
                .contains("SET pso.operation_stage_code = #{operationStageCode}")
                .contains("pso.operation_stage_updated_by = #{operatorUserId}")
                .contains("pso.partner_sku = #{partnerSku}")
                .contains("pv.partner_sku = #{partnerSku}")
                .doesNotContain("UPDATE product_master");
    }

    private String compactSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
    }
}
