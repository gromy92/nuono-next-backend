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
import java.time.LocalDate;
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
public class OperationCalendarRuleService {

    public static final String DOMAIN_TYPE = "operation_business_calendar_rule";

    private final OperationCalendarRuleRepository repository;
    private final OperationConfigBundleRepository bundleRepository;
    private final OperationConfigScopeService scopeService;
    private final VersionPublishService versionPublishService;
    private final Clock clock;

    @Autowired
    public OperationCalendarRuleService(
            OperationCalendarRuleRepository repository,
            OperationConfigBundleRepository bundleRepository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService
    ) {
        this(repository, bundleRepository, scopeService, versionPublishService, Clock.systemDefaultZone());
    }

    OperationCalendarRuleService(
            OperationCalendarRuleRepository repository,
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

    public OperationCalendarRule saveDraft(
            BusinessAccessContext context,
            OperationCalendarRuleDraftCommand command
    ) {
        validateCommand(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        validateBundleDraftContext(context, command);
        if (command.getId() == null) {
            OperationCalendarRule created = createDraft(context, command);
            refreshBundleActivityRuleCount(created.getBundleVersionId(), context.getSessionUserId());
            return created;
        }
        OperationCalendarRule updated = updateDraft(context, command);
        refreshBundleActivityRuleCount(updated.getBundleVersionId(), context.getSessionUserId());
        return updated;
    }

    public OperationCalendarRule publish(
            BusinessAccessContext context,
            Long ruleId,
            String message
    ) {
        return publish(context, List.of(), ruleId, message);
    }

    public OperationCalendarRule publish(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ruleId,
            String message
    ) {
        OperationCalendarRule rule = requireRule(ruleId);
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
                message
        ));
        OperationCalendarRule published = rule.withPublishStatus(
                OperationConfigPublishStatus.PUBLISHED,
                context.getSessionUserId(),
                now(),
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context)
        );
        repository.updateRule(published);
        return published;
    }

    public OperationCalendarRule disable(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ruleId,
            String message
    ) {
        OperationCalendarRule rule = requireRule(ruleId);
        requireLegacyRuleLevelPublishing(rule);
        if (rule.getPublishRecordId() == null) {
            throw new IllegalStateException("Calendar rule missing publish record: " + rule.getId());
        }
        scopeService.requireStoreSiteScope(
                context,
                bossUserIds,
                rule.getOwnerUserId(),
                rule.getStoreCode(),
                rule.getSiteCode()
        );
        versionPublishService.disable(new VersionPublishActionCommand(
                rule.getPublishRecordId(),
                operator(context),
                snapshot(rule),
                message
        ));
        OperationCalendarRule disabled = rule.withDisabled(context.getSessionUserId(), now());
        repository.updateRule(disabled);
        return disabled;
    }

    public List<OperationCalendarRule> listActive(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return listActive(context, List.of(), ownerUserId, storeCode, siteCode);
    }

    public List<OperationCalendarRule> listActive(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        return activeCompatibilityView(repository.listActiveRules(ownerUserId, storeCode, siteCode));
    }

    public List<OperationCalendarRule> listActive(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        if (bundleVersionId == null) {
            return activeCompatibilityView(repository.listActiveRules(ownerUserId, storeCode, siteCode));
        }
        BundleContext bundleContext = requireBundleVisible(context, bundleVersionId);
        requireScopeInBundle(bundleContext.getBundle(), ownerUserId, storeCode, siteCode);
        if (!VersionPublishStatus.PUBLISHED.equals(bundleContext.getPublishRecord().getStatus())) {
            return List.of();
        }
        return repository.listRulesByBundleVersion(bundleVersionId).stream()
                .filter(OperationCalendarRule::isEnabled)
                .filter(rule -> scopeMatches(rule, ownerUserId, storeCode, siteCode))
                .map(this::activeCompatibilityView)
                .collect(Collectors.toList());
    }

    public List<OperationCalendarRule> copyPreviousYear(
            BusinessAccessContext context,
            OperationCalendarRuleCopyPreviousYearCommand command
    ) {
        validateCopyCommand(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        LocalDate sourceFrom = LocalDate.of(command.getSourceYear(), 1, 1);
        LocalDate sourceTo = LocalDate.of(command.getSourceYear(), 12, 31);
        int yearDelta = command.getTargetYear() - command.getSourceYear();
        List<OperationCalendarRule> sources = repository.listRules(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode()
                ).stream()
                .filter(rule -> OperationConfigPublishStatus.PUBLISHED.equals(rule.getPublishStatus()))
                .filter(rule -> overlaps(rule, sourceFrom, sourceTo))
                .sorted(Comparator
                        .comparing(OperationCalendarRule::getDateFrom)
                        .thenComparing(OperationCalendarRule::getId))
                .collect(Collectors.toList());
        List<OperationCalendarRule> copied = new ArrayList<>();
        for (OperationCalendarRule source : sources) {
            copied.add(saveDraft(context, new OperationCalendarRuleDraftCommand(
                    null,
                    command.getBossUserIds(),
                    source.getOwnerUserId(),
                    source.getStoreCode(),
                    source.getSiteCode(),
                    copiedName(source.getRuleName(), command.getSourceYear(), command.getTargetYear()),
                    source.getActivityType(),
                    source.getDateFrom().plusYears(yearDelta),
                    source.getDateTo().plusYears(yearDelta),
                    source.getRecurringExpression(),
                    source.getTargetScopeType(),
                    source.getTargetScopeValue(),
                    source.getFactorValue(),
                    source.getFactorPurpose(),
                    source.isEnabled()
            )));
        }
        return copied;
    }

    public List<OperationCalendarRule> batchUpdateDrafts(
            BusinessAccessContext context,
            OperationCalendarRuleBatchUpdateCommand command
    ) {
        validateBatchCommand(command);
        scopeService.requireStoreSiteScope(
                context,
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        List<OperationCalendarRule> updatedRules = new ArrayList<>();
        for (Long ruleId : command.getRuleIds()) {
            OperationCalendarRule existing = requireRule(ruleId);
            requireDraft(existing);
            requireSameScope(command, existing);
            OperationCalendarRule updated = updateDraft(context, new OperationCalendarRuleDraftCommand(
                    existing.getId(),
                    command.getBossUserIds(),
                    existing.getOwnerUserId(),
                    existing.getStoreCode(),
                    existing.getSiteCode(),
                    existing.getRuleName(),
                    existing.getActivityType(),
                    existing.getDateFrom(),
                    existing.getDateTo(),
                    existing.getRecurringExpression(),
                    existing.getTargetScopeType(),
                    existing.getTargetScopeValue(),
                    command.getFactorValue() == null ? existing.getFactorValue() : command.getFactorValue(),
                    existing.getFactorPurpose(),
                    command.getEnabled() == null ? existing.isEnabled() : command.getEnabled(),
                    existing.getBundleVersionId()
            ));
            updatedRules.add(updated);
        }
        return updatedRules;
    }

    public List<OperationCalendarRule> history(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return history(context, List.of(), ownerUserId, storeCode, siteCode);
    }

    public List<OperationCalendarRule> history(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        return repository.listRules(ownerUserId, storeCode, siteCode);
    }

    public List<OperationCalendarRule> history(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long bundleVersionId
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        if (bundleVersionId == null) {
            return repository.listRules(ownerUserId, storeCode, siteCode);
        }
        BundleContext bundleContext = requireBundleVisible(context, bundleVersionId);
        requireScopeInBundle(bundleContext.getBundle(), ownerUserId, storeCode, siteCode);
        return repository.listRulesByBundleVersion(bundleVersionId).stream()
                .filter(rule -> scopeMatches(rule, ownerUserId, storeCode, siteCode))
                .collect(Collectors.toList());
    }

    private OperationCalendarRule createDraft(
            BusinessAccessContext context,
            OperationCalendarRuleDraftCommand command
    ) {
        Long ruleId = repository.nextRuleId();
        LocalDateTime now = now();
        VersionPublishRecord publishRecord = versionPublishService.createDraft(new VersionPublishDraftCommand(
                DOMAIN_TYPE,
                ruleId,
                "calendar-rule-" + ruleId + "-v1",
                command.getStoreCode() + "/" + command.getSiteCode(),
                operator(context)
        ));
        OperationCalendarRule rule = new OperationCalendarRule(
                ruleId,
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getRuleName(),
                command.getActivityType(),
                command.getDateFrom(),
                command.getDateTo(),
                command.getRecurringExpression(),
                command.getTargetScopeType(),
                command.getTargetScopeValue(),
                command.getFactorValue(),
                command.getFactorPurpose(),
                command.isEnabled(),
                publishRecord.getId(),
                OperationConfigPublishStatus.DRAFT,
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context),
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now,
                command.getBundleVersionId()
        );
        repository.insertRule(rule);
        return rule;
    }

    private OperationCalendarRule updateDraft(
            BusinessAccessContext context,
            OperationCalendarRuleDraftCommand command
    ) {
        OperationCalendarRule existing = requireRule(command.getId());
        requireDraft(existing);
        requireSameBundle(existing, command);
        validateBundleDraftContext(context, command);
        OperationCalendarRule updated = existing.withDraftUpdate(command, context.getSessionUserId(), now());
        repository.updateRule(updated);
        versionPublishService.updateDraft(new VersionPublishDraftUpdateCommand(
                existing.getPublishRecordId(),
                updated.getStoreCode() + "/" + updated.getSiteCode(),
                operator(context),
                snapshot(existing),
                snapshot(updated),
                "edit calendar rule draft"
        ));
        return updated;
    }

    private OperationCalendarRule requireRule(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new IllegalArgumentException("ruleId must be positive");
        }
        OperationCalendarRule rule = repository.findRule(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Calendar rule not found: " + ruleId);
        }
        return rule;
    }

    private void requireDraft(OperationCalendarRule rule) {
        if (!OperationConfigPublishStatus.DRAFT.equals(rule.getPublishStatus())) {
            throw new IllegalStateException("Only draft calendar rules can be changed: " + rule.getPublishStatus());
        }
        if (rule.getPublishRecordId() == null) {
            throw new IllegalStateException("Calendar rule missing publish record: " + rule.getId());
        }
    }

    private void requireLegacyRuleLevelPublishing(OperationCalendarRule rule) {
        if (rule.getBundleVersionId() != null) {
            throw new IllegalStateException("Bundle-owned calendar rules are published by the enclosing operations config bundle");
        }
    }

    private void validateBundleDraftContext(
            BusinessAccessContext context,
            OperationCalendarRuleDraftCommand command
    ) {
        if (command.getBundleVersionId() == null) {
            return;
        }
        BundleContext bundleContext = requireBundleVisible(context, command.getBundleVersionId());
        if (!VersionPublishStatus.DRAFT.equals(bundleContext.getPublishRecord().getStatus())) {
            throw new IllegalStateException("Only draft operation config bundles can be edited");
        }
        requireScopeInBundle(bundleContext.getBundle(), command.getOwnerUserId(), command.getStoreCode(), command.getSiteCode());
    }

    private void requireSameBundle(OperationCalendarRule existing, OperationCalendarRuleDraftCommand command) {
        Long existingBundleId = existing.getBundleVersionId();
        Long requestedBundleId = command.getBundleVersionId();
        if (existingBundleId == null && requestedBundleId == null) {
            return;
        }
        if (existingBundleId == null || !existingBundleId.equals(requestedBundleId)) {
            throw new IllegalStateException("Calendar rule cannot move between operation config bundles");
        }
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
            throw new BusinessAccessDeniedException("当前活动因子不属于该运营配置版本作用范围。");
        }
    }

    private boolean scopeMatches(OperationCalendarRule rule, Long ownerUserId, String storeCode, String siteCode) {
        return ownerUserId.equals(rule.getOwnerUserId())
                && (isBossWideScope(rule.getStoreCode(), rule.getSiteCode())
                        || (normalize(storeCode).equals(normalize(rule.getStoreCode()))
                                && normalize(siteCode).equals(normalize(rule.getSiteCode()))));
    }

    private boolean isBossWideScope(String storeCode, String siteCode) {
        return OperationConfigScopeService.BOSS_WIDE_STORE_CODE.equals(normalize(storeCode))
                && OperationConfigScopeService.BOSS_WIDE_SITE_CODE.equals(normalize(siteCode));
    }

    private List<OperationCalendarRule> activeCompatibilityView(List<OperationCalendarRule> rules) {
        return rules.stream()
                .map(this::activeCompatibilityView)
                .collect(Collectors.toList());
    }

    private OperationCalendarRule activeCompatibilityView(OperationCalendarRule rule) {
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void refreshBundleActivityRuleCount(Long bundleVersionId, Long updatedBy) {
        if (bundleVersionId == null) {
            return;
        }
        bundleRepository.updateActivityRuleCount(
                bundleVersionId,
                repository.countRulesByBundleVersion(bundleVersionId),
                updatedBy
        );
    }

    private void validateCommand(OperationCalendarRuleDraftCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (!StringUtils.hasText(command.getRuleName())) {
            throw new IllegalArgumentException("ruleName is required");
        }
        if (!StringUtils.hasText(command.getActivityType())) {
            throw new IllegalArgumentException("activityType is required");
        }
        if (command.getDateFrom() == null || command.getDateTo() == null || command.getDateTo().isBefore(command.getDateFrom())) {
            throw new IllegalArgumentException("date range is invalid");
        }
        if (!StringUtils.hasText(command.getTargetScopeType())) {
            throw new IllegalArgumentException("targetScopeType is required");
        }
        if (command.getFactorValue() == null || BigDecimal.ZERO.compareTo(command.getFactorValue()) >= 0) {
            throw new IllegalArgumentException("factorValue must be positive");
        }
        if (!StringUtils.hasText(command.getFactorPurpose())) {
            throw new IllegalArgumentException("factorPurpose is required");
        }
    }

    private void validateCopyCommand(OperationCalendarRuleCopyPreviousYearCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.getOwnerUserId() == null || !StringUtils.hasText(command.getStoreCode()) || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("scope is required");
        }
        if (command.getSourceYear() < 2000 || command.getTargetYear() < 2000) {
            throw new IllegalArgumentException("year is invalid");
        }
    }

    private void validateBatchCommand(OperationCalendarRuleBatchUpdateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        if (command.getOwnerUserId() == null || !StringUtils.hasText(command.getStoreCode()) || !StringUtils.hasText(command.getSiteCode())) {
            throw new IllegalArgumentException("scope is required");
        }
        if (command.getRuleIds() == null || command.getRuleIds().isEmpty()) {
            throw new IllegalArgumentException("ruleIds are required");
        }
        if (command.getEnabled() == null && command.getFactorValue() == null) {
            throw new IllegalArgumentException("batch update requires a change");
        }
        if (command.getFactorValue() != null && BigDecimal.ZERO.compareTo(command.getFactorValue()) >= 0) {
            throw new IllegalArgumentException("factorValue must be positive");
        }
    }

    private void requireSameScope(OperationCalendarRuleBatchUpdateCommand command, OperationCalendarRule rule) {
        if (!command.getOwnerUserId().equals(rule.getOwnerUserId())
                || !command.getStoreCode().equals(rule.getStoreCode())
                || !command.getSiteCode().equals(rule.getSiteCode())) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
    }

    private boolean overlaps(OperationCalendarRule rule, LocalDate from, LocalDate to) {
        return rule.getDateFrom() != null
                && rule.getDateTo() != null
                && !rule.getDateTo().isBefore(from)
                && !rule.getDateFrom().isAfter(to);
    }

    private String copiedName(String sourceName, int sourceYear, int targetYear) {
        String safeName = sourceName == null ? "Copied calendar rule" : sourceName;
        String sourceYearText = String.valueOf(sourceYear);
        if (safeName.contains(sourceYearText)) {
            return safeName.replace(sourceYearText, String.valueOf(targetYear));
        }
        return safeName + " " + targetYear;
    }

    private VersionPublishOperator operator(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        return new VersionPublishOperator(context.getSessionUserId(), context.getRoleName());
    }

    private String snapshot(OperationCalendarRule rule) {
        return "{\"ruleId\":" + rule.getId()
                + ",\"name\":\"" + escape(rule.getRuleName()) + "\""
                + ",\"storeCode\":\"" + escape(rule.getStoreCode()) + "\""
                + ",\"siteCode\":\"" + escape(rule.getSiteCode()) + "\""
                + ",\"factor\":\"" + rule.getFactorValue() + "\"}";
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
    }
}
