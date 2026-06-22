package com.nuono.next.preorderprofit;

import java.util.ArrayList;
import java.util.List;

public class PreOrderProfitCandidateView extends PreOrderProfitCandidateCommand {
    private Long id;
    private String latestCalculationStatus;
    private PreOrderProfitCalculationView latestCalculation;
    private Long competitorCount;
    private Long purchaseOrderCount;
    private final List<PreOrderProfitCompetitorView> competitors = new ArrayList<>();
    private final List<PreOrderProfitPurchaseOrderView> purchaseOrders = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLatestCalculationStatus() {
        return latestCalculationStatus;
    }

    public void setLatestCalculationStatus(String latestCalculationStatus) {
        this.latestCalculationStatus = latestCalculationStatus;
    }

    public PreOrderProfitCalculationView getLatestCalculation() {
        return latestCalculation;
    }

    public void setLatestCalculation(PreOrderProfitCalculationView latestCalculation) {
        this.latestCalculation = latestCalculation;
    }

    public Long getCompetitorCount() {
        return competitorCount;
    }

    public void setCompetitorCount(Long competitorCount) {
        this.competitorCount = competitorCount;
    }

    public Long getPurchaseOrderCount() {
        return purchaseOrderCount;
    }

    public void setPurchaseOrderCount(Long purchaseOrderCount) {
        this.purchaseOrderCount = purchaseOrderCount;
    }

    public List<PreOrderProfitCompetitorView> getCompetitors() {
        return competitors;
    }

    public List<PreOrderProfitPurchaseOrderView> getPurchaseOrders() {
        return purchaseOrders;
    }
}
