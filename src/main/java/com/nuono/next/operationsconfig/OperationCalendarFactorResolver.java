package com.nuono.next.operationsconfig;

import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesProductDimensionRepository;
import com.nuono.next.sales.SalesProductDimensionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationCalendarFactorResolver {

    private final OperationCalendarRuleRepository calendarRepository;
    private final SalesProductDimensionRepository productDimensionRepository;
    private final OperationConfigBundleRepository bundleRepository;
    private final OperationConfigTypedVersionRepository typedVersionRepository;

    @Autowired
    public OperationCalendarFactorResolver(
            OperationCalendarRuleRepository calendarRepository,
            SalesProductDimensionRepository productDimensionRepository,
            OperationConfigBundleRepository bundleRepository,
            OperationConfigTypedVersionRepository typedVersionRepository
    ) {
        this.calendarRepository = calendarRepository;
        this.productDimensionRepository = productDimensionRepository;
        this.bundleRepository = bundleRepository;
        this.typedVersionRepository = typedVersionRepository;
    }

    OperationCalendarFactorResolver(
            OperationCalendarRuleRepository calendarRepository,
            SalesProductDimensionRepository productDimensionRepository,
            OperationConfigBundleRepository bundleRepository
    ) {
        this(calendarRepository, productDimensionRepository, bundleRepository, null);
    }

    OperationCalendarFactorResolver(
            OperationCalendarRuleRepository calendarRepository,
            SalesProductDimensionRepository productDimensionRepository
    ) {
        this(calendarRepository, productDimensionRepository, null, null);
    }

    public OperationCalendarFactorMatchResult resolve(OperationCalendarFactorQuery query) {
        validate(query);
        SalesProductDimensionSnapshot product = resolveProduct(query);
        OperationConfigTypedVersionEvidence calendarVersion = OperationConfigTypedVersionEvidence.resolve(
                typedVersionRepository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        );
        OperationConfigTypedVersion typedCalendarVersion = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                typedVersionRepository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        ).orElse(null);
        List<Candidate> candidates = new ArrayList<>();
        List<OperationCalendarFactorEvidence> evidence = new ArrayList<>();
        List<OperationCalendarRule> typedRules = typedCalendarVersion == null
                ? List.of()
                : OperationConfigTypedVersionContentSupport.calendarRules(
                        typedCalendarVersion,
                        query.getOwnerUserId(),
                        query.getStoreCode(),
                        query.getSiteCode(),
                        query.getDate()
                );
        List<OperationCalendarRule> activeRules = typedRules.isEmpty()
                ? calendarRepository.listActiveRulesForFactorResolution(
                        query.getOwnerUserId(),
                        query.getStoreCode(),
                        query.getSiteCode()
                )
                : typedRules;
        boolean hasCurrentBundleRules = activeRules.stream().anyMatch(rule -> rule.getBundleVersionId() != null);
        for (OperationCalendarRule rule : activeRules) {
            if (hasCurrentBundleRules && rule.getBundleVersionId() == null) {
                continue;
            }
            if (!dateMatches(rule, query.getDate())) {
                continue;
            }
            MatchDecision decision = matches(rule, query, product);
            if (decision.matched) {
                Candidate candidate = new Candidate(
                        rule,
                        priority(rule),
                        scopePriority(rule, query),
                        decision.reason,
                        bundleEvidence(rule)
                );
                candidates.add(candidate);
                evidence.add(candidate.evidence("matched", decision.reason, calendarVersion));
            } else {
                BundleEvidence bundle = bundleEvidence(rule);
                evidence.add(new OperationCalendarFactorEvidence(
                        rule.getId(),
                        rule.getRuleName(),
                        rule.getTargetScopeType(),
                        rule.getTargetScopeValue(),
                        decision.state,
                        decision.reason,
                        priority(rule),
                        bundle.bundleVersionId,
                        bundle.bundleVersionNo,
                        bundle.bundleSourceRole,
                        bundle.bundleSourceLabel,
                        calendarVersion.getVersionNo(),
                        calendarVersion.getVersionName(),
                        calendarVersion.getSourceLabel()
                ));
            }
        }
        if (candidates.isEmpty()) {
            return new OperationCalendarFactorMatchResult(
                    BigDecimal.ONE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    calendarVersion.getVersionNo(),
                    calendarVersion.getVersionName(),
                    calendarVersion.getSourceLabel(),
                    evidence
            );
        }
        Candidate selected = candidates.stream()
                .max(candidateComparator())
                .orElseThrow();
        List<OperationCalendarFactorEvidence> finalEvidence = new ArrayList<>();
        for (OperationCalendarFactorEvidence item : evidence) {
            if (selected.rule.getId().equals(item.getRuleId())) {
                finalEvidence.add(item.withMatchState("selected", "highest_priority_matching_rule"));
            } else if ("matched".equals(item.getMatchState())) {
                finalEvidence.add(item.withMatchState("matched_lower_priority", item.getReason()));
            } else {
                finalEvidence.add(item);
            }
        }
        return new OperationCalendarFactorMatchResult(
                selected.rule.getFactorValue() == null ? BigDecimal.ONE : selected.rule.getFactorValue(),
                selected.rule.getId(),
                selected.rule.getRuleName(),
                selected.bundle.bundleVersionId,
                selected.bundle.bundleVersionNo,
                selected.bundle.bundleSourceRole,
                selected.bundle.bundleSourceLabel,
                calendarVersion.getVersionNo(),
                calendarVersion.getVersionName(),
                calendarVersion.getSourceLabel(),
                finalEvidence
        );
    }

    private void validate(OperationCalendarFactorQuery query) {
        if (query == null || query.getOwnerUserId() == null
                || !StringUtils.hasText(query.getStoreCode())
                || !StringUtils.hasText(query.getSiteCode())
                || query.getDate() == null) {
            throw new IllegalArgumentException("factor query scope and date are required");
        }
    }

    private SalesProductDimensionSnapshot resolveProduct(OperationCalendarFactorQuery query) {
        List<SalesProductDimensionSnapshot> snapshots = productDimensionRepository.list(new SalesFactQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode(),
                null,
                null,
                query.getPartnerSku(),
                query.getSku()
        ));
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    private boolean dateMatches(OperationCalendarRule rule, LocalDate date) {
        return rule.getDateFrom() != null
                && rule.getDateTo() != null
                && !date.isBefore(rule.getDateFrom())
                && !date.isAfter(rule.getDateTo());
    }

    private MatchDecision matches(
            OperationCalendarRule rule,
            OperationCalendarFactorQuery query,
            SalesProductDimensionSnapshot product
    ) {
        String type = normalize(rule.getTargetScopeType());
        if ("all_products".equals(type) || "all".equals(type)) {
            return MatchDecision.matched("all_products_scope");
        }
        if (product == null) {
            return MatchDecision.unmatched("product_projection_missing", "no_product_dimension_for_partner_sku_or_psku");
        }
        if ("brand".equals(type)) {
            return equalsNormalized(rule.getTargetScopeValue(), product.getBrand())
                    ? MatchDecision.matched("brand_matched")
                    : MatchDecision.unmatched("not_matched", "brand_not_matched");
        }
        if ("product_fulltype".equals(type) || "fulltype".equals(type)) {
            return equalsNormalized(rule.getTargetScopeValue(), product.getProductFulltype())
                    ? MatchDecision.matched("product_fulltype_matched")
                    : MatchDecision.unmatched("not_matched", "product_fulltype_not_matched");
        }
        if ("category".equals(type)) {
            return startsWithNormalized(product.getProductFulltype(), rule.getTargetScopeValue())
                    ? MatchDecision.matched("category_matched")
                    : MatchDecision.unmatched("not_matched", "category_not_matched");
        }
        if ("psku".equals(type) || "pskus".equals(type)) {
            return containsTarget(rule.getTargetScopeValue(), query.getSku(), product.getSku())
                    || containsTarget(rule.getTargetScopeValue(), query.getPartnerSku(), product.getPartnerSku())
                    ? MatchDecision.matched("psku_matched")
                    : MatchDecision.unmatched("not_matched", "psku_not_matched");
        }
        return MatchDecision.unmatched("unsupported_target_scope", "unsupported_target_scope_type");
    }

    private Comparator<Candidate> candidateComparator() {
        return Comparator
                .comparingInt((Candidate candidate) -> candidate.bundlePriority)
                .thenComparingInt(candidate -> candidate.priority)
                .thenComparingInt(candidate -> candidate.scopePriority)
                .thenComparing(candidate -> candidate.rule.getPublishRecordId(), Comparator.nullsFirst(Long::compareTo))
                .thenComparing(candidate -> candidate.rule.getUpdatedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(candidate -> candidate.rule.getId(), Comparator.nullsFirst(Long::compareTo));
    }

    private BundleEvidence bundleEvidence(OperationCalendarRule rule) {
        if (rule == null || rule.getBundleVersionId() == null) {
            return BundleEvidence.none();
        }
        OperationConfigBundle bundle = bundleRepository == null
                ? null
                : bundleRepository.findBundle(rule.getBundleVersionId()).orElse(null);
        if (bundle == null) {
            return new BundleEvidence(
                    rule.getBundleVersionId(),
                    "OPS_CONFIG_" + rule.getBundleVersionId(),
                    rule.getPublishSourceRole(),
                    rule.getPublishSourceLabel()
            );
        }
        return new BundleEvidence(
                bundle.getId(),
                bundle.getVersionNo(),
                bundle.getPublishSourceRole(),
                bundle.getPublishSourceLabel()
        );
    }

    private int priority(OperationCalendarRule rule) {
        String type = normalize(rule.getTargetScopeType());
        if ("psku".equals(type) || "pskus".equals(type)) {
            return 400;
        }
        if ("brand".equals(type) || "product_fulltype".equals(type) || "fulltype".equals(type) || "category".equals(type)) {
            return 300;
        }
        if ("all_products".equals(type) || "all".equals(type)) {
            return 100;
        }
        return 0;
    }

    private int scopePriority(OperationCalendarRule rule, OperationCalendarFactorQuery query) {
        boolean exactStore = query.getStoreCode().equals(rule.getStoreCode());
        boolean exactSite = query.getSiteCode().equals(rule.getSiteCode());
        if (exactStore && exactSite) {
            return 30;
        }
        if (exactStore || exactSite) {
            return 20;
        }
        return 10;
    }

    private boolean containsTarget(String rawTargets, String... values) {
        if (!StringUtils.hasText(rawTargets)) {
            return false;
        }
        String[] targets = rawTargets.split("[,，\\s]+");
        for (String target : targets) {
            for (String value : values) {
                if (equalsNormalized(target, value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean equalsNormalized(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && normalize(left).equals(normalize(right));
    }

    private boolean startsWithNormalized(String value, String prefix) {
        return StringUtils.hasText(value) && StringUtils.hasText(prefix) && normalize(value).startsWith(normalize(prefix));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class Candidate {
        private final OperationCalendarRule rule;
        private final int priority;
        private final int scopePriority;
        private final String reason;
        private final BundleEvidence bundle;
        private final int bundlePriority;

        private Candidate(
                OperationCalendarRule rule,
                int priority,
                int scopePriority,
                String reason,
                BundleEvidence bundle
        ) {
            this.rule = rule;
            this.priority = priority;
            this.scopePriority = scopePriority;
            this.reason = reason;
            this.bundle = bundle;
            this.bundlePriority = bundle.bundleVersionId == null ? 0 : 1;
        }

        private OperationCalendarFactorEvidence evidence(
                String matchState,
                String reason,
                OperationConfigTypedVersionEvidence calendarVersion
        ) {
            return new OperationCalendarFactorEvidence(
                    rule.getId(),
                    rule.getRuleName(),
                    rule.getTargetScopeType(),
                    rule.getTargetScopeValue(),
                    matchState,
                    reason,
                    priority,
                    bundle.bundleVersionId,
                    bundle.bundleVersionNo,
                    bundle.bundleSourceRole,
                    bundle.bundleSourceLabel,
                    calendarVersion.getVersionNo(),
                    calendarVersion.getVersionName(),
                    calendarVersion.getSourceLabel()
            );
        }
    }

    private static class BundleEvidence {
        private final Long bundleVersionId;
        private final String bundleVersionNo;
        private final String bundleSourceRole;
        private final String bundleSourceLabel;

        private BundleEvidence(
                Long bundleVersionId,
                String bundleVersionNo,
                String bundleSourceRole,
                String bundleSourceLabel
        ) {
            this.bundleVersionId = bundleVersionId;
            this.bundleVersionNo = bundleVersionNo;
            this.bundleSourceRole = bundleSourceRole;
            this.bundleSourceLabel = bundleSourceLabel;
        }

        private static BundleEvidence none() {
            return new BundleEvidence(null, null, null, null);
        }
    }

    private static class MatchDecision {
        private final boolean matched;
        private final String state;
        private final String reason;

        private MatchDecision(boolean matched, String state, String reason) {
            this.matched = matched;
            this.state = state;
            this.reason = reason;
        }

        private static MatchDecision matched(String reason) {
            return new MatchDecision(true, "matched", reason);
        }

        private static MatchDecision unmatched(String state, String reason) {
            return new MatchDecision(false, state, reason);
        }
    }
}
