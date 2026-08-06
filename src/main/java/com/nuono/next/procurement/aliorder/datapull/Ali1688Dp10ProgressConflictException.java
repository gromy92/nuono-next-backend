package com.nuono.next.procurement.aliorder.datapull;

/** Signals that another completed window advanced the same scope before this end page committed. */
public final class Ali1688Dp10ProgressConflictException extends RuntimeException {

    public Ali1688Dp10ProgressConflictException() {
        super("DP10_PROGRESS_CONFLICT");
    }
}
