package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Operation-aware reduction of newly missed slots before task-store reconciliation. */
public final class DataPullCatchUpPlan {

    public enum Strategy {
        EXACT_WINDOWS,
        LATEST_CURRENT,
        ROLLING_DATE_UNION
    }

    private final Strategy strategy;
    private final List<DataPullScheduleSlot> taskSlots;

    private DataPullCatchUpPlan(Strategy strategy, List<DataPullScheduleSlot> taskSlots) {
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.taskSlots = List.copyOf(taskSlots);
    }

    public static DataPullCatchUpPlan from(
            OperationCode operationCode,
            List<DataPullScheduleSlot> missedSlots
    ) {
        List<DataPullScheduleSlot> normalized = normalizedSlots(
                Objects.requireNonNull(operationCode, "operationCode"), missedSlots
        );
        Instant reference = normalized.isEmpty()
                ? Instant.EPOCH
                : normalized.get(normalized.size() - 1).getScheduledAt().toInstant();
        return from(operationCode, normalized, reference);
    }

    public static DataPullCatchUpPlan from(
            OperationCode operationCode,
            List<DataPullScheduleSlot> missedSlots,
            Instant reconcileNow
    ) {
        OperationCode operation = Objects.requireNonNull(operationCode, "operationCode");
        Objects.requireNonNull(reconcileNow, "reconcileNow");
        List<DataPullScheduleSlot> slots = normalizedSlots(operation, missedSlots);
        if (slots.isEmpty()) {
            return new DataPullCatchUpPlan(Strategy.EXACT_WINDOWS, slots);
        }
        switch (strategyFor(operation)) {
            case ROLLING_DATE_UNION:
                requireKind(slots, DataPullBusinessWindow.Kind.INCLUSIVE_DATE_RANGE);
                return new DataPullCatchUpPlan(
                        Strategy.ROLLING_DATE_UNION,
                        List.of(rollingUnionSlot(operation, slots))
                );
            case LATEST_CURRENT:
                if (operation == OperationCode.DP05) {
                    requireKind(slots, DataPullBusinessWindow.Kind.CURRENT_VALID_ITEMS);
                } else {
                    requireKind(slots, DataPullBusinessWindow.Kind.CURRENT_COMPLETE_SNAPSHOT);
                }
                return latestCurrent(slots);
            case EXACT_WINDOWS:
                break;
            default:
                throw new IllegalStateException("unsupported catch-up strategy");
        }
        switch (operation) {
            case DP01:
            case DP03:
            case DP04:
            case DP07A:
            case DP05:
                throw new IllegalStateException("compacted strategy reached exact-window branch");
            case DP08A:
                requireKind(slots, DataPullBusinessWindow.Kind.POINT_IN_TIME_RANKING);
                return new DataPullCatchUpPlan(Strategy.EXACT_WINDOWS, slots);
            case DP08B:
                requireKind(slots, DataPullBusinessWindow.Kind.DAILY_RANKING_GAP_TARGETS);
                return new DataPullCatchUpPlan(Strategy.EXACT_WINDOWS, slots);
            default:
                return new DataPullCatchUpPlan(Strategy.EXACT_WINDOWS, slots);
        }
    }

    public static Strategy strategyFor(OperationCode operationCode) {
        switch (Objects.requireNonNull(operationCode, "operationCode")) {
            case DP01:
            case DP03:
                return Strategy.ROLLING_DATE_UNION;
            case DP04:
            case DP05:
            case DP07A:
                return Strategy.LATEST_CURRENT;
            default:
                return Strategy.EXACT_WINDOWS;
        }
    }

    private static DataPullCatchUpPlan latestCurrent(List<DataPullScheduleSlot> slots) {
        return new DataPullCatchUpPlan(
                Strategy.LATEST_CURRENT,
                List.of(slots.get(slots.size() - 1))
        );
    }

    private static List<DataPullScheduleSlot> normalizedSlots(
            OperationCode operation,
            List<DataPullScheduleSlot> missedSlots
    ) {
        List<DataPullScheduleSlot> slots = new ArrayList<>(
                Objects.requireNonNull(missedSlots, "missedSlots")
        );
        for (DataPullScheduleSlot slot : slots) {
            if (slot == null || slot.getOperationCode() != operation) {
                throw new IllegalArgumentException("catch-up slots must belong to one operation");
            }
        }
        slots.sort(Comparator.comparing(DataPullScheduleSlot::getScheduledAt));
        return List.copyOf(slots);
    }

    private static void requireKind(
            List<DataPullScheduleSlot> slots,
            DataPullBusinessWindow.Kind expected
    ) {
        for (DataPullScheduleSlot slot : slots) {
            if (slot.getBusinessWindow().getKind() != expected) {
                throw new IllegalStateException("operation catch-up received the wrong window kind");
            }
        }
    }

    private static DataPullScheduleSlot rollingUnionSlot(
            OperationCode operation,
            List<DataPullScheduleSlot> slots
    ) {
        LocalDate from = null;
        LocalDate to = null;
        for (DataPullScheduleSlot slot : slots) {
            DataPullBusinessWindow window = slot.getBusinessWindow();
            from = from == null || window.getDateFromInclusive().isBefore(from)
                    ? window.getDateFromInclusive() : from;
            to = to == null || window.getDateToInclusive().isAfter(to)
                    ? window.getDateToInclusive() : to;
        }
        DataPullScheduleSlot latest = slots.get(slots.size() - 1);
        DataPullBusinessWindow union = DataPullBusinessWindow.inclusiveDateRange(
                operation.name() + ":date-range:" + from + ".." + to,
                latest.getBusinessWindow().getAnchorDate(),
                from,
                to
        );
        return new DataPullScheduleSlot(operation, latest.getScheduledAt(), union);
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public List<DataPullScheduleSlot> getTaskSlots() {
        return taskSlots;
    }
}
