package com.nuono.next.procurementorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;

final class ProcurementPurchaseOrderServiceTestFactory {

    private ProcurementPurchaseOrderServiceTestFactory() {
    }

    static LocalDbProcurementPurchaseOrderService create(
            ProcurementPurchaseOrderMapper mapper,
            ProductSelectionMapper productSelectionMapper,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectMapper objectMapper,
            WarehouseLogisticsQuotePriceService quotePriceService
    ) {
        WarehouseShippingQuoteProjectionService projectionService =
                new WarehouseShippingQuoteProjectionService(mapper, quotePriceService, objectMapper);
        WarehouseShippingQuoteChannelService channelService =
                new WarehouseShippingQuoteChannelService(mapper, quotePriceService, projectionService);
        WarehouseForwarderEligibilityService eligibilityService =
                new WarehouseForwarderEligibilityService(mapper);
        WarehouseLogisticsQuoteOptionService optionService =
                new WarehouseLogisticsQuoteOptionService(mapper, channelService, eligibilityService);
        WarehouseForwarderEligibilityWorkflow eligibilityWorkflow =
                new WarehouseForwarderEligibilityWorkflow(mapper, eligibilityService, optionService);
        return new LocalDbProcurementPurchaseOrderService(
                mapper,
                productSelectionMapper,
                ali1688CollectionService,
                objectMapper,
                channelService,
                eligibilityService,
                eligibilityWorkflow,
                new WarehouseShippingLineReassignmentService(mapper, objectMapper)
        );
    }
}
