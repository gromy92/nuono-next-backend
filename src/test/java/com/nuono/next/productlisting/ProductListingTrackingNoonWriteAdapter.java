package com.nuono.next.productlisting;

import java.util.List;

class ProductListingTrackingNoonWriteAdapter implements ProductListingNoonWriteAdapter {

    private final ProductListingNoonWriteResult result;
    private final ProductListingNoonWriteResult continuationResult;
    private final ProductListingNoonWriteStepResult readBackStep;
    private int callCount;
    private int continueAfterCreateCallCount;
    private int verifyReadBackCallCount;
    private int resolveCreateReferenceCallCount;
    private ProductListingNoonWriteStepResult createReferenceStep;
    private ProductListingNoonWriteRequest lastRequest;
    private String lastContinueSkuParent;
    private String lastContinuePskuCode;
    private String lastReadBackSkuParent;
    private String lastReadBackPskuCode;

    ProductListingTrackingNoonWriteAdapter(ProductListingNoonWriteResult result) {
        this(result, null, null);
    }

    ProductListingTrackingNoonWriteAdapter(
            ProductListingNoonWriteResult result,
            ProductListingNoonWriteStepResult readBackStep
    ) {
        this(result, null, readBackStep);
    }

    ProductListingTrackingNoonWriteAdapter(
            ProductListingNoonWriteResult result,
            ProductListingNoonWriteResult continuationResult,
            ProductListingNoonWriteStepResult readBackStep
    ) {
        this.result = result;
        this.continuationResult = continuationResult;
        this.readBackStep = readBackStep;
    }

    @Override
    public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
        callCount++;
        lastRequest = request;
        return result;
    }

    @Override
    public ProductListingNoonWriteResult continueAfterCreate(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode
    ) {
        continueAfterCreateCallCount++;
        lastRequest = request;
        lastContinueSkuParent = skuParent;
        lastContinuePskuCode = pskuCode;
        return continuationResult;
    }

    @Override
    public ProductListingNoonWriteStepResult resolveCreateReference(ProductListingNoonWriteRequest request) {
        resolveCreateReferenceCallCount++;
        lastRequest = request;
        return createReferenceStep;
    }

    @Override
    public ProductListingNoonWriteStepResult verifyReadBack(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode,
            List<String> expectedImageValues
    ) {
        verifyReadBackCallCount++;
        lastRequest = request;
        lastReadBackSkuParent = skuParent;
        lastReadBackPskuCode = pskuCode;
        return readBackStep;
    }

    int callCount() {
        return callCount;
    }

    int continueAfterCreateCallCount() {
        return continueAfterCreateCallCount;
    }

    int verifyReadBackCallCount() {
        return verifyReadBackCallCount;
    }

    int resolveCreateReferenceCallCount() {
        return resolveCreateReferenceCallCount;
    }

ProductListingTrackingNoonWriteAdapter withCreateReferenceStep(ProductListingNoonWriteStepResult step) {
        this.createReferenceStep = step;
        return this;
    }

    String lastContinueSkuParent() {
        return lastContinueSkuParent;
    }

    String lastContinuePskuCode() {
        return lastContinuePskuCode;
    }

    String lastReadBackSkuParent() {
        return lastReadBackSkuParent;
    }

    String lastReadBackPskuCode() {
        return lastReadBackPskuCode;
    }

    ProductListingNoonWriteRequest lastRequest() {
        return lastRequest;
    }
}
