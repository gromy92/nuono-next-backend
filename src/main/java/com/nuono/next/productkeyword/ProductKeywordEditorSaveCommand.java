package com.nuono.next.productkeyword;

import java.util.ArrayList;
import java.util.List;

public class ProductKeywordEditorSaveCommand {
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private List<Row> rows = new ArrayList<>();
    private List<Long> deletedKeywordIds = new ArrayList<>();

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

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }

    public List<Long> getDeletedKeywordIds() {
        return deletedKeywordIds;
    }

    public void setDeletedKeywordIds(List<Long> deletedKeywordIds) {
        this.deletedKeywordIds = deletedKeywordIds == null ? new ArrayList<>() : new ArrayList<>(deletedKeywordIds);
    }

    public static class Row {
        private Long keywordId;
        private String keyword;
        private boolean saveKeyword;
        private List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources = new ArrayList<>();

        public Long getKeywordId() {
            return keywordId;
        }

        public void setKeywordId(Long keywordId) {
            this.keywordId = keywordId;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public boolean isSaveKeyword() {
            return saveKeyword;
        }

        public void setSaveKeyword(boolean saveKeyword) {
            this.saveKeyword = saveKeyword;
        }

        public List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> getCompetitorSources() {
            return competitorSources;
        }

        public void setCompetitorSources(List<ProductKeywordCompetitorKeywordCommand.CompetitorSource> competitorSources) {
            this.competitorSources = competitorSources == null ? new ArrayList<>() : new ArrayList<>(competitorSources);
        }
    }
}
