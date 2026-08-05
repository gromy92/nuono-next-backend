package com.nuono.next.procurement.aliorder.datapull;

/** Routing outcome from one short VERIFY/APPLY transaction; COMPLETE still requires stage cleanup. */
public enum Ali1688Dp10FactAdvance {
    VERIFYING,
    APPLYING,
    COMPLETE
}
