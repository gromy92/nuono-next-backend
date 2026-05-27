package com.nuono.next.operationsconfig;

import com.nuono.next.sales.SalesActivityWindowCompatibilitySource;
import com.nuono.next.sales.SalesActivityWindowRecord;
import com.nuono.next.sales.SalesActivityWindowScope;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationCalendarSalesActivityWindowAdapter implements SalesActivityWindowCompatibilitySource {

    private final OperationCalendarRuleRepository repository;
    private final OperationConfigBundleRepository bundleRepository;

    @Autowired
    public OperationCalendarSalesActivityWindowAdapter(
            OperationCalendarRuleRepository repository,
            OperationConfigBundleRepository bundleRepository
    ) {
        this.repository = repository;
        this.bundleRepository = bundleRepository;
    }

    OperationCalendarSalesActivityWindowAdapter(OperationCalendarRuleRepository repository) {
        this(repository, null);
    }

    @Override
    public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
        if (scope == null || scope.getOwnerUserId() == null || scope.getStoreCode() == null || scope.getSiteCode() == null) {
            return List.of();
        }
        List<OperationCalendarRule> scopedRules = repository.listActiveRules(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode()
        );
        boolean hasBundleRules = scopedRules.stream().anyMatch(rule -> rule.getBundleVersionId() != null);
        return scopedRules.stream()
                .filter(rule -> !hasBundleRules || rule.getBundleVersionId() != null)
                .filter(rule -> overlaps(scope, rule))
                .map(this::toWindow)
                .sorted(Comparator
                        .comparing(SalesActivityWindowRecord::getDateFrom)
                        .thenComparing(SalesActivityWindowRecord::getId))
                .collect(Collectors.toList());
    }

    private boolean overlaps(SalesActivityWindowScope scope, OperationCalendarRule rule) {
        return rule.getDateFrom() != null
                && rule.getDateTo() != null
                && !rule.getDateTo().isBefore(scope.getDateFrom())
                && !rule.getDateFrom().isAfter(scope.getDateTo());
    }

    private SalesActivityWindowRecord toWindow(OperationCalendarRule rule) {
        OperationConfigBundle bundle = bundle(rule);
        return new SalesActivityWindowRecord(
                rule.getId(),
                rule.getOwnerUserId(),
                rule.getStoreCode(),
                rule.getSiteCode(),
                rule.getRuleName(),
                rule.getActivityType(),
                categoryScope(rule),
                rule.getDateFrom(),
                rule.getDateTo(),
                safeFactor(rule.getFactorValue()),
                rule.isEnabled(),
                compatibilityVersion(rule),
                rule.getCreatedBy(),
                rule.getUpdatedBy(),
                rule.getBundleVersionId(),
                bundle == null ? bundleVersionNo(rule) : bundle.getVersionNo(),
                bundle == null ? rule.getPublishSourceRole() : bundle.getPublishSourceRole(),
                bundle == null ? rule.getPublishSourceLabel() : bundle.getPublishSourceLabel()
        );
    }

    private String categoryScope(OperationCalendarRule rule) {
        if (rule.getTargetScopeValue() != null && !rule.getTargetScopeValue().isBlank()) {
            return rule.getTargetScopeValue();
        }
        return rule.getTargetScopeType();
    }

    private BigDecimal safeFactor(BigDecimal factorValue) {
        return factorValue == null ? BigDecimal.ONE : factorValue;
    }

    private int compatibilityVersion(OperationCalendarRule rule) {
        if (rule.getBundleVersionId() != null) {
            return rule.getBundleVersionId() > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : rule.getBundleVersionId().intValue();
        }
        Long publishRecordId = rule.getPublishRecordId();
        if (publishRecordId == null || publishRecordId <= 0) {
            return 1;
        }
        if (publishRecordId > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return publishRecordId.intValue();
    }

    private OperationConfigBundle bundle(OperationCalendarRule rule) {
        if (bundleRepository == null || rule == null || rule.getBundleVersionId() == null) {
            return null;
        }
        return bundleRepository.findBundle(rule.getBundleVersionId()).orElse(null);
    }

    private String bundleVersionNo(OperationCalendarRule rule) {
        return rule.getBundleVersionId() == null ? null : "OPS_CONFIG_" + rule.getBundleVersionId();
    }
}
