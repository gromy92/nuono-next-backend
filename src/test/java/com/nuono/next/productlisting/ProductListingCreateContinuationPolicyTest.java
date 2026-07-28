package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingCreateContinuationPolicyTest {
    private static final long TASK_ID = 20002L;
    private static final String STORE_CODE = "STR245027-NAE";
    private static final String PARTNER_SKU = "NN-TEST-PSKU";

    @Test
    void successfulTaskAndUnrelatedCreateFailureCannotEnterContinuation() {
        ProductListingNoonWriteStepResult created =
                step("create_product", "succeeded", null, "skuParent=ZPARENT;pskuCode=PCODE");
        ProductListingNoonWriteResult projectionFailure =
                ProductListingNoonWriteResult.succeeded(List.of(created));
        ProductListingNoonWriteStepResult partnerExists =
                step("create_product", "failed", "noon_write_failed", null);
        ProductListingNoonWriteResult unrelatedFailure = ProductListingNoonWriteResult.failed(
                "validation", "partner_sku_already_exists", "already exists", List.of(partnerExists)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> requireRecoverable(projectionFailure)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> requireRecoverable(unrelatedFailure)
        );
    }

    @Test
    void uncertainCreateRequiresPersistedReadOnlyResolutionBeforeContinuationWrite() {
        ProductListingNoonWriteStepResult uncertain =
                step("create_product", "failed", "noon_create_outcome_unknown", null);
        uncertain.setWriteMayHaveOccurred(true);
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        steps.add(uncertain);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write", "noon_create_outcome_unknown", "unknown", steps
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> requireRecoverable(result)
        );
        steps.add(0, absenceProof(TASK_ID, STORE_CODE, PARTNER_SKU));
        assertDoesNotThrow(
                () -> requireRecoverable(result)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> requireContinuationWriteAllowed(result)
        );

        steps.add(step(
                "resolve_create_reference",
                "succeeded",
                null,
                "skuParent=ZPARENT;pskuCode=PCODE"
        ));
        assertDoesNotThrow(
                () -> requireContinuationWriteAllowed(result)
        );
    }

    @Test
    void absenceProofFromAnotherTaskCannotUnlockResolution() {
        ProductListingNoonWriteResult result =
                uncertainResult(absenceProof(99999L, STORE_CODE, PARTNER_SKU));

        assertThrows(IllegalArgumentException.class, () -> requireRecoverable(result));
    }

    @Test
    void absenceProofMustMatchCurrentStoreAndPartnerSku() {
        ProductListingNoonWriteResult wrongStore =
                uncertainResult(absenceProof(TASK_ID, "STR-OTHER", PARTNER_SKU));
        ProductListingNoonWriteResult wrongPartnerSku =
                uncertainResult(absenceProof(TASK_ID, STORE_CODE, "OTHER-PSKU"));

        assertThrows(IllegalArgumentException.class, () -> requireRecoverable(wrongStore));
        assertThrows(IllegalArgumentException.class, () -> requireRecoverable(wrongPartnerSku));
    }

    @Test
    void absenceProofPersistedAfterUnknownCreateCannotUnlockResolution() {
        ProductListingNoonWriteStepResult uncertain = unknownCreate();
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "unknown",
                List.of(uncertain, absenceProof(TASK_ID, STORE_CODE, PARTNER_SKU))
        );

        assertThrows(IllegalArgumentException.class, () -> requireRecoverable(result));
    }

    @Test
    void partialSuccessfulCreateReferenceCannotEnterContinuation() {
        ProductListingNoonWriteStepResult partial =
                step("create_product", "succeeded", null, "skuParent=ZPARENT");
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "projection",
                        "projection_failed",
                        "projection failed",
                        List.of(partial)
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> requireContinuationWriteAllowed(result)
        );

        ProductListingNoonWriteResult empty =
                ProductListingNoonWriteResult.failed(
                        "projection",
                        "projection_failed",
                        "projection failed",
                        List.of(step(
                                "create_product",
                                "succeeded",
                                null,
                                "skuParent= ;pskuCode="
                        ))
                );
        assertThrows(
                IllegalArgumentException.class,
                () -> requireContinuationWriteAllowed(empty)
        );
    }

    @Test
    void partialResolveAndSplitCreateReferencesCannotUnlockUnknownCreate() {
        ProductListingNoonWriteStepResult unknown = unknownCreate();
        ProductListingNoonWriteStepResult partialResolve = step(
                "resolve_create_reference",
                "succeeded",
                null,
                "pskuCode=PCODE"
        );
        ProductListingNoonWriteResult partial = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "unknown",
                List.of(
                        absenceProof(TASK_ID, STORE_CODE, PARTNER_SKU),
                        unknown,
                        partialResolve
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> requireContinuationWriteAllowed(partial)
        );

        ProductListingNoonWriteStepResult splitCreate = step(
                "create_product",
                "succeeded",
                null,
                "skuParent=ZPARENT"
        );
        ProductListingNoonWriteResult split = ProductListingNoonWriteResult.failed(
                "projection",
                "projection_failed",
                "projection failed",
                List.of(splitCreate, partialResolve)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> requireContinuationWriteAllowed(split)
        );
    }

    private void requireRecoverable(ProductListingNoonWriteResult result) {
        ProductListingCreateContinuationPolicy.requireRecoverable(
                result, TASK_ID, STORE_CODE, PARTNER_SKU);
    }

    private void requireContinuationWriteAllowed(ProductListingNoonWriteResult result) {
        ProductListingCreateContinuationPolicy.requireContinuationWriteAllowed(
                result, TASK_ID, STORE_CODE, PARTNER_SKU);
    }

    private ProductListingNoonWriteResult uncertainResult(
            ProductListingNoonWriteStepResult absence
    ) {
        return ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "unknown",
                List.of(absence, unknownCreate())
        );
    }

    private ProductListingNoonWriteStepResult unknownCreate() {
        ProductListingNoonWriteStepResult uncertain =
                step("create_product", "failed", "noon_create_outcome_unknown", null);
        uncertain.setWriteMayHaveOccurred(true);
        return uncertain;
    }

    private ProductListingNoonWriteStepResult absenceProof(
            long taskId,
            String storeCode,
            String partnerSku
    ) {
        ProductListingNoonWriteStepResult absence = step(
                "pre_create_absence_verified",
                "succeeded",
                null,
                "storeCode=" + storeCode
                        + ";partnerSku=" + partnerSku
                        + ";realRunTaskId=" + taskId
                        + ";checkedAt=2026-07-27T10:15:30+08:00"
        );
        absence.setWriteMayHaveOccurred(false);
        return absence;
    }

    private ProductListingNoonWriteStepResult step(
            String key,
            String status,
            String failureCode,
            String externalReference
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(key);
        step.setStatus(status);
        step.setFailureCode(failureCode);
        step.setExternalReference(externalReference);
        return step;
    }
}
