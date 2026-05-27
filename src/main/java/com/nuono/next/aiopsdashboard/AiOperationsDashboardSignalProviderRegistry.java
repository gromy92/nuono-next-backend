package com.nuono.next.aiopsdashboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiOperationsDashboardSignalProviderRegistry {

    private final List<AiOperationsDashboardSignalProvider> providers;

    public AiOperationsDashboardSignalProviderRegistry(List<AiOperationsDashboardSignalProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public List<AiOperationsDashboardOverview.Signal> collect(AiOperationsDashboardQuery query) {
        return contribute(query, null).getSignals();
    }

    public AiOperationsDashboardContribution contribute(
            AiOperationsDashboardQuery query,
            AiOperationsDashboardScope scope
    ) {
        if (providers.isEmpty()) {
            return AiOperationsDashboardContribution.empty();
        }

        List<AiOperationsDashboardOverview.MetricCard> metricCards = new ArrayList<>();
        List<AiOperationsDashboardOverview.Signal> signals = new ArrayList<>();
        List<AiOperationsDashboardOverview.EvidenceItem> evidence = new ArrayList<>();
        for (AiOperationsDashboardSignalProvider provider : providers) {
            try {
                AiOperationsDashboardContribution contribution = provider.contribute(query, scope);
                if (contribution != null) {
                    metricCards.addAll(contribution.getMetricCards());
                    signals.addAll(contribution.getSignals());
                    evidence.addAll(contribution.getEvidence());
                }
            } catch (RuntimeException exception) {
                signals.add(new AiOperationsDashboardOverview.Signal(
                        provider.getClass().getSimpleName(),
                        "信号源暂不可用",
                        "provider_unavailable",
                        "warning",
                        exception.getMessage(),
                        provider.getClass().getName(),
                        List.of()
                ));
            }
        }
        return new AiOperationsDashboardContribution(metricCards, signals, evidence);
    }
}
