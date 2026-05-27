package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.OperationConfigBundleMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationConfigBundleRepository implements OperationConfigBundleRepository {

    private final OperationConfigBundleMapper mapper;

    public MyBatisOperationConfigBundleRepository(OperationConfigBundleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextBundleId() {
        return mapper.nextOperationConfigBundleId();
    }

    @Override
    public void insert(OperationConfigBundle bundle) {
        mapper.insertBundle(bundle);
    }

    @Override
    public List<OperationConfigBundle> listBundles() {
        return mapper.selectBundles();
    }

    @Override
    public Optional<OperationConfigBundle> findBundle(Long bundleId) {
        return Optional.ofNullable(mapper.selectBundle(bundleId));
    }

    @Override
    public void replaceScopeStores(Long bundleId, List<OperationConfigBundleScopeStore> scopeStores) {
        mapper.deleteScopeStores(bundleId);
        for (OperationConfigBundleScopeStore scopeStore : scopeStores) {
            mapper.insertScopeStore(bundleId, scopeStore);
        }
    }

    @Override
    public List<OperationConfigBundleScopeStore> listScopeStores(Long bundleId) {
        return mapper.selectScopeStores(bundleId);
    }

    @Override
    public void updateScopeSummary(Long bundleId, String scopeSummary, Integer affectedStoreCount, Long updatedBy) {
        mapper.updateScopeSummary(bundleId, scopeSummary, affectedStoreCount, updatedBy);
    }

    @Override
    public void updateActivityRuleCount(Long bundleId, Integer activityRuleCount, Long updatedBy) {
        mapper.updateActivityRuleCount(bundleId, activityRuleCount, updatedBy);
    }

    @Override
    public void updateLifecycleRuleSummary(Long bundleId, String lifecycleRuleSummary, Long updatedBy) {
        mapper.updateLifecycleRuleSummary(bundleId, lifecycleRuleSummary, updatedBy);
    }

    @Override
    public void deleteBundle(Long bundleId) {
        mapper.deleteBundle(bundleId);
    }
}
