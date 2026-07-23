package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NoonProductListApplyCommand {
    private Long ownerUserId;
    private String projectCode;
    private String projectName;
    private String storeCode;
    private String siteCode;
    private String sourceBatchId;
    private boolean automaticDetailBackfill;
    private List<Map<String, Object>> items = new ArrayList<>();

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

    public String getSourceBatchId() {
        return sourceBatchId;
    }

    public boolean isAutomaticDetailBackfill() {
        return automaticDetailBackfill;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public static class Builder {
        private final NoonProductListApplyCommand command = new NoonProductListApplyCommand();

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

        public Builder sourceBatchId(String sourceBatchId) {
            command.sourceBatchId = sourceBatchId;
            return this;
        }

        public Builder automaticDetailBackfill(boolean automaticDetailBackfill) {
            command.automaticDetailBackfill = automaticDetailBackfill;
            return this;
        }

        public Builder items(List<? extends Map<String, ?>> items) {
            command.items = NoonInterfacePullPage.copyItems(items);
            return this;
        }

        public NoonProductListApplyCommand build() {
            return command;
        }
    }
}
