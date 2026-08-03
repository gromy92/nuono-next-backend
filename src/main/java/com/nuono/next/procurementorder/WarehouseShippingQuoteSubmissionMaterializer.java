package com.nuono.next.procurementorder;

import static com.nuono.next.procurementorder.WarehouseLogisticsQuoteAvailability.hasUsablePrice;
import static com.nuono.next.procurementorder.WarehouseLogisticsQuoteAvailability.statusFor;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.applyChannel;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.isZd;
import static com.nuono.next.procurementorder.WarehouseShippingQuoteChannelIdentity.safe;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

final class WarehouseShippingQuoteSubmissionMaterializer {

    private static final String NOT_SUBMITTED = "NOT_SUBMITTED";

    private WarehouseShippingQuoteSubmissionMaterializer() {
    }

    static List<PurchaseOrderLogisticsQuoteLineRecord> materialize(
            ProcurementPurchaseOrderMapper mapper,
            List<PurchaseOrderLogisticsQuoteLineRecord> lines,
            List<ShippingOrderSegmentRecord> segments,
            Long operatorUserId
    ) {
        Map<Long, ShippingOrderSegmentRecord> segmentById = safe(segments).stream()
                .filter(segment -> segment.id != null)
                .collect(Collectors.toMap(segment -> segment.id, Function.identity(), (left, ignored) -> left));
        List<PurchaseOrderLogisticsQuoteLineRecord> materialized = new ArrayList<>();
        for (PurchaseOrderLogisticsQuoteLineRecord line : safe(lines)) {
            ShippingOrderSegmentRecord segment = segmentById.get(line.shippingOrderSegmentId);
            requirePrice(line, segment);
            PurchaseOrderLogisticsQuoteLineRecord exact = findOrCreateExactLine(
                    mapper, line, segment, operatorUserId);
            copySubmissionFacts(exact, line);
            WarehouseShippingQuoteSnapshotRefresher.confirm(mapper, exact, operatorUserId);
            materialized.add(exact);
        }
        return materialized;
    }

    private static void requirePrice(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ShippingOrderSegmentRecord segment
    ) {
        boolean priceRequired = segment == null ? !isZd(line) : !isZd(segment);
        if (priceRequired && !hasUsablePrice(line)) {
            throw new IllegalArgumentException("仓库单还有物流报价缺失，不能提交。");
        }
    }

    private static PurchaseOrderLogisticsQuoteLineRecord findOrCreateExactLine(
            ProcurementPurchaseOrderMapper mapper,
            PurchaseOrderLogisticsQuoteLineRecord line,
            ShippingOrderSegmentRecord segment,
            Long operatorUserId
    ) {
        if (line.id != null) {
            return WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, line, line, operatorUserId);
        }
        PurchaseOrderLogisticsQuoteLineRecord exact =
                mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                        line.shippingOrderId,
                        line.purchaseOrderItemSiteId,
                        segment == null ? line.forwarderCode : segment.forwarderCode,
                        segment == null ? line.routeCode : segment.routeCode,
                        segment == null ? line.serviceCode : segment.serviceCode
                );
        if (exact != null) {
            return WarehouseShippingQuoteSnapshotRefresher.refresh(mapper, exact, line, operatorUserId);
        }
        exact = WarehouseLogisticsQuoteLineFactory.copyOf(line);
        exact.id = mapper.nextLogisticsQuoteLineId();
        exact.shippingSubmitStatus = NOT_SUBMITTED;
        if (segment != null) {
            applyChannel(exact, segment);
        }
        mapper.insertLogisticsQuoteLine(exact, operatorUserId);
        return exact;
    }

    private static void copySubmissionFacts(
            PurchaseOrderLogisticsQuoteLineRecord target,
            PurchaseOrderLogisticsQuoteLineRecord source
    ) {
        target.unitPrice = hasUsablePrice(source) ? source.unitPrice : null;
        target.currency = hasUsablePrice(source) ? source.currency : null;
        target.billingUnit = hasUsablePrice(source) ? source.billingUnit : null;
        target.estimatedAmount = hasUsablePrice(source) ? source.estimatedAmount : null;
        target.quoteStatus = statusFor(target.unitPrice);
        if (StringUtils.hasText(source.yiteMaterial)) {
            target.yiteMaterial = source.yiteMaterial;
        }
    }
}
