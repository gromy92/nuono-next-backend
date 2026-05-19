package com.nuono.next.procurement;

public class AliAiBulkInquiryCreatePageProbeCommand {

    private String pageUrl;
    private String sampleHtml;
    private Boolean openIfMissing;

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getSampleHtml() {
        return sampleHtml;
    }

    public void setSampleHtml(String sampleHtml) {
        this.sampleHtml = sampleHtml;
    }

    public Boolean getOpenIfMissing() {
        return openIfMissing;
    }

    public void setOpenIfMissing(Boolean openIfMissing) {
        this.openIfMissing = openIfMissing;
    }
}
