package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.versioning.VersionPublishActionCommand;
import com.nuono.next.versioning.VersionPublishDraftCommand;
import com.nuono.next.versioning.VersionPublishDraftUpdateCommand;
import com.nuono.next.versioning.VersionPublishOperator;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishService;
import com.nuono.next.versioning.VersionPublishStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationLifecycleRuleService {

    public static final String DOMAIN_TYPE = "operation_lifecycle_rule";

    private static final long VERSION_BASE_ID = 83000L;

    private final OperationLifecycleRuleRepository repository;
    private final OperationConfigBundleRepository bundleRepository;
    private final OperationConfigScopeService scopeService;
    private final VersionPublishService versionPublishService;
    private final Clock clock;

    @Autowired
    public OperationLifecycleRuleService(
            OperationLifecycleRuleRepository repository,
            OperationConfigBundleRepository bundleRepository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService
    ) {
        this(repository, bundleRepository, scopeService, versionPublishService, Clock.systemDefaultZone());
    }

    OperationLifecycleRuleService(
            OperationLifecycleRuleRepository repository,
            OperationConfigBundleRepository bundleRepository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService,
            Clock clock
    ) {
        this.repository = repository;
        this.bundleRepository = bundleRepository;
        this.scopeService = scopeService;
        this.versionPublishService = versionPublishService;
        this.clock = clock;
    }

    public OperationLifecycleRuleStateView currentState(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return currentState(context, bossUserIds, ownerUserId, storeCode, siteCode, null);
    }

    public OperationLifecycleRuleStateView currentState(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        if (bundleVersionId != null) {
            return bundleState(context, ownerUserId, storeCode, siteCode, bundleVersionId);
        }
        OperationLifecycleRuleView current = repository.findLatestPublished(ownerUserId, storeCode, siteCode)
                .map(rule -> OperationLifecycleRuleView.fromRule(activeCompatibilityView(rule)))
                .orElse(OperationLifecycleRuleView.fallback(ownerUserId, storeCode, siteCode));
        OperationLifecycleRuleView draft = repository.findLatestDraft(ownerUserId, storeCode, siteCode)
                .map(OperationLifecycleRuleView::fromRule)
                .orElse(null);
        return new OperationLifecycleRuleStateView(
                current,
                draft,
                draft == null ? List.of() : diff(current.getThresholds(), draft.getThresholds()),
                history(ownerUserId, storeCode, siteCode),
                ownerUserId + "/" + storeCode + "/" + siteCode
        );
    }

    public OperationLifecycleRule createDraftFromCurrent(
            BusinessAccessContext context,
            OperationLifecycleRuleCreateDraftCommand command
    ) {
        validateScopeCommand(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        validateBundleDraftContext(context, command.getBundleVersionId(), command.getOwnerUserId(), command.getStoreCode(), command.getSiteCode());
        OperationLifecycleRuleView source = repository.findLatestPublished(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode()
                )
                .map(OperationLifecycleRuleView::fromRule)
                .orElse(OperationLifecycleRuleView.fallback(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode()
                ));
        return createDraft(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                source.getRuleVersion(),
                source.getThresholds(),
                command.getBundleVersionId()
        );
    }

    public OperationLifecycleRule saveDraft(
            BusinessAccessContext context,
            OperationLifecycleRuleDraftCommand command
    ) {
        validateDraftCommand(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        validateBundleDraftContext(context, command.getBundleVersionId(), command.getOwnerUserId(), command.getStoreCode(), command.getSiteCode());
        if (command.getId() == null) {
            OperationLifecycleRule created = createDraft(
                    context,
                    command.getBossUserIds(),
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    command.getSiteCode(),
                    "DEFAULT_V1",
                    command.getThresholds(),
                    command.getBundleVersionId()
            );
            refreshBundleLifecycleRuleSummary(created.getBundleVersionId(), context.getSessionUserId());
            return created;
        }
        OperationLifecycleRule existing = requireRule(command.getId());
        requireDraft(existing);
        requireSameScope(command, existing);
        requireSameBundle(existing, command);
        OperationLifecycleRule updated = existing.withDraftUpdate(command, context.getSessionUserId(), now());
        repository.updateRule(updated);
        refreshBundleLifecycleRuleSummary(updated.getBundleVersionId(), context.getSessionUserId());
        versionPublishService.updateDraft(new VersionPublishDraftUpdateCommand(
                existing.getPublishRecordId(),
                scopeSummary(updated),
                operator(context),
                snapshot(existing),
                snapshot(updated),
                "edit lifecycle rule draft"
        ));
        return updated;
    }

    public OperationLifecycleRule publish(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ruleId,
            String message
    ) {
        OperationLifecycleRule rule = requireRule(ruleId);
        requireDraft(rule);
        requireLegacyRuleLevelPublishing(rule);
        scopeService.requireStoreSiteScope(
                context,
                bossUserIds,
                rule.getOwnerUserId(),
                rule.getStoreCode(),
                rule.getSiteCode()
        );
        versionPublishService.publish(new VersionPublishActionCommand(
                rule.getPublishRecordId(),
                operator(context),
                snapshot(rule),
                StringUtils.hasText(message) ? message : "publish lifecycle rule"
        ));
        OperationLifecycleRule published = rule.withPublishStatus(
                OperationConfigPublishStatus.PUBLISHED,
                context.getSessionUserId(),
                now(),
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context)
        );
        repository.updateRule(published);
        return published;
    }

    public List<OperationLifecycleRuleView> history(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        return history(ownerUserId, storeCode, siteCode);
    }

    public List<OperationLifecycleRuleView> history(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        if (bundleVersionId == null) {
            return history(ownerUserId, storeCode, siteCode);
        }
        BundleContext bundleContext = requireBundleVisible(context, bundleVersionId);
        requireScopeInBundle(bundleContext.getBundle(), ownerUserId, storeCode, siteCode);
        return repository.listRulesByBundleVersion(bundleVersionId).stream()
                .filter(rule -> scopeMatches(rule, ownerUserId, storeCode, siteCode))
                .sorted(Comparator.comparing(OperationLifecycleRule::getUpdatedAt).reversed())
                .map(rule -> OperationLifecycleRuleView.fromRule(bundleContext.isPublished()
                        ? activeCompatibilityView(rule)
                        : rule))
                .collect(Collectors.toList());
    }

    private List<OperationLifecycleRuleView> history(Long ownerUserId, String storeCode, String siteCode) {
        return repository.listRules(ownerUserId, storeCode, siteCode).stream()
                .sorted(Comparator.comparing(OperationLifecycleRule::getUpdatedAt).reversed())
                .map(OperationLifecycleRuleView::fromRule)
                .collect(Collectors.toList());
    }

    private OperationLifecycleRule createDraft(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            Long bundleVersionId
    ) {
        validateThresholds(thresholds);
        Long ruleId = repository.nextRuleId();
        String ruleVersion = "LIFECYCLE_CONFIG_v" + Math.max(1L, ruleId - VERSION_BASE_ID);
        LocalDateTime now = now();
        OperationLifecycleRule draft = new OperationLifecycleRule(
                ruleId,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds,
                null,
                OperationConfigPublishStatus.DRAFT,
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context),
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now,
                bundleVersionId
        );
        VersionPublishRecord publishRecord = versionPublishService.createDraft(new VersionPublishDraftCommand(
                DOMAIN_TYPE,
                ruleId,
                ruleVersion,
                scopeSummary(draft),
                operator(context)
        ));
        OperationLifecycleRule saved = new OperationLifecycleRule(
                ruleId,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds,
                publishRecord.getId(),
                OperationConfigPublishStatus.DRAFT,
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context),
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now,
                bundleVersionId
        );
        repository.insertRule(saved);
        return saved;
    }

    private OperationLifecycleRule requireRule(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new IllegalArgumentException("ruleId must be positive");
        }
        OperationLifecycleRule rule = repository.findRule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Lifecycle rule not found: " + ruleId);
        }
        return rule;
    }

    private void requireDraft(OperationLifecycleRule rule) {
        if (!OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus())) {
            throw new IllegalStateException("Only draft lifecycle rules can be changed: " + rule.getPublishStatus());
        }
        if (rule.getPublishRecordId() == null) {
            throw new IllegalStateException("Lifecycle rule missing publish record: " + rule.getId());
        }
    }

    private void requireSameScope(OperationLifecycleRuleDraftCommand command, OperationLifecycleRule rule) {
        if (!command.getOwnerUserId().equals(rule.getOwnerUserId())
                || !command.getStoreCode().equals(rule.getStoreCode())
                || !command.getSiteCode().equals(rule.getSiteCode())) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
    }

    private void requireSameBundle(OperationLifecycleRule existing, OperationLifecycleRuleDraftCommand command) {
        Long existingBundleId = existing.getBundleVersionId();
        Long requestedBundleId = command.getBundleVersionId();
        if (existingBundleId == null && requestedBundleId == null) {
            return;
        }
        if (existingBundleId == null || !existingBundleId.equals(requestedBundleId)) {
            throw new IllegalStateException("Lifecycle rule cannot move between operation config bundles");
        }
    }

    private void requireLegacyRuleLevelPublishing(OperationLifecycleRule rule) {
        if (rule.getBundleVersionId() != null) {
            throw new IllegalStateException("Bundle-owned lifecycle rules are published by the enclosing operations config bundle");
        }
    }

    private void validateScopeCommand(OperationLifecycleRuleCreateDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("scope is required");
        }
    }

    private void validateDraftCommand(OperationLifecycleRuleDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("scope is required");
        }
        validateThresholds(command.getThresholds());
    }

    private void validateBundleDraftContext(
            BusinessAccessContext context,
            Long bundleVersionId,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        if (bundleVersionId == null) {
            return;
        }
        BundleContext bundleContext = requireBundleVisible(context, bundleVersionId);
        if (!VersionPublishStatus.DRAFT.equals(bundleContext.getPublishRecord().getStatus())) {
            throw new IllegalStateException("Only draft operation config bundles can be edited");
        }
        requireScopeInBundle(bundleContext.getBundle(), ownerUserId, storeCode, siteCode);
    }

    private OperationLifecycleRuleStateView bundleState(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        BundleContext bundleContext = requireBundleVisible(context, bundleVersionId);
        requireScopeInBundle(bundleContext.getBundle(), ownerUserId, storeCode, siteCode);
        List<OperationLifecycleRule> bundleRules = repository.listRulesByBundleVersion(bundleVersionId).stream()
                .filter(rule -> scopeMatches(rule, ownerUserId, storeCode, siteCode))
                .sorted(Comparator.comparing(OperationLifecycleRule::getUpdatedAt).reversed())
                .collect(Collectors.toList());
        OperationLifecycleRuleView current = bundleContext.isPublished() && !bundleRules.isEmpty()
                ? OperationLifecycleRuleView.fromRule(activeCompatibilityView(bundleRules.get(0)))
                : repository.findLatestPublished(ownerUserId, storeCode, siteCode)
                        .map(rule -> OperationLifecycleRuleView.fromRule(activeCompatibilityView(rule)))
                        .orElse(OperationLifecycleRuleView.fallback(ownerUserId, storeCode, siteCode));
        OperationLifecycleRuleView draft = VersionPublishStatus.DRAFT.equals(bundleContext.getPublishRecord().getStatus())
                ? bundleRules.stream()
                        .filter(rule -> OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus()))
                        .findFirst()
                        .map(OperationLifecycleRuleView::fromRule)
                        .orElse(null)
                : null;
        List<OperationLifecycleRuleView> history = bundleRules.stream()
                .map(rule -> OperationLifecycleRuleView.fromRule(bundleContext.isPublished()
                        ? activeCompatibilityView(rule)
                        : rule))
                .collect(Collectors.toList());
        return new OperationLifecycleRuleStateView(
                current,
                draft,
                draft == null ? List.of() : diff(current.getThresholds(), draft.getThresholds()),
                history,
                ownerUserId + "/" + storeCode + "/" + siteCode
        );
    }

    private BundleContext requireBundleVisible(BusinessAccessContext context, Long bundleVersionId) {
        if (bundleVersionId == null || bundleVersionId <= 0) {
            throw new IllegalArgumentException("bundleVersionId must be positive");
        }
        OperationConfigBundle bundle = bundleRepository.findBundle(bundleVersionId)
                .orElseThrow(() -> new IllegalArgumentException("operation config bundle not found"));
        if (!isBundleVisible(context, bundle)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该运营配置版本。");
        }
        return new BundleContext(bundle, resolveBundlePublishRecord(bundle));
    }

    private VersionPublishRecord resolveBundlePublishRecord(OperationConfigBundle bundle) {
        return versionPublishService.listHistory(OperationConfigBundleService.DOMAIN_TYPE, bundle.getId()).stream()
                .filter(record -> bundle.getPublishRecordId() != null && bundle.getPublishRecordId().equals(record.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("publish record not found for bundle: " + bundle.getId()));
    }

    private boolean isBundleVisible(BusinessAccessContext context, OperationConfigBundle bundle) {
        if (context == null || bundle == null) {
            return false;
        }
        String sourceRole = OperationConfigVersionSource.safeRole(bundle.getPublishSourceRole());
        if (context.isSystemAdmin()) {
            return OperationConfigVersionSource.SYSTEM_ADMIN.equals(sourceRole);
        }
        if (!context.isBusinessAccount()) {
            return false;
        }
        List<OperationConfigBundleScopeStore> scopeStores = bundleRepository.listScopeStores(bundle.getId());
        if (scopeStores.stream().anyMatch(store -> isScopeStoreVisible(context, store))) {
            return true;
        }
        return !OperationConfigVersionSource.SYSTEM_ADMIN.equals(sourceRole)
                && context.getSessionUserId() != null
                && context.getSessionUserId().equals(bundle.getCreatedBy());
    }

    private boolean isScopeStoreVisible(BusinessAccessContext context, OperationConfigBundleScopeStore store) {
        if (store == null) {
            return false;
        }
        if (isBossWideScope(store.getStoreCode(), store.getSiteCode())) {
            return store.getOwnerUserId() != null && store.getOwnerUserId().equals(context.getBusinessOwnerUserId());
        }
        if (!context.canAccessStore(store.getStoreCode())) {
            return false;
        }
        Long mappedOwnerUserId = context.resolveOwnerUserIdForStore(store.getStoreCode());
        return store.getOwnerUserId() == null
                || mappedOwnerUserId == null
                || store.getOwnerUserId().equals(mappedOwnerUserId);
    }

    private void requireScopeInBundle(
            OperationConfigBundle bundle,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        boolean included = bundleRepository.listScopeStores(bundle.getId()).stream()
                .anyMatch(scopeStore -> scopeStore.getOwnerUserId().equals(ownerUserId)
                        && (isBossWideScope(scopeStore.getStoreCode(), scopeStore.getSiteCode())
                                || (normalize(scopeStore.getStoreCode()).equals(normalize(storeCode))
                                        && normalize(scopeStore.getSiteCode()).equals(normalize(siteCode)))));
        if (!included) {
            throw new BusinessAccessDeniedException("当前生命周期规则不属于该运营配置版本作用范围。");
        }
    }

    private boolean scopeMatches(OperationLifecycleRule rule, Long ownerUserId, String storeCode, String siteCode) {
        return ownerUserId.equals(rule.getOwnerUserId())
                && (isBossWideScope(rule.getStoreCode(), rule.getSiteCode())
                        || (normalize(storeCode).equals(normalize(rule.getStoreCode()))
                                && normalize(siteCode).equals(normalize(rule.getSiteCode()))));
    }

    private boolean isBossWideScope(String storeCode, String siteCode) {
        return OperationConfigScopeService.BOSS_WIDE_STORE_CODE.equals(normalize(storeCode))
                && OperationConfigScopeService.BOSS_WIDE_SITE_CODE.equals(normalize(siteCode));
    }

    private OperationLifecycleRule activeCompatibilityView(OperationLifecycleRule rule) {
        if (rule.getBundleVersionId() == null
                || OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus())) {
            return rule;
        }
        return rule.withPublishStatus(
                OperationConfigPublishStatus.PUBLISHED,
                rule.getUpdatedBy(),
                rule.getUpdatedAt()
        );
    }

    private void refreshBundleLifecycleRuleSummary(Long bundleVersionId, Long updatedBy) {
        if (bundleVersionId == null) {
            return;
        }
        int ruleCount = repository.countRulesByBundleVersion(bundleVersionId);
        String summary = ruleCount <= 0 ? "未配置" : "生命周期规则 " + ruleCount + " 组";
        bundleRepository.updateLifecycleRuleSummary(bundleVersionId, summary, updatedBy);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateThresholds(OperationLifecycleRuleThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("thresholds are required");
        }
        requireNonNegative(thresholds.getNewMinAgeDays(), "newMinAgeDays");
        requireNonNegative(thresholds.getNewMaxAgeDays(), "newMaxAgeDays");
        if (thresholds.getNewMinAgeDays() > thresholds.getNewMaxAgeDays()) {
            throw new IllegalArgumentException("newMinAgeDays must be less than or equal to newMaxAgeDays");
        }
        requirePositive(thresholds.getHighPriceThreshold(), "highPriceThreshold");
        requireNonNegative(thresholds.getGrowthMinMonthlySales(), "growthMinMonthlySales");
        requireNonNegative(thresholds.getGrowthMinActiveSalesDays(), "growthMinActiveSalesDays");
        requireNonNegative(thresholds.getGrowthMaxVolatility(), "growthMaxVolatility");
        requireNonNegative(thresholds.getStableVolatilityMin(), "stableVolatilityMin");
        requireNonNegative(thresholds.getStableVolatilityMax(), "stableVolatilityMax");
        if (thresholds.getStableVolatilityMin().compareTo(thresholds.getStableVolatilityMax()) > 0) {
            throw new IllegalArgumentException("stableVolatilityMin must be less than or equal to stableVolatilityMax");
        }
        requireNonNegative(thresholds.getDeclineMaxVolatility(), "declineMaxVolatility");
        requireNonNegative(thresholds.getLongTailMaxVolatility(), "longTailMaxVolatility");
        requireNonNegative(thresholds.getLongTailMaxMonthlySales(), "longTailMaxMonthlySales");
    }

    private void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private void requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
    }

    private List<OperationLifecycleRuleDiffView> diff(
            OperationLifecycleRuleThresholds before,
            OperationLifecycleRuleThresholds after
    ) {
        List<OperationLifecycleRuleDiffView> diffs = new ArrayList<>();
        addDiff(diffs, "newMaxAgeDays", "新品期最长周期", before.getNewMaxAgeDays(), after.getNewMaxAgeDays());
        addDiff(diffs, "newMinAgeDays", "新品期最小周期", before.getNewMinAgeDays(), after.getNewMinAgeDays());
        addDiff(diffs, "highPriceThreshold", "高客单价阈值", before.getHighPriceThreshold(), after.getHighPriceThreshold());
        addDiff(diffs, "growthMinSalesGrowthRate", "成长期最小销量环比增长率", before.getGrowthMinSalesGrowthRate(), after.getGrowthMinSalesGrowthRate());
        addDiff(diffs, "growthMinPvGrowthRate", "成长期最小浏览环比增长率", before.getGrowthMinPvGrowthRate(), after.getGrowthMinPvGrowthRate());
        addDiff(diffs, "growthMinMonthlySales", "成长期最小月销量", before.getGrowthMinMonthlySales(), after.getGrowthMinMonthlySales());
        addDiff(diffs, "growthMinActiveSalesDays", "成长期最小月动销天数", before.getGrowthMinActiveSalesDays(), after.getGrowthMinActiveSalesDays());
        addDiff(diffs, "growthMaxVolatility", "成长期最大波动率", before.getGrowthMaxVolatility(), after.getGrowthMaxVolatility());
        addDiff(diffs, "stableMinPvGrowthRate", "稳定期最小浏览环比增长率", before.getStableMinPvGrowthRate(), after.getStableMinPvGrowthRate());
        addDiff(diffs, "stableVolatilityMin", "稳定期波动率下限", before.getStableVolatilityMin(), after.getStableVolatilityMin());
        addDiff(diffs, "stableVolatilityMax", "稳定期波动率上限", before.getStableVolatilityMax(), after.getStableVolatilityMax());
        addDiff(diffs, "declineMaxVolatility", "衰退期最大波动率", before.getDeclineMaxVolatility(), after.getDeclineMaxVolatility());
        addDiff(diffs, "declineMaxSalesGrowthRate", "衰退销量环比阈值", before.getDeclineMaxSalesGrowthRate(), after.getDeclineMaxSalesGrowthRate());
        addDiff(diffs, "longTailMaxVolatility", "长尾期最大波动率", before.getLongTailMaxVolatility(), after.getLongTailMaxVolatility());
        addDiff(diffs, "longTailMaxMonthlySales", "长尾期最大月销量", before.getLongTailMaxMonthlySales(), after.getLongTailMaxMonthlySales());
        return diffs;
    }

    private void addDiff(List<OperationLifecycleRuleDiffView> diffs, String field, String label, Object before, Object after) {
        String beforeText = valueText(before);
        String afterText = valueText(after);
        if (!beforeText.equals(afterText)) {
            diffs.add(new OperationLifecycleRuleDiffView(field, label, beforeText, afterText));
        }
    }

    private String valueText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return String.valueOf(value);
    }

    private VersionPublishOperator operator(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        return new VersionPublishOperator(context.getSessionUserId(), context.getRoleName());
    }

    private String scopeSummary(OperationLifecycleRule rule) {
        return rule.getOwnerUserId() + "/" + rule.getStoreCode() + "/" + rule.getSiteCode();
    }

    private String snapshot(OperationLifecycleRule rule) {
        return "{\"ruleId\":" + rule.getId()
                + ",\"ruleVersion\":\"" + escape(rule.getRuleVersion()) + "\""
                + ",\"scope\":\"" + escape(scopeSummary(rule)) + "\""
                + ",\"sourceRuleVersion\":\"" + escape(rule.getSourceRuleVersion()) + "\"}";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static class BundleContext {
        private final OperationConfigBundle bundle;
        private final VersionPublishRecord publishRecord;

        private BundleContext(OperationConfigBundle bundle, VersionPublishRecord publishRecord) {
            this.bundle = bundle;
            this.publishRecord = publishRecord;
        }

        private OperationConfigBundle getBundle() {
            return bundle;
        }

        private VersionPublishRecord getPublishRecord() {
            return publishRecord;
        }

        private boolean isPublished() {
            return VersionPublishStatus.PUBLISHED.equals(publishRecord.getStatus());
        }
    }
}
