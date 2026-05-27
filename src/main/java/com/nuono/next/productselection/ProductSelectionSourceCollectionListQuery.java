package com.nuono.next.productselection;

public class ProductSelectionSourceCollectionListQuery {

    private Integer page;
    private Integer pageSize;
    private String sourcePlatform;
    private String sourceTitle;
    private String sourceTitleCn;
    private String status;

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

    public String getSourcePlatform() {
        return sourcePlatform;
    }

    public void setSourcePlatform(String sourcePlatform) {
        this.sourcePlatform = sourcePlatform;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public String getSourceTitleCn() {
        return sourceTitleCn;
    }

    public void setSourceTitleCn(String sourceTitleCn) {
        this.sourceTitleCn = sourceTitleCn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
