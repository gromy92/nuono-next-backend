package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.applyChannel;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.isYite;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.isZd;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.matches;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.normalizeStatus;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.normalizeSubmitStatus;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.safe;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.sameCode;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseShippingQuoteChannelService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String PENDING = "PENDING_QUOTE";
    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseLogisticsQuotePriceService priceService;
    private final WarehouseShippingQuoteProjectionService quoteProjectionService;

    public WarehouseShippingQuoteChannelService(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseLogisticsQuotePriceService priceService,
            WarehouseShippingQuoteProjectionService quoteProjectionService
    ) {
        this.mapper = mapper;
        this.priceService = priceService;
        this.quoteProjectionService = quoteProjectionService;
    }

    Map<Long, List<PurchaseOrderLogisticsQuoteLineRecord>> loadConfirmations(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines
    ) {
        return safe(lines).stream()
                .map(line -> line.shippingOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        shippingOrderId -> safe(mapper.listConfirmedLogisticsQuoteLinesByShippingOrder(shippingOrderId))
                ));
    }

    PurchaseOrderLogisticsQuoteChannelLineView resolvePrice(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate,
            Map<Long, List<PurchaseOrderLogisticsQuoteLineRecord>> confirmationsByShippingOrder
    ) {
        PurchaseOrderLogisticsQuoteLineRecord confirmation = safe(
                confirmationsByShippingOrder.get(line.shippingOrderId)
        ).stream()
                .filter(row -> Objects.equals(row.purchaseOrderItemSiteId, line.purchaseOrderItemSiteId))
                .filter(row -> matches(row, candidate))
                .findFirst()
                .orElse(line);
        return priceService.resolve(line, candidate, confirmation);
    }

    PurchaseOrderLogisticsQuoteLineRecord requireChannelLine(
            PurchaseOrderLogisticsQuoteLineRecord baseLine,
            ForwarderRouteRecommendationRecord candidate,
            Long operatorUserId
    ) {
        PurchaseOrderLogisticsQuoteLineRecord existing =
                mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                        baseLine.shippingOrderId,
                        baseLine.purchaseOrderItemSiteId,
                        candidate.forwarderCode,
                        candidate.routeCode,
                        candidate.serviceCode
                );
        if (existing != null) {
            return WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, existing, baseLine, operatorUserId);
        }
        PurchaseOrderLogisticsQuoteLineRecord created = WarehouseLogisticsQuoteLineFactory.copyOf(baseLine);
        created.id = mapper.nextLogisticsQuoteLineId();
        created.quoteStatus = PENDING;
        created.shippingSubmitStatus = NOT_SUBMITTED;
        applyChannel(created, candidate);
        mapper.insertLogisticsQuoteLine(created, operatorUserId);
        return created;
    }

    List<PurchaseOrderLogisticsQuoteLineRecord> resolveSelectedLines(
            List<PurchaseOrderLogisticsQuoteLineRecord> baseLines,
            List<ShippingOrderSegmentRecord> segments,
            List<PurchaseOrderLogisticsQuoteLineRecord> confirmations
    ) {
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, Function.identity(), (left, ignored) -> left));
        List<PurchaseOrderLogisticsQuoteLineRecord> resolved = new ArrayList<>();
        for (PurchaseOrderLogisticsQuoteLineRecord baseLine : safe(baseLines)) {
            ShippingOrderSegmentRecord segment = segmentById.get(baseLine.shippingOrderSegmentId);
            PurchaseOrderLogisticsQuoteLineRecord exact = safe(confirmations).stream()
                    .filter(line -> Objects.equals(line.purchaseOrderItemSiteId, baseLine.purchaseOrderItemSiteId))
                    .filter(line -> matches(line, segment))
                    .findFirst()
                    .orElse(null);
            if (exact != null) {
                resolved.add(WarehouseShippingQuoteSnapshotRefresher.rebind(exact, baseLine));
                continue;
            }
            if (CONFIRMED.equals(normalizeStatus(baseLine.quoteStatus)) && matches(baseLine, segment)) {
                resolved.add(baseLine);
                continue;
            }
            if (segment != null) {
                applyChannel(baseLine, segment);
                baseLine.quoteStatus = PENDING;
                baseLine.unitPrice = null;
            }
            resolved.add(baseLine);
        }
        return resolved;
    }

    void requireSubmittable(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            List<ShippingOrderSegmentRecord> segments,
            String emptyMessage
    ) {
        if (safe(lines).isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, Function.identity(), (left, ignored) -> left));
        boolean missingQuote = lines.stream().anyMatch(line -> {
            ShippingOrderSegmentRecord segment = segmentById.get(line.shippingOrderSegmentId);
            if (!segmentById.isEmpty() && segment == null) {
                return true;
            }
            if (segment != null && !matches(line, segment)) {
                return true;
            }
            boolean priceBlocking = segment == null ? !isZd(line) : !isZd(segment);
            return priceBlocking && !CONFIRMED.equals(normalizeStatus(line.quoteStatus));
        });
        if (missingQuote) {
            throw new IllegalArgumentException("仓库单还有物流报价缺失，不能提交。");
        }
        boolean missingYiteMaterial = lines.stream()
                .filter(line -> isYite(line, segmentById.get(line.shippingOrderSegmentId)))
                .anyMatch(line -> !StringUtils.hasText(line.yiteMaterial));
        if (missingYiteMaterial) {
            throw new IllegalArgumentException("义特材质缺失，不能提交发货单。");
        }
    }

    List<PurchaseOrderLogisticsQuoteLineRecord> materializeSubmissionFacts(
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            List<ShippingOrderSegmentRecord> segments,
            Long operatorUserId
    ) {
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, Function.identity(), (left, ignored) -> left));
        List<PurchaseOrderLogisticsQuoteLineRecord> materialized = new ArrayList<>();
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            if (line.id != null) {
                materialized.add(WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, line, line, operatorUserId));
                continue;
            }
            ShippingOrderSegmentRecord segment = segmentById.get(line.shippingOrderSegmentId);
            if (segment == null || !isZd(segment)) {
                throw new IllegalArgumentException("仓库单还有物流报价缺失，不能提交。");
            }
            PurchaseOrderLogisticsQuoteLineRecord exact =
                    mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                            line.shippingOrderId,
                            line.purchaseOrderItemSiteId,
                            segment.forwarderCode,
                            segment.routeCode,
                            segment.serviceCode
                    );
            if (exact == null) {
                exact = WarehouseLogisticsQuoteLineFactory.copyOf(line);
                exact.id = mapper.nextLogisticsQuoteLineId();
                exact.shippingSubmitStatus = NOT_SUBMITTED;
                applyChannel(exact, segment);
                mapper.insertLogisticsQuoteLine(exact, operatorUserId);
            } else {
                exact = WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, exact, line, operatorUserId);
            }
            exact.quoteStatus = CONFIRMED;
            WarehouseShippingQuoteSnapshotRefresher.confirm(mapper, exact, operatorUserId);
            materialized.add(exact);
        }
        return materialized;
    }

    void refreshSelectedSegmentStates(
            Long shippingOrderId,
            Long ownerUserId,
            List<ShippingOrderSegmentRecord> segments,
            Set<Long> selectedSegmentIds,
            Long operatorUserId
    ) {
        List<ShippingOrderSegmentRecord> selected = safe(segments).stream()
                .filter(segment -> selectedSegmentIds == null
                        || selectedSegmentIds.isEmpty()
                        || selectedSegmentIds.contains(segment.id))
                .collect(Collectors.toList());
        if (selected.isEmpty() && selectedSegmentIds != null && !selectedSegmentIds.isEmpty()) {
            return;
        }
        if (selected.isEmpty()) {
            safe(mapper.listConfirmedLogisticsQuoteLinesByShippingOrder(shippingOrderId)).stream()
                    .findFirst()
                    .ifPresent(line -> mapper.refreshShippingOrderQuoteState(
                            shippingOrderId, line, operatorUserId));
            return;
        }
        for (ShippingOrderSegmentRecord segment : selected) {
            PurchaseOrderLogisticsQuoteLineRecord channel = new PurchaseOrderLogisticsQuoteLineRecord();
            applyChannel(channel, segment);
            mapper.refreshShippingOrderSegmentState(
                    shippingOrderId,
                    List.of(segment.id),
                    channel,
                    operatorUserId
            );
        }
        mapper.refreshShippingOrderHeaderState(shippingOrderId, ownerUserId, operatorUserId);
    }

    PurchaseOrderLogisticsQuoteLineRecord requireImportedChannelLine(
            boolean shippingOrder,
            Long documentId,
            PurchaseOrderLogisticsQuoteLineRecord selected,
            String forwarderCode,
            String forwarderName,
            String routeCode,
            String routeName,
            String serviceCode,
            String serviceName,
            Long operatorUserId
    ) {
        if (!shippingOrder || selected == null) {
            return selected;
        }
        if (matches(selected, forwarderCode, routeCode, serviceCode)) {
            return selected.id == null ? selected : WarehouseShippingQuoteSnapshotRefresher.refresh(
                    mapper, selected, selected, operatorUserId);
        }
        PurchaseOrderLogisticsQuoteLineRecord exact =
                mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                        documentId,
                        selected.purchaseOrderItemSiteId,
                        forwarderCode,
                        routeCode,
                        serviceCode
                );
        if (exact != null) {
            return WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, exact, selected, operatorUserId);
        }
        if (selected.id != null
                && ((!StringUtils.hasText(selected.forwarderCode) && !StringUtils.hasText(selected.routeCode))
                || (sameCode(selected.forwarderCode, forwarderCode) && !StringUtils.hasText(selected.routeCode)))) {
            applyChannel(selected, forwarderCode, forwarderName, routeCode, routeName, serviceCode, serviceName);
            return selected;
        }
        PurchaseOrderLogisticsQuoteLineRecord created = WarehouseLogisticsQuoteLineFactory.copyOf(selected);
        created.id = mapper.nextLogisticsQuoteLineId();
        created.quoteStatus = PENDING;
        created.shippingSubmitStatus = normalizeSubmitStatus(selected.shippingSubmitStatus);
        applyChannel(created, forwarderCode, forwarderName, routeCode, routeName, serviceCode, serviceName);
        mapper.insertLogisticsQuoteLine(created, operatorUserId);
        return created;
    }

    void persistConfirmedQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceFilename,
            String sourceType
    ) {
        quoteProjectionService.persistConfirmedQuote(line, operatorUserId, sourceFilename, sourceType);
    }

    String snapshot(PurchaseOrderLogisticsQuoteLineRecord line) {
        return quoteProjectionService.snapshot(line);
    }
}
