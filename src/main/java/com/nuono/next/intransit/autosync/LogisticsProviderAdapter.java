package com.nuono.next.intransit.autosync;

public interface LogisticsProviderAdapter {
    String sourceSystem();

    LogisticsProviderFetchResult fetch(LogisticsProviderFetchRequest request);
}
