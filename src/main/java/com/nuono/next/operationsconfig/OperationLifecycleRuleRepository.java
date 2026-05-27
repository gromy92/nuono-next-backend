package com.nuono.next.operationsconfig;

import java.util.List;
import java.util.Optional;

public interface OperationLifecycleRuleRepository {

    Long nextRuleId();

    void insertRule(OperationLifecycleRule rule);

    void updateRule(OperationLifecycleRule rule);

    OperationLifecycleRule findRule(Long id);

    List<OperationLifecycleRule> listRules(Long ownerUserId, String storeCode, String siteCode);

    Optional<OperationLifecycleRule> findLatestPublished(Long ownerUserId, String storeCode, String siteCode);

    Optional<OperationLifecycleRule> findLatestDraft(Long ownerUserId, String storeCode, String siteCode);

    List<OperationLifecycleRule> listRulesByBundleVersion(Long bundleVersionId);

    int countRulesByBundleVersion(Long bundleVersionId);
}
