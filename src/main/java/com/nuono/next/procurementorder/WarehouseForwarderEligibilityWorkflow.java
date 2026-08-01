package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.requiredCode;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class WarehouseForwarderEligibilityWorkflow {

    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseForwarderEligibilityService eligibilityService;
    private final WarehouseLogisticsQuoteOptionService optionService;

    WarehouseForwarderEligibilityWorkflow(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseForwarderEligibilityService eligibilityService,
            WarehouseLogisticsQuoteOptionService optionService
    ) {
        this.mapper = mapper;
        this.eligibilityService = eligibilityService;
        this.optionService = optionService;
    }

    List<LogisticsQuoteExportOption> collectOptions(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        return optionService.collect(lines);
    }

    void updateRule(
            ShippingOrderRecord visibleOrder,
            Long shippingOrderLineId,
            UpdateShippingOrderLineEligibilityCommand command,
            Long operatorUserId
    ) {
        ShippingOrderRecord order = lockNotSubmittedOrder(
                visibleOrder,
                "只有未提交发货的仓库单才能修改承运状态。"
        );
        ShippingOrderLineRecord shippingLine = mapper.selectShippingOrderLineById(
                order.id, shippingOrderLineId, order.ownerUserId
        );
        if (shippingLine == null) {
            throw new IllegalArgumentException("承运状态商品不存在或已删除。");
        }
        requireNotSubmittedLine(shippingLine, "只有未提交仓库的商品才能修改承运状态。");
        if (shippingLine.productVariantId == null) {
            throw new IllegalArgumentException("商品缺少系统商品身份，不能维护承运状态。");
        }
        lockEligibilityScopes(order.ownerUserId, List.of(shippingLine.productVariantId));
        PurchaseOrderLogisticsQuoteLineRecord line = safe(
                mapper.listLogisticsQuoteCandidatesByShippingOrder(order.id)
        ).stream()
                .filter(candidate -> Objects.equals(candidate.shippingOrderLineId, shippingOrderLineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("承运状态商品报价行不存在或已删除。"));
        String forwarderCode = requiredCode(
                command == null ? null : command.forwarderCode,
                "请选择货代。"
        );
        boolean available = optionService.collect(List.of(line)).stream()
                .anyMatch(option -> sameCode(option.candidate.forwarderCode, forwarderCode));
        if (!available) {
            throw new IllegalArgumentException("当前站点和运输方式不支持该货代。");
        }
        eligibilityService.updateRule(line, forwarderCode, command, operatorUserId);
    }

    void lockEligibilityScopes(Long ownerUserId, Collection<Long> productVariantIds) {
        List<Long> requested = (productVariantIds == null ? List.<Long>of() : productVariantIds).stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (requested.isEmpty()) {
            return;
        }
        List<Long> locked = safe(mapper.lockProductVariantsForForwarderEligibility(ownerUserId, requested));
        if (!requested.equals(locked)) {
            throw new IllegalArgumentException("商品承运范围已变化，请刷新后重试。");
        }
    }

    void lockShippingLineEligibilityScopes(Long ownerUserId, List<ShippingOrderLineRecord> lines) {
        lockEligibilityScopes(ownerUserId, safe(lines).stream()
                .map(line -> line.productVariantId)
                .collect(Collectors.toList()));
    }

    void lockQuoteLineEligibilityScopes(Long ownerUserId, List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        lockEligibilityScopes(ownerUserId, safe(lines).stream()
                .map(line -> line.productVariantId)
                .collect(Collectors.toList()));
    }

    void requirePurchaseOrderSubmittable(
            Long ownerUserId,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        lockQuoteLineEligibilityScopes(ownerUserId, lines);
        List<PurchaseOrderLogisticsQuoteLineRecord> confirmed = safe(lines).stream()
                .filter(line -> "CONFIRMED".equals(normalized(line.quoteStatus)))
                .collect(Collectors.toList());
        eligibilityService.applyCurrentChannels(confirmed);
        eligibilityService.requireSubmittable(confirmed);
    }

    ShippingOrderRecord lockNotSubmittedOrder(ShippingOrderRecord visibleOrder, String message) {
        ShippingOrderRecord order = visibleOrder == null ? null : mapper.selectShippingOrderByIdForUpdate(
                visibleOrder.id, visibleOrder.ownerUserId
        );
        if (order == null) {
            throw new IllegalArgumentException("发货单不存在或已删除。");
        }
        requirePending(order.shippingSubmitStatus, message);
        return order;
    }

    void requireNotSubmittedLine(ShippingOrderLineRecord line, String message) {
        if (line == null || !NOT_SUBMITTED.equals(normalized(line.shippingSubmitStatus))) {
            throw new IllegalArgumentException(message);
        }
    }

    void requireNotSubmittedLines(List<ShippingOrderLineRecord> lines, String message) {
        if (safe(lines).stream().anyMatch(line -> !NOT_SUBMITTED.equals(normalized(line.shippingSubmitStatus)))) {
            throw new IllegalArgumentException(message);
        }
    }

    List<ShippingOrderLineRecord> applyToShippingLines(
            List<ShippingOrderLineRecord> lines,
            List<ShippingOrderSegmentRecord> segments
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> eligibilityLines = safe(lines).stream()
                .map(this::toEligibilityLine)
                .collect(Collectors.toList());
        eligibilityService.applySelected(eligibilityLines, segments);
        Map<Long, PurchaseOrderLogisticsQuoteLineRecord> eligibilityByLineId = eligibilityLines.stream()
                .collect(Collectors.toMap(
                        line -> line.shippingOrderLineId,
                        line -> line,
                        (left, ignored) -> left
                ));
        safe(lines).forEach(line -> applyEligibilityResult(line, eligibilityByLineId.get(line.id)));
        return safe(lines);
    }

    private void applyEligibilityResult(
            ShippingOrderLineRecord target,
            PurchaseOrderLogisticsQuoteLineRecord result
    ) {
        target.eligibilityStatus = result.eligibilityStatus;
        target.unitPrice = result.unitPrice;
        target.currency = result.currency;
        target.billingUnit = result.billingUnit;
        target.quoteStatus = result.quoteStatus;
    }

    private PurchaseOrderLogisticsQuoteLineRecord toEligibilityLine(ShippingOrderLineRecord source) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.ownerUserId = source.ownerUserId;
        line.shippingOrderLineId = source.id;
        line.shippingOrderSegmentId = source.shippingOrderSegmentId;
        line.productVariantId = source.productVariantId;
        line.partnerSku = source.partnerSku;
        line.pskuCode = source.pskuCode;
        line.siteCode = source.siteCode;
        line.plannedTransportMode = source.plannedTransportMode;
        line.unitPrice = source.unitPrice;
        line.currency = source.currency;
        line.billingUnit = source.billingUnit;
        line.quoteStatus = source.quoteStatus;
        line.shippingSubmitStatus = source.shippingSubmitStatus;
        line.eligibilityStatus = source.eligibilityStatus;
        return line;
    }

    private static void requirePending(String status, String message) {
        if (!NOT_SUBMITTED.equals(normalized(status))) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean sameCode(String left, String right) {
        return normalized(left).equals(normalized(right));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
