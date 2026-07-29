package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteChannelLineView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderLogisticsQuoteChannelOptionView {
    public String routeCode;
    public String routeName;
    public String serviceCode;
    public String serviceName;
    public String quoteVersionCode;
    public LocalDate quoteEffectiveFrom;
    public LocalDateTime quoteRecordedAt;
    public String siteCode;
    public String transportMode;
    public String transportModeLabel;
    public String country;
    public String targetPlatform;
    public String deliveryCity;
    public String destinationNode;
    public String transitTimeText;
    public String priceSummary;
    public Integer totalLineCount = 0;
    public Integer pendingLineCount = 0;
    public Integer confirmedLineCount = 0;
    public Integer newProductLineCount = 0;
    public List<PurchaseOrderLogisticsQuotePublishedPriceView> publishedPrices = new ArrayList<>();
    public List<PurchaseOrderLogisticsQuoteSurchargeView> surcharges = new ArrayList<>();
    public List<PurchaseOrderLogisticsQuoteChannelLineView> lineQuotes = new ArrayList<>();
}
