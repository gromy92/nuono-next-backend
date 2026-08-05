package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScheduledScope;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullBusinessWindow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Captures and binds exactly one DP-08-B target payload cohort per missed business date. */
final class Dp08BTaskScopeBinder {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final Dp08ScopeCatalog catalog;
    private final Clock clock;

    Dp08BTaskScopeBinder(Dp08ScopeCatalog catalog, Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    List<DataPullScope> listCurrentScopes() {
        return catalog.listListTargetScopes(LocalDate.now(clock.withZone(SHANGHAI)));
    }

    List<DataPullScheduledScope> prepare(
            List<DataPullScheduledScope> scheduledScopes,
            List<AdmittedDataPullScope> admittedScopes
    ) {
        List<DataPullScheduledScope> scheduled = List.copyOf(
                Objects.requireNonNull(scheduledScopes, "scheduledScopes")
        );
        Map<String, AdmittedDataPullScope> admissions = admissions(admittedScopes);
        Map<LocalDate, List<DataPullScheduledScope>> byDate = groupByDate(scheduled, admissions);
        Map<LocalDate, Dp08ListTargetPreparation> preparations = new LinkedHashMap<>();
        for (LocalDate factDate : byDate.keySet()) {
            Dp08ListTargetPreparation preparation =
                    catalog.prepareListTargetScopesForEnqueue(factDate);
            preparation.completeAfterAdmission(align(preparation.getScopes(), admissions));
            preparations.put(factDate, preparation);
        }
        List<DataPullScheduledScope> eligible = new ArrayList<>();
        for (DataPullScheduledScope item : scheduled) {
            LocalDate factDate = factDate(item);
            LocalDateTime slotUtc = item.getSlot().getScheduledAt()
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            if (preparations.get(factDate).isEligibleAt(
                    item.getScope().getStableScopeKey(), slotUtc
            )) {
                eligible.add(item);
            }
        }
        return List.copyOf(eligible);
    }

    private static Map<LocalDate, List<DataPullScheduledScope>> groupByDate(
            List<DataPullScheduledScope> scheduled,
            Map<String, AdmittedDataPullScope> admissions
    ) {
        Map<LocalDate, List<DataPullScheduledScope>> result = new TreeMap<>();
        for (DataPullScheduledScope item : scheduled) {
            DataPullScheduledScope value = Objects.requireNonNull(item, "scheduledScope");
            AdmittedDataPullScope admission = admissions.get(
                    value.getScope().getStableScopeKey()
            );
            if (admission == null) {
                throw new IllegalStateException("DP-08-B planned scope is not admitted");
            }
            new AdmittedDataPullScope(value.getScope(), admission.getAdmission());
            result.computeIfAbsent(factDate(value), ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }

    private static LocalDate factDate(DataPullScheduledScope item) {
        if (item.getSlot().getOperationCode() != OperationCode.DP08B
                || item.getSlot().getBusinessWindow().getKind()
                != DataPullBusinessWindow.Kind.DAILY_RANKING_GAP_TARGETS) {
            throw new IllegalStateException("DP-08-B slot preparation received a wrong slot");
        }
        return item.getSlot().getBusinessWindow().getAnchorDate();
    }

    private static List<AdmittedDataPullScope> align(
            List<DataPullScope> scopes,
            Map<String, AdmittedDataPullScope> admissions
    ) {
        if (scopes.size() != admissions.size()) {
            throw new IllegalStateException("DP-08-B fact-date source cohort drift");
        }
        List<AdmittedDataPullScope> result = new ArrayList<>(scopes.size());
        for (DataPullScope scope : scopes) {
            AdmittedDataPullScope admission = admissions.get(scope.getStableScopeKey());
            if (admission == null) {
                throw new IllegalStateException("DP-08-B fact-date scope was not admitted");
            }
            result.add(new AdmittedDataPullScope(scope, admission.getAdmission()));
        }
        return List.copyOf(result);
    }

    private static Map<String, AdmittedDataPullScope> admissions(
            List<AdmittedDataPullScope> values
    ) {
        Map<String, AdmittedDataPullScope> result = new LinkedHashMap<>();
        for (AdmittedDataPullScope item : List.copyOf(
                Objects.requireNonNull(values, "admittedScopes")
        )) {
            AdmittedDataPullScope admission = Objects.requireNonNull(item, "admittedScope");
            if (result.put(admission.getScope().getStableScopeKey(), admission) != null) {
                throw new IllegalStateException("DP-08-B duplicate admission");
            }
        }
        return result;
    }
}
