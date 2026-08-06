package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Immutable, declarative catalog for every operation in the DP business schedule. */
@Component
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class DataPullScheduleRegistry {
    private static final int NO_JITTER = 1;
    private static final int DP02_JITTER_BUCKETS = 11;
    private static final Map<OperationCode, DataPullSchedule> SCHEDULES = schedules();

    public Optional<DataPullSchedule> find(OperationCode operationCode) {
        return Optional.ofNullable(SCHEDULES.get(
                Objects.requireNonNull(operationCode, "operationCode")
        ));
    }

    public void requireComplete() {
        if (SCHEDULES.size() != OperationCode.values().length) {
            throw new IllegalStateException("data-pull schedule catalog is incomplete");
        }
    }

    private static Map<OperationCode, DataPullSchedule> schedules() {
        Map<OperationCode, DataPullSchedule> schedules = new EnumMap<>(OperationCode.class);
        register(schedules, dateRange(OperationCode.DP01, at(20, 0), 30, 1));
        register(schedules, dateRange(
                OperationCode.DP02, at(8, 30), DP02_JITTER_BUCKETS, 1, 1
        ));
        register(schedules, dateRange(OperationCode.DP03, at(22, 30), 7, 1));
        register(schedules, declared(
                OperationCode.DP04, DeclaredDataPullSchedule.WindowRule.COMPLETE_SNAPSHOT, at(3, 0)
        ));
        register(schedules, declared(
                OperationCode.DP05, DeclaredDataPullSchedule.WindowRule.CURRENT_VALID_ITEMS, at(3, 30)
        ));
        register(schedules, dateRange(OperationCode.DP06, at(6, 30), 1, 1));
        register(schedules, declared(
                OperationCode.DP07A, DeclaredDataPullSchedule.WindowRule.COMPLETE_SNAPSHOT, at(23, 0)
        ));
        register(schedules, dateRange(OperationCode.DP07B, at(23, 30), 1, 1));
        register(schedules, declared(
                OperationCode.DP08A,
                DeclaredDataPullSchedule.WindowRule.POINT_IN_TIME_RANKING,
                LocalTime.MIDNIGHT,
                at(6, 0),
                LocalTime.NOON,
                at(18, 0)
        ));
        register(schedules, declared(
                OperationCode.DP08B,
                DeclaredDataPullSchedule.WindowRule.DAILY_RANKING_GAP_TARGETS, at(2, 0)
        ));
        register(schedules, declared(
                OperationCode.DP10,
                DeclaredDataPullSchedule.WindowRule.FULL_THEN_HIGH_WATERMARK, at(3, 0)
        ));
        return Collections.unmodifiableMap(schedules);
    }

    private static DataPullSchedule dateRange(
            OperationCode operation,
            LocalTime runAt,
            int fromDaysBefore,
            int toDaysBefore
    ) {
        return dateRange(operation, runAt, NO_JITTER, fromDaysBefore, toDaysBefore);
    }

    private static DataPullSchedule dateRange(
            OperationCode operation,
            LocalTime runAt,
            int jitterBuckets,
            int fromDaysBefore,
            int toDaysBefore
    ) {
        return new DeclaredDataPullSchedule(
                operation,
                List.of(runAt),
                jitterBuckets,
                DeclaredDataPullSchedule.WindowRule.INCLUSIVE_DATE_RANGE,
                fromDaysBefore,
                toDaysBefore
        );
    }

    private static DataPullSchedule declared(
            OperationCode operation,
            DeclaredDataPullSchedule.WindowRule windowRule,
            LocalTime... runTimes
    ) {
        return new DeclaredDataPullSchedule(
                operation,
                List.of(runTimes),
                NO_JITTER,
                windowRule,
                0,
                0
        );
    }

    private static void register(
            Map<OperationCode, DataPullSchedule> schedules,
            DataPullSchedule schedule
    ) {
        DataPullSchedule previous = schedules.putIfAbsent(schedule.operationCode(), schedule);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate data-pull schedule for " + schedule.operationCode()
            );
        }
    }

    private static LocalTime at(int hour, int minute) {
        return LocalTime.of(hour, minute);
    }

}
