package com.nuono.next.procurementorder;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ProductForwarderChannelQuoteRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteChannelLineView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WarehouseLogisticsQuotePriceService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String PENDING = "PENDING_QUOTE";

    private final ProcurementPurchaseOrderMapper mapper;
    private final WarehouseProductLogisticsPriceBridge productPriceBridge;

    public WarehouseLogisticsQuotePriceService(
            ProcurementPurchaseOrderMapper mapper,
            WarehouseProductLogisticsPriceBridge productPriceBridge
    ) {
        this.mapper = mapper;
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
        if (isOwnConfirmedSnapshot(channelConfirmation, candidate)) {
            PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
            view.quoteStatus = CONFIRMED;
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
        if (current != null && current.unitCostCny != null) {
            PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
            view.quoteStatus = PENDING;
            view.unitPrice = current.unitCostCny;
            view.currency = defaultText(current.currencyCode, "CNY");
            view.billingUnit = current.chargeUnit;
            view.priceSource = "PRODUCT_CURRENT";
            return view;
        }
        return legacyView(line, selectLegacyCurrent(line, candidate));
    }

    public void syncConfirmedQuote(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Long operatorUserId,
            String sourceType
    ) {
        productPriceBridge.syncConfirmedQuote(line, operatorUserId, sourceType);
    }

    private ProductForwarderChannelQuoteRecord selectLegacyCurrent(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        if (line == null
                || candidate == null
                || line.ownerUserId == null
                || line.productVariantId == null
                || !StringUtils.hasText(candidate.forwarderCode)
                || !StringUtils.hasText(candidate.routeCode)) {
            return null;
        }
        return mapper.selectCurrentProductForwarderChannelQuote(
                line.ownerUserId,
                line.sourceStoreCode,
                line.logicalStoreId,
                line.partnerSku,
                line.productVariantId,
                candidate.forwarderCode,
                normalize(firstText(candidate.siteCode, line.siteCode)),
                candidate.routeCode,
                candidate.serviceCode
        );
    }

    private PurchaseOrderLogisticsQuoteChannelLineView legacyView(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ProductForwarderChannelQuoteRecord current
    ) {
        PurchaseOrderLogisticsQuoteChannelLineView view = baseView(line);
        view.quoteStatus = PENDING;
        if (current != null && current.unitPrice != null) {
            view.unitPrice = current.unitPrice;
            view.currency = current.currency;
            view.billingUnit = current.billingUnit;
            view.priceSource = "LEGACY_CHANNEL_QUOTE";
        }
        return view;
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

    private boolean isOwnConfirmedSnapshot(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        return line != null
                && candidate != null
                && CONFIRMED.equalsIgnoreCase(defaultText(line.quoteStatus, ""))
                && line.unitPrice != null
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

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
