package com.nuono.next.salesforecast;

public class SalesForecastFollowUpView {

    private final String partnerSku;
    private final String sku;
    private final boolean marked;

    public SalesForecastFollowUpView(String partnerSku, String sku, boolean marked) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.marked = marked;
    }

    public static SalesForecastFollowUpView fromRecord(SalesForecastFollowUpRecord record) {
        return new SalesForecastFollowUpView(record.getPartnerSku(), record.getSku(), record.isMarked());
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public boolean isMarked() {
        return marked;
    }
}
