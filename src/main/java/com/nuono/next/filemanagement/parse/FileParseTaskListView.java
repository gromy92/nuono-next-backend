package com.nuono.next.filemanagement.parse;

import java.util.ArrayList;
import java.util.List;

public class FileParseTaskListView {

    private Integer total = 0;
    private Integer page = 1;
    private Integer pageSize = 20;
    private List<FileParseTaskListItemView> items = new ArrayList<>();

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total == null ? 0 : total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page == null ? 1 : page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize == null ? 20 : pageSize;
    }

    public List<FileParseTaskListItemView> getItems() {
        return items;
    }

    public void setItems(List<FileParseTaskListItemView> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
