package com.nuono.next.competitoranalysis;

public class CompetitorKeywordRefreshContext {
    private Long taskId;
    private Long searchRunId;
    private Long keywordRunId;
    private CompetitorWatchProductRow watchProduct;
    private CompetitorKeywordRow keyword;
    private Long actorUserId;
    private Runnable leaseValidator = () -> { };

    public static Builder builder() {
        return new Builder();
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
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
    public void validateLease() { leaseValidator.run(); }

    public static final class Builder {
        private final CompetitorKeywordRefreshContext context = new CompetitorKeywordRefreshContext();

        public Builder taskId(Long taskId) {
            context.taskId = taskId;
            return this;
        }

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

        public Builder leaseValidator(Runnable leaseValidator) {
            context.leaseValidator = leaseValidator == null ? () -> { } : leaseValidator;
            return this;
        }

        public CompetitorKeywordRefreshContext build() {
            return context;
        }
    }
}
