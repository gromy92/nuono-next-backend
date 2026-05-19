package com.nuono.next.mobile;

import java.util.ArrayList;
import java.util.List;

public class MobileDashboardAlertsView {

    private Integer taskFailedCount;

    private Integer warehouseNoticeCount;

    private Integer systemNoticeCount;

    private List<MobileDashboardAlertItemView> items = new ArrayList<>();

    public Integer getTaskFailedCount() {
        return taskFailedCount;
    }

    public void setTaskFailedCount(Integer taskFailedCount) {
        this.taskFailedCount = taskFailedCount;
    }

    public Integer getWarehouseNoticeCount() {
        return warehouseNoticeCount;
    }

    public void setWarehouseNoticeCount(Integer warehouseNoticeCount) {
        this.warehouseNoticeCount = warehouseNoticeCount;
    }

    public Integer getSystemNoticeCount() {
        return systemNoticeCount;
    }

    public void setSystemNoticeCount(Integer systemNoticeCount) {
        this.systemNoticeCount = systemNoticeCount;
    }

    public List<MobileDashboardAlertItemView> getItems() {
        return items;
    }

    public void setItems(List<MobileDashboardAlertItemView> items) {
        this.items = items;
    }
}
