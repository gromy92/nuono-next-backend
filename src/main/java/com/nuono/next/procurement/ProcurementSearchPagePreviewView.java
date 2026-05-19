package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import java.util.ArrayList;
import java.util.List;

public class ProcurementSearchPagePreviewView {

    private boolean ready;

    private String message;

    private String pageTitle;

    private Integer detectedOfferCount;

    private Integer extractedCount;

    private List<String> warnings = new ArrayList<>();

    private List<CandidateView> candidates = new ArrayList<>();

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public Integer getDetectedOfferCount() {
        return detectedOfferCount;
    }

    public void setDetectedOfferCount(Integer detectedOfferCount) {
        this.detectedOfferCount = detectedOfferCount;
    }

    public Integer getExtractedCount() {
        return extractedCount;
    }

    public void setExtractedCount(Integer extractedCount) {
        this.extractedCount = extractedCount;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<CandidateView> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<CandidateView> candidates) {
        this.candidates = candidates;
    }
}
