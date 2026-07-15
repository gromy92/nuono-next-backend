package com.nuono.next.operationsskin;

import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsSkinService {
    private static final String REFERENCE_IMAGE = "REFERENCE_IMAGE";
    private static final String HERO_MAIN = "HERO_MAIN";
    private static final int HERO_COMPONENT_REQUIRED_COUNT = 4;
    private static final List<String> HERO_COMPONENT_ORDER = List.of(
            "FRAME",
            "BRAND_LOCKUP",
            "SPEC_BG",
            "MAIN_TITLE_BG"
    );
    private static final DateTimeFormatter API_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationsSkinMapper operationsSkinMapper;

    public OperationsSkinService(OperationsSkinMapper operationsSkinMapper) {
        this.operationsSkinMapper = operationsSkinMapper;
    }

    public List<OperationsSkinView> list(
            BusinessAccessContext context,
            String storeCode,
            String keyword,
            String status
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status) == null ? null : OperationsSkinStatus.normalize(status);
        return operationsSkinMapper.selectSkins(ownerUserId, normalizedStoreCode, normalizedKeyword, normalizedStatus).stream()
                .map(this::toDetailView)
                .collect(Collectors.toList());
    }

    @Transactional
    public OperationsSkinView create(BusinessAccessContext context, OperationsSkinSaveRequest request) {
        String normalizedStoreCode = requireStoreCode(request == null ? null : request.getStoreCode());
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        String skinName = requireSkinName(request == null ? null : request.getSkinName());
        String status = OperationsSkinStatus.normalize(request == null ? null : request.getStatus());
        List<OperationsSkinAssetRecord> assets = normalizeAssets(request == null ? null : request.getAssets());
        String coverImageUrl = trimToNull(request == null ? null : request.getCoverImageUrl());
        if (coverImageUrl == null && !assets.isEmpty()) {
            coverImageUrl = assets.get(0).getImageUrl();
        }
        ensureNameAvailable(ownerUserId, normalizedStoreCode, skinName, null);

        LocalDateTime now = LocalDateTime.now();
        OperationsSkinRecord record = new OperationsSkinRecord();
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(normalizedStoreCode);
        record.setSkinName(skinName);
        record.setStatus(status);
        record.setCoverImageUrl(coverImageUrl);
        record.setStyleDescription(trimToNull(request == null ? null : request.getStyleDescription()));
        record.setRemark(trimToNull(request == null ? null : request.getRemark()));
        record.setSortOrder(defaultSortOrder(request == null ? null : request.getSortOrder()));
        record.setCreatedBy(context.getSessionUserId());
        record.setUpdatedBy(context.getSessionUserId());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(false);

        operationsSkinMapper.insertSkin(record);
        if (record.getId() == null) {
            throw new IllegalStateException("operations skin id generation failed");
        }
        insertAssets(record.getId(), assets);
        return detail(record.getId(), ownerUserId, normalizedStoreCode);
    }

    @Transactional
    public OperationsSkinView update(BusinessAccessContext context, Long id, OperationsSkinSaveRequest request) {
        String normalizedStoreCode = requireStoreCode(request == null ? null : request.getStoreCode());
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        OperationsSkinRecord existing = requireAccessibleSkin(id, ownerUserId, normalizedStoreCode);
        String skinName = requireSkinName(request == null ? null : request.getSkinName());
        String status = OperationsSkinStatus.normalize(request == null ? null : request.getStatus());
        ensureNameAvailable(ownerUserId, normalizedStoreCode, skinName, existing.getId());

        List<OperationsSkinAssetRecord> assets = normalizeAssets(request == null ? null : request.getAssets());
        String coverImageUrl = trimToNull(request == null ? null : request.getCoverImageUrl());
        if (coverImageUrl == null && !assets.isEmpty()) {
            coverImageUrl = assets.get(0).getImageUrl();
        }

        OperationsSkinRecord update = new OperationsSkinRecord();
        update.setId(existing.getId());
        update.setOwnerUserId(ownerUserId);
        update.setStoreCode(normalizeStoreCode(existing.getStoreCode()));
        update.setSkinName(skinName);
        update.setStatus(status);
        update.setCoverImageUrl(coverImageUrl);
        update.setStyleDescription(trimToNull(request == null ? null : request.getStyleDescription()));
        update.setRemark(trimToNull(request == null ? null : request.getRemark()));
        update.setSortOrder(defaultSortOrder(request == null ? null : request.getSortOrder()));
        update.setUpdatedBy(context.getSessionUserId());
        update.setUpdatedAt(LocalDateTime.now());

        if (operationsSkinMapper.updateSkin(update) == 0) {
            throw notFound();
        }
        operationsSkinMapper.softDeleteAssets(existing.getId(), ownerUserId, normalizeStoreCode(existing.getStoreCode()));
        insertAssets(existing.getId(), assets);
        return detail(existing.getId(), ownerUserId, normalizedStoreCode);
    }

    @Transactional
    public OperationsSkinView updateStatus(
            BusinessAccessContext context,
            Long id,
            OperationsSkinStatusRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(request == null ? null : request.getStoreCode());
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        OperationsSkinRecord existing = requireAccessibleSkin(id, ownerUserId, normalizedStoreCode);
        String status = OperationsSkinStatus.normalize(request == null ? null : request.getStatus());
        String persistedStoreCode = normalizeStoreCode(existing.getStoreCode());
        if (operationsSkinMapper.updateStatus(existing.getId(), ownerUserId, persistedStoreCode, status, context.getSessionUserId()) == 0) {
            throw notFound();
        }
        return detail(existing.getId(), ownerUserId, normalizedStoreCode);
    }

    @Transactional
    public void delete(BusinessAccessContext context, Long id, String storeCode) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        OperationsSkinRecord existing = requireAccessibleSkin(id, ownerUserId, normalizedStoreCode);
        String persistedStoreCode = normalizeStoreCode(existing.getStoreCode());
        operationsSkinMapper.softDeleteAssets(existing.getId(), ownerUserId, persistedStoreCode);
        operationsSkinMapper.softDeleteComponents(existing.getId(), ownerUserId, persistedStoreCode, context.getSessionUserId());
        if (operationsSkinMapper.softDeleteSkin(existing.getId(), ownerUserId, persistedStoreCode, context.getSessionUserId()) == 0) {
            throw notFound();
        }
    }

    public OperationsSkinView detail(BusinessAccessContext context, Long id, String storeCode) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        return detail(id, ownerUserId, normalizedStoreCode);
    }

    @Transactional
    public OperationsSkinView saveComponents(
            BusinessAccessContext context,
            Long id,
            OperationsSkinComponentsSaveRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(request == null ? null : request.getStoreCode());
        Long ownerUserId = resolveOwnerUserId(context, normalizedStoreCode);
        OperationsSkinRecord existing = requireAccessibleSkin(id, ownerUserId, normalizedStoreCode);
        List<OperationsSkinComponentRecord> components = normalizeComponents(
                request == null ? null : request.getComponents(),
                context.getSessionUserId()
        );
        operationsSkinMapper.softDeleteComponents(
                existing.getId(),
                ownerUserId,
                normalizeStoreCode(existing.getStoreCode()),
                context.getSessionUserId()
        );
        for (OperationsSkinComponentRecord component : components) {
            component.setSkinId(existing.getId());
            operationsSkinMapper.insertComponent(component);
        }
        return detail(existing.getId(), ownerUserId, normalizedStoreCode);
    }

    private OperationsSkinView detail(Long id, Long ownerUserId, String storeCode) {
        OperationsSkinRecord record = requireAccessibleSkin(id, ownerUserId, storeCode);
        return toDetailView(record);
    }

    private OperationsSkinView toDetailView(OperationsSkinRecord record) {
        List<OperationsSkinAssetRecord> assets = operationsSkinMapper.selectAssets(
                record.getId(),
                record.getOwnerUserId(),
                normalizeStoreCode(record.getStoreCode())
        );
        List<OperationsSkinComponentRecord> components = operationsSkinMapper.selectComponents(
                record.getId(),
                record.getOwnerUserId(),
                normalizeStoreCode(record.getStoreCode())
        );
        OperationsSkinView view = new OperationsSkinView();
        view.setId(record.getId());
        view.setStoreCode(record.getStoreCode());
        view.setSkinName(record.getSkinName());
        view.setStatus(record.getStatus());
        view.setCoverImageUrl(record.getCoverImageUrl());
        view.setStyleDescription(record.getStyleDescription());
        view.setRemark(record.getRemark());
        view.setSortOrder(record.getSortOrder());
        view.setUpdatedAt(formatUpdatedAt(record.getUpdatedAt()));
        view.setAssetCount(record.getAssetCount() == null ? assets.size() : record.getAssetCount());
        view.setAssets(assets.stream().map(this::toAssetView).collect(Collectors.toList()));
        view.setComponents(components.stream()
                .sorted(Comparator
                        .comparingInt(this::componentSortOrder)
                        .thenComparing(component -> component.getZIndex() == null ? 0 : component.getZIndex())
                        .thenComparing(component -> component.getId() == null ? 0L : component.getId()))
                .map(this::toComponentView)
                .collect(Collectors.toList()));
        int heroComponentCount = (int) components.stream()
                .filter(this::isCompletedHeroComponent)
                .count();
        view.setHeroComponentCount(record.getHeroComponentCount() == null ? heroComponentCount : record.getHeroComponentCount());
        view.setHeroComponentRequiredCount(HERO_COMPONENT_REQUIRED_COUNT);
        return view;
    }

    private OperationsSkinAssetView toAssetView(OperationsSkinAssetRecord record) {
        OperationsSkinAssetView view = new OperationsSkinAssetView();
        view.setId(record.getId());
        view.setAssetType(record.getAssetType());
        view.setImageUrl(record.getImageUrl());
        view.setCaption(record.getCaption());
        view.setSortOrder(record.getSortOrder());
        return view;
    }

    private OperationsSkinComponentView toComponentView(OperationsSkinComponentRecord record) {
        OperationsSkinComponentView view = new OperationsSkinComponentView();
        view.setId(record.getId());
        view.setTemplateRole(record.getTemplateRole());
        view.setComponentKey(record.getComponentKey());
        view.setImageUrl(record.getImageUrl());
        view.setX(record.getX());
        view.setY(record.getY());
        view.setWidth(record.getWidth());
        view.setHeight(record.getHeight());
        view.setZIndex(record.getZIndex());
        view.setRequired(record.getRequired());
        view.setLocked(record.getLocked());
        view.setStyleJson(record.getStyleJson());
        return view;
    }

    private void insertAssets(Long skinId, List<OperationsSkinAssetRecord> assets) {
        for (OperationsSkinAssetRecord asset : assets) {
            asset.setSkinId(skinId);
            operationsSkinMapper.insertAsset(asset);
        }
    }

    private List<OperationsSkinAssetRecord> normalizeAssets(List<OperationsSkinAssetView> rawAssets) {
        if (rawAssets == null || rawAssets.isEmpty()) {
            return List.of();
        }
        List<OperationsSkinAssetRecord> result = new ArrayList<>();
        for (OperationsSkinAssetView rawAsset : rawAssets) {
            if (rawAsset == null) {
                continue;
            }
            String imageUrl = trimToNull(rawAsset.getImageUrl());
            if (imageUrl == null) {
                continue;
            }
            OperationsSkinAssetRecord record = new OperationsSkinAssetRecord();
            record.setAssetType(REFERENCE_IMAGE);
            record.setImageUrl(imageUrl);
            record.setCaption(trimToNull(rawAsset.getCaption()));
            record.setSortOrder(defaultSortOrder(rawAsset.getSortOrder()));
            record.setDeleted(false);
            result.add(record);
        }
        return result;
    }

    private List<OperationsSkinComponentRecord> normalizeComponents(
            List<OperationsSkinComponentView> rawComponents,
            Long operatorUserId
    ) {
        if (rawComponents == null || rawComponents.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<OperationsSkinComponentRecord> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (OperationsSkinComponentView rawComponent : rawComponents) {
            if (rawComponent == null) {
                continue;
            }
            String templateRole = requireComponentCode(rawComponent.getTemplateRole(), "组件模板不能为空。");
            String componentKey = requireComponentCode(rawComponent.getComponentKey(), "组件类型不能为空。");
            String uniqueKey = templateRole + "::" + componentKey;
            if (!seen.add(uniqueKey)) {
                throw new IllegalArgumentException("同一组件位不能重复。");
            }
            String imageUrl = trimToNull(rawComponent.getImageUrl());
            if (imageUrl == null) {
                continue;
            }
            requirePngImageUrl(imageUrl);

            OperationsSkinComponentRecord record = new OperationsSkinComponentRecord();
            record.setTemplateRole(templateRole);
            record.setComponentKey(componentKey);
            record.setImageUrl(imageUrl);
            record.setX(defaultNumber(rawComponent.getX()));
            record.setY(defaultNumber(rawComponent.getY()));
            record.setWidth(defaultNumber(rawComponent.getWidth()));
            record.setHeight(defaultNumber(rawComponent.getHeight()));
            record.setZIndex(defaultNumber(rawComponent.getZIndex()));
            record.setRequired(rawComponent.getRequired() == null ? isHeroComponent(templateRole, componentKey) : rawComponent.getRequired());
            record.setLocked(rawComponent.getLocked() == null ? isHeroComponent(templateRole, componentKey) : rawComponent.getLocked());
            record.setStyleJson(trimToNull(rawComponent.getStyleJson()) == null ? "{}" : rawComponent.getStyleJson().trim());
            record.setCreatedBy(operatorUserId);
            record.setUpdatedBy(operatorUserId);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setDeleted(false);
            result.add(record);
        }
        return result;
    }

    private String requireComponentCode(String raw, String message) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private void requirePngImageUrl(String imageUrl) {
        String path = imageUrl.split("\\?", 2)[0].toLowerCase(Locale.ROOT);
        if (!path.endsWith(".png")) {
            throw new IllegalArgumentException("皮肤组件只支持 PNG 图片。");
        }
    }

    private int defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isCompletedHeroComponent(OperationsSkinComponentRecord record) {
        return record != null
                && HERO_MAIN.equals(record.getTemplateRole())
                && trimToNull(record.getImageUrl()) != null;
    }

    private boolean isHeroComponent(String templateRole, String componentKey) {
        return HERO_MAIN.equals(templateRole) && HERO_COMPONENT_ORDER.contains(componentKey);
    }

    private int componentSortOrder(OperationsSkinComponentRecord record) {
        if (record == null) {
            return 1000;
        }
        if (HERO_MAIN.equals(record.getTemplateRole())) {
            int index = HERO_COMPONENT_ORDER.indexOf(record.getComponentKey());
            if (index >= 0) {
                return index;
            }
        }
        return 100 + Math.max(0, record.getZIndex() == null ? 0 : record.getZIndex());
    }

    private OperationsSkinRecord requireAccessibleSkin(Long id, Long ownerUserId, String storeCode) {
        OperationsSkinRecord record = id == null ? null : operationsSkinMapper.selectSkinById(id, ownerUserId, storeCode);
        if (record == null
                || !Objects.equals(ownerUserId, record.getOwnerUserId())) {
            throw notFound();
        }
        return record;
    }

    private void ensureNameAvailable(Long ownerUserId, String storeCode, String skinName, Long excludeId) {
        if (operationsSkinMapper.countByName(ownerUserId, storeCode, skinName, excludeId) > 0) {
            throw new IllegalArgumentException("当前店铺已存在同名皮肤。");
        }
    }

    private String requireSkinName(String raw) {
        String skinName = trimToNull(raw);
        if (skinName == null) {
            throw new IllegalArgumentException("皮肤名称不能为空。");
        }
        if (skinName.length() > 120) {
            throw new IllegalArgumentException("皮肤名称不能超过 120 个字符。");
        }
        return skinName;
    }

    private Long resolveOwnerUserId(BusinessAccessContext context, String storeCode) {
        if (context == null) {
            throw notFound();
        }
        if (!context.canAccessStore(storeCode)) {
            throw notFound();
        }
        Long ownerUserId = context.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw notFound();
        }
        return ownerUserId;
    }

    private String requireStoreCode(String raw) {
        String normalized = normalizeStoreCode(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("店铺编码不能为空。");
        }
        return normalized;
    }

    private String normalizeStoreCode(String raw) {
        String value = trimToNull(raw);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String formatUpdatedAt(LocalDateTime updatedAt) {
        return updatedAt == null ? null : API_TIME_FORMATTER.format(updatedAt);
    }

    private OperationsSkinNotFoundException notFound() {
        return new OperationsSkinNotFoundException("皮肤不存在或无权访问。");
    }
}
