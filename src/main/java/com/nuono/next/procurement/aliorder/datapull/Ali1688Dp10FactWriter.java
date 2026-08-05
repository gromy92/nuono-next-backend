package com.nuono.next.procurement.aliorder.datapull;

/** Fenced bounded fact Seam; each call performs one short durable action. */
public interface Ali1688Dp10FactWriter {

    Ali1688Dp10FactAdvance advance(Ali1688Dp10ApplyCommand command);
}
