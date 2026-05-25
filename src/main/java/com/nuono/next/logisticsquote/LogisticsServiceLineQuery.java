package com.nuono.next.logisticsquote;

import org.springframework.util.StringUtils;

public class LogisticsServiceLineQuery {

    private final String forwarderCode;
    private final String country;
    private final String transportMode;
    private final String serviceScope;

    public LogisticsServiceLineQuery(String forwarderCode, String country, String transportMode, String serviceScope) {
        this.forwarderCode = forwarderCode;
        this.country = country;
        this.transportMode = transportMode;
        this.serviceScope = serviceScope;
    }

    public boolean matches(LogisticsServiceLineFact fact) {
        return matches(forwarderCode, fact.getForwarderCode())
                && matches(country, fact.getCountry())
                && matches(transportMode, fact.getTransportMode())
                && matches(serviceScope, fact.getServiceScope());
    }

    private boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equals(actual);
    }
}
