package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ProductSelectionGroupProfitSnapshotCommand {

    private Long operatorUserId;
    private String currencyCode;
    private BigDecimal profitAmount;
    private BigDecimal profitMargin;
    private Map<String, Object> snapshot = new HashMap<>();

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getProfitAmount() {
        return profitAmount;
    }

    public void setProfitAmount(BigDecimal profitAmount) {
        this.profitAmount = profitAmount;
    }

    public BigDecimal getProfitMargin() {
        return profitMargin;
    }

    public void setProfitMargin(BigDecimal profitMargin) {
        this.profitMargin = profitMargin;
    }

    public Map<String, Object> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Map<String, Object> snapshot) {
        this.snapshot = snapshot == null ? new HashMap<>() : new HashMap<>(snapshot);
    }
}
