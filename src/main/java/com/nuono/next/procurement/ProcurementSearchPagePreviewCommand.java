package com.nuono.next.procurement;

public class ProcurementSearchPagePreviewCommand {

    private String html;

    private Integer maxCandidates;

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public Integer getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(Integer maxCandidates) {
        this.maxCandidates = maxCandidates;
    }
}
