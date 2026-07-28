package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingTaskLeaseMapper;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

final class ProductListingNoonCheckpoint {
    private static final String WRITE_NOT_STARTED =
            "real_run_write_not_started";

    private ProductListingNoonCheckpoint() {
    }

    static void bind(
            ProductListingNoonWriteRequest request,
            ProductListingTaskLease lease,
            ObjectMapper objectMapper
    ) {
        bind(request, lease, objectMapper, UnaryOperator.identity());
    }

    static void bind(
            ProductListingNoonWriteRequest request,
            ProductListingTaskLease lease,
            ObjectMapper objectMapper,
            UnaryOperator<ProductListingNoonWriteResult> resultMapper
    ) {
        request.setNoonResultCheckpoint(result ->
                persist(
                        lease,
                        objectMapper,
                        resultMapper.apply(result)
                ));
    }

    static void persist(
            ProductListingTaskLease lease,
            ObjectMapper objectMapper,
            ProductListingNoonWriteResult result
    ) {
        try {
            lease.checkpointNoonResultOrThrow(
                    objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize product listing Noon checkpoint.",
                    exception
            );
        }
    }

    static int claim(
            ProductListingTaskLeaseMapper mapper,
            ObjectMapper objectMapper,
            ProductListingTaskRecord task,
            LocalDateTime startedAt,
            String partnerSku
    ) {
        return mapper.markTaskRunning(
                task.getId(),
                startedAt,
                writeNotStartedJson(objectMapper, task, partnerSku)
        );
    }

    static String writeNotStartedJson(
            ObjectMapper objectMapper,
            ProductListingTaskRecord task,
            String partnerSku
    ) {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey(WRITE_NOT_STARTED);
        step.setStatus("succeeded");
        step.setWriteMayHaveOccurred(false);
        step.setExternalReference(
                "storeCode=" + normalized(task.getStoreCode())
                        + ";partnerSku=" + normalized(partnerSku)
                        + ";realRunTaskId=" + task.getId()
        );
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "recovery",
                        WRITE_NOT_STARTED,
                        "No Noon write had started at this checkpoint.",
                        List.of(step)
                );
        result.setWriteMayHaveOccurred(false);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize product listing pre-write checkpoint.",
                    exception
            );
        }
    }

    static ProductListingNoonWriteStepResult pendingCreateStep() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("create_product");
        step.setStatus("failed");
        step.setFailureCode("noon_create_outcome_unknown");
        step.setFailureMessage(
                "Noon create request is about to start; "
                        + "verify the result before retrying."
        );
        step.setWriteMayHaveOccurred(true);
        return step;
    }

    static void persistUnknownCreateIntent(
            ProductListingNoonWriteRequest request,
            List<ProductListingNoonWriteStepResult> steps
    ) {
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "recovery",
                        "noon_create_outcome_unknown",
                        "Noon create outcome requires read-only verification.",
                        steps
                );
        result.setWriteMayHaveOccurred(true);
        request.checkpointNoonResultOrThrow(result);
    }

    static void persistWriteProgress(
            ProductListingNoonWriteRequest request,
            List<ProductListingNoonWriteStepResult> steps
    ) {
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "recovery",
                        "real_run_in_progress",
                        "Noon write is in progress.",
                        steps
                );
        result.setWriteMayHaveOccurred(true);
        request.checkpointNoonResultOrThrow(result);
    }

    static ProductListingNoonWriteResult mappedFailure(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            RuntimeException exception,
            List<ProductListingNoonWriteStepResult> steps,
            String fallbackMessage,
            ProductListingWriteAuthRecovery writeAuthRecovery,
            ProductListingNoonWriteFailureSupport failureSupport
    ) {
        ProductListingNoonWriteResult mapped =
                writeAuthRecovery.mapFailure(
                        request,
                        binding,
                        exception,
                        steps,
                        fallbackMessage
                );
        if (ProductListingWriteAuthRecovery.FAILURE_CODE.equals(
                mapped.getFailureCode()) || !steps.isEmpty()) {
            return mapped;
        }
        return failureSupport.preCreateFailure(exception);
    }

    static ProductListingNoonWriteResult mappedPreWriteFailure(
            ProductListingNoonWriteRequest request,
            NoonPullStoreBinding binding,
            RuntimeException exception,
            List<ProductListingNoonWriteStepResult> evidence,
            ProductListingWriteAuthRecovery writeAuthRecovery,
            ProductListingNoonWriteFailureSupport failureSupport
    ) {
        List<ProductListingNoonWriteStepResult> failureSteps =
                new ArrayList<>();
        ProductListingNoonWriteResult result =
                writeAuthRecovery.mapFailure(
                        request,
                        binding,
                        exception,
                        failureSteps,
                        "Product listing Noon write preflight failed."
                );
        if (!ProductListingWriteAuthRecovery.FAILURE_CODE.equals(
                result.getFailureCode())) {
            result = failureSupport.preCreateFailure(exception);
        }
        List<ProductListingNoonWriteStepResult> combined =
                new ArrayList<>(evidence);
        combined.addAll(result.getSteps());
        result.setSteps(combined);
        result.setWriteMayHaveOccurred(false);
        return result;
    }

    static void markCreateOutcomeUnknown(
            ProductListingNoonWriteStepResult step,
            String failureMessage
    ) {
        if (step == null
                || !"create_product".equals(step.getStepKey())) {
            return;
        }
        step.setStatus("failed");
        step.setFailureCode("noon_create_outcome_unknown");
        step.setFailureMessage(failureMessage);
        step.setExternalReference(null);
        step.setWriteMayHaveOccurred(true);
    }

    static void rethrowIfLeaseLost(RuntimeException exception) {
        if (ProductListingNoonWriteRequest
                .isExecutionLeaseLost(exception)) {
            throw exception;
        }
    }

    static ProductListingNoonWriteResult finishReadBack(
            ProductListingNoonWriteRequest request,
            List<ProductListingNoonWriteStepResult> steps,
            ProductListingNoonWriteStepResult readBack
    ) {
        steps.add(readBack);
        if (!"succeeded".equals(readBack.getStatus())) {
            boolean authorizationRecovery =
                    ProductListingWriteAuthRecovery.FAILURE_CODE
                            .equals(readBack.getFailureCode());
            ProductListingNoonWriteResult failure =
                    ProductListingNoonWriteResult.failed(
                            authorizationRecovery
                                    ? "authorization"
                                    : "noon_readback",
                            readBack.getFailureCode(),
                            readBack.getFailureMessage(),
                            steps
                    );
            if (authorizationRecovery) {
                failure.setRecoveryId(readBack.getRecoveryId());
                failure.setWriteMayHaveOccurred(
                        readBack.getWriteMayHaveOccurred());
            }
            request.checkpointNoonResultOrThrow(failure);
            return failure;
        }
        ProductListingNoonWriteResult success =
                ProductListingNoonWriteResult.succeeded(steps);
        request.checkpointNoonResultOrThrow(success);
        return success;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Steps
            extends ArrayList<ProductListingNoonWriteStepResult> {
        private static final long serialVersionUID = 1L;

        private final ProductListingNoonWriteRequest request;
        private boolean checkpointing;

        Steps(
                ProductListingNoonWriteRequest request,
                boolean checkpointing
        ) {
            this.request = request;
            this.checkpointing = checkpointing;
        }

        void enableCheckpointing() {
            checkpointing = true;
        }

        @Override
        public boolean add(ProductListingNoonWriteStepResult step) {
            boolean added = super.add(step);
            if (added && checkpointing) {
                persistWriteProgress(request, this);
            }
            return added;
        }
    }
}
