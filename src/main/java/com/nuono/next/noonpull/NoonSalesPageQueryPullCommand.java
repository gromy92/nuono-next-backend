package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonSalesPageQueryPullCommand {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Integer maxPages;
    private NoonPullRequestBudget requestBudget;
    private String requestSummary;

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

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    public NoonPullRequestBudget getRequestBudget() {
        return requestBudget;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public static final class Builder {
        private final NoonSalesPageQueryPullCommand command = new NoonSalesPageQueryPullCommand();

        public Builder ownerUserId(Long ownerUserId) {
            command.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            command.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            command.siteCode = siteCode;
            return this;
        }

        public Builder dateFrom(LocalDate dateFrom) {
            command.dateFrom = dateFrom;
            return this;
        }

        public Builder dateTo(LocalDate dateTo) {
            command.dateTo = dateTo;
            return this;
        }

        public Builder maxPages(Integer maxPages) {
            command.maxPages = maxPages;
            return this;
        }

        public Builder requestBudget(NoonPullRequestBudget requestBudget) {
            command.requestBudget = requestBudget;
            return this;
        }

        public Builder requestSummary(String requestSummary) {
            command.requestSummary = requestSummary;
            return this;
        }

        public NoonSalesPageQueryPullCommand build() {
            return command;
        }
    }
}
