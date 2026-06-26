package com.nuono.next.intransit;

import java.util.Collections;
import java.util.List;

public final class InTransitSuperSearchCommands {

    private InTransitSuperSearchCommands() {
    }

    public static class InTransitSuperSearchQuery {
        private Long ownerUserId;
        private String keyword;
        private String projectCode;
        private boolean includeHistory;
        private Integer limit;
        private boolean accessScopeRestricted;
        private List<InTransitStoreSiteScope> allowedStoreSites = Collections.emptyList();

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(String projectCode) {
            this.projectCode = projectCode;
        }

        public boolean isIncludeHistory() {
            return includeHistory;
        }

        public void setIncludeHistory(boolean includeHistory) {
            this.includeHistory = includeHistory;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public boolean isAccessScopeRestricted() {
            return accessScopeRestricted;
        }

        public void setAccessScopeRestricted(boolean accessScopeRestricted) {
            this.accessScopeRestricted = accessScopeRestricted;
        }

        public List<InTransitStoreSiteScope> getAllowedStoreSites() {
            return allowedStoreSites;
        }

        public void setAllowedStoreSites(List<InTransitStoreSiteScope> allowedStoreSites) {
            this.allowedStoreSites = allowedStoreSites == null ? Collections.emptyList() : allowedStoreSites;
        }
    }
}
