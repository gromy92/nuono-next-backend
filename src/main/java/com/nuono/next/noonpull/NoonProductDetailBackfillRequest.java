package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;

public class NoonProductDetailBackfillRequest {
    private List<String> allSkuParents = new ArrayList<>();
    private String openedSkuParent;
    private List<String> missingBaselineSkuParents = new ArrayList<>();
    private List<String> explicitRefreshSkuParents = new ArrayList<>();
    private List<String> publishReadbackSkuParents = new ArrayList<>();
    private List<String> prioritySkuParents = new ArrayList<>();
    private Integer maxDetailFetches;

    public static Builder builder() {
        return new Builder();
    }

    public List<String> getAllSkuParents() {
        return allSkuParents;
    }

    public String getOpenedSkuParent() {
        return openedSkuParent;
    }

    public List<String> getMissingBaselineSkuParents() {
        return missingBaselineSkuParents;
    }

    public List<String> getExplicitRefreshSkuParents() {
        return explicitRefreshSkuParents;
    }

    public List<String> getPublishReadbackSkuParents() {
        return publishReadbackSkuParents;
    }

    public List<String> getPrioritySkuParents() {
        return prioritySkuParents;
    }

    public Integer getMaxDetailFetches() {
        return maxDetailFetches == null || maxDetailFetches <= 0 ? 0 : maxDetailFetches;
    }

    public static class Builder {
        private final NoonProductDetailBackfillRequest request = new NoonProductDetailBackfillRequest();

        public Builder allSkuParents(List<String> allSkuParents) {
            request.allSkuParents = allSkuParents == null ? new ArrayList<>() : allSkuParents;
            return this;
        }

        public Builder openedSkuParent(String openedSkuParent) {
            request.openedSkuParent = openedSkuParent;
            return this;
        }

        public Builder missingBaselineSkuParents(List<String> missingBaselineSkuParents) {
            request.missingBaselineSkuParents = missingBaselineSkuParents == null ? new ArrayList<>() : missingBaselineSkuParents;
            return this;
        }

        public Builder explicitRefreshSkuParents(List<String> explicitRefreshSkuParents) {
            request.explicitRefreshSkuParents = explicitRefreshSkuParents == null ? new ArrayList<>() : explicitRefreshSkuParents;
            return this;
        }

        public Builder publishReadbackSkuParents(List<String> publishReadbackSkuParents) {
            request.publishReadbackSkuParents = publishReadbackSkuParents == null ? new ArrayList<>() : publishReadbackSkuParents;
            return this;
        }

        public Builder prioritySkuParents(List<String> prioritySkuParents) {
            request.prioritySkuParents = prioritySkuParents == null ? new ArrayList<>() : prioritySkuParents;
            return this;
        }

        public Builder maxDetailFetches(Integer maxDetailFetches) {
            request.maxDetailFetches = maxDetailFetches;
            return this;
        }

        public NoonProductDetailBackfillRequest build() {
            return request;
        }
    }
}
