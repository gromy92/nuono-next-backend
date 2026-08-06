package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;

/** Decoded durable order-stage row. */
public final class Ali1688Dp10StagedOrder {
    private final int ordinal;
    private final String providerOrderNo;
    private final Ali1688Dp10ItemState state;
    private final String sanitizedCode;
    private final Ali1688HistoricalOrderProvider.OrderSnapshot order;

    Ali1688Dp10StagedOrder(
            int ordinal,
            String providerOrderNo,
            Ali1688Dp10ItemState state,
            String sanitizedCode,
            Ali1688HistoricalOrderProvider.OrderSnapshot order
    ) {
        this.ordinal = ordinal;
        this.providerOrderNo = providerOrderNo;
        this.state = state;
        this.sanitizedCode = sanitizedCode;
        this.order = order;
    }

    public int getOrdinal() { return ordinal; }
    public String getProviderOrderNo() { return providerOrderNo; }
    public Ali1688Dp10ItemState getState() { return state; }
    public String getSanitizedCode() { return sanitizedCode; }
    public Ali1688HistoricalOrderProvider.OrderSnapshot getOrder() { return order; }
}
