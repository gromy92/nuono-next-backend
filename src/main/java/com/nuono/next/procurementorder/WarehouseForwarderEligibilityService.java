package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.STATUSES;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.blockMessage;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.nonNull;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalizeStatus;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.normalized;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.requiredCode;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.safe;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.statusFor;
import static com.nuono.next.procurementorder.WarehouseForwarderEligibilityPolicy.trim;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class WarehouseForwarderEligibilityService {

    private static final String PENDING_QUOTE = "PENDING_QUOTE";
    public static final String SUPPORTED = WarehouseForwarderEligibilityPolicy.SUPPORTED;
    public static final String INQUIRY_REQUIRED = WarehouseForwarderEligibilityPolicy.INQUIRY_REQUIRED;
    public static final String UNSUPPORTED = WarehouseForwarderEligibilityPolicy.UNSUPPORTED;
    private final ProcurementPurchaseOrderMapper mapper;

    public WarehouseForwarderEligibilityService(ProcurementPurchaseOrderMapper mapper) {
        this.mapper = mapper;
    }

    Map<String, ProductForwarderTransportEligibilityRecord> loadCurrent(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        Map<String, ProductForwarderTransportEligibilityRecord> result = new LinkedHashMap<>();
        Map<Long, List<Long>> variantsByOwner = safe(lines).stream()
                .filter(line -> line.ownerUserId != null && line.productVariantId != null)
                .collect(Collectors.groupingBy(
                        line -> line.ownerUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                line -> line.productVariantId,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(LinkedHashSet::new),
                                        ArrayList::new
                                )
                        )
                ));
        for (Map.Entry<Long, List<Long>> entry : variantsByOwner.entrySet()) {
            for (ProductForwarderTransportEligibilityRecord rule : safe(
                    mapper.listCurrentProductForwarderTransportEligibilities(entry.getKey(), entry.getValue())
            )) {
                result.putIfAbsent(WarehouseForwarderEligibilityPolicy.key(rule), rule);
            }
        }
        return result;
    }

    void apply(
            PurchaseOrderLogisticsQuoteChannelLineView view,
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate,
            Map<String, ProductForwarderTransportEligibilityRecord> currentRules
    ) {
        if (line != null && "SUBMITTED".equals(normalized(line.shippingSubmitStatus))) {
            view.eligibilityStatus = effectiveStatus(line.eligibilityStatus);
            return;
        }
        view.eligibilityStatus = statusFor(line, candidate, currentRules);
        if (UNSUPPORTED.equals(view.eligibilityStatus)) {
            view.unitPrice = null;
            view.currency = null;
            view.billingUnit = null;
            view.priceSource = null;
            view.quoteStatus = PENDING_QUOTE;
        }
    }

    void applySelected(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            List<ShippingOrderSegmentRecord> segments
    ) {
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, Function.identity(), (left, ignored) -> left));
        Map<String, ProductForwarderTransportEligibilityRecord> rules = loadCurrent(lines);
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            ShippingOrderSegmentRecord segment = segmentById.get(line.shippingOrderSegmentId);
            apply(line, WarehouseShippingQuoteChannelIdentity.candidateFrom(segment), rules);
        }
    }

    void applyCurrentChannels(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        Map<String, ProductForwarderTransportEligibilityRecord> rules = loadCurrent(lines);
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            apply(line, WarehouseShippingQuoteChannelIdentity.candidateFrom(line), rules);
        }
    }

    private void apply(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate,
            Map<String, ProductForwarderTransportEligibilityRecord> currentRules
    ) {
        if (line == null) {
            return;
        }
        if ("SUBMITTED".equals(normalized(line.shippingSubmitStatus))) {
            line.eligibilityStatus = effectiveStatus(line.eligibilityStatus);
            return;
        }
        line.eligibilityStatus = statusFor(line, candidate, currentRules);
        if (UNSUPPORTED.equals(line.eligibilityStatus)) {
            line.unitPrice = null;
            line.currency = null;
            line.billingUnit = null;
            line.quoteStatus = PENDING_QUOTE;
        }
    }

    void requireQuotable(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            ForwarderRouteRecommendationRecord candidate
    ) {
        Map<String, ProductForwarderTransportEligibilityRecord> rules = loadCurrent(lines);
        requireRuleStatus(lines, candidate, rules, UNSUPPORTED, "该货代当前不接，不能保存报价。");
        requireRuleStatus(lines, candidate, rules, WarehouseForwarderEligibilityPolicy.UNKNOWN,
                "承运状态异常，不能保存报价，请刷新后重试。");
    }

    void requireExportable(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            ForwarderRouteRecommendationRecord candidate
    ) {
        Map<String, ProductForwarderTransportEligibilityRecord> rules = loadCurrent(lines);
        requireRuleStatus(lines, candidate, rules, UNSUPPORTED,
                "该货代当前不接，审核单未导出。请换货代或拆分商品。");
        requireRuleStatus(lines, candidate, rules, WarehouseForwarderEligibilityPolicy.UNKNOWN,
                "承运状态异常，审核单未导出，请刷新后重试。");
    }

    void requireSubmittable(List<PurchaseOrderLogisticsQuoteLineRecord> lines) {
        requireLineStatus(lines, UNSUPPORTED, "所选货代当前不接，不能提交发货。请换货代或拆分商品。");
        requireLineStatus(lines, INQUIRY_REQUIRED, "仍需向货代询价确认，不能提交发货。");
        requireLineStatus(lines, WarehouseForwarderEligibilityPolicy.UNKNOWN,
                "承运状态缺失或异常，不能提交发货，请刷新后重试。");
    }

    void updateRule(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String forwarderCode,
            UpdateShippingOrderLineEligibilityCommand command,
            Long operatorUserId
    ) {
        if (line == null) {
            throw new IllegalArgumentException("承运状态商品不存在或已删除。");
        }
        String normalizedForwarder = requiredCode(forwarderCode, "请选择货代。");
        String status = normalizeStatus(command == null ? null : command.eligibilityStatus);
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("请选择有效的承运状态。");
        }
        ProductForwarderTransportEligibilityRecord current =
                mapper.selectActiveProductForwarderTransportEligibilityForUpdate(
                        line.ownerUserId,
                        line.productVariantId,
                        normalized(line.siteCode),
                        normalizedForwarder,
                        normalized(line.plannedTransportMode)
                );
        int currentVersion = nonNull(
                mapper.selectLatestProductForwarderTransportEligibilityVersionForUpdate(
                        line.ownerUserId,
                        line.productVariantId,
                        normalized(line.siteCode),
                        normalizedForwarder,
                        normalized(line.plannedTransportMode)
                )
        );
        LocalDate today = LocalDate.now();
        if (SUPPORTED.equals(status)) {
            closeCurrent(current, today, operatorUserId);
            return;
        }
        if (current != null && status.equals(normalizeStatus(current.eligibilityStatus))) {
            return;
        }
        closeCurrent(current, today, operatorUserId);
        ProductForwarderTransportEligibilityRecord next = new ProductForwarderTransportEligibilityRecord();
        next.id = mapper.nextProductForwarderTransportEligibilityId();
        next.ownerUserId = line.ownerUserId;
        next.productMasterId = line.productMasterId;
        next.productVariantId = line.productVariantId;
        next.logicalStoreId = line.logicalStoreId;
        next.sourceStoreCode = trim(line.sourceStoreCode);
        next.partnerSku = trim(line.partnerSku);
        next.siteCode = normalized(line.siteCode);
        next.forwarderCode = normalizedForwarder;
        next.transportMode = normalized(line.plannedTransportMode);
        next.eligibilityStatus = status;
        next.effectiveFrom = today;
        next.version = currentVersion + 1;
        try {
            if (mapper.insertProductForwarderTransportEligibility(next, operatorUserId) != 1) {
                throw concurrentUpdate();
            }
        } catch (DuplicateKeyException exception) {
            throw concurrentUpdate();
        }
    }

    void snapshot(
            Long shippingOrderId,
            Long ownerUserId,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            Long operatorUserId
    ) {
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            if (line.shippingOrderLineId == null || mapper.snapshotShippingOrderLineEligibility(
                    shippingOrderId, ownerUserId, line, operatorUserId) <= 0) {
                throw new IllegalArgumentException("仓库单承运结论保存失败，请刷新后重试。");
            }
        }
    }

    private void closeCurrent(
            ProductForwarderTransportEligibilityRecord current,
            LocalDate effectiveTo,
            Long operatorUserId
    ) {
        if (current != null && mapper.closeProductForwarderTransportEligibility(
                current.id, current.version, effectiveTo, operatorUserId) <= 0) {
            throw new IllegalArgumentException("承运状态已被其他操作更新，请刷新后重试。");
        }
    }

    private static IllegalArgumentException concurrentUpdate() {
        return new IllegalArgumentException("承运状态已被其他操作更新，请刷新后重试。");
    }

    private void requireRuleStatus(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            ForwarderRouteRecommendationRecord candidate,
            Map<String, ProductForwarderTransportEligibilityRecord> rules,
            String status,
            String message
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> invalid = safe(lines).stream()
                .filter(line -> status.equals(statusFor(line, candidate, rules)))
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(blockMessage(invalid, message));
        }
    }

    private void requireLineStatus(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            String status,
            String message
    ) {
        List<PurchaseOrderLogisticsQuoteLineRecord> invalid = safe(lines).stream()
                .filter(line -> status.equals(normalizeStatus(line.eligibilityStatus)))
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(blockMessage(invalid, message));
        }
    }

    static String key(Long owner, Long variant, String site, String forwarder, String mode) {
        return WarehouseForwarderEligibilityPolicy.key(owner, variant, site, forwarder, mode);
    }

    static String effectiveStatus(String value) {
        return WarehouseForwarderEligibilityPolicy.effectiveStatus(value);
    }
}
