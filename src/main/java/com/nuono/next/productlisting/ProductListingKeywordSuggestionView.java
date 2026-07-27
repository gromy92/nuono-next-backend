package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingKeywordSuggestionView {
    private Long draftId;
    private List<Item> items = new ArrayList<>();

    public static ProductListingKeywordSuggestionView empty() {
        return new ProductListingKeywordSuggestionView();
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    public static class Item {
        private String keyword;
        private String keywordNorm;
        private String locale;

        public Item() {
        }

        public Item(String keyword, String keywordNorm, String locale) {
            this.keyword = keyword;
            this.keywordNorm = keywordNorm;
            this.locale = locale;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getKeywordNorm() {
            return keywordNorm;
        }

        public void setKeywordNorm(String keywordNorm) {
            this.keywordNorm = keywordNorm;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }
    }
}
