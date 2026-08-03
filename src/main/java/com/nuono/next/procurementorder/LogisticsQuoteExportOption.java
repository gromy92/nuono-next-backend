package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import java.util.ArrayList;
import java.util.List;

final class LogisticsQuoteExportOption {
    ForwarderRouteRecommendationRecord candidate;
    String templateType;
    Integer pendingLineCount = 0;
    Integer confirmedLineCount = 0;
    Integer newProductLineCount = 0;
    Integer supportedLineCount = 0;
    Integer inquiryRequiredLineCount = 0;
    Integer unsupportedLineCount = 0;
    List<PurchaseOrderLogisticsQuotePublishedPriceView> publishedPrices = new ArrayList<>();
    List<PurchaseOrderLogisticsQuoteSurchargeView> surcharges = new ArrayList<>();
    List<PurchaseOrderLogisticsQuoteChannelLineView> lineQuotes = new ArrayList<>();
}
