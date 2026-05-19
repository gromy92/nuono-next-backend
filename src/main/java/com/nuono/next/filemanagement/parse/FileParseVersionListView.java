package com.nuono.next.filemanagement.parse;

import java.util.List;

public class FileParseVersionListView {

    private Long targetPlanId;
    private int total;
    private int page;
    private int pageSize;
    private List<FileParseVersionSummaryView> items;

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public List<FileParseVersionSummaryView> getItems() {
        return items;
    }

    public void setItems(List<FileParseVersionSummaryView> items) {
        this.items = items;
    }
}
