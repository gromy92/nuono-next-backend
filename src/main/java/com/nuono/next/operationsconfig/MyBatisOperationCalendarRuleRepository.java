package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.OperationCalendarRuleMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationCalendarRuleRepository implements OperationCalendarRuleRepository {

    private final OperationCalendarRuleMapper mapper;

    public MyBatisOperationCalendarRuleRepository(OperationCalendarRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextRuleId() {
        return mapper.nextOperationCalendarRuleId();
    }

    @Override
    public void insertRule(OperationCalendarRule rule) {
        mapper.insertRule(rule);
    }

    @Override
    public void updateRule(OperationCalendarRule rule) {
        mapper.updateRule(rule);
    }

    @Override
    public OperationCalendarRule findRule(Long id) {
        return mapper.selectRuleById(id);
    }

    @Override
    public List<OperationCalendarRule> listActiveRules(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectActiveRules(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<OperationCalendarRule> listActiveRulesForFactorResolution(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectActiveRulesForFactorResolution(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<OperationCalendarRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectRules(ownerUserId, storeCode, siteCode);
    }

    @Override
    public List<OperationCalendarRule> listRulesByBundleVersion(Long bundleVersionId) {
        return mapper.selectRulesByBundleVersion(bundleVersionId);
    }

    @Override
    public int countRulesByBundleVersion(Long bundleVersionId) {
        return mapper.countRulesByBundleVersion(bundleVersionId);
    }
}
