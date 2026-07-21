package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardVersionCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.BatchFreightCostView;
import com.nuono.next.intransit.InTransitFreightCostRecords.ForwarderFreightComparisonView;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.intransit.InTransitFreightCostRecords.SkuFreightCostHistoryView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/in-transit-goods")
public class InTransitFreightCostController {

    private final InTransitBatchService batchService;
    private final InTransitFreightCostService freightCostService;
    private final BusinessAccessResolver businessAccessResolver;
    private final InTransitGoodsAccessScopeService accessScopeService;

    public InTransitFreightCostController(
            InTransitBatchService batchService,
            InTransitFreightCostService freightCostService,
            BusinessAccessResolver businessAccessResolver,
            InTransitGoodsAccessScopeService accessScopeService
    ) {
        this.batchService = batchService;
        this.freightCostService = freightCostService;
        this.businessAccessResolver = businessAccessResolver;
        this.accessScopeService = accessScopeService;
    }

    @GetMapping("/batches/{batchId}/freight-costs")
    public BatchFreightCostView batchFreightCosts(
            @PathVariable Long batchId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        try {
            accessScopeService.requireBatchAccess(context, batchService.getBatch(context.getBusinessOwnerUserId(), batchId));
            return freightCostService.batchActualCosts(context.getBusinessOwnerUserId(), batchId);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/freight-costs/statistics")
    public FreightStatisticsView freightStatistics(
            @RequestParam(required = false) String fromMonth,
            @RequestParam(required = false) String toMonth,
            @RequestParam(required = false) Long standardForwarderId,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        try {
            return freightCostService.statistics(
                    context.getBusinessOwnerUserId(),
                    parseMonthStart(fromMonth),
                    parseMonthEndExclusive(toMonth),
                    standardForwarderId
            );
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/freight-costs/sku-history")
    public SkuFreightCostHistoryView skuFreightHistory(
            @RequestParam String psku,
            @RequestParam String targetSiteCode,
            @RequestParam(required = false) String fromMonth,
            @RequestParam(required = false) String toMonth,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        try {
            return freightCostService.skuHistory(
                    context.getBusinessOwnerUserId(),
                    psku,
                    targetSiteCode,
                    parseMonthStart(fromMonth),
                    parseMonthEndExclusive(toMonth)
            );
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/freight-costs/forwarder-comparison")
    public ForwarderFreightComparisonView forwarderFreightComparison(
            @RequestParam String psku,
            @RequestParam String targetSiteCode,
            @RequestParam(required = false) String transportMode,
            @RequestParam(required = false) String destinationCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        try {
            return freightCostService.forwarderComparison(
                    context.getBusinessOwnerUserId(),
                    psku,
                    targetSiteCode,
                    transportMode,
                    destinationCode
            );
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/freight-costs/rate-card-versions")
    public RateCardVersionView saveFreightRateCardVersion(
            @RequestBody(required = false) SaveRateCardVersionCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        SaveRateCardVersionCommand resolved = command == null ? new SaveRateCardVersionCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        try {
            return freightCostService.saveRateCardVersion(resolved);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    private LocalDateTime parseMonthStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim()).atDay(1).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("月份格式必须为 yyyy-MM。", exception);
        }
    }

    private LocalDateTime parseMonthEndExclusive(String value) {
        LocalDateTime start = parseMonthStart(value);
        return start == null ? null : start.plusMonths(1);
    }
}
