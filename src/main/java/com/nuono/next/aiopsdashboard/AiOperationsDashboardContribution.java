package com.nuono.next.aiopsdashboard;

import java.util.List;

public class AiOperationsDashboardContribution {

    private final List<AiOperationsDashboardOverview.MetricCard> metricCards;
    private final List<AiOperationsDashboardOverview.Signal> signals;
    private final List<AiOperationsDashboardOverview.EvidenceItem> evidence;

    public AiOperationsDashboardContribution(
            List<AiOperationsDashboardOverview.MetricCard> metricCards,
            List<AiOperationsDashboardOverview.Signal> signals,
            List<AiOperationsDashboardOverview.EvidenceItem> evidence
    ) {
        this.metricCards = metricCards == null ? List.of() : List.copyOf(metricCards);
        this.signals = signals == null ? List.of() : List.copyOf(signals);
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static AiOperationsDashboardContribution empty() {
        return new AiOperationsDashboardContribution(List.of(), List.of(), List.of());
    }

    public static AiOperationsDashboardContribution signals(List<AiOperationsDashboardOverview.Signal> signals) {
        return new AiOperationsDashboardContribution(List.of(), signals, List.of());
    }

    public List<AiOperationsDashboardOverview.MetricCard> getMetricCards() {
        return metricCards;
    }

    public List<AiOperationsDashboardOverview.Signal> getSignals() {
        return signals;
    }

    public List<AiOperationsDashboardOverview.EvidenceItem> getEvidence() {
        return evidence;
    }
}
