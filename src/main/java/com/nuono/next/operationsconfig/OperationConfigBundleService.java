package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.versioning.VersionPublishActionCommand;
import com.nuono.next.versioning.VersionPublishCopyCommand;
import com.nuono.next.versioning.VersionPublishDraftCommand;
import com.nuono.next.versioning.VersionPublishOperator;
import com.nuono.next.versioning.VersionPublishRecord;
import com.nuono.next.versioning.VersionPublishService;
import com.nuono.next.versioning.VersionPublishStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationConfigBundleService {

    public static final String DOMAIN_TYPE = "operation_config_bundle";
    private static final long LIFECYCLE_VERSION_BASE_ID = 83000L;

    private final OperationConfigBundleRepository repository;
    private final OperationCalendarRuleRepository calendarRuleRepository;
    private final OperationLifecycleRuleRepository lifecycleRuleRepository;
    private final OperationConfigScopeService scopeService;
    private final VersionPublishService versionPublishService;
    private final Clock clock;

    @Autowired
    public OperationConfigBundleService(
            OperationConfigBundleRepository repository,
            OperationCalendarRuleRepository calendarRuleRepository,
            OperationLifecycleRuleRepository lifecycleRuleRepository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService
    ) {
        this(
                repository,
                calendarRuleRepository,
                lifecycleRuleRepository,
                scopeService,
                versionPublishService,
                Clock.systemDefaultZone()
        );
    }

    OperationConfigBundleService(
            OperationConfigBundleRepository repository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService
    ) {
        this(repository, null, null, scopeService, versionPublishService, Clock.systemDefaultZone());
    }

    OperationConfigBundleService(
            OperationConfigBundleRepository repository,
            OperationCalendarRuleRepository calendarRuleRepository,
            OperationLifecycleRuleRepository lifecycleRuleRepository,
            OperationConfigScopeService scopeService,
            VersionPublishService versionPublishService,
            Clock clock
    ) {
        this.repository = repository;
        this.calendarRuleRepository = calendarRuleRepository;
        this.lifecycleRuleRepository = lifecycleRuleRepository;
        this.scopeService = scopeService;
        this.versionPublishService = versionPublishService;
        this.clock = clock;
    }

    public OperationConfigBundleVersionView createEmptyDraft(
            BusinessAccessContext context,
            OperationConfigBundleDraftCommand command
    ) {
        requireOperationConfigCapability(context);
        Long bundleId = repository.nextBundleId();
        String versionNo = "OPS_CONFIG_" + bundleId;
        String scopeSummary = "未设置范围";
        VersionPublishRecord publishRecord = versionPublishService.createDraft(new VersionPublishDraftCommand(
                DOMAIN_TYPE,
                bundleId,
                versionNo,
                scopeSummary,
                operator(context)
        ));
        LocalDateTime now = LocalDateTime.now(clock);
        OperationConfigBundle bundle = new OperationConfigBundle(
                bundleId,
                publishRecord.getId(),
                versionNo,
                StringUtils.hasText(command == null ? null : command.getDisplayName())
                        ? command.getDisplayName().trim()
                        : versionNo,
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context),
                scopeSummary,
                0,
                0,
                "未配置",
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now
        );
        repository.insert(bundle);
        return toView(bundle, publishRecord);
    }

    public List<OperationConfigBundleVersionView> listVersions(BusinessAccessContext context) {
        requireOperationConfigCapability(context);
        return repository.listBundles().stream()
                .filter(bundle -> isBundleVisible(context, bundle))
                .map(bundle -> toView(bundle, resolvePublishRecord(bundle)))
                .sorted(Comparator.comparing(OperationConfigBundleVersionView::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OperationConfigBundleVersionView::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    public List<OperationConfigDefaultVersionView> listDefaultVersions(BusinessAccessContext context) {
        requireOperationConfigCapability(context);
        if (!context.isSystemAdmin()) {
            return List.of();
        }
        return List.of(defaultCalendarVersion(), defaultLifecycleVersion());
    }

    public OperationConfigBundleVersionView updateScope(
            BusinessAccessContext context,
            Long bundleId,
            OperationConfigBundleScopeCommand command
    ) {
        requireOperationConfigCapability(context);
        OperationConfigBundle bundle = repository.findBundle(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("operation config bundle not found"));
        requireBundleVisible(context, bundle);
        List<OperationConfigBundleScopeStore> scopeStores = resolveScopeStores(context, command);
        repository.replaceScopeStores(bundle.getId(), scopeStores);
        repository.updateScopeSummary(
                bundle.getId(),
                scopeSummary(scopeStores),
                scopeStores.size(),
                context.getSessionUserId()
        );
        OperationConfigBundle updated = repository.findBundle(bundle.getId()).orElse(bundle);
        return toView(updated, resolvePublishRecord(updated));
    }

    public OperationConfigBundleVersionView publish(
            BusinessAccessContext context,
            Long bundleId,
            String message
    ) {
        requireOperationConfigCapability(context);
        OperationConfigBundle bundle = repository.findBundle(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("operation config bundle not found"));
        requireBundleVisible(context, bundle);
        List<OperationConfigBundleScopeStore> scopeStores = repository.listScopeStores(bundle.getId());
        validatePublishScope(context, scopeStores);
        OperationConfigBundle refreshed = refreshContentSummary(bundle, context.getSessionUserId());
        VersionPublishRecord published = versionPublishService.publish(new VersionPublishActionCommand(
                refreshed.getPublishRecordId(),
                operator(context),
                snapshot(refreshed, scopeStores),
                StringUtils.hasText(message) ? message.trim() : "publish operation config bundle"
        ));
        markPreviousSameScopeVersionsHistorical(context, refreshed, scopeStores);
        return toView(refreshed, published);
    }

    public OperationConfigCurrentBundleView resolveCurrent(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        requireOperationConfigCapability(context);
        requireConcreteScopeReadable(context, ownerUserId, storeCode, siteCode);
        CurrentCandidate selected = null;
        for (OperationConfigBundle bundle : repository.listBundles()) {
            if (!isBundleVisible(context, bundle)) {
                continue;
            }
            VersionPublishRecord publishRecord = resolvePublishRecord(bundle);
            if (!VersionPublishStatus.PUBLISHED.equals(publishRecord.getStatus())) {
                continue;
            }
            CurrentScopeMatch match = matchScope(
                    repository.listScopeStores(bundle.getId()),
                    ownerUserId,
                    storeCode,
                    siteCode
            );
            if (match == null) {
                continue;
            }
            CurrentCandidate candidate = new CurrentCandidate(bundle, publishRecord, match);
            if (isBetterCurrentCandidate(candidate, selected)) {
                selected = candidate;
            }
        }
        if (selected == null) {
            return new OperationConfigCurrentBundleView(null, "NONE");
        }
        return new OperationConfigCurrentBundleView(
                toView(selected.getBundle(), selected.getPublishRecord()),
                selected.getMatch().getMatchType()
        );
    }

    private OperationConfigDefaultVersionView defaultCalendarVersion() {
        return new OperationConfigDefaultVersionView(
                "DEFAULT_CALENDAR_CONFIG",
                "默认日历配置",
                "business_calendar",
                "SYSTEM_DEFAULT",
                "系统默认",
                "数据-工作表1.csv",
                "8 个日期范围，5 个系数/选择项",
                List.of(
                        defaultItem("业务日历", "斋月 (Ramadan)", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "开斋节 (Eid al-Fitr)", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "古尔邦节 (Eid al-Adha)", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "白色星期五", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "黄色星期五", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "双十一 (11.11)", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "开学季模式", "提前一年", "日期范围", null, null, null),
                        defaultItem("业务日历", "夏季模式", "提前一年", "日期范围", null, null, null),
                        defaultItem("历史数据推算", "节日爆发系数", "每周1", null, null, "类目/系数", null),
                        defaultItem("历史数据推算", "月度薪酬爆发系数", "每月5日", null, null, "类目/系数/日期", null),
                        defaultItem("历史数据推算", "流行产品衰退系数", "每3天", null, null, "关键词/系数", null),
                        defaultItem("上架选择", "流行产品关键词", "上架选择", "字符串或选择", null, null, null),
                        defaultItem("上架选择", "季节产品", "上架选择", "选择 夏季/雨季", null, null, null)
                )
        );
    }

    private OperationConfigDefaultVersionView defaultLifecycleVersion() {
        OperationLifecycleRuleThresholds thresholds = OperationLifecycleRuleThresholds.defaultV1();
        return new OperationConfigDefaultVersionView(
                "DEFAULT_LIFECYCLE_CONFIG",
                "默认生命周期配置",
                "product_lifecycle",
                "SYSTEM_DEFAULT",
                "系统默认",
                "数据-工作表1.csv",
                "14 条 DEFAULT_V1 配置",
                List.of(
                        defaultItem("新品期", "新品期最长周期", "随时", "数值", formatInteger(thresholds.getNewMaxAgeDays()), null, null),
                        defaultItem("新品期", "新品期最小周期", "随时", "数值", formatInteger(thresholds.getNewMinAgeDays()), null, null),
                        defaultItem("新品期", "高客单价阈值", "随时", "数值", formatDecimal(thresholds.getHighPriceThreshold()), null, "高客单价可能生命周期不明显"),
                        defaultItem("成长期", "成长期最小销量环比增长率", "随时", "数值", formatDecimal(thresholds.getGrowthMinSalesGrowthRate()), null, null),
                        defaultItem("成长期", "成长期最小浏览环比增长率", "随时", "数值", formatDecimal(thresholds.getGrowthMinPvGrowthRate()), null, null),
                        defaultItem("成长期", "成长期最小月销量", "随时", "数值", formatDecimal(thresholds.getGrowthMinMonthlySales()), null, null),
                        defaultItem("成长期", "成长期最小月动销天数", "随时", "数值", formatInteger(thresholds.getGrowthMinActiveSalesDays()), null, "这个计算月有销量的天数"),
                        defaultItem("成长期", "成长期最大波动率", "随时", "数值", formatDecimal(thresholds.getGrowthMaxVolatility()), null, null),
                        defaultItem("稳定期", "稳定期最小浏览环比增长率", "随时", "数值", formatDecimal(thresholds.getStableMinPvGrowthRate()), null, null),
                        defaultItem("稳定期", "稳定期波动率范围", "随时", "数组", "[" + formatDecimal(thresholds.getStableVolatilityMin()) + ", " + formatDecimal(thresholds.getStableVolatilityMax()) + "]", null, null),
                        defaultItem("衰退期", "衰退期最大波动率", "随时", "数值", formatDecimal(thresholds.getDeclineMaxVolatility()), null, null),
                        defaultItem("衰退期", "衰退最小销量环比增长率", "随时", "数值", formatDecimal(thresholds.getDeclineMaxSalesGrowthRate()), null, null),
                        defaultItem("长尾期", "长尾期最大波动率", "随时", "数值", formatDecimal(thresholds.getLongTailMaxVolatility()), null, null),
                        defaultItem("长尾期", "长尾期最大月销红量", "随时", "数值", formatDecimal(thresholds.getLongTailMaxMonthlySales()), null, null)
                )
        );
    }

    public OperationConfigBundleVersionView copyVersion(BusinessAccessContext context, Long sourceBundleId) {
        requireOperationConfigCapability(context);
        OperationConfigBundle source = repository.findBundle(sourceBundleId)
                .orElseThrow(() -> new IllegalArgumentException("operation config bundle not found"));
        requireBundleVisible(context, source);
        OperationConfigBundle refreshedSource = refreshContentSummary(source, context.getSessionUserId());
        Long bundleId = repository.nextBundleId();
        String versionNo = "OPS_CONFIG_" + bundleId;
        VersionPublishRecord publishRecord = versionPublishService.copyFromVersion(new VersionPublishCopyCommand(
                refreshedSource.getPublishRecordId(),
                bundleId,
                versionNo,
                refreshedSource.getScopeSummary(),
                operator(context),
                "copy operation config bundle"
        ));
        LocalDateTime now = LocalDateTime.now(clock);
        OperationConfigBundle copied = new OperationConfigBundle(
                bundleId,
                publishRecord.getId(),
                versionNo,
                copyDisplayName(refreshedSource),
                OperationConfigVersionSource.role(context),
                OperationConfigVersionSource.labelForContext(context),
                refreshedSource.getScopeSummary(),
                refreshedSource.getAffectedStoreCount(),
                refreshedSource.getActivityRuleCount(),
                refreshedSource.getLifecycleRuleSummary(),
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now
        );
        repository.insert(copied);
        repository.replaceScopeStores(bundleId, repository.listScopeStores(refreshedSource.getId()));
        copyCalendarRules(refreshedSource.getId(), bundleId, context, now);
        copyLifecycleRules(refreshedSource.getId(), bundleId, context, now);
        return toView(copied, publishRecord);
    }

    public void deleteVersion(BusinessAccessContext context, Long bundleId) {
        requireOperationConfigCapability(context);
        OperationConfigBundle bundle = repository.findBundle(bundleId)
                .orElseThrow(() -> new IllegalArgumentException("operation config bundle not found"));
        requireBundleVisible(context, bundle);
        VersionPublishRecord publishRecord = resolvePublishRecord(bundle);
        if (VersionPublishStatus.DISABLED.equals(publishRecord.getStatus())) {
            return;
        }
        if (VersionPublishStatus.DRAFT.equals(publishRecord.getStatus())) {
            versionPublishService.disable(new VersionPublishActionCommand(
                    bundle.getPublishRecordId(),
                    operator(context),
                    "{\"deletedBundleId\":" + bundle.getId() + "}",
                    "delete operation config bundle"
            ));
            repository.deleteBundle(bundle.getId());
            return;
        }
        versionPublishService.disable(new VersionPublishActionCommand(
                bundle.getPublishRecordId(),
                operator(context),
                "{\"disabledBundleId\":" + bundle.getId() + "}",
                "disable operation config bundle"
        ));
    }

    private OperationConfigBundle refreshContentSummary(OperationConfigBundle bundle, Long updatedBy) {
        if (bundle == null || bundle.getId() == null) {
            return bundle;
        }
        int activityRuleCount = calendarRuleRepository == null
                ? safeInt(bundle.getActivityRuleCount())
                : calendarRuleRepository.countRulesByBundleVersion(bundle.getId());
        int lifecycleRuleCount = lifecycleRuleRepository == null
                ? lifecycleCountFromSummary(bundle.getLifecycleRuleSummary())
                : lifecycleRuleRepository.countRulesByBundleVersion(bundle.getId());
        String lifecycleRuleSummary = lifecycleSummary(lifecycleRuleCount);
        if (activityRuleCount != safeInt(bundle.getActivityRuleCount())) {
            repository.updateActivityRuleCount(bundle.getId(), activityRuleCount, updatedBy);
        }
        if (!lifecycleRuleSummary.equals(bundle.getLifecycleRuleSummary())) {
            repository.updateLifecycleRuleSummary(bundle.getId(), lifecycleRuleSummary, updatedBy);
        }
        return repository.findBundle(bundle.getId()).orElse(bundle);
    }

    private int lifecycleCountFromSummary(String lifecycleRuleSummary) {
        if (!StringUtils.hasText(lifecycleRuleSummary) || "未配置".equals(lifecycleRuleSummary.trim())) {
            return 0;
        }
        return 1;
    }

    private String lifecycleSummary(int lifecycleRuleCount) {
        return lifecycleRuleCount <= 0 ? "未配置" : "生命周期规则 " + lifecycleRuleCount + " 组";
    }

    private void copyCalendarRules(
            Long sourceBundleId,
            Long targetBundleId,
            BusinessAccessContext context,
            LocalDateTime now
    ) {
        if (calendarRuleRepository == null) {
            return;
        }
        for (OperationCalendarRule sourceRule : calendarRuleRepository.listRulesByBundleVersion(sourceBundleId)) {
            Long ruleId = calendarRuleRepository.nextRuleId();
            VersionPublishRecord publishRecord = versionPublishService.copyFromVersion(new VersionPublishCopyCommand(
                    sourceRule.getPublishRecordId(),
                    ruleId,
                    "calendar-rule-" + ruleId + "-v1",
                    sourceRule.getStoreCode() + "/" + sourceRule.getSiteCode(),
                    operator(context),
                    "copy operation config calendar rule"
            ));
            calendarRuleRepository.insertRule(new OperationCalendarRule(
                    ruleId,
                    sourceRule.getOwnerUserId(),
                    sourceRule.getStoreCode(),
                    sourceRule.getSiteCode(),
                    sourceRule.getRuleName(),
                    sourceRule.getActivityType(),
                    sourceRule.getDateFrom(),
                    sourceRule.getDateTo(),
                    sourceRule.getRecurringExpression(),
                    sourceRule.getTargetScopeType(),
                    sourceRule.getTargetScopeValue(),
                    sourceRule.getFactorValue(),
                    sourceRule.getFactorPurpose(),
                    sourceRule.isEnabled(),
                    publishRecord.getId(),
                    OperationConfigPublishStatus.DRAFT,
                    OperationConfigVersionSource.role(context),
                    OperationConfigVersionSource.labelForContext(context),
                    context.getSessionUserId(),
                    context.getSessionUserId(),
                    now,
                    now,
                    targetBundleId
            ));
        }
    }

    private void copyLifecycleRules(
            Long sourceBundleId,
            Long targetBundleId,
            BusinessAccessContext context,
            LocalDateTime now
    ) {
        if (lifecycleRuleRepository == null) {
            return;
        }
        for (OperationLifecycleRule sourceRule : lifecycleRuleRepository.listRulesByBundleVersion(sourceBundleId)) {
            Long ruleId = lifecycleRuleRepository.nextRuleId();
            String ruleVersion = "LIFECYCLE_CONFIG_v" + Math.max(1L, ruleId - LIFECYCLE_VERSION_BASE_ID);
            VersionPublishRecord publishRecord = versionPublishService.copyFromVersion(new VersionPublishCopyCommand(
                    sourceRule.getPublishRecordId(),
                    ruleId,
                    ruleVersion,
                    sourceRule.getStoreCode() + "/" + sourceRule.getSiteCode(),
                    operator(context),
                    "copy operation config lifecycle rule"
            ));
            lifecycleRuleRepository.insertRule(new OperationLifecycleRule(
                    ruleId,
                    sourceRule.getOwnerUserId(),
                    sourceRule.getStoreCode(),
                    sourceRule.getSiteCode(),
                    ruleVersion,
                    sourceRule.getRuleVersion(),
                    sourceRule.getThresholds(),
                    publishRecord.getId(),
                    OperationConfigPublishStatus.DRAFT,
                    OperationConfigVersionSource.role(context),
                    OperationConfigVersionSource.labelForContext(context),
                    context.getSessionUserId(),
                    context.getSessionUserId(),
                    now,
                    now,
                    targetBundleId
            ));
        }
    }

    private void markPreviousSameScopeVersionsHistorical(
            BusinessAccessContext context,
            OperationConfigBundle publishedBundle,
            List<OperationConfigBundleScopeStore> publishedScopeStores
    ) {
        String publishedScopeKey = normalizedScopeSet(publishedScopeStores);
        if (!StringUtils.hasText(publishedScopeKey)) {
            return;
        }
        for (OperationConfigBundle candidate : repository.listBundles()) {
            if (candidate.getId().equals(publishedBundle.getId())) {
                continue;
            }
            VersionPublishRecord candidateRecord = resolvePublishRecord(candidate);
            if (!VersionPublishStatus.PUBLISHED.equals(candidateRecord.getStatus())) {
                continue;
            }
            if (!publishedScopeKey.equals(normalizedScopeSet(repository.listScopeStores(candidate.getId())))) {
                continue;
            }
            versionPublishService.markHistorical(
                    candidate.getPublishRecordId(),
                    operator(context),
                    "superseded by operation config bundle " + publishedBundle.getId()
            );
        }
    }

    private String normalizedScopeSet(List<OperationConfigBundleScopeStore> scopeStores) {
        if (scopeStores == null || scopeStores.isEmpty()) {
            return "";
        }
        return scopeStores.stream()
                .map(store -> store.getOwnerUserId() + "|"
                        + normalize(store.getStoreCode()) + "|"
                        + normalize(store.getSiteCode()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    private VersionPublishRecord resolvePublishRecord(OperationConfigBundle bundle) {
        if (bundle == null || bundle.getId() == null) {
            throw new IllegalStateException("operation config bundle is missing id");
        }
        List<VersionPublishRecord> records = versionPublishService.listHistory(DOMAIN_TYPE, bundle.getId());
        Optional<VersionPublishRecord> byId = records.stream()
                .filter(record -> bundle.getPublishRecordId() != null && bundle.getPublishRecordId().equals(record.getId()))
                .findFirst();
        return byId.orElseGet(() -> records.stream()
                .max(Comparator.comparing(VersionPublishRecord::getCreatedAt)
                        .thenComparing(VersionPublishRecord::getId))
                .orElseThrow(() -> new IllegalStateException("publish record not found for bundle: " + bundle.getId())));
    }

    private OperationConfigBundleVersionView toView(
            OperationConfigBundle bundle,
            VersionPublishRecord publishRecord
    ) {
        return new OperationConfigBundleVersionView(
                bundle.getId(),
                publishRecord.getId(),
                publishRecord.getVersionNo(),
                bundle.getDisplayName(),
                publishRecord.getStatus().name(),
                bundle.getPublishSourceRole(),
                bundle.getPublishSourceLabel(),
                bundle.getScopeSummary(),
                bundle.getAffectedStoreCount(),
                bundle.getActivityRuleCount(),
                bundle.getLifecycleRuleSummary(),
                publishRecord.getPublishedBy(),
                publishRecord.getPublishedAt(),
                bundle.getCreatedBy(),
                bundle.getCreatedAt(),
                repository.listScopeStores(bundle.getId())
        );
    }

    private void requireBundleVisible(BusinessAccessContext context, OperationConfigBundle bundle) {
        if (!isBundleVisible(context, bundle)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该运营配置版本。");
        }
    }

    private boolean isBundleVisible(BusinessAccessContext context, OperationConfigBundle bundle) {
        if (bundle == null) {
            return false;
        }
        String sourceRole = OperationConfigVersionSource.safeRole(bundle.getPublishSourceRole());
        if (context.isSystemAdmin()) {
            return OperationConfigVersionSource.SYSTEM_ADMIN.equals(sourceRole);
        }
        if (!context.isBusinessAccount()) {
            return false;
        }
        List<OperationConfigBundleScopeStore> scopeStores = repository.listScopeStores(bundle.getId());
        if (scopeStores.isEmpty()) {
            return !OperationConfigVersionSource.SYSTEM_ADMIN.equals(sourceRole)
                    && context.getSessionUserId() != null
                    && context.getSessionUserId().equals(bundle.getCreatedBy());
        }
        if (scopeStores.stream().anyMatch(store -> isScopeStoreVisible(context, store))) {
            return true;
        }
        return false;
    }

    private boolean isScopeStoreVisible(BusinessAccessContext context, OperationConfigBundleScopeStore store) {
        if (store == null) {
            return false;
        }
        if (OperationConfigScopeService.BOSS_WIDE_STORE_CODE.equals(store.getStoreCode())
                && OperationConfigScopeService.BOSS_WIDE_SITE_CODE.equals(store.getSiteCode())) {
            return store.getOwnerUserId() != null && store.getOwnerUserId().equals(context.getBusinessOwnerUserId());
        }
        if (!context.canAccessStore(store.getStoreCode())) {
            return false;
        }
        Long mappedOwnerUserId = context.resolveOwnerUserIdForStore(store.getStoreCode());
        return store.getOwnerUserId() != null
                && mappedOwnerUserId != null
                && store.getOwnerUserId().equals(mappedOwnerUserId);
    }

    private String copyDisplayName(OperationConfigBundle source) {
        String baseName = StringUtils.hasText(source.getDisplayName())
                ? source.getDisplayName().trim()
                : source.getVersionNo();
        return baseName + " 副本";
    }

    private List<OperationConfigBundleScopeStore> resolveScopeStores(
            BusinessAccessContext context,
            OperationConfigBundleScopeCommand command
    ) {
        if (context.isSystemAdmin()) {
            return resolveSystemAdminScopeStores(context, command);
        }
        List<OperationConfigBundleScopeStoreCommand> stores = command == null ? List.of() : command.getStores();
        Map<String, OperationConfigBundleScopeStore> deduplicated = new LinkedHashMap<>();
        for (OperationConfigBundleScopeStoreCommand store : stores) {
            if (store == null) {
                continue;
            }
            OperationConfigStoreScope validated = scopeService.requireStoreSiteScope(
                    context,
                    command == null ? List.of() : command.getBossUserIds(),
                    store.getOwnerUserId(),
                    store.getStoreCode(),
                    store.getSiteCode()
            );
            OperationConfigBundleScopeStore scopeStore = new OperationConfigBundleScopeStore(
                    validated.getOwnerUserId(),
                    validated.getStoreCode(),
                    validated.getSiteCode()
            );
            deduplicated.put(scopeKey(scopeStore), scopeStore);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private List<OperationConfigBundleScopeStore> resolveSystemAdminScopeStores(
            BusinessAccessContext context,
            OperationConfigBundleScopeCommand command
    ) {
        List<Long> bossUserIds = normalizeIds(command == null ? List.of() : command.getBossUserIds());
        if (bossUserIds.size() != 1) {
            throw new BusinessAccessDeniedException("系统发布一次只能选择一个老板范围。");
        }
        Long ownerUserId = bossUserIds.get(0);
        List<OperationConfigBundleScopeStoreCommand> requestedStores =
                command == null ? List.of() : command.getStores();
        for (OperationConfigBundleScopeStoreCommand requestedStore : requestedStores) {
            if (requestedStore == null) {
                continue;
            }
            if (!ownerUserId.equals(requestedStore.getOwnerUserId())
                    || !isBossWideScope(requestedStore.getStoreCode(), requestedStore.getSiteCode())) {
                throw new BusinessAccessDeniedException("系统发布只允许选择老板级全部店铺范围。");
            }
        }
        OperationConfigScopeView scope = scopeService.resolveScope(context, bossUserIds);
        OperationConfigStoreScope bossWideStore = scope.getStores().stream()
                .filter(store -> ownerUserId.equals(store.getOwnerUserId()))
                .filter(store -> isBossWideScope(store.getStoreCode(), store.getSiteCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessAccessDeniedException("选择的老板范围不存在或不可访问。"));
        return List.of(new OperationConfigBundleScopeStore(
                bossWideStore.getOwnerUserId(),
                bossWideStore.getStoreCode(),
                bossWideStore.getSiteCode()
        ));
    }

    private String scopeKey(OperationConfigBundleScopeStore store) {
        return store.getOwnerUserId() + "|" + store.getStoreCode() + "|" + store.getSiteCode();
    }

    private String scopeSummary(List<OperationConfigBundleScopeStore> stores) {
        if (stores == null || stores.isEmpty()) {
            return "未设置范围";
        }
        if (stores.size() == 1) {
            OperationConfigBundleScopeStore store = stores.get(0);
            return store.getStoreCode() + " / " + store.getSiteCode();
        }
        return "已选择 " + stores.size() + " 个店铺";
    }

    private void validatePublishScope(
            BusinessAccessContext context,
            List<OperationConfigBundleScopeStore> scopeStores
    ) {
        if (scopeStores == null || scopeStores.isEmpty()) {
            throw new IllegalStateException("operation config bundle scope is required before publish");
        }
        if (context.isSystemAdmin()) {
            if (scopeStores.size() != 1
                    || scopeStores.get(0).getOwnerUserId() == null
                    || !isBossWideScope(scopeStores.get(0).getStoreCode(), scopeStores.get(0).getSiteCode())) {
                throw new BusinessAccessDeniedException("系统发布只允许选择老板级全部店铺范围。");
            }
            return;
        }
        for (OperationConfigBundleScopeStore scopeStore : scopeStores) {
            if (!isScopeStoreVisible(context, scopeStore)) {
                throw new BusinessAccessDeniedException("当前账号不能操作该运营配置版本。");
            }
        }
    }

    private void requireConcreteScopeReadable(
            BusinessAccessContext context,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (context.isSystemAdmin()) {
            scopeService.requireStoreSiteScope(context, List.of(ownerUserId), ownerUserId, storeCode, siteCode);
            return;
        }
        scopeService.requireStoreSiteScope(context, List.of(), ownerUserId, storeCode, siteCode);
    }

    private CurrentScopeMatch matchScope(
            List<OperationConfigBundleScopeStore> scopeStores,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        CurrentScopeMatch best = null;
        for (OperationConfigBundleScopeStore scopeStore : scopeStores) {
            CurrentScopeMatch match = matchScopeStore(scopeStore, ownerUserId, storeCode, siteCode);
            if (match != null && (best == null || match.getPriority() > best.getPriority())) {
                best = match;
            }
        }
        return best;
    }

    private CurrentScopeMatch matchScopeStore(
            OperationConfigBundleScopeStore scopeStore,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        if (scopeStore == null || !ownerUserId.equals(scopeStore.getOwnerUserId())) {
            return null;
        }
        if (isBossWideScope(scopeStore.getStoreCode(), scopeStore.getSiteCode())) {
            return new CurrentScopeMatch("BOSS_WIDE", 1);
        }
        if (normalize(storeCode).equals(normalize(scopeStore.getStoreCode()))
                && normalize(siteCode).equals(normalize(scopeStore.getSiteCode()))) {
            return new CurrentScopeMatch("STORE_SITE", 2);
        }
        return null;
    }

    private boolean isBetterCurrentCandidate(CurrentCandidate candidate, CurrentCandidate selected) {
        if (selected == null) {
            return true;
        }
        if (candidate.getMatch().getPriority() != selected.getMatch().getPriority()) {
            return candidate.getMatch().getPriority() > selected.getMatch().getPriority();
        }
        LocalDateTime candidatePublishedAt = candidate.getPublishRecord().getPublishedAt();
        LocalDateTime selectedPublishedAt = selected.getPublishRecord().getPublishedAt();
        if (candidatePublishedAt != null && selectedPublishedAt != null
                && !candidatePublishedAt.equals(selectedPublishedAt)) {
            return candidatePublishedAt.isAfter(selectedPublishedAt);
        }
        if (candidatePublishedAt != null && selectedPublishedAt == null) {
            return true;
        }
        if (candidatePublishedAt == null && selectedPublishedAt != null) {
            return false;
        }
        return candidate.getBundle().getId() != null
                && selected.getBundle().getId() != null
                && candidate.getBundle().getId() > selected.getBundle().getId();
    }

    private boolean isBossWideScope(String storeCode, String siteCode) {
        return OperationConfigScopeService.BOSS_WIDE_STORE_CODE.equals(normalize(storeCode))
                && OperationConfigScopeService.BOSS_WIDE_SITE_CODE.equals(normalize(siteCode));
    }

    private List<Long> normalizeIds(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Long value : values) {
            if (value != null && value > 0 && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private void requireOperationConfigCapability(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        if (!context.hasCapability(BusinessCapability.ADVANCED_OPERATIONS_CONFIG)) {
            throw new BusinessAccessDeniedException("当前账号没有对应业务菜单权限。");
        }
    }

    private VersionPublishOperator operator(BusinessAccessContext context) {
        return new VersionPublishOperator(context.getSessionUserId(), context.getRoleName());
    }

    private String snapshot(OperationConfigBundle bundle, List<OperationConfigBundleScopeStore> scopeStores) {
        return "{\"bundleId\":" + bundle.getId()
                + ",\"versionNo\":\"" + escape(bundle.getVersionNo()) + "\""
                + ",\"scopeSummary\":\"" + escape(scopeSummary(scopeStores)) + "\""
                + ",\"activityRuleCount\":" + safeInt(bundle.getActivityRuleCount())
                + ",\"lifecycleRuleSummary\":\"" + escape(bundle.getLifecycleRuleSummary()) + "\"}";
    }

    private OperationConfigDefaultVersionItemView defaultItem(
            String groupName,
            String itemName,
            String cadence,
            String valueType,
            String defaultValue,
            String resultShape,
            String note
    ) {
        return new OperationConfigDefaultVersionItemView(
                groupName,
                itemName,
                cadence,
                valueType,
                defaultValue,
                resultShape,
                note
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatInteger(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static class CurrentCandidate {
        private final OperationConfigBundle bundle;
        private final VersionPublishRecord publishRecord;
        private final CurrentScopeMatch match;

        CurrentCandidate(
                OperationConfigBundle bundle,
                VersionPublishRecord publishRecord,
                CurrentScopeMatch match
        ) {
            this.bundle = bundle;
            this.publishRecord = publishRecord;
            this.match = match;
        }

        OperationConfigBundle getBundle() {
            return bundle;
        }

        VersionPublishRecord getPublishRecord() {
            return publishRecord;
        }

        CurrentScopeMatch getMatch() {
            return match;
        }
    }

    private static class CurrentScopeMatch {
        private final String matchType;
        private final int priority;

        CurrentScopeMatch(String matchType, int priority) {
            this.matchType = matchType;
            this.priority = priority;
        }

        String getMatchType() {
            return matchType;
        }

        int getPriority() {
            return priority;
        }
    }
}
