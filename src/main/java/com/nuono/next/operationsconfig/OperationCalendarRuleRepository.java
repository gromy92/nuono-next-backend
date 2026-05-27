package com.nuono.next.operationsconfig;

import java.util.List;

public interface OperationCalendarRuleRepository {

    Long nextRuleId();

    void insertRule(OperationCalendarRule rule);

    void updateRule(OperationCalendarRule rule);

    OperationCalendarRule findRule(Long id);

    List<OperationCalendarRule> listActiveRules(Long ownerUserId, String storeCode, String siteCode);

    default List<OperationCalendarRule> listActiveRulesForFactorResolution(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return listActiveRules(ownerUserId, storeCode, siteCode);
    }

    List<OperationCalendarRule> listRules(Long ownerUserId, String storeCode, String siteCode);

    List<OperationCalendarRule> listRulesByBundleVersion(Long bundleVersionId);

    int countRulesByBundleVersion(Long bundleVersionId);
}
