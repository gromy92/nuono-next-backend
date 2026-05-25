package com.nuono.next.logisticsquote;

public class LogisticsServiceLineFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String forwarderName;
    private final String country;
    private final String fulfillmentMode;
    private final String destinationNode;
    private final String transportMode;
    private final String serviceScope;
    private final String channelName;
    private final String originWarehouse;
    private final String destinationWarehouse;
    private final String departureFrequency;
    private final Integer estimatedDaysMin;
    private final Integer estimatedDaysMax;
    private final String effectiveFrom;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsServiceLineFact(
            String naturalKey,
            String forwarderCode,
            String forwarderName,
            String country,
            String fulfillmentMode,
            String destinationNode,
            String transportMode,
            String serviceScope,
            String channelName,
            String originWarehouse,
            String destinationWarehouse,
            String departureFrequency,
            Integer estimatedDaysMin,
            Integer estimatedDaysMax,
            String effectiveFrom,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.forwarderName = forwarderName;
        this.country = country;
        this.fulfillmentMode = fulfillmentMode;
        this.destinationNode = destinationNode;
        this.transportMode = transportMode;
        this.serviceScope = serviceScope;
        this.channelName = channelName;
        this.originWarehouse = originWarehouse;
        this.destinationWarehouse = destinationWarehouse;
        this.departureFrequency = departureFrequency;
        this.estimatedDaysMin = estimatedDaysMin;
        this.estimatedDaysMax = estimatedDaysMax;
        this.effectiveFrom = effectiveFrom;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getForwarderName() {
        return forwarderName;
    }

    public String getCountry() {
        return country;
    }

    public String getFulfillmentMode() {
        return fulfillmentMode;
    }

    public String getDestinationNode() {
        return destinationNode;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public String getServiceScope() {
        return serviceScope;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getOriginWarehouse() {
        return originWarehouse;
    }

    public String getDestinationWarehouse() {
        return destinationWarehouse;
    }

    public String getDepartureFrequency() {
        return departureFrequency;
    }

    public Integer getEstimatedDaysMin() {
        return estimatedDaysMin;
    }

    public Integer getEstimatedDaysMax() {
        return estimatedDaysMax;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
