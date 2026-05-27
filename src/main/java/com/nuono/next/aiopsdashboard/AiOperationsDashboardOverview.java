package com.nuono.next.aiopsdashboard;

import java.math.BigDecimal;
import java.util.List;

public class AiOperationsDashboardOverview {

    private final AiOperationsDashboardScope scope;
    private final Summary summary;
    private final List<MetricCard> metricCards;
    private final List<Signal> signals;
    private final AiSummary aiSummary;
    private final ItemCollection<SuggestionItem> suggestions;
    private final ItemCollection<EvidenceItem> evidence;
    private final List<String> qualityStates;

    public AiOperationsDashboardOverview(
            AiOperationsDashboardScope scope,
            Summary summary,
            List<MetricCard> metricCards,
            List<Signal> signals,
            AiSummary aiSummary,
            ItemCollection<SuggestionItem> suggestions,
            ItemCollection<EvidenceItem> evidence,
            List<String> qualityStates
    ) {
        this.scope = scope;
        this.summary = summary;
        this.metricCards = metricCards == null ? List.of() : List.copyOf(metricCards);
        this.signals = signals == null ? List.of() : List.copyOf(signals);
        this.aiSummary = aiSummary;
        this.suggestions = suggestions;
        this.evidence = evidence;
        this.qualityStates = qualityStates == null ? List.of() : List.copyOf(qualityStates);
    }

    public AiOperationsDashboardScope getScope() {
        return scope;
    }

    public Summary getSummary() {
        return summary;
    }

    public List<MetricCard> getMetricCards() {
        return metricCards;
    }

    public List<Signal> getSignals() {
        return signals;
    }

    public AiSummary getAiSummary() {
        return aiSummary;
    }

    public ItemCollection<SuggestionItem> getSuggestions() {
        return suggestions;
    }

    public ItemCollection<EvidenceItem> getEvidence() {
        return evidence;
    }

    public List<String> getQualityStates() {
        return qualityStates;
    }

    public static class Summary {
        private final String title;
        private final String state;
        private final String description;

        public Summary(String title, String state, String description) {
            this.title = title;
            this.state = state;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getState() {
            return state;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class MetricCard {
        private final String key;
        private final String title;
        private final String state;
        private final String qualityState;
        private final BigDecimal value;
        private final String unit;
        private final String description;

        public MetricCard(
                String key,
                String title,
                String state,
                String qualityState,
                BigDecimal value,
                String unit,
                String description
        ) {
            this.key = key;
            this.title = title;
            this.state = state;
            this.qualityState = qualityState;
            this.value = value;
            this.unit = unit;
            this.description = description;
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public String getState() {
            return state;
        }

        public String getQualityState() {
            return qualityState;
        }

        public BigDecimal getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Signal {
        private final String key;
        private final String title;
        private final String state;
        private final String severity;
        private final String description;
        private final String source;
        private final String drillThroughPath;
        private final List<EvidenceItem> evidence;

        public Signal(
                String key,
                String title,
                String state,
                String severity,
                String description,
                String source,
                List<EvidenceItem> evidence
        ) {
            this(key, title, state, severity, description, source, null, evidence);
        }

        public Signal(
                String key,
                String title,
                String state,
                String severity,
                String description,
                String source,
                String drillThroughPath,
                List<EvidenceItem> evidence
        ) {
            this.key = key;
            this.title = title;
            this.state = state;
            this.severity = severity;
            this.description = description;
            this.source = source;
            this.drillThroughPath = drillThroughPath;
            this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public String getState() {
            return state;
        }

        public String getSeverity() {
            return severity;
        }

        public String getDescription() {
            return description;
        }

        public String getSource() {
            return source;
        }

        public String getDrillThroughPath() {
            return drillThroughPath;
        }

        public List<EvidenceItem> getEvidence() {
            return evidence;
        }
    }

    public static class AiSummary {
        private final String title;
        private final String state;
        private final String qualityState;
        private final String content;
        private final String generatedAt;

        public AiSummary(String title, String state, String qualityState, String content, String generatedAt) {
            this.title = title;
            this.state = state;
            this.qualityState = qualityState;
            this.content = content;
            this.generatedAt = generatedAt;
        }

        public String getTitle() {
            return title;
        }

        public String getState() {
            return state;
        }

        public String getQualityState() {
            return qualityState;
        }

        public String getContent() {
            return content;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }
    }

    public static class ItemCollection<T> {
        private final String state;
        private final String title;
        private final List<T> items;

        public ItemCollection(String state, String title, List<T> items) {
            this.state = state;
            this.title = title;
            this.items = items == null ? List.of() : List.copyOf(items);
        }

        public String getState() {
            return state;
        }

        public String getTitle() {
            return title;
        }

        public List<T> getItems() {
            return items;
        }
    }

    public static class SuggestionItem {
        private final String id;
        private final String title;
        private final String status;
        private final String description;
        private final List<EvidenceItem> evidence;

        public SuggestionItem(String id, String title, String status, String description, List<EvidenceItem> evidence) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.description = description;
            this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getStatus() {
            return status;
        }

        public String getDescription() {
            return description;
        }

        public List<EvidenceItem> getEvidence() {
            return evidence;
        }
    }

    public static class EvidenceItem {
        private final String id;
        private final String label;
        private final String source;
        private final String state;
        private final String description;

        public EvidenceItem(String id, String label, String source, String state, String description) {
            this.id = id;
            this.label = label;
            this.source = source;
            this.state = state;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getSource() {
            return source;
        }

        public String getState() {
            return state;
        }

        public String getDescription() {
            return description;
        }
    }
}
