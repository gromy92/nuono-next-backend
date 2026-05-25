package com.nuono.next.noonpull;

public class NoonProductListInitializationCommand {
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private NoonPullRequestBudget requestBudget;
    private String requestSummary;

    public static Builder builder() {
        return new Builder();
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public NoonPullRequestBudget getRequestBudget() {
        return requestBudget;
    }

    public String getRequestSummary() {
        return requestSummary;
    }

    public static class Builder {
        private final NoonProductListInitializationCommand command = new NoonProductListInitializationCommand();

        public Builder ownerUserId(Long ownerUserId) {
            command.ownerUserId = ownerUserId;
            return this;
        }

        public Builder projectCode(String projectCode) {
            command.projectCode = projectCode;
            return this;
        }

        public Builder projectName(String projectName) {
            command.projectName = projectName;
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

        public Builder requestBudget(NoonPullRequestBudget requestBudget) {
            command.requestBudget = requestBudget;
            return this;
        }

        public Builder requestSummary(String requestSummary) {
            command.requestSummary = requestSummary;
            return this;
        }

        public NoonProductListInitializationCommand build() {
            return command;
        }
    }
}
