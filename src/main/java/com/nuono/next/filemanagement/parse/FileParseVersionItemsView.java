package com.nuono.next.filemanagement.parse;

import java.util.List;

public class FileParseVersionItemsView {

    private Long versionId;
    private String versionNo;
    private Long targetPlanId;
    private int total;
    private int page;
    private int pageSize;
    private List<FileParseProcessingColumnView> columns;
    private List<FileParseVersionItemView> items;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

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

    public List<FileParseProcessingColumnView> getColumns() {
        return columns;
    }

    public void setColumns(List<FileParseProcessingColumnView> columns) {
        this.columns = columns;
    }

    public List<FileParseVersionItemView> getItems() {
        return items;
    }

    public void setItems(List<FileParseVersionItemView> items) {
        this.items = items;
    }
}
