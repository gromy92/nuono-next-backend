package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitProductMatchViews.CandidateListView;
import com.nuono.next.intransit.InTransitProductMatchViews.RematchView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/in-transit-goods/batches/{batchId}/product-match-candidates")
public class InTransitProductMatchController {
    private final InTransitBatchService batchService;
    private final InTransitGoodsAccessScopeService accessScopeService;
    private final InTransitProductMatchService productMatchService;
    private final BusinessAccessResolver businessAccessResolver;

    public InTransitProductMatchController(
            InTransitBatchService batchService,
            InTransitGoodsAccessScopeService accessScopeService,
            InTransitProductMatchService productMatchService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.batchService = batchService;
        this.accessScopeService = accessScopeService;
        this.productMatchService = productMatchService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping
    public CandidateListView list(@PathVariable Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = requireBatchAccess(batchId, request);
        return productMatchService.list(context.getBusinessOwnerUserId(), batchId);
    }

    @PostMapping("/rematch")
    public RematchView rematch(@PathVariable Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = requireBatchAccess(batchId, request);
        return productMatchService.rematch(
                context,
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                batchId
        );
    }

    private BusinessAccessContext requireBatchAccess(Long batchId, HttpServletRequest request) {
        BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.IN_TRANSIT_GOODS
        );
        accessScopeService.requireBatchAccess(
                context,
                batchService.getBatch(context.getBusinessOwnerUserId(), batchId)
        );
        return context;
    }
}
