package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteChannelLineView;
import java.util.ArrayList;
import java.util.List;

final class LogisticsQuoteExportOption {
    ForwarderRouteRecommendationRecord candidate;
    String templateType;
    Integer pendingLineCount = 0;
    Integer confirmedLineCount = 0;
    Integer newProductLineCount = 0;
    List<PurchaseOrderLogisticsQuotePublishedPriceView> publishedPrices = new ArrayList<>();
    List<PurchaseOrderLogisticsQuoteSurchargeView> surcharges = new ArrayList<>();
    List<PurchaseOrderLogisticsQuoteChannelLineView> lineQuotes = new ArrayList<>();
}
