package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationLifecycleRuleStateView {

    private final OperationLifecycleRuleView current;
    private final OperationLifecycleRuleView draft;
    private final List<OperationLifecycleRuleDiffView> diffs;
    private final List<OperationLifecycleRuleView> history;
    private final String impactScope;

    public OperationLifecycleRuleStateView(
            OperationLifecycleRuleView current,
            OperationLifecycleRuleView draft,
            List<OperationLifecycleRuleDiffView> diffs,
            List<OperationLifecycleRuleView> history,
            String impactScope
    ) {
        this.current = current;
        this.draft = draft;
        this.diffs = diffs == null ? List.of() : List.copyOf(diffs);
        this.history = history == null ? List.of() : List.copyOf(history);
        this.impactScope = impactScope;
    }

    public OperationLifecycleRuleView getCurrent() { return current; }
    public OperationLifecycleRuleView getDraft() { return draft; }
    public List<OperationLifecycleRuleDiffView> getDiffs() { return diffs; }
    public List<OperationLifecycleRuleView> getHistory() { return history; }
    public String getImpactScope() { return impactScope; }
}
