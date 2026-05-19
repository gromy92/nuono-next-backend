package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseAiChunksView {

    private Long taskId;
    private int total;
    private int page;
    private int pageSize;
    private List<FileParseAiChunkView> items = new ArrayList<>();

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
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

    public List<FileParseAiChunkView> getItems() {
        return items;
    }

    public void setItems(List<FileParseAiChunkView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
