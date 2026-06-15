package com.nuono.next.orderfinance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public class OrderFinanceQuery {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final LocalDate dateFrom;
    private final LocalDate dateTo;
    private final String currency;
    private final String search;
    private final List<String> partnerSkuList;
    private final String partnerSku;
    private final String sku;
    private final boolean missingPartnerSku;
    private final boolean missingPartnerSkuSelected;

    public OrderFinanceQuery(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String currency,
            String search,
            List<String> partnerSkuList,
            String partnerSku,
            String sku
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = normalize(storeCode);
        this.siteCode = normalize(siteCode);
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.currency = StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : null;
        this.search = StringUtils.hasText(search) ? search.trim() : null;
        this.partnerSkuList = normalizeList(partnerSkuList);
        this.partnerSku = StringUtils.hasText(partnerSku) ? partnerSku.trim() : null;
        this.sku = StringUtils.hasText(sku) ? sku.trim() : null;
        this.missingPartnerSku = isMissingPartnerSkuToken(this.partnerSku);
        this.missingPartnerSkuSelected = this.partnerSkuList.stream().anyMatch(OrderFinanceQuery::isMissingPartnerSkuToken);
    }

    public static OrderFinanceQuery summary(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String currency,
            String search,
            List<String> partnerSkuList
    ) {
        return new OrderFinanceQuery(ownerUserId, storeCode, siteCode, dateFrom, dateTo, currency, search, partnerSkuList, null, null);
    }

    public static OrderFinanceQuery detail(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String currency,
            String partnerSku,
            String sku
    ) {
        return new OrderFinanceQuery(ownerUserId, storeCode, siteCode, dateFrom, dateTo, currency, null, List.of(), partnerSku, sku);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    private static boolean isMissingPartnerSkuToken(String value) {
        return "(missing)".equalsIgnoreCase(value) || "__missing__".equalsIgnoreCase(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getCurrency() { return currency; }
    public String getSearch() { return search; }
    public List<String> getPartnerSkuList() { return partnerSkuList; }
    public String getPartnerSku() { return partnerSku; }
    public String getSku() { return sku; }
    public boolean isMissingPartnerSku() { return missingPartnerSku; }
    public boolean isMissingPartnerSkuSelected() { return missingPartnerSkuSelected; }
}
