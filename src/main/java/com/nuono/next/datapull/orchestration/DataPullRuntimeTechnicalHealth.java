package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory technical failures for schedule reconciliation; never a business data verdict. */
public final class DataPullRuntimeTechnicalHealth {

    private final ConcurrentMap<OperationCode, FailureState> failures =
            new ConcurrentHashMap<>();

    public void observe(ScheduleReconciliationOutcome outcome, Instant observedAt) {
        ScheduleReconciliationOutcome value = Objects.requireNonNull(outcome, "outcome");
        Instant timestamp = Objects.requireNonNull(observedAt, "observedAt");
        for (ScheduleReconciliationOutcome.OperationOutcome operation : value.getOperations()) {
            if (!operation.isFailed()) {
                failures.remove(operation.getOperationCode());
                continue;
            }
            failures.compute(operation.getOperationCode(), (ignored, previous) ->
                    new FailureState(
                            operation.getFailureCode(),
                            nextFailureCount(previous),
                            timestamp
                    )
            );
        }
    }

    private int nextFailureCount(FailureState previous) {
        if (previous == null) return 1;
        return previous.consecutiveFailures == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : previous.consecutiveFailures + 1;
    }

    public Snapshot snapshot() {
        Map<String, FailureDetail> details = new LinkedHashMap<>();
        failures.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach((entry) -> details.put(
                        entry.getKey().name(),
                        entry.getValue().detail()
                ));
        return new Snapshot(details);
    }

    private static final class FailureState {
        private final String code;
        private final int consecutiveFailures;
        private final Instant observedAt;

        private FailureState(String code, int consecutiveFailures, Instant observedAt) {
            this.code = Objects.requireNonNull(code, "code");
            this.consecutiveFailures = consecutiveFailures;
            this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }

        private FailureDetail detail() {
            return new FailureDetail(code, consecutiveFailures, observedAt);
        }
    }

    public static final class Snapshot {
        private final Map<String, FailureDetail> failures;

        private Snapshot(Map<String, FailureDetail> failures) {
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }

        public boolean isHealthy() {
            return failures.isEmpty();
        }

        public Map<String, FailureDetail> getFailures() {
            return failures;
        }
    }

    public static final class FailureDetail {
        private final String code;
        private final int consecutiveFailures;
        private final Instant observedAt;

        private FailureDetail(String code, int consecutiveFailures, Instant observedAt) {
            this.code = code;
            this.consecutiveFailures = consecutiveFailures;
            this.observedAt = observedAt;
        }

        public String getCode() {
            return code;
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        public Instant getObservedAt() {
            return observedAt;
        }
    }
}
