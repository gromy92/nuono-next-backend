package com.nuono.next.noonauth;

import java.util.List;
import java.util.Set;

public interface NoonAuthRecoveryProjectBatchCatalog {
    List<NoonAuthRecoveryProjectCandidate> listEligibleProjects(Set<String> projectAllowlist);

    static NoonAuthRecoveryProjectBatchCatalog empty() {
        return projectAllowlist -> List.of();
    }
}
