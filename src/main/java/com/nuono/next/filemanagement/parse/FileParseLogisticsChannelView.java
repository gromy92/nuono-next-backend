package com.nuono.next.filemanagement.parse;

import java.util.LinkedHashMap;
import java.util.Map;

public class FileParseLogisticsChannelView {

    private Long versionItemId;
    private String naturalKey;
    private String naturalKeyHash;
    private String channelKey;
    private String country;
    private String city;
    private String shippingMethod;
    private String feeItem;
    private String billingRule;
    private String leadTime;
    private boolean selected;
    private Map<String, Object> fields = new LinkedHashMap<>();

    public Long getVersionItemId() {
        return versionItemId;
    }

    public void setVersionItemId(Long versionItemId) {
        this.versionItemId = versionItemId;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public void setNaturalKey(String naturalKey) {
        this.naturalKey = naturalKey;
    }

    public String getNaturalKeyHash() {
        return naturalKeyHash;
    }

    public void setNaturalKeyHash(String naturalKeyHash) {
        this.naturalKeyHash = naturalKeyHash;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public void setChannelKey(String channelKey) {
        this.channelKey = channelKey;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getShippingMethod() {
        return shippingMethod;
    }

    public void setShippingMethod(String shippingMethod) {
        this.shippingMethod = shippingMethod;
    }

    public String getFeeItem() {
        return feeItem;
    }

    public void setFeeItem(String feeItem) {
        this.feeItem = feeItem;
    }

    public String getBillingRule() {
        return billingRule;
    }

    public void setBillingRule(String billingRule) {
        this.billingRule = billingRule;
    }

    public String getLeadTime() {
        return leadTime;
    }

    public void setLeadTime(String leadTime) {
        this.leadTime = leadTime;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : fields;
    }
}
