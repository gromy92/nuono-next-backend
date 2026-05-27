package com.nuono.next.noonpull;

import java.time.LocalDate;
import org.springframework.util.StringUtils;

public class NoonInterfacePullRequest {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullDataDomain dataDomain;
    private String requestName;
    private String targetIdentity;
    private Integer timeoutSeconds;
    private Integer maxPages;
    private Integer resumePage;
    private Integer initialProcessedItemCount;
    private Integer initialRequestCount;
    private NoonPullRequestBudget budget;
    private String requestSummary;
    private LocalDate dateFrom;
    private LocalDate dateTo;

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

    public String getRequestName() {
        return requestName;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public Integer getMaxPages() {
        return maxPages == null || maxPages <= 0 ? 100 : maxPages;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public Integer getResumePage() {
        return resumePage == null || resumePage <= 0 ? 1 : resumePage;
    }

    public Integer getInitialProcessedItemCount() {
        return initialProcessedItemCount == null ? 0 : initialProcessedItemCount;
    }

    public Integer getInitialRequestCount() {
        return initialRequestCount == null ? 0 : initialRequestCount;
    }

    public NoonPullRequestBudget getBudget() {
        return budget;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String safeDescriptor() {
        String name = StringUtils.hasText(requestName) ? requestName : "interface-pull";
        return name + " target=" + targetIdentity + " deadlineSeconds=" + timeoutSeconds;
    }

    public static class Builder {
        private final NoonInterfacePullRequest request = new NoonInterfacePullRequest();

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

        public Builder requestName(String requestName) {
            request.requestName = requestName;
            return this;
        }

        public Builder targetIdentity(String targetIdentity) {
            request.targetIdentity = targetIdentity;
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            request.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxPages(Integer maxPages) {
            request.maxPages = maxPages;
            return this;
        }

        public Builder requestSummary(String requestSummary) {
            request.requestSummary = requestSummary;
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

        public Builder budget(NoonPullRequestBudget budget) {
            request.budget = budget;
            return this;
        }

        public Builder resumePage(Integer resumePage) {
            request.resumePage = resumePage;
            return this;
        }

        public Builder resumeFromTask(NoonPullTaskRecord task) {
            if (task != null) {
                request.resumePage = parseResumePage(task.getNextResumePosition());
                request.initialProcessedItemCount = task.getProcessedItemCount();
                request.initialRequestCount = task.getRequestCount();
            }
            return this;
        }

        public NoonInterfacePullRequest build() {
            return request;
        }

        private Integer parseResumePage(String resumePosition) {
            if (!StringUtils.hasText(resumePosition) || !resumePosition.startsWith("page:")) {
                return 1;
            }
            try {
                return Integer.parseInt(resumePosition.substring("page:".length()));
            } catch (NumberFormatException exception) {
                return 1;
            }
        }
    }
}
