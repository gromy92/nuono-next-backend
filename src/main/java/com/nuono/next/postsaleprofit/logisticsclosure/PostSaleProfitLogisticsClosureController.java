package com.nuono.next.postsaleprofit.logisticsclosure;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationRequest;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationResultView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateListView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureConfirmCommand;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchListView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureRejectCommand;
import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/post-sale-profit/logistics-closure")
public class PostSaleProfitLogisticsClosureController {
    private final PostSaleProfitLogisticsClosureService service;
    private final BusinessAccessResolver businessAccessResolver;

    public PostSaleProfitLogisticsClosureController(
            PostSaleProfitLogisticsClosureService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/purchase-batches")
    public LogisticsClosurePurchaseBatchListView listPurchaseBatches(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer pageSize,
            HttpServletRequest request
    ) {
        ValidatedStoreScope scope = validateStoreScope(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return service.listPurchaseBatches(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                dateFrom,
                dateTo,
                trim(keyword),
                trim(status),
                page == null ? 1 : page,
                pageSize == null ? 50 : pageSize
        );
    }

    @GetMapping("/candidates")
    public LogisticsClosureCandidateListView listCandidates(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String sourceType,
            @RequestParam String sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            HttpServletRequest request
    ) {
        ValidatedStoreScope scope = validateStoreScope(storeCode, siteCode);
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return service.listCandidates(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                dateFrom,
                dateTo,
                trim(sourceType),
                trim(sourceId)
        );
    }

    @PostMapping("/allocations/confirm")
    public LogisticsClosureAllocationResultView confirmAllocation(
            @RequestBody LogisticsClosureAllocationRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return service.confirmAllocation(confirmCommand(body, context, scope));
    }

    @PostMapping("/allocations/reject")
    public LogisticsClosureAllocationResultView rejectCandidate(
            @RequestBody LogisticsClosureAllocationRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        LogisticsClosureRejectCommand command = new LogisticsClosureRejectCommand();
        copyAllocationRequest(body, context, scope, command);
        return service.rejectCandidate(command);
    }

    @PostMapping("/allocations/delete")
    public LogisticsClosureAllocationResultView deleteAllocation(
            @RequestBody LogisticsClosureDeleteRequest body,
            HttpServletRequest request
    ) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        ValidatedStoreScope scope = validateStoreScope(body.getStoreCode(), body.getSiteCode());
        BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                scope.storeCode
        );
        return service.deleteAllocation(
                ownerUserId(context, scope.storeCode),
                scope.storeCode,
                scope.siteCode,
                context.getSessionUserId(),
                body.getAllocationId()
        );
    }

    private LogisticsClosureConfirmCommand confirmCommand(
            LogisticsClosureAllocationRequest body,
            BusinessAccessContext context,
            ValidatedStoreScope scope
    ) {
        LogisticsClosureConfirmCommand command = new LogisticsClosureConfirmCommand();
        copyAllocationRequest(body, context, scope, command);
        return command;
    }

    private void copyAllocationRequest(
            LogisticsClosureAllocationRequest body,
            BusinessAccessContext context,
            ValidatedStoreScope scope,
            LogisticsClosureConfirmCommand command
    ) {
        command.setOwnerUserId(ownerUserId(context, scope.storeCode));
        command.setOperatorUserId(context.getSessionUserId());
        command.setStoreCode(scope.storeCode);
        command.setSiteCode(scope.siteCode);
        command.setSourceType(trim(body.getSourceType()));
        command.setSourceId(trim(body.getSourceId()));
        command.setInTransitBatchId(body.getInTransitBatchId());
        command.setInTransitGoodsLineId(body.getInTransitGoodsLineId());
        command.setAllocatedQuantity(body.getAllocatedQuantity());
        command.setMatchMethod(trim(body.getMatchMethod()));
        command.setReason(trim(body.getReason()));
    }

    private ValidatedStoreScope validateStoreScope(String storeCode, String siteCode) {
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode are required.");
        }
        return new ValidatedStoreScope(storeCode.trim(), siteCode.trim());
    }

    private Long ownerUserId(BusinessAccessContext context, String storeCode) {
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        return ownerUserId == null ? context.getBusinessOwnerUserId() : ownerUserId;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static class LogisticsClosureDeleteRequest {
        private String storeCode;
        private String siteCode;
        private Long allocationId;

        public String getStoreCode() { return storeCode; }
        public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
        public String getSiteCode() { return siteCode; }
        public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
        public Long getAllocationId() { return allocationId; }
        public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    }

    private static final class ValidatedStoreScope {
        private final String storeCode;
        private final String siteCode;

        private ValidatedStoreScope(String storeCode, String siteCode) {
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }
}
