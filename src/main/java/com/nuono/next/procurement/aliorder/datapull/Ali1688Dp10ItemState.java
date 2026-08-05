package com.nuono.next.procurement.aliorder.datapull;

/** Durable per-order state inside one DP-10 list page. */
public enum Ali1688Dp10ItemState {
    PENDING_DETAIL,
    COMPLETE,
    SKIP_BUSINESS_ITEM,
    SKIP_NOT_FOUND,
    SKIP_LATER_IDENTITY_CONFLICT
}
