package com.nuono.next.mobile;

import java.util.ArrayList;
import java.util.List;

public class MobilePageResponse<T> {

    private List<T> content = new ArrayList<>();

    private Integer currentPage;

    private Integer pageSize;

    private Long totalItems;

    private Integer totalPages;

    public MobilePageResponse() {
    }

    public MobilePageResponse(List<T> content, Integer currentPage, Integer pageSize, Long totalItems) {
        this.content = content;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        if (pageSize == null || pageSize <= 0) {
            this.totalPages = 0;
        } else {
            this.totalPages = (int) Math.ceil((totalItems == null ? 0D : totalItems.doubleValue()) / pageSize);
        }
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Long totalItems) {
        this.totalItems = totalItems;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
}
