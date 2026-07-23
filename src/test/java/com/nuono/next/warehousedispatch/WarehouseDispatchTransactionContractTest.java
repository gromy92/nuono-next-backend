package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

class WarehouseDispatchTransactionContractTest {

    private static final Set<String> TRANSACTIONAL_OPERATIONS = Set.of(
            "updateItemFulfillment",
            "createConfirmation",
            "listReceiptOrders",
            "listReadyItems",
            "listPurchaseOrderLogisticsComparisons",
            "createDispatchPlan",
            "previewMobileShippingDecision",
            "confirmMobileShippingDecision",
            "createShippingBatch",
            "listShippingBatches",
            "getShippingBatch",
            "createShippingTargetOption",
            "selectShippingOption",
            "createOutboundOrders",
            "listOutboundOrders",
            "createPackingList",
            "listPackingLists",
            "replacePackingBoxes",
            "savePackingBox",
            "confirmPackingList",
            "confirmPackingLists",
            "shipPackingList",
            "listDispatchPlans",
            "readyForLogistics",
            "getLogisticsHandoff",
            "reopenDraft",
            "markLogisticsHandoffSuccess",
            "markLogisticsHandoffFailure"
    );

    @Test
    void inheritedWarehouseOperationsRetainSpringTransactionMetadata() {
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();
        Set<String> discovered = Arrays.stream(LocalDbWarehouseDispatchService.class.getMethods())
                .filter(method -> TRANSACTIONAL_OPERATIONS.contains(method.getName()))
                .peek(method -> assertTransactional(attributes, method))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(discovered).isEqualTo(TRANSACTIONAL_OPERATIONS);
    }

    private void assertTransactional(AnnotationTransactionAttributeSource attributes, Method method) {
        assertThat(attributes.getTransactionAttribute(method, LocalDbWarehouseDispatchService.class))
                .as(method.getName())
                .isNotNull();
    }
}
