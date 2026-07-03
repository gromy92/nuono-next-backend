package com.nuono.next.productlogisticscost;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualRateCardCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.BatchCategoryAssignmentCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.BatchCategoryAssignmentResult;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-logistics-costs")
public class ProductLogisticsCostController {

    private final ProductLogisticsCostLedgerService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductLogisticsCostController(
            ProductLogisticsCostLedgerService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/current")
    public CurrentCostView currentCosts(
            @RequestParam(required = false) Long logicalStoreId,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) Long productVariantId,
            @RequestParam(required = false) String partnerSku,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String transportMode,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.currentCosts(
                context.getBusinessOwnerUserId(),
                logicalStoreId,
                storeCode,
                productVariantId,
                partnerSku,
                siteCode,
                forwarderCode,
                transportMode,
                limit
        );
    }

    @GetMapping("/history")
    public CostHistoryView history(
            @RequestParam(required = false) Long logicalStoreId,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) Long productVariantId,
            @RequestParam(required = false) String partnerSku,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String transportMode,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.history(
                context.getBusinessOwnerUserId(),
                logicalStoreId,
                storeCode,
                productVariantId,
                partnerSku,
                siteCode,
                forwarderCode,
                transportMode,
                limit
        );
    }

    @PostMapping("/current/manual")
    public CurrentCostRow manualCurrentQuote(
            @RequestBody ManualCurrentQuoteCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.manualCurrentQuote(
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                command
        );
    }

    @PostMapping("/current/categories/batch")
    public BatchCategoryAssignmentResult batchAssignCategories(
            @RequestBody BatchCategoryAssignmentCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.batchAssignCategories(
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                command
        );
    }

    @GetMapping("/rate-cards")
    public RateCardView rateCards(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String forwarderCode,
            @RequestParam(required = false) String transportMode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.rateCards(
                context.getBusinessOwnerUserId(),
                siteCode,
                forwarderCode,
                transportMode
        );
    }

    @PostMapping("/rate-cards/manual")
    public RateCardRow manualRateCard(
            @RequestBody ManualRateCardCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.manualRateCard(
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                command
        );
    }

    @GetMapping("/exceptions")
    public CostExceptionView openExceptions(
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        return service.openExceptions(context.getBusinessOwnerUserId(), limit);
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }
}
