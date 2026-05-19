package com.nuono.next.logisticsquote;

public class LogisticsQuoteOperationPriceItemsSummaryView {

    private int totalItems;

    private int airItemCount;

    private int seaItemCount;

    private int warehouseItemCount;

    private int adjustedItemCount;

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getAirItemCount() {
        return airItemCount;
    }

    public void setAirItemCount(int airItemCount) {
        this.airItemCount = airItemCount;
    }

    public int getSeaItemCount() {
        return seaItemCount;
    }

    public void setSeaItemCount(int seaItemCount) {
        this.seaItemCount = seaItemCount;
    }

    public int getWarehouseItemCount() {
        return warehouseItemCount;
    }

    public void setWarehouseItemCount(int warehouseItemCount) {
        this.warehouseItemCount = warehouseItemCount;
    }

    public int getAdjustedItemCount() {
        return adjustedItemCount;
    }

    public void setAdjustedItemCount(int adjustedItemCount) {
        this.adjustedItemCount = adjustedItemCount;
    }
}
