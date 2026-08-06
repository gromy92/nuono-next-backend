package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One fact-date target cohort plus its trustworthy earliest binding times. */
public final class Dp08ListTargetPreparation {
    private final DataPullScopePreparation preparation;
    private final Map<String, LocalDateTime> effectiveFromByScope;
    private final boolean requireEffectiveTime;

    private Dp08ListTargetPreparation(
            DataPullScopePreparation preparation,
            Map<String, LocalDateTime> effectiveFromByScope,
            boolean requireEffectiveTime
    ) {
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.effectiveFromByScope = Map.copyOf(effectiveFromByScope);
        this.requireEffectiveTime = requireEffectiveTime;
    }

    public static Dp08ListTargetPreparation readOnly(List<DataPullScope> scopes) {
        return new Dp08ListTargetPreparation(
                DataPullScopePreparation.readOnly(scopes), Map.of(), false
        );
    }

    static Dp08ListTargetPreparation binding(
            DataPullScopePreparation preparation,
            List<DataPullScopeBindingCandidate> candidates
    ) {
        Map<String, LocalDateTime> effective = new LinkedHashMap<>();
        for (DataPullScopeBindingCandidate candidate : candidates) {
            DataPullScopeBindingCandidate value = Objects.requireNonNull(candidate, "candidate");
            if (effective.put(value.getScopeKey(), value.getEffectiveFromUtc()) != null) {
                throw new IllegalStateException("DP-08-B duplicate binding effective time");
            }
        }
        return new Dp08ListTargetPreparation(preparation, effective, true);
    }

    public List<DataPullScope> getScopes() {
        return preparation.getScopes();
    }

    public void completeAfterAdmission(List<AdmittedDataPullScope> admittedScopes) {
        preparation.completeAfterAdmission(admittedScopes);
    }

    public boolean isEligibleAt(String scopeKey, LocalDateTime scheduleSlotUtc) {
        String key = Objects.requireNonNull(scopeKey, "scopeKey");
        LocalDateTime effective = effectiveFromByScope.get(key);
        if (effective == null) {
            if (requireEffectiveTime) {
                throw new IllegalStateException("DP-08-B binding effective time is missing: " + key);
            }
            return true;
        }
        return !effective.isAfter(Objects.requireNonNull(scheduleSlotUtc, "scheduleSlotUtc"));
    }
}
