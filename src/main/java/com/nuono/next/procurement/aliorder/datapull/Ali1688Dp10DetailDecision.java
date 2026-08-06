package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;

/** A detail response reduced to one durable business decision. */
final class Ali1688Dp10DetailDecision {
    private final Ali1688Dp10ItemState state;
    private final Ali1688HistoricalOrderProvider.OrderSnapshot order;
    private final String sanitizedCode;

    Ali1688Dp10DetailDecision(
            Ali1688Dp10ItemState state,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            String sanitizedCode
    ) {
        this.state = state;
        this.order = order;
        this.sanitizedCode = sanitizedCode;
    }

    Ali1688Dp10ItemState getState() { return state; }
    Ali1688HistoricalOrderProvider.OrderSnapshot getOrder() { return order; }
    String getSanitizedCode() { return sanitizedCode; }
}
