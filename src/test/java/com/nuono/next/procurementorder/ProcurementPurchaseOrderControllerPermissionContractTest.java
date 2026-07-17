package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcurementPurchaseOrderControllerPermissionContractTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/nuono/next/procurementorder/ProcurementPurchaseOrderController.java"
    );

    @Test
    void warehouseOrderCompatibilityApisUseWarehouseDispatchCapability() throws IOException {
        String source = Files.readString(CONTROLLER);

        assertThat(source).contains(
                "listOrders(\n                    requireListOrdersAccess(request, storeCode, submittedOnly),",
                "accessResolver.requireAnyBusinessContext(\n                    request,\n                    BusinessCapability.PROCUREMENT,\n                    BusinessCapability.WAREHOUSE_DISPATCH",
                "listShippingOrders(requireWarehouseDispatchAccess(request), keyword)",
                "listLogisticsBills(requireWarehouseDispatchAccess(request), keyword)",
                "getLogisticsBill(requireWarehouseDispatchAccess(request), expectedBillId)",
                "createShippingOrder(requireWarehouseDispatchAccess(request), command)",
                "generateShippingOrderExpectedBill(requireWarehouseDispatchAccess(request), shippingOrderId, command)",
                "getShippingOrder(requireWarehouseDispatchAccess(request), shippingOrderId)",
                "updateShippingOrder(requireWarehouseDispatchAccess(request), shippingOrderId, command)",
                "updateShippingOrderLineYiteMaterial(requireWarehouseDispatchAccess(request), shippingOrderId, lineId, command)",
                "updateShippingOrderLineQuote(requireWarehouseDispatchAccess(request), shippingOrderId, lineId, command)",
                "updateShippingOrderLineQuotes(requireWarehouseDispatchAccess(request), shippingOrderId, command)",
                "listShippingOrderLogisticsQuoteOptions(requireWarehouseDispatchAccess(request), shippingOrderId, command)",
                "requireWarehouseDispatchAccess(request),\n                            shippingOrderId,",
                "submitShippingOrder(requireWarehouseDispatchAccess(request), shippingOrderId)",
                "accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH)"
        );
        assertThat(source).contains(
                "getOrder(requireAccess(request, null), orderId)",
                "createOrder(requireAccess(request, storeCode), command)"
        );
    }
}
