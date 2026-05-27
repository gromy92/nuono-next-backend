package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonReportPullRequest {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullDataDomain dataDomain;
    private String reportType;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer maxPollAttempts;

    public static Builder builder() {
        return new Builder();
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public String getReportType() {
        return reportType;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public Integer getMaxPollAttempts() {
        return maxPollAttempts == null || maxPollAttempts <= 0 ? 3 : maxPollAttempts;
    }

    public String descriptor() {
        return reportType + " " + dateFrom + ".." + dateTo;
    }

    public static class Builder {
        private final NoonReportPullRequest request = new NoonReportPullRequest();

        public Builder ownerUserId(Long ownerUserId) {
            request.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            request.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            request.siteCode = siteCode;
            return this;
        }

        public Builder dataDomain(NoonPullDataDomain dataDomain) {
            request.dataDomain = dataDomain;
            return this;
        }

        public Builder reportType(String reportType) {
            request.reportType = reportType;
            return this;
        }

        public Builder dateFrom(LocalDate dateFrom) {
            request.dateFrom = dateFrom;
            return this;
        }

        public Builder dateTo(LocalDate dateTo) {
            request.dateTo = dateTo;
            return this;
        }

        public Builder maxPollAttempts(Integer maxPollAttempts) {
            request.maxPollAttempts = maxPollAttempts;
            return this;
        }

        public NoonReportPullRequest build() {
            return request;
        }
    }
}
