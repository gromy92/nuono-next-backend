package com.nuono.next.filemanagement.parse;

import java.util.List;

public class FileParseOverviewItemsView {

    private Long taskId;
    private Long resultId;
    private int total;
    private int page;
    private int pageSize;
    private List<FileParseProcessingColumnView> columns;
    private List<FileParseOverviewItemView> items;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
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

    public List<FileParseOverviewItemView> getItems() {
        return items;
    }

    public void setItems(List<FileParseOverviewItemView> items) {
        this.items = items;
    }
}
