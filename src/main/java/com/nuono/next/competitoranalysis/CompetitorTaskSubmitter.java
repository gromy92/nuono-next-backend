package com.nuono.next.competitoranalysis;

@FunctionalInterface
interface CompetitorTaskSubmitter {
    void submit(String accountKey, Runnable task);
}
