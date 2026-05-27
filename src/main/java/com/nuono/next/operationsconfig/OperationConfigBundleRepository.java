package com.nuono.next.operationsconfig;

import java.util.List;
import java.util.Optional;

public interface OperationConfigBundleRepository {

    Long nextBundleId();

    void insert(OperationConfigBundle bundle);

    List<OperationConfigBundle> listBundles();

    Optional<OperationConfigBundle> findBundle(Long bundleId);

    void replaceScopeStores(Long bundleId, List<OperationConfigBundleScopeStore> scopeStores);

    List<OperationConfigBundleScopeStore> listScopeStores(Long bundleId);

    void updateScopeSummary(Long bundleId, String scopeSummary, Integer affectedStoreCount, Long updatedBy);

    void updateActivityRuleCount(Long bundleId, Integer activityRuleCount, Long updatedBy);

    void updateLifecycleRuleSummary(Long bundleId, String lifecycleRuleSummary, Long updatedBy);

    void deleteBundle(Long bundleId);
}
