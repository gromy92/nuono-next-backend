package com.nuono.next.competitoranalysis.noon;

public class NoonSearchRequest {
    private String siteCode;
    private String locale;
    private String keyword;
    private Integer limit;

    public static Builder builder() {
        return new Builder();
    }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public static final class Builder {
        private final NoonSearchRequest request = new NoonSearchRequest();

        public Builder siteCode(String siteCode) {
            request.siteCode = siteCode;
            return this;
        }

        public Builder locale(String locale) {
            request.locale = locale;
            return this;
        }

        public Builder keyword(String keyword) {
            request.keyword = keyword;
            return this;
        }

        public Builder limit(Integer limit) {
            request.limit = limit;
            return this;
        }

        public NoonSearchRequest build() {
            return request;
        }
    }
}
