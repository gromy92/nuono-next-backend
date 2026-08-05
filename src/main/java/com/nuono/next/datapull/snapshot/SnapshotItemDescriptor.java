package com.nuono.next.datapull.snapshot;

/** Stable identity and content fingerprint for items kept in deterministic input order. */
public interface SnapshotItemDescriptor<T> {
    String stableIdentity(T item);

    String stableContentFingerprint(T item);

    /**
     * Whether this row passed the complete per-item fact contract and may reserve its identity.
     * Technical presence evidence can return false so a later valid fact is not discarded merely
     * because an earlier business-defective row exposed the same identity.
     */
    default boolean isValidatedIdentityCandidate(T item) {
        return true;
    }

    /** Whether this row permits missing identities to be retired when the generation is sealed. */
    default boolean isAbsenceReconciliationSafe(T item) {
        return true;
    }
}
