package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseLogisticsQuotePriceService {

    private static final String SUBMITTED = "SUBMITTED";

    private final WarehouseProductLogisticsPriceBridge productPriceBridge;

    public WarehouseLogisticsQuotePriceService(WarehouseProductLogisticsPriceBridge productPriceBridge) {
        this.productPriceBridge = productPriceBridge;
    }

    public PurchaseOrderLogisticsQuoteChannelLineView resolve(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return resolve(line, candidate, line);
    }

    public PurchaseOrderLogisticsQuoteChannelLineView resolve(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate,
            PurchaseOrderLogisticsQuoteLineRecord channelConfirmation
    ) {
        if (isOwnSnapshot(channelConfirmation, candidate)) {
            PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
            view.quoteStatus = WarehouseLogisticsQuoteAvailability.statusFor(channelConfirmation.unitPrice);
            view.unitPrice = channelConfirmation.unitPrice;
            view.currency = channelConfirmation.currency;
            view.billingUnit = channelConfirmation.billingUnit;
            view.yiteMaterial = defaultText(channelConfirmation.yiteMaterial, line.yiteMaterial);
            view.priceSource = "SHIPPING_ORDER_SNAPSHOT";
            return view;
        }
        CurrentCostRow current = productPriceBridge.findCurrentCost(
                line,
                candidate == null ? null : candidate.forwarderCode
        );
        if (current != null && WarehouseLogisticsQuoteAvailability.hasUsablePrice(current.unitCostCny)) {
            PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
            view.quoteStatus = WarehouseLogisticsQuoteAvailability.AVAILABLE;
            view.unitPrice = current.unitCostCny;
            view.currency = defaultText(current.currencyCode, "CNY");
            view.billingUnit = current.chargeUnit;
            view.priceSource = "PRODUCT_CURRENT";
            return view;
        }
        PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
        view.quoteStatus = WarehouseLogisticsQuoteAvailability.MISSING;
        return view;
    }

    public void syncConfirmedQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceType
    ) {
        productPriceBridge.syncConfirmedQuote(line, operatorUserId, sourceType);
    }

    private PurchaseOrderLogisticsQuoteChannelLineView baseView(PurchaseOrderLogisticsQuoteLineRecord line) {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        view.shippingOrderLineId = line.shippingOrderLineId == null ? null : String.valueOf(line.shippingOrderLineId);
        view.purchaseOrderItemSiteId = line.purchaseOrderItemSiteId == null
                ? null
                : String.valueOf(line.purchaseOrderItemSiteId);
        view.partnerSku = line.partnerSku;
        view.barcode = line.barcode;
        view.yiteMaterial = line.yiteMaterial;
        return view;
    }

    private boolean isOwnSnapshot(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return line != null
                && candidate != null
                && SUBMITTED.equalsIgnoreCase(defaultText(line.shippingSubmitStatus, ""))
                && sameCode(line.forwarderCode, candidate.forwarderCode)
                && sameCode(line.routeCode, candidate.routeCode)
                && sameNullableCode(line.serviceCode, candidate.serviceCode);
    }

    private static boolean sameCode(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private static boolean sameNullableCode(String left, String right) {
        return defaultText(left, "").equalsIgnoreCase(defaultText(right, ""));
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
