package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;

public class CompetitorKeywordRow {
    private Long id;
    private Long watchProductId;
    private Long productKeywordId;
    private String keyword;
    private String keywordNorm;
    private String locale;
    private String status;
    private Integer displayOrder;
    private String lastProviderStatus;
    private LocalDateTime lastSucceededAt;
    private String lastErrorCode;
    private String lastErrorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWatchProductId() { return watchProductId; }
    public void setWatchProductId(Long watchProductId) { this.watchProductId = watchProductId; }
    public Long getProductKeywordId() { return productKeywordId; }
    public void setProductKeywordId(Long productKeywordId) { this.productKeywordId = productKeywordId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getKeywordNorm() { return keywordNorm; }
    public void setKeywordNorm(String keywordNorm) { this.keywordNorm = keywordNorm; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getLastProviderStatus() { return lastProviderStatus; }
    public void setLastProviderStatus(String lastProviderStatus) { this.lastProviderStatus = lastProviderStatus; }
    public LocalDateTime getLastSucceededAt() { return lastSucceededAt; }
    public void setLastSucceededAt(LocalDateTime lastSucceededAt) { this.lastSucceededAt = lastSucceededAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
}
