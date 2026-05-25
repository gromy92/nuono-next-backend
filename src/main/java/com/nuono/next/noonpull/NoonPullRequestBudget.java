package com.nuono.next.noonpull;

public class NoonPullRequestBudget {
    private Integer maxPagesPerRun;
    private Integer maxProductsPerRun;
    private Integer maxDetailFetchesPerRun;
    private Integer maxRequestsPerRun;
    private Integer cooldownSeconds;
    private Integer concurrencyLimit;

    public static Builder builder() {
        return new Builder();
    }

    public Integer getMaxPagesPerRun() {
        return maxPagesPerRun;
    }

    public Integer getMaxProductsPerRun() {
        return maxProductsPerRun;
    }

    public Integer getMaxDetailFetchesPerRun() {
        return maxDetailFetchesPerRun;
    }

    public Integer getMaxRequestsPerRun() {
        return maxRequestsPerRun;
    }

    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    public Integer getConcurrencyLimit() {
        return concurrencyLimit;
    }

    public static class Builder {
        private final NoonPullRequestBudget budget = new NoonPullRequestBudget();

        public Builder maxPagesPerRun(Integer maxPagesPerRun) {
            budget.maxPagesPerRun = maxPagesPerRun;
            return this;
        }

        public Builder maxProductsPerRun(Integer maxProductsPerRun) {
            budget.maxProductsPerRun = maxProductsPerRun;
            return this;
        }

        public Builder maxDetailFetchesPerRun(Integer maxDetailFetchesPerRun) {
            budget.maxDetailFetchesPerRun = maxDetailFetchesPerRun;
            return this;
        }

        public Builder maxRequestsPerRun(Integer maxRequestsPerRun) {
            budget.maxRequestsPerRun = maxRequestsPerRun;
            return this;
        }

        public Builder cooldownSeconds(Integer cooldownSeconds) {
            budget.cooldownSeconds = cooldownSeconds;
            return this;
        }

        public Builder concurrencyLimit(Integer concurrencyLimit) {
            budget.concurrencyLimit = concurrencyLimit;
            return this;
        }

        public NoonPullRequestBudget build() {
            return budget;
        }
    }
}
