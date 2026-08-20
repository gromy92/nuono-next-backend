package com.nuono.next.procurement.aliorder;

/** Execution-mode-neutral command behind the owner-scoped immediate-sync endpoint. */
public interface Ali1688HistoricalOrderManualSync {

    boolean request(Long ownerUserId, Long authorizationId, Long operatorUserId);
}
