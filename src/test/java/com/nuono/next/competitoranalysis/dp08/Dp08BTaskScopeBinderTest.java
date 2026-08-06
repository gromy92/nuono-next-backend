package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.datapull.orchestration.DataPullScheduledScope;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullCatchUpPlan;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScheduleSlot;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp08BTaskScopeBinderTest {
    private static final DataPullScope SCOPE = new DataPullScope(
            "NOON", 307L, 108065L, "account-307", "egress-1",
            "PRJ108065", "STR108065-NSA", "SA", "dp08b-scope"
    );

    @Test
    void preparesEveryMissedBusinessDateBeforeReturningAnyTaskScope() {
        RecordingCatalog catalog = new RecordingCatalog(null);
        Dp08BTaskScopeBinder binder = new Dp08BTaskScopeBinder(
                catalog, clockAtShanghai("2026-08-04T02:30:00")
        );
        List<DataPullScheduledScope> scheduled = scheduledAcrossOutage();

        List<DataPullScheduledScope> prepared = binder.prepare(
                scheduled, List.of(admitted())
        );

        assertThat(prepared).containsExactlyElementsOf(scheduled);
        assertThat(catalog.preparedDates).containsExactly(
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 4)
        );
        assertThat(catalog.completedDates).containsExactlyElementsOf(catalog.preparedDates);
    }

    @Test
    void sourceFirstEffectiveAfterHistoricalSlotSkipsOnlyThatScopeSlot() {
        RecordingCatalog catalog = new RecordingCatalog(LocalDate.of(2026, 8, 2));
        Dp08BTaskScopeBinder binder = new Dp08BTaskScopeBinder(
                catalog, clockAtShanghai("2026-08-04T02:30:00")
        );
        List<DataPullScheduledScope> scheduled = scheduledAcrossOutage();

        List<DataPullScheduledScope> prepared = binder.prepare(
                scheduled, List.of(admitted())
        );

        assertThat(prepared).extracting(item -> item.getSlot().getBusinessWindow().getAnchorDate())
                .containsExactly(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4));
        assertThat(catalog.completedDates).hasSize(3);
    }

    @Test
    void beforeTwoAmStillEnumeratesScopesSoPriorDatesCanCatchUp() {
        RecordingCatalog catalog = new RecordingCatalog(null);
        Dp08BTaskScopeBinder binder = new Dp08BTaskScopeBinder(
                catalog, clockAtShanghai("2026-08-04T01:30:00")
        );

        assertThat(binder.listCurrentScopes()).containsExactly(SCOPE);
        assertThat(catalog.listedDates).containsExactly(LocalDate.of(2026, 8, 4));
    }

    private static List<DataPullScheduledScope> scheduledAcrossOutage() {
        DataPullSchedule schedule = new DataPullScheduleRegistry()
                .find(OperationCode.DP08B).orElseThrow();
        List<DataPullScheduleSlot> slots = schedule.missedSlots(
                SCOPE.getStableScopeKey(),
                atShanghai("2026-08-01T02:00:00"),
                atShanghai("2026-08-04T02:30:00")
        );
        List<DataPullScheduledScope> result = new ArrayList<>();
        for (DataPullScheduleSlot slot : slots) {
            result.add(new DataPullScheduledScope(
                    SCOPE, slot, DataPullCatchUpPlan.Strategy.EXACT_WINDOWS
            ));
        }
        return List.copyOf(result);
    }

    private static AdmittedDataPullScope admitted() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 1, 0, 0);
        return new AdmittedDataPullScope(
                SCOPE,
                DataPullScopeAdmission.cutoverExisting(SCOPE, "cutover-20260801", at)
        );
    }

    private static Clock clockAtShanghai(String value) {
        return Clock.fixed(atShanghai(value), ZoneOffset.UTC);
    }

    private static Instant atShanghai(String value) {
        return ZonedDateTime.of(
                LocalDateTime.parse(value), DataPullSchedule.ZONE_ID
        ).toInstant();
    }

    private static final class RecordingCatalog implements Dp08ScopeCatalog {
        private final LocalDate lateDate;
        private final List<LocalDate> listedDates = new ArrayList<>();
        private final List<LocalDate> preparedDates = new ArrayList<>();
        private final List<LocalDate> completedDates = new ArrayList<>();

        private RecordingCatalog(LocalDate lateDate) {
            this.lateDate = lateDate;
        }

        @Override
        public List<DataPullScope> listKeywordScopes() {
            return List.of();
        }

        @Override
        public List<DataPullScope> listListTargetScopes(LocalDate factDate) {
            listedDates.add(factDate);
            return List.of(SCOPE);
        }

        @Override
        public Dp08ListTargetPreparation prepareListTargetScopesForEnqueue(
                LocalDate factDate
        ) {
            preparedDates.add(factDate);
            LocalDateTime slotUtc = factDate.atTime(2, 0)
                    .atZone(DataPullSchedule.ZONE_ID)
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            LocalDateTime effective = factDate.equals(lateDate)
                    ? slotUtc.plusMinutes(1) : slotUtc;
            DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                    OperationCode.DP08B, SCOPE.getStableScopeKey(),
                    Dp08ScopeSnapshotCodec.LIST_TARGET_V1, "{}", effective
            );
            DataPullScopePreparation preparation = DataPullScopePreparation.deferred(
                    List.of(SCOPE), ignored -> completedDates.add(factDate)
            );
            return Dp08ListTargetPreparation.binding(preparation, List.of(candidate));
        }
    }
}
