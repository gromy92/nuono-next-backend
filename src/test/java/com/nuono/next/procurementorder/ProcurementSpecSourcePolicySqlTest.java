package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ProcurementSpecSourcePolicySqlTest {

    @Test
    void purchaseOrderReadsAli1688SpecsWithoutCrossSourceFallback() {
        assertThat(ProcurementPurchaseOrderMapper.PRODUCT_LENGTH_EXPR)
                .isEqualTo("ali1688Spec.product_length_cm");
        assertThat(ProcurementPurchaseOrderMapper.PRODUCT_WIDTH_EXPR)
                .isEqualTo("ali1688Spec.product_width_cm");
        assertThat(ProcurementPurchaseOrderMapper.PRODUCT_HEIGHT_EXPR)
                .isEqualTo("ali1688Spec.product_height_cm");
        assertThat(ProcurementPurchaseOrderMapper.PRODUCT_WEIGHT_EXPR)
                .isEqualTo("ali1688Spec.product_weight_g");
        assertThat(ProcurementPurchaseOrderMapper.SPEC_SOURCE_TYPE_EXPR)
                .isEqualTo("ali1688Spec.source_type");

        String sql = ProcurementPurchaseOrderMapper.ITEM_SELECT.replaceAll("\\s+", " ");
        assertAli1688Only(sql);
    }

    @Test
    void preApplicationWarehouseOrderQuoteQueriesReadAli1688Only() throws Exception {
        for (Method method : List.of(
                ProcurementPurchaseOrderMapper.class.getMethod(
                        "listLogisticsQuoteCandidatesByOrder",
                        Long.class
                ),
                ProcurementPurchaseOrderMapper.class.getMethod(
                        "listLogisticsQuoteCandidatesByShippingOrder",
                        Long.class
                ),
                ProcurementPurchaseOrderMapper.class.getMethod(
                        "listLogisticsQuoteCandidatesByShippingOrderSegments",
                        Long.class,
                        List.class
                )
        )) {
            String sql = String.join(" ", method.getAnnotation(Select.class).value())
                    .replaceAll("\\s+", " ");
            assertAli1688Only(sql);
        }
    }

    private void assertAli1688Only(String sql) {
        assertThat(sql)
                .contains("ali1688Spec.source_type = 'ali1688'")
                .contains("ali1688Spec.product_length_cm")
                .contains("ali1688Spec.product_weight_g")
                .doesNotContain("pvs.effective_source_id")
                .doesNotContain("warehouseSpec.source_type = 'warehouse'")
                .doesNotContain("officialSpec.source_type = 'noon_official'");
    }
}
