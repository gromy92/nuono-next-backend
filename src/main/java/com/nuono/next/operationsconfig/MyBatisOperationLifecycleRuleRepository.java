package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.OperationLifecycleRuleMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationLifecycleRuleRepository implements OperationLifecycleRuleRepository {

    private final OperationLifecycleRuleMapper mapper;

    public MyBatisOperationLifecycleRuleRepository(OperationLifecycleRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextRuleId() {
        return mapper.nextOperationLifecycleRuleId();
    }

    @Override
    public void insertRule(OperationLifecycleRule rule) {
        mapper.insertRule(rule);
    }

    @Override
    public void updateRule(OperationLifecycleRule rule) {
        mapper.updateRule(rule);
    }

    @Override
    public OperationLifecycleRule findRule(Long id) {
        return mapper.selectRuleById(id);
    }

    @Override
    public List<OperationLifecycleRule> listRules(Long ownerUserId, String storeCode, String siteCode) {
        return mapper.selectRules(ownerUserId, storeCode, siteCode);
    }

    @Override
    public Optional<OperationLifecycleRule> findLatestPublished(Long ownerUserId, String storeCode, String siteCode) {
        return Optional.ofNullable(mapper.selectLatestPublished(ownerUserId, storeCode, siteCode));
    }

    @Override
    public Optional<OperationLifecycleRule> findLatestDraft(Long ownerUserId, String storeCode, String siteCode) {
        return Optional.ofNullable(mapper.selectLatestDraft(ownerUserId, storeCode, siteCode));
    }

    @Override
    public List<OperationLifecycleRule> listRulesByBundleVersion(Long bundleVersionId) {
        return mapper.selectRulesByBundleVersion(bundleVersionId);
    }

    @Override
    public int countRulesByBundleVersion(Long bundleVersionId) {
        return mapper.countRulesByBundleVersion(bundleVersionId);
    }
}
