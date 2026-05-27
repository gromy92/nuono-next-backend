package com.nuono.next.logisticsquote;

import org.springframework.util.StringUtils;

public class LogisticsServiceLineQuery {

    private final String forwarderCode;
    private final String country;
    private final String transportMode;
    private final String serviceScope;
    private final String destinationNode;

    public LogisticsServiceLineQuery(String forwarderCode, String country, String transportMode, String serviceScope) {
        this(forwarderCode, country, transportMode, serviceScope, null);
    }

    public LogisticsServiceLineQuery(
            String forwarderCode,
            String country,
            String transportMode,
            String serviceScope,
            String destinationNode
    ) {
        this.forwarderCode = forwarderCode;
        this.country = country;
        this.transportMode = transportMode;
        this.serviceScope = serviceScope;
        this.destinationNode = destinationNode;
    }

    public boolean matches(LogisticsServiceLineFact fact) {
        return matches(forwarderCode, fact.getForwarderCode())
                && matches(country, fact.getCountry())
                && matches(transportMode, fact.getTransportMode())
                && matches(serviceScope, fact.getServiceScope())
                && matches(destinationNode, fact.getDestinationNode());
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getCountry() {
        return country;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public String getServiceScope() {
        return serviceScope;
    }

    public String getDestinationNode() {
        return destinationNode;
    }

    private boolean matches(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equals(actual);
    }
}
