package com.nuono.next.intransit.autosync;

public interface FreightBillProviderAdapter {
    String sourceSystem();

    FreightBillFetchResult fetchFreightBills(LogisticsProviderFetchRequest request);
}
