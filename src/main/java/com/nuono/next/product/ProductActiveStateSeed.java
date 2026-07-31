package com.nuono.next.product;

public abstract class ProductActiveStateSeed implements ProductActiveStateEvidenceCarrier {
    private final ProductActiveStateEvidence activeStateEvidence = new ProductActiveStateEvidence();

    @Override
    public final ProductActiveStateEvidence activeStateEvidence() {
        return activeStateEvidence;
    }
}
