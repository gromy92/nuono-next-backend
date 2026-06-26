package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class PurchaseOrderModulePackagingTest {

    @Test
    void purchaseOrderBackendModuleIsPackagedWithRuntimeResources() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        assertThatCode(() -> Class.forName("com.nuono.next.procurementorder.ProcurementPurchaseOrderController"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper"))
                .doesNotThrowAnyException();
        assertThat(classLoader.getResource("db/init/119_procurement_purchase_order.sql"))
                .isNotNull();
        assertThat(classLoader.getResource("db/init/122_procurement_purchase_order_logistics_plan.sql"))
                .isNotNull();
        assertThat(classLoader.getResource("db/init/123_procurement_purchase_order_transport_mode.sql"))
                .isNotNull();
    }
}
