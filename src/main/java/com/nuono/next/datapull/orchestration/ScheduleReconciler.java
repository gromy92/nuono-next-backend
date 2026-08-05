package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Fair facade that advances at most three persisted schedule operations per runtime tick. */
public final class ScheduleReconciler implements DataPullRuntimeReconciler {

    private static final BiConsumer<ScheduleReconciliationOutcome, Instant> NO_OBSERVER =
            (outcome, observedAt) -> { };

    private final DataPullScheduleRegistry scheduleRegistry;
    private final DataPullJobRegistry jobRegistry;
    private final LegacyScheduleReconciliationEngine legacyEngine;
    private final OperationTransaction operationTransaction;
    private final ScheduleBatchEngine boundedEngine;
    private final BiConsumer<ScheduleReconciliationOutcome, Instant> outcomeObserver;

    /**
     * Compatibility constructor that is deliberately fail-closed until the persistent Adapter is
     * wired at the composition root.
     */
    public ScheduleReconciler(
            DataPullScheduleRegistry scheduleRegistry,
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store
    ) {
        this(
                scheduleRegistry,
                jobRegistry,
                store,
                DataPullScheduleAnchorStore.failClosed(),
                DataPullScopeAdmissionStore.failClosed(),
                Supplier::get,
                null,
                NO_OBSERVER
        );
    }

    public ScheduleReconciler(
            DataPullScheduleRegistry scheduleRegistry,
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            DataPullScheduleAnchorStore anchorStore
    ) {
        this(
                scheduleRegistry,
                jobRegistry,
                store,
                anchorStore,
                DataPullScopeAdmissionStore.failClosed(),
                Supplier::get,
                null,
                NO_OBSERVER
        );
    }

    public ScheduleReconciler(
            DataPullScheduleRegistry scheduleRegistry,
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            DataPullScheduleAnchorStore anchorStore,
            DataPullScopeAdmissionStore admissionStore
    ) {
        this(
                scheduleRegistry, jobRegistry, store, anchorStore, admissionStore,
                Supplier::get, null, NO_OBSERVER
        );
    }

    ScheduleReconciler(
            DataPullScheduleRegistry scheduleRegistry,
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            DataPullScheduleAnchorStore anchorStore,
            DataPullScopeAdmissionStore admissionStore,
            OperationTransaction operationTransaction,
            ScheduleBatchEngine boundedEngine
    ) {
        this(
                scheduleRegistry, jobRegistry, store, anchorStore, admissionStore,
                operationTransaction, boundedEngine, NO_OBSERVER
        );
    }

    public ScheduleReconciler(
            DataPullScheduleRegistry scheduleRegistry,
            DataPullJobRegistry jobRegistry,
            DataPullTaskStore store,
            DataPullScheduleAnchorStore anchorStore,
            DataPullScopeAdmissionStore admissionStore,
            OperationTransaction operationTransaction,
            ScheduleBatchEngine boundedEngine,
            BiConsumer<ScheduleReconciliationOutcome, Instant> outcomeObserver
    ) {
        this.scheduleRegistry = Objects.requireNonNull(scheduleRegistry, "scheduleRegistry");
        this.jobRegistry = Objects.requireNonNull(jobRegistry, "jobRegistry");
        this.legacyEngine = new LegacyScheduleReconciliationEngine(
                store, anchorStore, admissionStore
        );
        this.operationTransaction = Objects.requireNonNull(
                operationTransaction,
                "operationTransaction"
        );
        this.boundedEngine = boundedEngine;
        this.outcomeObserver = Objects.requireNonNull(outcomeObserver, "outcomeObserver");
    }

    @Override
    public int reconcileAt(Instant observedAt) {
        Instant nonNullObservedAt = Objects.requireNonNull(observedAt, "observedAt");
        ScheduleReconciliationOutcome outcome = reconcileOperations(nonNullObservedAt);
        outcomeObserver.accept(outcome, nonNullObservedAt);
        return outcome.getReconciledTaskCount();
    }

