package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseProcessingItemsView {

    private Long taskId;
    private Long resultId;
    private Integer revisionNo = 1;
    private Integer total = 0;
    private Integer page = 1;
    private Integer pageSize = 100;
    private List<FileParseProcessingColumnView> columns = new ArrayList<>();
    private List<FileParseProcessingItemView> items = new ArrayList<>();

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

    public Integer getRevisionNo() {
        return revisionNo;
    }

    public void setRevisionNo(Integer revisionNo) {
        this.revisionNo = revisionNo;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<FileParseProcessingColumnView> getColumns() {
        return columns;
    }

    public void setColumns(List<FileParseProcessingColumnView> columns) {
        this.columns = columns == null ? new ArrayList<>() : columns;
    }

    public List<FileParseProcessingItemView> getItems() {
        return items;
    }

    public void setItems(List<FileParseProcessingItemView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
