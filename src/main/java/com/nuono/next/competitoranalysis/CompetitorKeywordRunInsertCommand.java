package com.nuono.next.competitoranalysis;

public class CompetitorKeywordRunInsertCommand {
    private Long id;
    private Long searchRunId;
    private Long keywordId;
    private String keywordSnapshot;
    private String localeSnapshot;
    private String providerStatus;
    private Integer resultCount;
    private String sourceUrl;
    private String parserVersion;
    private Integer providerHttpStatus;
    private String responseHash;
    private String errorCode;
    private String errorMessage;
    private Long actorUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSearchRunId() { return searchRunId; }
    public void setSearchRunId(Long searchRunId) { this.searchRunId = searchRunId; }
    public Long getKeywordId() { return keywordId; }
    public void setKeywordId(Long keywordId) { this.keywordId = keywordId; }
    public String getKeywordSnapshot() { return keywordSnapshot; }
    public void setKeywordSnapshot(String keywordSnapshot) { this.keywordSnapshot = keywordSnapshot; }
    public String getLocaleSnapshot() { return localeSnapshot; }
    public void setLocaleSnapshot(String localeSnapshot) { this.localeSnapshot = localeSnapshot; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
    public void setProviderHttpStatus(Integer providerHttpStatus) { this.providerHttpStatus = providerHttpStatus; }
    public String getResponseHash() { return responseHash; }
    public void setResponseHash(String responseHash) { this.responseHash = responseHash; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
}