    public List<DataPullTask> reconcile(Instant nowInclusive) {
        return reconcileOperations(nowInclusive).requireAllOperationsSuccessful();
    }

    public ScheduleReconciliationOutcome reconcileOperations(Instant nowInclusive) {
        Instant nonNullNow = Objects.requireNonNull(nowInclusive, "nowInclusive");
        if (boundedEngine != null) return reconcileBounded(nonNullNow);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(nonNullNow, ZoneOffset.UTC);
        List<DataPullTask> reconciled = new ArrayList<>();
        List<ScheduleReconciliationOutcome.OperationOutcome> outcomes = new ArrayList<>();
        DataPullRuntimeCancellation.requireActive();
        for (DataPullJob job : jobRegistry.all()) {
            DataPullRuntimeCancellation.requireActive();
            try {
                DataPullSchedule schedule = scheduleRegistry.find(job.operationCode()).orElseThrow(
                        () -> new IllegalStateException(
                                "no data-pull schedule registered for "
                                        + job.operationCode().name()
                        )
                );
                List<DataPullTask> operationTasks = operationTransaction.execute(
                        () -> legacyEngine.reconcile(job, schedule, nonNullNow, nowUtc)
                );
                reconciled.addAll(operationTasks);
                outcomes.add(ScheduleReconciliationOutcome.OperationOutcome.succeeded(
                        job.operationCode(), operationTasks.size()
                ));
            } catch (RuntimeException invalidOperationCohort) {
                DataPullRuntimeCancellation.rethrowIfCancellation(invalidOperationCohort);
                outcomes.add(ScheduleReconciliationOutcome.OperationOutcome.failed(
                        job.operationCode()
                ));
            }
        }
        return new ScheduleReconciliationOutcome(outcomes, reconciled);
    }

    private ScheduleReconciliationOutcome reconcileBounded(Instant observedAt) {
        List<OperationCode> available = new ArrayList<>();
        for (DataPullJob job : jobRegistry.all()) available.add(job.operationCode());
        List<OperationCode> reserved = boundedEngine.reserveOperations(available);
        List<DataPullTask> reconciled = new ArrayList<>();
        List<ScheduleReconciliationOutcome.OperationOutcome> outcomes = new ArrayList<>();
        for (OperationCode operation : reserved) {
            DataPullRuntimeCancellation.requireActive();
            try {
                DataPullJob job = jobRegistry.require(operation);
                DataPullSchedule schedule = scheduleRegistry.find(operation).orElseThrow(
                        () -> new IllegalStateException(
                                "no data-pull schedule registered for " + operation.name()
                        )
                );
                ScheduleBatchEngine.Advance advance = operationTransaction.executeValue(
                        () -> boundedEngine.advance(job, schedule, observedAt)
                );
                if (advance.isFailed()) {
                    outcomes.add(ScheduleReconciliationOutcome.OperationOutcome.failed(operation));
                    continue;
                }
                List<DataPullTask> operationTasks = advance.getTasks();
                reconciled.addAll(operationTasks);
                outcomes.add(ScheduleReconciliationOutcome.OperationOutcome.succeeded(
                        operation, operationTasks.size()
                ));
            } catch (RuntimeException failedOperation) {
                DataPullRuntimeCancellation.rethrowIfCancellation(failedOperation);
                outcomes.add(ScheduleReconciliationOutcome.OperationOutcome.failed(operation));
            }
        }
        return new ScheduleReconciliationOutcome(outcomes, reconciled);
    }

    @FunctionalInterface
    public interface OperationTransaction {
        List<DataPullTask> execute(Supplier<List<DataPullTask>> action);

        default <T> T executeValue(Supplier<T> action) {
            AtomicReference<T> result = new AtomicReference<>();
            execute(() -> {
                result.set(Objects.requireNonNull(action.get(), "transaction value"));
                return List.of();
            });
            return Objects.requireNonNull(result.get(), "transaction did not execute");
        }
    }

}
