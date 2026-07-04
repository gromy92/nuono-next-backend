package com.nuono.next.preorderprofit;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/pre-order-profit")
public class PreOrderProfitController {
    private final ObjectProvider<PreOrderProfitService> serviceProvider;
    private final PreOrderProfitCalculator calculator;
    private final BusinessAccessResolver businessAccessResolver;

    public PreOrderProfitController(
            ObjectProvider<PreOrderProfitService> serviceProvider,
            PreOrderProfitCalculator calculator,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.calculator = calculator;
        this.businessAccessResolver = businessAccessResolver;
    }

    @PostMapping("/calculate")
    public PreOrderProfitCalculationView calculate(
            HttpServletRequest request,
            @RequestBody PreOrderProfitCalculationRequest command
    ) {
        requireCommand(command);
        businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return calculator.calculate(command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/candidates")
    public List<PreOrderProfitCandidateView> listCandidates(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String logisticsCarrierId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        return requireService().listCandidates(context, storeCode, siteCode, keyword, status, categoryId, logisticsCarrierId);
    }

    @GetMapping("/candidates/{candidateId}")
    public PreOrderProfitCandidateView candidate(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @PathVariable Long candidateId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        try {
            return requireService().getCandidate(context, candidateId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/candidates")
    public PreOrderProfitCandidateView createCandidate(
            HttpServletRequest request,
            @RequestBody PreOrderProfitCandidateCommand command
    ) {
        requireCommand(command);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return requireService().createCandidate(context, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/candidates/{candidateId}")
    public PreOrderProfitCandidateView updateCandidate(
            HttpServletRequest request,
            @PathVariable Long candidateId,
            @RequestBody PreOrderProfitCandidateCommand command
    ) {
        requireCommand(command);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return requireService().updateCandidate(context, candidateId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/candidates/{candidateId}")
    public void deleteCandidate(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @PathVariable Long candidateId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        try {
            requireService().deleteCandidate(context, candidateId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/candidates/{candidateId}/competitors")
    public PreOrderProfitCompetitorView addCompetitor(
            HttpServletRequest request,
            @PathVariable Long candidateId,
            @RequestBody PreOrderProfitCompetitorCommand command
    ) {
        requireCommand(command);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return requireService().addCompetitor(context, candidateId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/candidates/{candidateId}/competitors/{competitorId}")
    public PreOrderProfitCompetitorView updateCompetitor(
            HttpServletRequest request,
            @PathVariable Long candidateId,
            @PathVariable Long competitorId,
            @RequestBody PreOrderProfitCompetitorCommand command
    ) {
        requireCommand(command);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return requireService().updateCompetitor(context, candidateId, competitorId, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/candidates/{candidateId}/competitors/{competitorId}")
    public void deleteCompetitor(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @PathVariable Long candidateId,
            @PathVariable Long competitorId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        try {
            requireService().deleteCompetitor(context, candidateId, competitorId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/purchase-orders")
    public List<PreOrderProfitPurchaseOrderView> listPurchaseOrders(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @RequestParam(required = false) String siteCode
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        return requireService().listPurchaseOrders(context, storeCode, siteCode);
    }

    @PostMapping("/purchase-orders")
    public PreOrderProfitPurchaseOrderView createPurchaseOrder(
            HttpServletRequest request,
            @RequestBody PreOrderProfitPurchaseOrderCommand command
    ) {
        requireCommand(command);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                command.getStoreCode()
        );
        try {
            return requireService().createPurchaseOrder(context, command);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/candidates/{candidateId}/purchase-orders/{purchaseOrderId}")
    public PreOrderProfitPurchaseOrderLinkView addCandidateToPurchaseOrder(
            HttpServletRequest request,
            @RequestParam String storeCode,
            @PathVariable Long candidateId,
            @PathVariable Long purchaseOrderId
    ) {
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRE_ORDER_PROFIT,
                storeCode
        );
        try {
            return requireService().addCandidateToPurchaseOrder(context, candidateId, purchaseOrderId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private void requireCommand(Object command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required.");
        }
        if (command instanceof PreOrderProfitCalculationRequest) {
            String storeCode = ((PreOrderProfitCalculationRequest) command).getStoreCode();
            if (!StringUtils.hasText(storeCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode is required.");
            }
        }
        if (command instanceof PreOrderProfitCompetitorCommand) {
            String storeCode = ((PreOrderProfitCompetitorCommand) command).getStoreCode();
            if (!StringUtils.hasText(storeCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode is required.");
            }
        }
        if (command instanceof PreOrderProfitPurchaseOrderCommand) {
            String storeCode = ((PreOrderProfitPurchaseOrderCommand) command).getStoreCode();
            if (!StringUtils.hasText(storeCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode is required.");
            }
        }
    }

    private PreOrderProfitService requireService() {
        PreOrderProfitService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "选品池利润计算服务不可用。");
        }
        return service;
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
