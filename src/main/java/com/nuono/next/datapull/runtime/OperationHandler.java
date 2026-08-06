package com.nuono.next.datapull.runtime;

/**
 * Deep Interface for one DP Implementation.
 *
 * <p>Each call advances one bounded external action or one short local
 * transaction and returns the durable state transition requested from the
 * runtime. The context type remains owned by the caller/Implementation, so
 * this core Module has no Spring or persistence dependency.</p>
 */
public interface OperationHandler<C> {

    OperationCode operationCode();

    AdvanceResult advance(C context);
}
