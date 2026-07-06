package com.nuono.next.operationsconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationConfigVersionLibraryService {
    private static final List<String> REPLENISHMENT_REQUIRED_ITEM_NAMES = List.of(
            "空运运输天数",
            "空运覆盖天数",
            "海运运输天数",
            "海运覆盖天数",
            "预测窗口天数",
            "库存来源",
            "在途必须有 ETA",
            "空运只应急",
            "建议数量取整"
    );
    private static final Set<String> REPLENISHMENT_POSITIVE_INTEGER_ITEM_NAMES = Set.of(
            "空运运输天数",
            "空运覆盖天数",
            "海运运输天数",
            "海运覆盖天数",
            "预测窗口天数"
    );

    private final OperationConfigDefaultVersionCatalog defaultVersionCatalog;
    private final OperationConfigTypedVersionRepository typedVersionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationConfigVersionLibraryService(
            OperationConfigDefaultVersionCatalog defaultVersionCatalog,
            OperationConfigTypedVersionRepository typedVersionRepository
    ) {
        this.defaultVersionCatalog = defaultVersionCatalog;
        this.typedVersionRepository = typedVersionRepository;
    }

    public List<OperationConfigVersionRowView> listVersions(BusinessAccessContext context) {
        requireOperationConfigCapability(context);
        List<OperationConfigVersionRowView> rows = new ArrayList<>();
        Set<String> persistedDefaultVersionNos = new HashSet<>();
        for (OperationConfigTypedVersion version : typedVersionRepository.listVersions()) {
            if ("DELETED".equals(version.getStatus())) {
                continue;
            }
            if (canViewVersion(context, version)) {
                rows.add(toRow(context, version));
                if (isSystemDefaultVersion(version.getVersionNo())) {
                    persistedDefaultVersionNos.add(version.getVersionNo());
                }
            }
        }
        for (OperationConfigVersionRowView defaultRow : defaultVersionCatalog.listRows(context.isSystemAdmin())) {
            if (!persistedDefaultVersionNos.contains(defaultRow.getVersionNo())) {
                rows.add(defaultRow);
            }
        }
        return List.copyOf(rows);
    }

    public OperationConfigVersionDetailView getDetail(BusinessAccessContext context, String versionNo) {
        requireOperationConfigCapability(context);
        if (isSystemDefaultVersion(versionNo)) {
            return typedVersionRepository.findByVersionNo(versionNo)
                    .map(version -> toDetail(context, version))
                    .orElseGet(() -> defaultVersionCatalog.getDetail(versionNo, context.isSystemAdmin()));
        }
        OperationConfigTypedVersion version = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        requireVersionVisible(context, version);
        return toDetail(context, version);
    }

    public OperationConfigVersionRowView copyVersion(BusinessAccessContext context, String sourceVersionNo) {
        requireOperationConfigCapability(context);
        if (!isSystemDefaultVersion(sourceVersionNo)) {
            OperationConfigTypedVersion sourceVersion = typedVersionRepository.findByVersionNo(sourceVersionNo)
                    .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
            requireVersionVisible(context, sourceVersion);
        }
        OperationConfigVersionDetailView source = resolveDetail(sourceVersionNo);
        Long id = typedVersionRepository.nextVersionId();
        LocalDateTime now = LocalDateTime.now();
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "COPY",
                source.getStatus(),
                "DRAFT",
                source.getVersionNo(),
                now
        );
        OperationConfigTypedVersion draft = new OperationConfigTypedVersion(
                id,
                versionPrefix(source.getConfigType()) + id,
                source.getDisplayName() + " 副本",
                source.getConfigType(),
                "DRAFT",
                source.getVersionNo(),
                operatorLabel(context),
                source.getSummary(),
                source.getItems().size(),
                "未设置范围",
                serializeItems(source.getItems()),
                serializeAudit(List.of(audit)),
                context.getSessionUserId(),
                context.getSessionUserId(),
                now,
                now
        );
        typedVersionRepository.insert(draft);
        return toRow(context, draft);
    }

    public OperationConfigVersionDetailView updateVersion(
            BusinessAccessContext context,
            String versionNo,
            OperationConfigVersionUpdateRequest request
    ) {
        requireOperationConfigCapability(context);
        if (isSystemDefaultVersion(versionNo)) {
            return updateSystemDefaultVersion(context, versionNo, request);
        }
        OperationConfigTypedVersion existing = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        requireVersionVisible(context, existing);
        requireDraft(existing);
        String requestConfigType = request == null ? null : request.getConfigType();
        if (!existing.getConfigType().equals(requestConfigType)) {
            throw new IllegalArgumentException("运营配置版本类型不匹配。");
        }
        List<OperationConfigDefaultVersionItemView> items = request.toItemViews();
        validateItems(existing.getConfigType(), items);
        String displayName = textOrFallback(request.getDisplayName(), existing.getDisplayName());
        String summary = textOrFallback(request.getSummary(), summaryFor(existing.getConfigType(), items.size()));
        LocalDateTime now = LocalDateTime.now();
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "EDIT",
                existing.getStatus(),
                existing.getStatus(),
                null,
                now
        );
        OperationConfigTypedVersion updated = new OperationConfigTypedVersion(
                existing.getId(),
                existing.getVersionNo(),
                displayName,
                existing.getConfigType(),
                existing.getStatus(),
                existing.getSourceVersionNo(),
                existing.getSourceLabel(),
                summary,
                items.size(),
                existing.getScopeSummary(),
                serializeItems(items),
                appendAudit(existing.getAuditJson(), audit),
                existing.getCreatedBy(),
                context.getSessionUserId(),
                existing.getCreatedAt(),
                now
        );
        typedVersionRepository.update(updated);
        return toDetail(context, updated);
    }

    public void deleteVersion(BusinessAccessContext context, String versionNo) {
        requireOperationConfigCapability(context);
        if (isSystemDefaultVersion(versionNo)) {
            throw new IllegalStateException("系统默认版本不可删除。");
        }
        OperationConfigTypedVersion existing = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        requireVersionVisible(context, existing);
        if (!"DRAFT".equals(existing.getStatus()) && !"DISABLED".equals(existing.getStatus())) {
            throw new IllegalStateException("只有草稿或停用版本可删除。");
        }
        LocalDateTime now = LocalDateTime.now();
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "DELETE",
                existing.getStatus(),
                "DELETED",
                null,
                now
        );
        typedVersionRepository.update(new OperationConfigTypedVersion(
                existing.getId(),
                existing.getVersionNo(),
                existing.getDisplayName(),
                existing.getConfigType(),
                "DELETED",
                existing.getSourceVersionNo(),
                existing.getSourceLabel(),
                existing.getSummary(),
                existing.getItemCount(),
                existing.getScopeSummary(),
                existing.getContentJson(),
                appendAudit(existing.getAuditJson(), audit),
                existing.getCreatedBy(),
                context.getSessionUserId(),
                existing.getCreatedAt(),
                now
        ));
    }

    @Transactional
    public OperationConfigVersionDetailView publishVersion(
            BusinessAccessContext context,
            String versionNo,
            OperationConfigVersionPublishRequest request
    ) {
        requireOperationConfigCapability(context);
        if (isSystemDefaultVersion(versionNo)) {
            throw new IllegalStateException("系统默认版本不可发布。");
        }
        OperationConfigTypedVersion draft = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        requireVersionVisible(context, draft);
        requireDraft(draft);
        validateItems(draft.getConfigType(), parseItems(draft.getContentJson()));
        LocalDateTime now = LocalDateTime.now();
        String nextScopeSummary = publishScopeSummary(request);
        requirePublishScope(context, request);
        for (OperationConfigTypedVersion candidate : typedVersionRepository.listVersions()) {
            if (!candidate.getVersionNo().equals(draft.getVersionNo())
                    && draft.getConfigType().equals(candidate.getConfigType())
                    && "CURRENT".equals(candidate.getStatus())
                    && nextScopeSummary.equals(candidate.getScopeSummary())) {
                typedVersionRepository.update(new OperationConfigTypedVersion(
                        candidate.getId(),
                        candidate.getVersionNo(),
                        candidate.getDisplayName(),
                        candidate.getConfigType(),
                        "HISTORICAL",
                        candidate.getSourceVersionNo(),
                        candidate.getSourceLabel(),
                        candidate.getSummary(),
                        candidate.getItemCount(),
                        candidate.getScopeSummary(),
                        candidate.getContentJson(),
                        candidate.getAuditJson(),
                        candidate.getCreatedBy(),
                        context.getSessionUserId(),
                        candidate.getCreatedAt(),
                        now
                ));
            }
        }
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "PUBLISH",
                draft.getStatus(),
                "CURRENT",
                request == null ? null : request.getMessage(),
                now
        );
        OperationConfigTypedVersion current = new OperationConfigTypedVersion(
                draft.getId(),
                draft.getVersionNo(),
                draft.getDisplayName(),
                draft.getConfigType(),
                "CURRENT",
                draft.getSourceVersionNo(),
                draft.getSourceLabel(),
                draft.getSummary(),
                draft.getItemCount(),
                nextScopeSummary,
                draft.getContentJson(),
                appendAudit(draft.getAuditJson(), audit),
                draft.getCreatedBy(),
                context.getSessionUserId(),
                draft.getCreatedAt(),
                now
        );
        typedVersionRepository.update(current);
        return toDetail(context, current);
    }

    public OperationConfigVersionDetailView disableVersion(
            BusinessAccessContext context,
            String versionNo,
            OperationConfigVersionDisableRequest request
    ) {
        requireOperationConfigCapability(context);
        if (isSystemDefaultVersion(versionNo)) {
            throw new IllegalStateException("系统默认版本不可停用。");
        }
        OperationConfigTypedVersion existing = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        requireVersionVisible(context, existing);
        if ("DRAFT".equals(existing.getStatus())) {
            throw new IllegalStateException("草稿版本应删除，不可停用。");
        }
        if ("DISABLED".equals(existing.getStatus())) {
            return toDetail(context, existing);
        }
        LocalDateTime now = LocalDateTime.now();
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "DISABLE",
                existing.getStatus(),
                "DISABLED",
                request == null ? null : request.getReason(),
                now
        );
        OperationConfigTypedVersion disabled = new OperationConfigTypedVersion(
                existing.getId(),
                existing.getVersionNo(),
                existing.getDisplayName(),
                existing.getConfigType(),
                "DISABLED",
                existing.getSourceVersionNo(),
                existing.getSourceLabel(),
                existing.getSummary(),
                existing.getItemCount(),
                existing.getScopeSummary(),
                existing.getContentJson(),
                appendAudit(existing.getAuditJson(), audit),
                existing.getCreatedBy(),
                context.getSessionUserId(),
                existing.getCreatedAt(),
                now
        );
        typedVersionRepository.update(disabled);
        return toDetail(context, disabled);
    }

    public OperationConfigVersionDetailView currentVersion(
            BusinessAccessContext context,
            String configType,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        requireOperationConfigCapability(context);
        String exactScope = publishScopeSummary(new OperationConfigVersionPublishRequest(ownerUserId, storeCode, siteCode, null));
        requireCurrentScope(context, ownerUserId, storeCode, siteCode);
        OperationConfigTypedVersion globalCurrent = null;
        OperationConfigTypedVersion exactCurrent = null;
        for (OperationConfigTypedVersion version : typedVersionRepository.listVersions()) {
            if (configType.equals(version.getConfigType())
                    && "CURRENT".equals(version.getStatus())
                    && exactScope.equals(version.getScopeSummary())) {
                exactCurrent = OperationConfigTypedVersionContentSupport.later(exactCurrent, version);
                continue;
            }
            if (configType.equals(version.getConfigType())
                    && "CURRENT".equals(version.getStatus())
                    && "全局当前".equals(version.getScopeSummary())) {
                globalCurrent = OperationConfigTypedVersionContentSupport.later(globalCurrent, version);
            }
        }
        if (exactCurrent != null) {
            return toDetail(context, exactCurrent);
        }
        if (globalCurrent != null) {
            return toDetail(context, globalCurrent);
        }
        if (OperationConfigVersionType.BUSINESS_CALENDAR.name().equals(configType)) {
            return getDetail(context, OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO);
        }
        if (OperationConfigVersionType.PRODUCT_LIFECYCLE.name().equals(configType)) {
            return getDetail(context, OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO);
        }
        if (OperationConfigVersionType.REPLENISHMENT_PLAN.name().equals(configType)) {
            return getDetail(context, OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO);
        }
        throw new IllegalArgumentException("unsupported operation config version type");
    }

    private void requireOperationConfigCapability(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        if (!context.hasCapability(BusinessCapability.ADVANCED_OPERATIONS_CONFIG)) {
            throw new BusinessAccessDeniedException("当前账号没有对应业务菜单权限。");
        }
    }

    private OperationConfigVersionDetailView resolveDetail(String versionNo) {
        if (isSystemDefaultVersion(versionNo)) {
            return typedVersionRepository.findByVersionNo(versionNo)
                    .map(version -> toDetail(version, systemDefaultActions(true)))
                    .orElseGet(() -> defaultVersionCatalog.getDetail(versionNo, true));
        }
        OperationConfigTypedVersion version = typedVersionRepository.findByVersionNo(versionNo)
                .orElseThrow(() -> new IllegalArgumentException("operation config version not found"));
        return toDetail(version, actionsForStatus(version.getStatus()));
    }

    private boolean canViewVersion(BusinessAccessContext context, OperationConfigTypedVersion version) {
        if (isSystemDefaultVersion(version.getVersionNo())) {
            return true;
        }
        if (context.isSystemAdmin()) {
            return true;
        }
        String scopeSummary = version.getScopeSummary();
        if (scopeSummary == null || scopeSummary.trim().isEmpty() || "未设置范围".equals(scopeSummary)) {
            return version.getCreatedBy() != null && version.getCreatedBy().equals(context.getSessionUserId());
        }
        ScopeKey scope = parseScope(scopeSummary);
        if (scope == null) {
            return isCreatorDraft(context, version);
        }
        if (context.isBossAccount()) {
            return scope.ownerUserId != null
                    && scope.ownerUserId.equals(context.getBusinessOwnerUserId())
                    && (context.getStoreCodes().isEmpty() || context.canAccessStore(scope.storeCode));
        }
        if (context.isOperatorAccount()) {
            Long mappedOwner = context.resolveOwnerUserIdForStore(scope.storeCode);
            return context.canAccessStore(scope.storeCode)
                    && (scope.ownerUserId == null || scope.ownerUserId.equals(mappedOwner));
        }
        return false;
    }

    private boolean isCreatorDraft(BusinessAccessContext context, OperationConfigTypedVersion version) {
        return "DRAFT".equals(version.getStatus())
                && version.getCreatedBy() != null
                && version.getCreatedBy().equals(context.getSessionUserId());
    }

    private void requireVersionVisible(BusinessAccessContext context, OperationConfigTypedVersion version) {
        if (!canViewVersion(context, version)) {
            throw new BusinessAccessDeniedException("不能访问未授权范围的运营配置版本。");
        }
    }

    private boolean isSystemDefaultVersion(String versionNo) {
        return OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO.equals(versionNo)
                || OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO.equals(versionNo)
                || OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO.equals(versionNo);
    }

    private OperationConfigVersionDetailView updateSystemDefaultVersion(
            BusinessAccessContext context,
            String versionNo,
            OperationConfigVersionUpdateRequest request
    ) {
        if (!context.isSystemAdmin()) {
            throw new BusinessAccessDeniedException("只有系统管理员可以编辑系统默认运营配置版本。");
        }
        OperationConfigVersionDetailView catalogDetail = defaultVersionCatalog.getDetail(versionNo, true);
        String requestConfigType = request == null ? null : request.getConfigType();
        if (!catalogDetail.getConfigType().equals(requestConfigType)) {
            throw new IllegalArgumentException("运营配置版本类型不匹配。");
        }
        List<OperationConfigDefaultVersionItemView> items = request.toItemViews();
        validateItems(catalogDetail.getConfigType(), items);
        LocalDateTime now = LocalDateTime.now();
        OperationConfigTypedVersion existing = typedVersionRepository.findByVersionNo(versionNo).orElse(null);
        String displayName = textOrFallback(
                request.getDisplayName(),
                existing == null ? catalogDetail.getDisplayName() : existing.getDisplayName()
        );
        String summary = textOrFallback(request.getSummary(), summaryFor(catalogDetail.getConfigType(), items.size()));
        OperationConfigVersionAuditView audit = new OperationConfigVersionAuditView(
                context.getSessionUserId(),
                operatorLabel(context),
                "EDIT",
                existing == null ? "SYSTEM_DEFAULT" : existing.getStatus(),
                "SYSTEM_DEFAULT",
                null,
                now
        );
        OperationConfigTypedVersion updated = new OperationConfigTypedVersion(
                existing == null ? typedVersionRepository.nextVersionId() : existing.getId(),
                catalogDetail.getVersionNo(),
                displayName,
                catalogDetail.getConfigType(),
                "SYSTEM_DEFAULT",
                null,
                "系统默认",
                summary,
                items.size(),
                "全局默认",
                serializeItems(items),
                appendAudit(existing == null ? null : existing.getAuditJson(), audit),
                existing == null ? context.getSessionUserId() : existing.getCreatedBy(),
                context.getSessionUserId(),
                existing == null ? now : existing.getCreatedAt(),
                now
        );
        if (existing == null) {
            typedVersionRepository.insert(updated);
        } else {
            typedVersionRepository.update(updated);
        }
        return toDetail(context, updated);
    }

    private OperationConfigVersionRowView toRow(BusinessAccessContext context, OperationConfigTypedVersion version) {
        return new OperationConfigVersionRowView(
                version.getVersionNo(),
                version.getDisplayName(),
                version.getConfigType(),
                configTypeLabel(version.getConfigType()),
                version.getStatus(),
                statusLabel(version.getStatus()),
                version.getSourceLabel(),
                version.getSummary(),
                version.getItemCount(),
                version.getScopeSummary(),
                version.getUpdatedBy(),
                version.getUpdatedAt(),
                actionsForVersion(context, version)
        );
    }

    private OperationConfigVersionDetailView toDetail(BusinessAccessContext context, OperationConfigTypedVersion version) {
        return toDetail(version, actionsForVersion(context, version));
    }

    private OperationConfigVersionDetailView toDetail(
            OperationConfigTypedVersion version,
            List<OperationConfigVersionActionView> actions
    ) {
        return new OperationConfigVersionDetailView(
                version.getVersionNo(),
                version.getDisplayName(),
                version.getConfigType(),
                configTypeLabel(version.getConfigType()),
                version.getStatus(),
                statusLabel(version.getStatus()),
                version.getSourceLabel(),
                version.getSummary(),
                version.getItemCount(),
                version.getScopeSummary(),
                version.getUpdatedBy(),
                version.getUpdatedAt(),
                actions,
                parseItems(version.getContentJson()),
                parseAudit(version.getAuditJson())
        );
    }

    private String configTypeLabel(String configType) {
        try {
            return OperationConfigVersionType.valueOf(configType).getLabel();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return configType;
        }
    }

    private String statusLabel(String status) {
        if ("DRAFT".equals(status)) {
            return "草稿";
        }
        if ("PUBLISHED".equals(status)) {
            return "已发布";
        }
        if ("CURRENT".equals(status)) {
            return "当前生效";
        }
        if ("HISTORICAL".equals(status)) {
            return "历史";
        }
        if ("DISABLED".equals(status)) {
            return "已停用";
        }
        if ("SYSTEM_DEFAULT".equals(status)) {
            return "系统默认";
        }
        if ("DELETED".equals(status)) {
            return "已删除";
        }
        return status;
    }

    private List<OperationConfigVersionActionView> actionsForVersion(
            BusinessAccessContext context,
            OperationConfigTypedVersion version
    ) {
        if (isSystemDefaultVersion(version.getVersionNo()) || "SYSTEM_DEFAULT".equals(version.getStatus())) {
            return systemDefaultActions(context.isSystemAdmin());
        }
        return actionsForStatus(version.getStatus());
    }

    private List<OperationConfigVersionActionView> systemDefaultActions(boolean editableBySystemAdmin) {
        return List.of(
                new OperationConfigVersionActionView(
                        "EDIT",
                        "编辑",
                        editableBySystemAdmin,
                        editableBySystemAdmin ? null : "系统默认版本仅系统管理员可编辑"
                ),
                new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                new OperationConfigVersionActionView("DELETE", "删除", false, "系统默认版本不可删除"),
                new OperationConfigVersionActionView("PUBLISH", "发布", false, "系统默认版本不可发布"),
                new OperationConfigVersionActionView("DISABLE", "停用", false, "系统默认版本不可停用")
        );
    }

    private List<OperationConfigVersionActionView> actionsForStatus(String status) {
        boolean draft = "DRAFT".equals(status);
        boolean deleteAllowed = draft || "DISABLED".equals(status);
        boolean disableAllowed = "PUBLISHED".equals(status) || "CURRENT".equals(status);
        if ("DELETED".equals(status)) {
            return List.of(
                    new OperationConfigVersionActionView("EDIT", "编辑", false, "已删除版本不可编辑"),
                    new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                    new OperationConfigVersionActionView("COPY", "复制版本", false, "已删除版本不可复制"),
                    new OperationConfigVersionActionView("DELETE", "删除", false, "版本已删除"),
                    new OperationConfigVersionActionView("PUBLISH", "发布", false, "已删除版本不可发布"),
                    new OperationConfigVersionActionView("DISABLE", "停用", false, "已删除版本不可停用")
            );
        }
        return List.of(
                new OperationConfigVersionActionView("EDIT", "编辑", draft, draft ? null : "只有草稿版本可编辑"),
                new OperationConfigVersionActionView("DETAIL", "查看详情", true, null),
                new OperationConfigVersionActionView("COPY", "复制版本", true, null),
                new OperationConfigVersionActionView("DELETE", "删除", deleteAllowed, deleteAllowed ? null : "只有草稿或停用版本可删除"),
                new OperationConfigVersionActionView("PUBLISH", "发布", draft, draft ? null : "只有草稿版本可发布"),
                new OperationConfigVersionActionView("DISABLE", "停用", disableAllowed, disableAllowed ? null : "只有已发布版本可停用")
        );
    }

    private String textOrFallback(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void requireDraft(OperationConfigTypedVersion version) {
        if (!"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("只有草稿版本可编辑。");
        }
    }

    private void validateItems(String configType, List<OperationConfigDefaultVersionItemView> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("运营配置版本内容不能为空。");
        }
        for (OperationConfigDefaultVersionItemView item : items) {
            if (item.getItemName() == null || item.getItemName().trim().isEmpty()) {
                throw new IllegalArgumentException("运营配置项名称不能为空。");
            }
            if (OperationConfigVersionType.BUSINESS_CALENDAR.name().equals(configType)) {
                validateCalendarTargetScope(item);
            }
        }
        if (OperationConfigVersionType.REPLENISHMENT_PLAN.name().equals(configType)) {
            validateReplenishmentPlanItems(items);
        }
    }

    private void validateCalendarTargetScope(OperationConfigDefaultVersionItemView item) {
        String rawScope = item.getResultShape();
        if (rawScope == null || rawScope.trim().isEmpty()) {
            return;
        }
        String trimmed = rawScope.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("all_products".equals(lower) || "all".equals(lower)) {
            return;
        }
        int separator = trimmed.indexOf(':');
        String type = separator > 0 ? trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT) : lower;
        String value = separator > 0 ? trimmed.substring(separator + 1).trim() : "";
        if ("site".equals(type)) {
            validateSiteCalendarTargetValue(value, rawScope);
            return;
        }
        if ("brand".equals(type) || "product_fulltype".equals(type) || "fulltype".equals(type) || "category".equals(type) || "family".equals(type)) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("品牌、Product Fulltype、类目、大类目范围必须填写范围值。");
            }
            return;
        }
        if ("psku".equals(type) || "pskus".equals(type)) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("PSKU 范围必须填写范围值。");
            }
            return;
        }
        throw new IllegalArgumentException("不支持的业务日历范围类型：" + rawScope);
    }

    private void validateSiteCalendarTargetValue(String value, String rawScope) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("站点范围必须填写站点值。");
        }
        if (value.endsWith("|")) {
            throw new IllegalArgumentException("不支持的业务日历范围类型：" + rawScope);
        }
        String[] parts = value.split("\\|");
        if (parts.length == 0 || parts[0].trim().isEmpty()) {
            throw new IllegalArgumentException("站点范围必须填写站点值。");
        }
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index].trim();
            if (part.isEmpty()) {
                throw new IllegalArgumentException("不支持的业务日历范围类型：" + rawScope);
            }
            int separator = part.indexOf(':');
            if (separator <= 0 || separator == part.length() - 1) {
                throw new IllegalArgumentException("不支持的业务日历范围类型：" + rawScope);
            }
            String dimensionType = part.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (!"brand".equals(dimensionType)
                    && !"product_fulltype".equals(dimensionType)
                    && !"fulltype".equals(dimensionType)
                    && !"category".equals(dimensionType)
                    && !"family".equals(dimensionType)) {
                throw new IllegalArgumentException("不支持的业务日历范围类型：" + rawScope);
            }
        }
    }

    private void validateReplenishmentPlanItems(List<OperationConfigDefaultVersionItemView> items) {
        Set<String> seenRequiredNames = new HashSet<>();
        for (OperationConfigDefaultVersionItemView item : items) {
            String itemName = item.getItemName().trim();
            if (!REPLENISHMENT_REQUIRED_ITEM_NAMES.contains(itemName)) {
                continue;
            }
            if (!seenRequiredNames.add(itemName)) {
                throw new IllegalArgumentException("补货计划参数配置项重复：" + itemName + "。");
            }
            validateReplenishmentPlanItemValue(itemName, item.getDefaultValue());
        }
        for (String requiredItemName : REPLENISHMENT_REQUIRED_ITEM_NAMES) {
            if (!seenRequiredNames.contains(requiredItemName)) {
                throw new IllegalArgumentException("补货计划参数缺少必填配置项：" + requiredItemName + "。");
            }
        }
    }

    private void validateReplenishmentPlanItemValue(String itemName, String value) {
        if (REPLENISHMENT_POSITIVE_INTEGER_ITEM_NAMES.contains(itemName)) {
            validateReplenishmentPositiveInteger(itemName, value);
            return;
        }
        if ("库存来源".equals(itemName)) {
            validateReplenishmentInventorySources(value);
            return;
        }
        if ("在途必须有 ETA".equals(itemName)) {
            if (!"true".equals(normalizeText(value))) {
                throw new IllegalArgumentException("补货计划参数「在途必须有 ETA」V1 只支持 true。");
            }
            return;
        }
        if ("空运只应急".equals(itemName)) {
            String normalized = normalizeText(value);
            if (!"true".equals(normalized) && !"false".equals(normalized)) {
                throw new IllegalArgumentException("补货计划参数「空运只应急」必须填写 true 或 false。");
            }
            return;
        }
        if ("建议数量取整".equals(itemName) && !"ceil".equals(normalizeText(value))) {
            throw new IllegalArgumentException("补货计划参数「建议数量取整」V1 只支持 ceil。");
        }
    }

    private void validateReplenishmentPositiveInteger(String itemName, String value) {
        if (!isPositiveIntegerText(value)) {
            throw new IllegalArgumentException("补货计划参数「" + itemName + "」必须填写大于等于 1 的正整数。");
        }
    }

    private void validateReplenishmentInventorySources(String value) {
        Set<String> sources = new HashSet<>();
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("补货计划参数「库存来源」必须且只能包含 FBN 和 SUPERMALL。");
        }
        for (String token : trimmed.split("[,，\\s]+")) {
            if (token == null || token.trim().isEmpty()) {
                continue;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (!"FBN".equals(normalized) && !"SUPERMALL".equals(normalized)) {
                throw new IllegalArgumentException("补货计划参数「库存来源」必须且只能包含 FBN 和 SUPERMALL。");
            }
            sources.add(normalized);
        }
        if (sources.size() != 2 || !sources.contains("FBN") || !sources.contains("SUPERMALL")) {
            throw new IllegalArgumentException("补货计划参数「库存来源」必须且只能包含 FBN 和 SUPERMALL。");
        }
    }

    private boolean isPositiveIntegerText(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        try {
            return Integer.parseInt(trimmed) >= 1;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String summaryFor(String configType, int itemCount) {
        if (OperationConfigVersionType.BUSINESS_CALENDAR.name().equals(configType)) {
            return itemCount + " 条日历配置";
        }
        if (OperationConfigVersionType.PRODUCT_LIFECYCLE.name().equals(configType)) {
            return itemCount + " 条生命周期配置";
        }
        return itemCount + " 条配置";
    }

    private String publishScopeSummary(OperationConfigVersionPublishRequest request) {
        if (request != null
                && request.getOwnerUserId() != null
                && request.getStoreCode() != null && !request.getStoreCode().trim().isEmpty()
                && request.getSiteCode() != null && !request.getSiteCode().trim().isEmpty()) {
            return request.getOwnerUserId() + "/" + request.getStoreCode().trim().toUpperCase() + "/" + request.getSiteCode().trim().toUpperCase();
        }
        return "全局当前";
    }

    private void requirePublishScope(BusinessAccessContext context, OperationConfigVersionPublishRequest request) {
        if (context.isSystemAdmin()) {
            return;
        }
        if (request == null || request.getOwnerUserId() == null
                || request.getStoreCode() == null || request.getStoreCode().trim().isEmpty()
                || request.getSiteCode() == null || request.getSiteCode().trim().isEmpty()) {
            throw new BusinessAccessDeniedException("非系统管理员发布运营配置版本时必须指定授权店铺范围。");
        }
        requireScopeAllowed(context, request.getOwnerUserId(), request.getStoreCode());
    }

    private void requireCurrentScope(BusinessAccessContext context, Long ownerUserId, String storeCode, String siteCode) {
        if (context.isSystemAdmin()) {
            return;
        }
        if (ownerUserId == null || storeCode == null || storeCode.trim().isEmpty()
                || siteCode == null || siteCode.trim().isEmpty()) {
            throw new BusinessAccessDeniedException("当前账号必须在授权店铺范围内读取当前运营配置版本。");
        }
        requireScopeAllowed(context, ownerUserId, storeCode);
    }

    private void requireScopeAllowed(BusinessAccessContext context, Long ownerUserId, String storeCode) {
        if (context.isBossAccount()) {
            Long expectedOwner = context.getBusinessOwnerUserId() == null ? context.getSessionUserId() : context.getBusinessOwnerUserId();
            if (!ownerUserId.equals(expectedOwner)) {
                throw new BusinessAccessDeniedException("不能操作其他老板的运营配置版本。");
            }
            if (!context.getStoreCodes().isEmpty() && !context.canAccessStore(storeCode)) {
                throw new BusinessAccessDeniedException("不能操作未授权店铺的运营配置版本。");
            }
            return;
        }
        if (context.isOperatorAccount()) {
            Long mappedOwner = context.resolveOwnerUserIdForStore(storeCode);
            if (!context.canAccessStore(storeCode) || mappedOwner == null || !ownerUserId.equals(mappedOwner)) {
                throw new BusinessAccessDeniedException("不能操作未授权店铺的运营配置版本。");
            }
            return;
        }
        throw new BusinessAccessDeniedException("当前账号不能操作运营配置版本。");
    }

    private ScopeKey parseScope(String scopeSummary) {
        String[] parts = scopeSummary.split("/");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new ScopeKey(Long.valueOf(parts[0]), parts[1], parts[2]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static class ScopeKey {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        private ScopeKey(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    private String operatorLabel(BusinessAccessContext context) {
        if (context.getRoleName() != null && !context.getRoleName().trim().isEmpty()) {
            return context.getRoleName().trim();
        }
        return context.getAccountType().name();
    }

    private String versionPrefix(String configType) {
        if (OperationConfigVersionType.BUSINESS_CALENDAR.name().equals(configType)) {
            return "CALENDAR_CONFIG_";
        }
        if (OperationConfigVersionType.PRODUCT_LIFECYCLE.name().equals(configType)) {
            return "LIFECYCLE_CONFIG_";
        }
        if (OperationConfigVersionType.REPLENISHMENT_PLAN.name().equals(configType)) {
            return "REPLENISHMENT_PLAN_";
        }
        throw new IllegalArgumentException("unsupported operation config version type");
    }

    private String serializeItems(List<OperationConfigDefaultVersionItemView> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("operation config version content serialization failed", exception);
        }
    }

    private List<OperationConfigDefaultVersionItemView> parseItems(String contentJson) {
        if (contentJson == null || contentJson.trim().isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<OperationConfigDefaultVersionItemView> items = new ArrayList<>();
            for (JsonNode item : root) {
                items.add(new OperationConfigDefaultVersionItemView(
                        textValue(item, "groupName"),
                        textValue(item, "itemName"),
                        textValue(item, "cadence"),
                        textValue(item, "valueType"),
                        textValue(item, "defaultValue"),
                        textValue(item, "resultShape"),
                        textValue(item, "note")
                ));
            }
            return List.copyOf(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("operation config version content parsing failed", exception);
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String appendAudit(String auditJson, OperationConfigVersionAuditView audit) {
        List<OperationConfigVersionAuditView> audits = new ArrayList<>(parseAudit(auditJson));
        audits.add(audit);
        return serializeAudit(audits);
    }

    private List<OperationConfigVersionAuditView> parseAudit(String auditJson) {
        if (auditJson == null || auditJson.trim().isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(auditJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<OperationConfigVersionAuditView> audits = new ArrayList<>();
            for (JsonNode item : root) {
                String operatedAt = textValue(item, "operatedAt");
                audits.add(new OperationConfigVersionAuditView(
                        longValue(item, "operatorUserId"),
                        textValue(item, "operatorLabel"),
                        textValue(item, "operation"),
                        textValue(item, "fromStatus"),
                        textValue(item, "toStatus"),
                        textValue(item, "reason"),
                        operatedAt == null ? null : LocalDateTime.parse(operatedAt)
                ));
            }
            return List.copyOf(audits);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("operation config version audit parsing failed", exception);
        }
    }

    private String serializeAudit(List<OperationConfigVersionAuditView> audits) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < audits.size(); index++) {
            OperationConfigVersionAuditView audit = audits.get(index);
            if (index > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"operatorUserId\":").append(audit.getOperatorUserId() == null ? "null" : audit.getOperatorUserId()).append(',')
                    .append("\"operatorLabel\":").append(jsonString(audit.getOperatorLabel())).append(',')
                    .append("\"operation\":").append(jsonString(audit.getOperation())).append(',')
                    .append("\"fromStatus\":").append(jsonString(audit.getFromStatus())).append(',')
                    .append("\"toStatus\":").append(jsonString(audit.getToStatus())).append(',')
                    .append("\"reason\":").append(jsonString(audit.getReason())).append(',')
                    .append("\"operatedAt\":").append(jsonString(audit.getOperatedAt() == null ? null : audit.getOperatedAt().toString()))
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asLong();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
