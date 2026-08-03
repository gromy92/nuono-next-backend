package com.nuono.next.product;

public interface ProductActiveStateEvidenceCarrier {
    ProductActiveStateEvidence activeStateEvidence();

    default String getActiveStateSource() {
        return activeStateEvidence().getSource();
    }

    default void setActiveStateSource(String source) {
        activeStateEvidence().setSource(source);
    }

    default String getActiveStateSyncedAt() {
        return activeStateEvidence().getSyncedAt();
    }

    default void setActiveStateSyncedAt(String syncedAt) {
        activeStateEvidence().setSyncedAt(syncedAt);
    }
}
