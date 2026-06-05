package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderItemAssignmentSummaryRow {

    private Long itemId;
    private Integer assignedQuantity;
    private String assignmentBreakdownText;
    private Integer consumableAssignmentCount;
    private Integer storeSiteAssignmentCount;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getAssignedQuantity() {
        return assignedQuantity;
    }

    public void setAssignedQuantity(Integer assignedQuantity) {
        this.assignedQuantity = assignedQuantity;
    }

    public String getAssignmentBreakdownText() {
        return assignmentBreakdownText;
    }

    public void setAssignmentBreakdownText(String assignmentBreakdownText) {
        this.assignmentBreakdownText = assignmentBreakdownText;
    }

    public Integer getConsumableAssignmentCount() {
        return consumableAssignmentCount;
    }

    public void setConsumableAssignmentCount(Integer consumableAssignmentCount) {
        this.consumableAssignmentCount = consumableAssignmentCount;
    }

    public Integer getStoreSiteAssignmentCount() {
        return storeSiteAssignmentCount;
    }

    public void setStoreSiteAssignmentCount(Integer storeSiteAssignmentCount) {
        this.storeSiteAssignmentCount = storeSiteAssignmentCount;
    }
}
