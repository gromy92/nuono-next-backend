package com.nuono.next.competitoranalysis.noon;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoonSearchPage {
    private String sourceUrl;
    private String parserVersion;
    private Integer providerHttpStatus;
    private String responseHash;
    private LocalDateTime capturedAt;
    private List<NoonSearchResult> results = new ArrayList<>();

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public void setCapturedAt(LocalDateTime capturedAt) { this.capturedAt = capturedAt; }
    public List<NoonSearchResult> getResults() { return results; }
    public void setResults(List<NoonSearchResult> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }
}
