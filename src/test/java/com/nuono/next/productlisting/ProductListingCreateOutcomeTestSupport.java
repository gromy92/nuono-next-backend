package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

abstract class ProductListingCreateOutcomeTestSupport {

    protected ProductListingTaskView taskView() {
        ProductListingTaskView view = new ProductListingTaskView();
        view.setTaskId(20002L);
        view.setDraftId(10001L);
        view.setOwnerUserId(10002L);
        view.setStoreCode("STR245027-NAE");
        view.setMode("REAL_RUN");
        view.setStatus("written_verify_failed");
        view.setFailureCode("noon_create_outcome_unknown");
        return view;
    }

    protected ProductListingTaskRecord uncertainTaskRecord(ObjectMapper objectMapper) throws Exception {
        ProductListingNoonWriteStepResult absence = new ProductListingNoonWriteStepResult();
        absence.setStepKey("pre_create_absence_verified");
        absence.setStatus("succeeded");
        absence.setExternalReference(
                "storeCode=STR245027-NAE;partnerSku=NN-TEST-PSKU;realRunTaskId=20002"
                        + ";checkedAt=2026-07-27T10:15:30+08:00");
        absence.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        create.setWriteMayHaveOccurred(true);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "unknown",
                List.of(absence, create)
        );
        result.setWriteMayHaveOccurred(true);
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(20002L);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode("REAL_RUN");
        record.setStatus("written_verify_failed");
        record.setFailureCode("noon_create_outcome_unknown");
        record.setInputSnapshotJson("{\"psku\":\"NN-TEST-PSKU\"}");
        record.setValidationJson("[]");
        record.setConfirmationJson("{\"confirmRealNoonWrite\":true}");
        record.setNoonResultJson(objectMapper.writeValueAsString(result));
        record.setCompletedAt(LocalDateTime.now());
        return record;
    }

    protected ProductListingNoonWriteStepResult foundReference() {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        return step;
    }

    protected ProductListingNoonWriteStepResult notFoundReference() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_create_reference_not_found");
        return step;
    }

    protected ProductListingNoonWriteStepResult authenticationRequiredReference() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_auth_required");
        return step;
    }

    protected ProductListingNoonWriteResult withReliableNotFoundSteps(
            ProductListingNoonWriteResult original,
            List<LocalDateTime> checkedAt
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>(
                original.getSteps()
        );
        for (int index = 0; index < checkedAt.size(); index++) {
            ProductListingNoonWriteStepResult step = notFoundReference();
            step.setExternalReference(
                    "lookupAttempt=" + (index + 1)
                            + ";lookupCheckedAt=" + checkedAt.get(index)
            );
            steps.add(step);
        }
        return ProductListingNoonWriteResult.failed(
                original.getFailureCategory(),
                original.getFailureCode(),
                original.getFailureMessage(),
                steps
        );
    }
}
