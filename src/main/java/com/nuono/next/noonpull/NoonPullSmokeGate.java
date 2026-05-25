package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoonPullSmokeGate {
    private final Map<String, Set<NoonPullDataDomain>> evidenceByEnvironment = new HashMap<>();
    private final Map<String, String> rollbackOrGlobalPauseByEnvironment = new HashMap<>();

    public void record(NoonPullSmokeEvidence evidence) {
        if (evidence == null || !evidence.isUsableEvidence()) {
            return;
        }
        evidenceByEnvironment
                .computeIfAbsent(evidence.getTargetEnvironment(), (ignored) -> EnumSet.noneOf(NoonPullDataDomain.class))
                .add(evidence.getDataDomain());
    }

    public void confirmRollbackOrGlobalPause(String targetEnvironment, String strategy) {
        rollbackOrGlobalPauseByEnvironment.put(targetEnvironment, strategy);
    }

    public NoonPullSmokeGateResult evaluate(String targetEnvironment) {
        Set<NoonPullDataDomain> domains = evidenceByEnvironment.getOrDefault(
                targetEnvironment,
                EnumSet.noneOf(NoonPullDataDomain.class)
        );
        List<String> missing = new ArrayList<>();
        if (!domains.contains(NoonPullDataDomain.PRODUCT)) {
            missing.add("PRODUCT_SMOKE");
        }
        if (!domains.contains(NoonPullDataDomain.SALES)) {
            missing.add("SALES_SMOKE");
        }
        if (!domains.contains(NoonPullDataDomain.ORDER)) {
            missing.add("ORDER_SMOKE");
        }
        if (!rollbackOrGlobalPauseByEnvironment.containsKey(targetEnvironment)) {
            missing.add("ROLLBACK_OR_GLOBAL_PAUSE");
        }
        return new NoonPullSmokeGateResult(missing);
    }
}
