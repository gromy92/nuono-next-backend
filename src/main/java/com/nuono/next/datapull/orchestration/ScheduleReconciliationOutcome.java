package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Typed result for one schedule-reconciliation pass, isolated by DP operation. */
public final class ScheduleReconciliationOutcome {

    public static final String SANITIZED_FAILURE_CODE = "SCHEDULE_RECONCILIATION_FAILED";

    private final List<OperationOutcome> operations;
    private final List<DataPullTask> reconciledTasks;

    ScheduleReconciliationOutcome(
            List<OperationOutcome> operations,
            List<DataPullTask> reconciledTasks
    ) {
        this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        this.reconciledTasks = List.copyOf(Objects.requireNonNull(
                reconciledTasks,
                "reconciledTasks"
        ));
        Set<OperationCode> identities = new HashSet<>();
        for (OperationOutcome operation : this.operations) {
            OperationOutcome value = Objects.requireNonNull(operation, "operation outcome");
            if (!identities.add(value.getOperationCode())) {
                throw new IllegalArgumentException("duplicate reconciliation operation outcome");
            }
        }
        int declaredTaskCount = this.operations.stream()
                .mapToInt(OperationOutcome::getReconciledTaskCount)
                .sum();
        if (declaredTaskCount != this.reconciledTasks.size()) {
            throw new IllegalArgumentException("operation task counts do not match reconciled tasks");
        }
    }

    public List<OperationOutcome> getOperations() {
        return operations;
    }

    public List<DataPullTask> getReconciledTasks() {
        return reconciledTasks;
    }

    public int getReconciledTaskCount() {
        return reconciledTasks.size();
    }

    public boolean hasFailures() {
        return operations.stream().anyMatch(OperationOutcome::isFailed);
    }

    List<DataPullTask> requireAllOperationsSuccessful() {
        List<String> failed = operations.stream()
                .filter(OperationOutcome::isFailed)
                .map((outcome) -> outcome.getOperationCode().name())
                .collect(Collectors.toCollection(ArrayList::new));
        if (!failed.isEmpty()) {
            throw new IllegalStateException(
                    "DP_SCHEDULE_RECONCILIATION_FAILED:" + String.join(",", failed)
            );
        }
        return reconciledTasks;
    }

    /** Sanitized per-operation status; no exception message or provider data is retained. */
    public static final class OperationOutcome {
        private final OperationCode operationCode;
        private final int reconciledTaskCount;
        private final String failureCode;

        private OperationOutcome(
                OperationCode operationCode,
                int reconciledTaskCount,
                String failureCode
        ) {
            this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
            if (reconciledTaskCount < 0) {
                throw new IllegalArgumentException("reconciledTaskCount must not be negative");
            }
            if (failureCode != null && reconciledTaskCount != 0) {
                throw new IllegalArgumentException("failed operation cannot reconcile tasks");
            }
            this.reconciledTaskCount = reconciledTaskCount;
            this.failureCode = failureCode;
        }

        static OperationOutcome succeeded(OperationCode operationCode, int taskCount) {
            return new OperationOutcome(operationCode, taskCount, null);
        }

        static OperationOutcome failed(OperationCode operationCode) {
            return new OperationOutcome(operationCode, 0, SANITIZED_FAILURE_CODE);
        }

        public OperationCode getOperationCode() {
            return operationCode;
        }

        public int getReconciledTaskCount() {
            return reconciledTaskCount;
        }

        public boolean isFailed() {
            return failureCode != null;
        }

        public String getFailureCode() {
            return failureCode;
        }
    }
}
