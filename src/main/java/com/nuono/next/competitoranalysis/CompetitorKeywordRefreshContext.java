package com.nuono.next.competitoranalysis;

public class CompetitorKeywordRefreshContext {
    private Long searchRunId;
    private Long keywordRunId;
    private CompetitorWatchProductRow watchProduct;
    private CompetitorKeywordRow keyword;
    private Long actorUserId;

    public static Builder builder() {
        return new Builder();
    }

    public Long getSearchRunId() { return searchRunId; }
    public void setSearchRunId(Long searchRunId) { this.searchRunId = searchRunId; }
    public Long getKeywordRunId() { return keywordRunId; }
    public void setKeywordRunId(Long keywordRunId) { this.keywordRunId = keywordRunId; }
    public CompetitorWatchProductRow getWatchProduct() { return watchProduct; }
    public void setWatchProduct(CompetitorWatchProductRow watchProduct) { this.watchProduct = watchProduct; }
    public CompetitorKeywordRow getKeyword() { return keyword; }
    public void setKeyword(CompetitorKeywordRow keyword) { this.keyword = keyword; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public static final class Builder {
        private final CompetitorKeywordRefreshContext context = new CompetitorKeywordRefreshContext();

        public Builder searchRunId(Long searchRunId) {
            context.searchRunId = searchRunId;
            return this;
        }

        public Builder keywordRunId(Long keywordRunId) {
            context.keywordRunId = keywordRunId;
            return this;
        }

        public Builder watchProduct(CompetitorWatchProductRow watchProduct) {
            context.watchProduct = watchProduct;
            return this;
        }

        public Builder keyword(CompetitorKeywordRow keyword) {
            context.keyword = keyword;
            return this;
        }

        public Builder actorUserId(Long actorUserId) {
            context.actorUserId = actorUserId;
            return this;
        }

        public CompetitorKeywordRefreshContext build() {
            return context;
        }
    }
}
