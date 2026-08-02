package com.nuono.next.noonauth;

/**
 * Defines what may happen to a durable business task after its Project cookie is recovered.
 */
public enum NoonAuthResumePolicy {
    /** The provider write is proven not to have started, so the same task may resume automatically. */
    AUTO_RESUME,
    /** A provider write may have started, so only a readback/manual decision may follow recovery. */
    READBACK_REQUIRED,
    /** No durable source task exists; recovery only repairs the Project binding. */
    NONE
}
