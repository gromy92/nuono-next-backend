package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure resolver shared by the in-memory and MyBatis atomic compaction Adapters. */
final class DataPullTaskCompaction {

    private static final Pattern DATE_RANGE = Pattern.compile(
            "^([A-Z0-9]+):date-range:(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})$"
    );
    private static final Comparator<DataPullTask> TASK_ORDER = Comparator
            .comparing(DataPullTask::getScheduleSlot)
            .thenComparing(DataPullTask::getId);

    private DataPullTaskCompaction() {
    }

    static Resolution resolve(
            DataPullTask proposed,
            List<DataPullTask> lockedNeverStarted,
            DataPullTaskCatchUpMode mode
    ) {
        DataPullTaskContract.requireEnqueueable(proposed);
        DataPullTaskCatchUpMode nonNullMode = Objects.requireNonNull(mode, "mode");
        requireAllowedOperation(proposed.getOperationCode(), nonNullMode);
        List<DataPullTask> existing = validateExisting(proposed, lockedNeverStarted);
        if (nonNullMode == DataPullTaskCatchUpMode.LATEST_CURRENT) {
            return latestCurrent(proposed, existing);
        }
        return rollingDateUnion(proposed, existing);
    }

    private static Resolution latestCurrent(
            DataPullTask proposed,
            List<DataPullTask> existing
    ) {
        DataPullTask latestExisting = existing.stream().max(TASK_ORDER).orElse(null);
        if (latestExisting != null
                && latestExisting.getScheduleSlot().equals(proposed.getScheduleSlot())
                && !latestExisting.getBusinessWindowKey().equals(proposed.getBusinessWindowKey())) {
            throw new IllegalStateException("current catch-up slot has conflicting window identities");
        }
        if (latestExisting != null && TASK_ORDER.compare(latestExisting, proposed) > 0) {
            return new Resolution(latestExisting, false, except(existing, latestExisting.getId()));
        }
        return new Resolution(proposed, true, existing);
    }

    private static Resolution rollingDateUnion(
            DataPullTask proposed,
            List<DataPullTask> existing
    ) {
        DateRange union = parseRange(proposed);
        DataPullTask latest = proposed;
        for (DataPullTask candidate : existing) {
            union = union.union(parseRange(candidate));
            if (TASK_ORDER.compare(candidate, latest) > 0) {
                latest = candidate;
            }
        }
        String mergedKey = proposed.getOperationCode().name()
                + ":date-range:" + union.from + ".." + union.to;
        java.time.LocalDateTime latestSlot = latest.getScheduleSlot();
        DataPullTask durable = existing.stream()
                .filter((candidate) -> candidate.getScheduleSlot().equals(latestSlot))
                .filter((candidate) -> candidate.getBusinessWindowKey().equals(mergedKey))
                .findFirst()
                .orElse(null);
        if (durable != null) {
            return new Resolution(durable, false, except(existing, durable.getId()));
        }
        DataPullTask replacement = queuedReplacement(
                proposed.getId(), latest, mergedKey, proposed.getCreatedAt()
        );
        return new Resolution(replacement, true, existing);
    }

    private static DataPullTask queuedReplacement(
            long id,
            DataPullTask base,
            String businessWindowKey,
            java.time.LocalDateTime now
    ) {
        return DataPullTask.queued(
                id,
                base.getOperationCode(),
                base.getProviderChannel(),
                base.getOwnerUserId(),
                base.getLogicalStoreId(),
                base.getAccountKey(),
                base.getEgressKey(),
                base.getProjectCode(),
                base.getStoreCode(),
                base.getSiteCode(),
                base.getScopeKey(),
                base.getScheduleSlot(),
                businessWindowKey,
                base.getStepCode(),
                now
        );
    }

    private static List<DataPullTask> validateExisting(
            DataPullTask proposed,
            List<DataPullTask> candidates
    ) {
        List<DataPullTask> existing = new ArrayList<>(
                Objects.requireNonNull(candidates, "lockedNeverStarted")
        );
        for (DataPullTask candidate : existing) {
            if (!DataPullTaskContract.isStrictlyNeverStarted(candidate)
                    || candidate.getOperationCode() != proposed.getOperationCode()
                    || !candidate.getScopeKey().equals(proposed.getScopeKey())) {
                throw new IllegalStateException("compaction candidate is outside its locked task set");
            }
        }
        return List.copyOf(existing);
    }

    private static void requireAllowedOperation(
            OperationCode operation,
            DataPullTaskCatchUpMode mode
    ) {
        boolean current = operation == OperationCode.DP04
                || operation == OperationCode.DP05
                || operation == OperationCode.DP07A;
        boolean rolling = operation == OperationCode.DP01 || operation == OperationCode.DP03;
        if ((mode == DataPullTaskCatchUpMode.LATEST_CURRENT && !current)
                || (mode == DataPullTaskCatchUpMode.ROLLING_DATE_UNION && !rolling)) {
            throw new IllegalArgumentException("operation is not eligible for requested catch-up compaction");
        }
    }

    private static DateRange parseRange(DataPullTask task) {
        Matcher matcher = DATE_RANGE.matcher(task.getBusinessWindowKey());
        if (!matcher.matches() || !matcher.group(1).equals(task.getOperationCode().name())) {
            throw new IllegalStateException("rolling catch-up task has an invalid date-range identity");
        }
        return new DateRange(LocalDate.parse(matcher.group(2)), LocalDate.parse(matcher.group(3)));
    }

    private static List<DataPullTask> except(List<DataPullTask> tasks, long keptTaskId) {
        List<DataPullTask> superseded = new ArrayList<>();
        for (DataPullTask task : tasks) {
            if (task.getId() != keptTaskId) {
                superseded.add(task);
            }
        }
        return List.copyOf(superseded);
    }

    static final class Resolution {
        private final DataPullTask replacement;
        private final boolean insertReplacement;
        private final List<DataPullTask> superseded;

        private Resolution(
                DataPullTask replacement,
                boolean insertReplacement,
                List<DataPullTask> superseded
        ) {
            this.replacement = replacement;
            this.insertReplacement = insertReplacement;
            this.superseded = List.copyOf(superseded);
        }

        DataPullTask getReplacement() { return replacement; }
        boolean isInsertReplacement() { return insertReplacement; }
        List<DataPullTask> getSuperseded() { return superseded; }
    }

    private static final class DateRange {
        private final LocalDate from;
        private final LocalDate to;

        private DateRange(LocalDate from, LocalDate to) {
            if (from.isAfter(to)) {
                throw new IllegalStateException("rolling catch-up range is inverted");
            }
            this.from = from;
            this.to = to;
        }

        private DateRange union(DateRange other) {
            return new DateRange(
                    from.isBefore(other.from) ? from : other.from,
                    to.isAfter(other.to) ? to : other.to
            );
        }
    }
}
