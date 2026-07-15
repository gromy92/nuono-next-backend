package com.nuono.next.productkeyword;

import java.util.List;

public class ProductKeywordCompetitorKeywordCommand {
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private List<String> keywords;
    private List<CompetitorSource> competitorSources;

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<CompetitorSource> getCompetitorSources() {
        return competitorSources;
    }

    public void setCompetitorSources(List<CompetitorSource> competitorSources) {
        this.competitorSources = competitorSources;
    }

    public static class CompetitorSource {
        private String label;
        private String url;
        private String sourceText;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSourceText() {
            return sourceText;
        }

        public void setSourceText(String sourceText) {
            this.sourceText = sourceText;
        }
    }
}
