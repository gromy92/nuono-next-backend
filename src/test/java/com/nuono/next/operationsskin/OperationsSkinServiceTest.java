package com.nuono.next.operationsskin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationsSkinServiceTest {

    private static final String STORE_CODE = "STR108065-NAE";
    private static final String SIBLING_SITE_STORE_CODE = "STR108065-KSA";
    private static final String ASSET_A = "/api/operations/skin-management/assets/a.png";
    private static final String ASSET_B = "/api/operations/skin-management/assets/b.png";

    @Test
    void createPersistsStoreScopedSkinAndAssetsWithResolvedOwnerAndDefaultCover() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinSaveRequest request = saveRequest(" " + STORE_CODE.toLowerCase() + " ", "  主图皮肤  ");
        request.setStatus(" ");
        request.setCoverImageUrl(" ");
        request.setStyleDescription("  干净白底  ");
        request.setRemark("  用于参考图  ");
        request.getAssets().add(asset("PRODUCT_IMAGE", ASSET_A, "  第一张  ", 20));
        request.getAssets().add(asset("IGNORED", ASSET_B, " 第二张 ", 10));

        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), request);

        assertNotNull(created.getId());
        assertEquals(STORE_CODE, created.getStoreCode());
        assertEquals("主图皮肤", created.getSkinName());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(ASSET_A, created.getCoverImageUrl());
        assertEquals("干净白底", created.getStyleDescription());
        assertEquals("用于参考图", created.getRemark());
        assertEquals(0, created.getSortOrder());
        assertEquals(2, created.getAssetCount());
        assertEquals(List.of(ASSET_B, ASSET_A), created.getAssets().stream()
                .map(OperationsSkinAssetView::getImageUrl)
                .collect(Collectors.toList()));
        assertEquals(List.of("REFERENCE_IMAGE", "REFERENCE_IMAGE"), created.getAssets().stream()
                .map(OperationsSkinAssetView::getAssetType)
                .collect(Collectors.toList()));

        OperationsSkinRecord persisted = mapper.skins.get(created.getId());
        assertEquals(307L, persisted.getOwnerUserId());
        assertEquals(90003L, persisted.getCreatedBy());
        assertEquals(90003L, persisted.getUpdatedBy());
        assertEquals(STORE_CODE, persisted.getStoreCode());
    }

    @Test
    void createRequiresSkinName() {
        OperationsSkinService service = new OperationsSkinService(new FakeOperationsSkinMapper());
        OperationsSkinSaveRequest request = saveRequest(STORE_CODE, "  ");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(context(307L, 90003L, STORE_CODE), request)
        );

        assertEquals("皮肤名称不能为空。", error.getMessage());
    }

    @Test
    void duplicateNameInSameStoreFails() {
        OperationsSkinService service = new OperationsSkinService(new FakeOperationsSkinMapper());

        service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "统一参考皮肤"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, " 统一参考皮肤 "))
        );

        assertEquals("当前店铺已存在同名皮肤。", error.getMessage());
    }

    @Test
    void skinCanBeReadAndMutatedFromSiblingSiteOfSameLogicalStore() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        mapper.linkLogicalStoreSites(STORE_CODE, SIBLING_SITE_STORE_CODE);
        OperationsSkinService service = new OperationsSkinService(mapper);

        OperationsSkinView created = service.create(
                context(307L, 90003L, STORE_CODE),
                saveRequest(STORE_CODE, "跨站点共用皮肤")
        );

        List<OperationsSkinView> visibleFromSiblingSite = service.list(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                SIBLING_SITE_STORE_CODE,
                null,
                null
        );

        assertEquals(List.of(created.getId()), visibleFromSiblingSite.stream()
                .map(OperationsSkinView::getId)
                .collect(Collectors.toList()));

        assertEquals(
                created.getId(),
                service.detail(
                        context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                        created.getId(),
                        SIBLING_SITE_STORE_CODE
                ).getId()
        );

        OperationsSkinSaveRequest update = saveRequest(SIBLING_SITE_STORE_CODE, "跨站点更新皮肤");
        OperationsSkinView updated = service.update(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                created.getId(),
                update
        );
        assertEquals("跨站点更新皮肤", updated.getSkinName());
        assertEquals(STORE_CODE, mapper.skins.get(created.getId()).getStoreCode());

        OperationsSkinView inactive = service.updateStatus(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                created.getId(),
                statusRequest(SIBLING_SITE_STORE_CODE, "INACTIVE")
        );
        assertEquals("INACTIVE", inactive.getStatus());

        OperationsSkinComponentsSaveRequest components = new OperationsSkinComponentsSaveRequest();
        components.setStoreCode(SIBLING_SITE_STORE_CODE);
        components.setComponents(List.of(
                component("HERO_MAIN", "FRAME", "/api/operations/skin-management/assets/frame.png", 0, 0, 1247, 1706, 40)
        ));
        OperationsSkinView withComponents = service.saveComponents(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                created.getId(),
                components
        );
        assertEquals(1, withComponents.getComponents().size());

        service.delete(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                created.getId(),
                SIBLING_SITE_STORE_CODE
        );
        assertEquals(true, mapper.skins.get(created.getId()).getDeleted());
    }

    @Test
    void duplicateNameAcrossSiblingSitesOfSameLogicalStoreFails() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        mapper.linkLogicalStoreSites(STORE_CODE, SIBLING_SITE_STORE_CODE);
        OperationsSkinService service = new OperationsSkinService(mapper);

        service.create(
                context(307L, 90003L, STORE_CODE),
                saveRequest(STORE_CODE, "逻辑店铺唯一皮肤")
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                        saveRequest(SIBLING_SITE_STORE_CODE, "逻辑店铺唯一皮肤")
                )
        );

        assertEquals("当前店铺已存在同名皮肤。", error.getMessage());
    }

    @Test
    void skinAssetCanBeReadThroughAnyAuthorizedSiblingSite() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        mapper.linkLogicalStoreSites(STORE_CODE, SIBLING_SITE_STORE_CODE);
        OperationsSkinService service = new OperationsSkinService(mapper);

        assertDoesNotThrow(() -> service.verifyReadableAssetStore(
                context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                STORE_CODE
        ));
    }

    @Test
    void skinAssetReadRejectsUnlinkedStoreScope() {
        OperationsSkinService service = new OperationsSkinService(new FakeOperationsSkinMapper());

        assertThrows(
                OperationsSkinNotFoundException.class,
                () -> service.verifyReadableAssetStore(
                        context(307L, 90003L, SIBLING_SITE_STORE_CODE),
                        STORE_CODE
                )
        );
    }

    @Test
    void authorizedStoreWithoutOwnerMapFallsBackToBusinessOwner() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);

        OperationsSkinView created = service.create(
                contextWithAuthorizedStoreNoOwnerMap(307L, 90003L, STORE_CODE),
                saveRequest(STORE_CODE, "授权店铺回退老板")
        );

        assertEquals(307L, mapper.skins.get(created.getId()).getOwnerUserId());
        assertEquals(STORE_CODE, created.getStoreCode());
    }

    @Test
    void storeScopedAccessRejectsContextWithoutAuthorizedStore() {
        OperationsSkinService service = new OperationsSkinService(new FakeOperationsSkinMapper());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        contextWithoutStoreScope(307L, 90003L),
                        saveRequest(STORE_CODE, "未授权店铺")
                )
        );

        assertEquals("皮肤不存在或无权访问。", error.getMessage());
    }

    @Test
    void statusUpdateAllowsKnownStatusesAndRejectsUnsupportedStatus() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "状态皮肤"));

        OperationsSkinView inactive = service.updateStatus(
                context(307L, 90003L, STORE_CODE),
                created.getId(),
                statusRequest(STORE_CODE, " inactive ")
        );
        assertEquals("INACTIVE", inactive.getStatus());

        OperationsSkinView active = service.updateStatus(
                context(307L, 90003L, STORE_CODE),
                created.getId(),
                statusRequest(STORE_CODE, "ACTIVE")
        );
        assertEquals("ACTIVE", active.getStatus());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(
                        context(307L, 90003L, STORE_CODE),
                        created.getId(),
                        statusRequest(STORE_CODE, "PAUSED")
                )
        );
        assertEquals("皮肤状态不支持。", error.getMessage());
        assertEquals("ACTIVE", mapper.skins.get(created.getId()).getStatus());
    }

    @Test
    void updateReplacesAssetsAndReturnsCurrentDetail() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinSaveRequest initial = saveRequest(STORE_CODE, "待更新皮肤");
        initial.getAssets().add(asset("SOURCE", ASSET_A, "旧图", 0));
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), initial);

        OperationsSkinSaveRequest update = saveRequest(STORE_CODE, "更新后皮肤");
        update.setStatus("INACTIVE");
        update.getAssets().add(asset("ANYTHING", ASSET_B, "新图", 5));

        OperationsSkinView updated = service.update(context(307L, 90003L, STORE_CODE), created.getId(), update);

        assertEquals("更新后皮肤", updated.getSkinName());
        assertEquals("INACTIVE", updated.getStatus());
        assertEquals(1, updated.getAssetCount());
        assertEquals(ASSET_B, updated.getCoverImageUrl());
        assertEquals(List.of(ASSET_B), updated.getAssets().stream()
                .map(OperationsSkinAssetView::getImageUrl)
                .collect(Collectors.toList()));
        assertEquals("REFERENCE_IMAGE", updated.getAssets().get(0).getAssetType());
        assertEquals(0, mapper.selectAssets(created.getId(), 408L, STORE_CODE).size());
        assertEquals(0, mapper.softDeleteAssets(created.getId(), 408L, STORE_CODE));
        assertEquals(1, mapper.selectAssets(created.getId(), 307L, STORE_CODE).size());
    }

    @Test
    void updateStatusAndDeleteRejectCrossScopeMutation() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "范围皮肤"));

        OperationsSkinSaveRequest wrongStoreUpdate = saveRequest("STR999-TEST", "不应更新");
        IllegalArgumentException updateError = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(context(307L, 90003L, "STR999-TEST"), created.getId(), wrongStoreUpdate)
        );
        assertEquals("皮肤不存在或无权访问。", updateError.getMessage());

        IllegalArgumentException statusError = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(
                        context(408L, 90004L, STORE_CODE),
                        created.getId(),
                        statusRequest(STORE_CODE, "INACTIVE")
                )
        );
        assertEquals("皮肤不存在或无权访问。", statusError.getMessage());

        IllegalArgumentException deleteError = assertThrows(
                IllegalArgumentException.class,
                () -> service.delete(context(307L, 90003L, "STR999-TEST"), created.getId(), "STR999-TEST")
        );
        assertEquals("皮肤不存在或无权访问。", deleteError.getMessage());

        OperationsSkinRecord persisted = mapper.skins.get(created.getId());
        assertEquals("范围皮肤", persisted.getSkinName());
        assertEquals("ACTIVE", persisted.getStatus());
        assertFalse(Boolean.TRUE.equals(persisted.getDeleted()));
    }

    @Test
    void deleteSoftDeletesAssetsBeforeDeletingSkin() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinSaveRequest request = saveRequest(STORE_CODE, "删除皮肤");
        request.getAssets().add(asset("SOURCE", ASSET_A, "第一张", 0));
        request.getAssets().add(asset("SOURCE", ASSET_B, "第二张", 1));
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), request);

        assertEquals(List.of(false, false), mapper.assetDeletedFlags(created.getId()));

        service.delete(context(307L, 90003L, STORE_CODE), created.getId(), STORE_CODE);

        assertEquals(List.of(true, true), mapper.assetDeletedFlags(created.getId()));
        assertEquals(true, mapper.skins.get(created.getId()).getDeleted());
    }

    @Test
    void saveComponentsPersistsHeroMainSlotsAndReturnsThemInDetail() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "PAPERSAY 黄框主图"));
        mapper.skins.get(created.getId()).setUpdatedAt(LocalDateTime.of(2026, 7, 15, 13, 25));
        mapper.skins.get(created.getId()).setUpdatedBy(100L);

        OperationsSkinComponentsSaveRequest request = new OperationsSkinComponentsSaveRequest();
        request.setStoreCode(STORE_CODE);
        request.setComponents(List.of(
                component("HERO_MAIN", "FRAME", "/api/operations/skin-management/assets/frame.png", 0, 0, 1247, 1706, 40),
                component("HERO_MAIN", "BRAND_LOCKUP", "/api/operations/skin-management/assets/brand.png", 0, 0, 510, 170, 50),
                component("HERO_MAIN", "SPEC_BG", "/api/operations/skin-management/assets/spec.png", 40, 210, 420, 68, 60),
                component("HERO_MAIN", "MAIN_TITLE_BG", "/api/operations/skin-management/assets/title.png", 0, 1450, 1247, 140, 20)
        ));

        OperationsSkinView updated = service.saveComponents(context(307L, 90003L, STORE_CODE), created.getId(), request);

        assertEquals(4, updated.getHeroComponentCount());
        assertEquals(4, updated.getHeroComponentRequiredCount());
        assertEquals(
                List.of("FRAME", "BRAND_LOCKUP", "SPEC_BG", "MAIN_TITLE_BG"),
                updated.getComponents().stream().map(OperationsSkinComponentView::getComponentKey).collect(Collectors.toList())
        );
        assertEquals(1247, updated.getComponents().get(0).getWidth());
        assertEquals(1706, updated.getComponents().get(0).getHeight());
        assertEquals(true, updated.getComponents().get(0).getRequired());
        assertEquals(true, updated.getComponents().get(0).getLocked());
        assertEquals(LocalDateTime.of(2026, 7, 17, 10, 0), mapper.skins.get(created.getId()).getUpdatedAt());
        assertEquals(90003L, mapper.skins.get(created.getId()).getUpdatedBy());

        OperationsSkinView detail = service.detail(context(307L, 90003L, STORE_CODE), created.getId(), STORE_CODE);
        assertEquals(4, detail.getComponents().size());
        assertEquals("HERO_MAIN", detail.getComponents().get(0).getTemplateRole());
    }

    @Test
    void saveComponentsRejectsDuplicateTemplateRoleAndComponentKey() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "重复组件位"));
        OperationsSkinComponentsSaveRequest request = new OperationsSkinComponentsSaveRequest();
        request.setStoreCode(STORE_CODE);
        request.setComponents(List.of(
                component("HERO_MAIN", "FRAME", "/api/operations/skin-management/assets/frame-a.png", 0, 0, 1247, 1706, 40),
                component(" hero_main ", " frame ", "/api/operations/skin-management/assets/frame-b.png", 0, 0, 1247, 1706, 40)
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveComponents(context(307L, 90003L, STORE_CODE), created.getId(), request)
        );

        assertEquals("同一组件位不能重复。", error.getMessage());
    }

    @Test
    void saveComponentsRejectsNonPngComponentImage() {
        FakeOperationsSkinMapper mapper = new FakeOperationsSkinMapper();
        OperationsSkinService service = new OperationsSkinService(mapper);
        OperationsSkinView created = service.create(context(307L, 90003L, STORE_CODE), saveRequest(STORE_CODE, "非 PNG 组件"));
        OperationsSkinComponentsSaveRequest request = new OperationsSkinComponentsSaveRequest();
        request.setStoreCode(STORE_CODE);
        request.setComponents(List.of(
                component("HERO_MAIN", "FRAME", "/api/operations/skin-management/assets/frame.jpg", 0, 0, 1247, 1706, 40)
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveComponents(context(307L, 90003L, STORE_CODE), created.getId(), request)
        );

        assertEquals("皮肤组件只支持 PNG 图片。", error.getMessage());
    }

    private static BusinessAccessContext context(Long ownerUserId, Long sessionUserId, String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(sessionUserId)
                .businessOwnerUserId(ownerUserId)
                .storeOwnerUserIds(Map.of(storeCode, ownerUserId))
                .storeCodes(Set.of(storeCode))
                .build();
    }

    private static BusinessAccessContext contextWithAuthorizedStoreNoOwnerMap(
            Long ownerUserId,
            Long sessionUserId,
            String storeCode
    ) {
        return BusinessAccessContext.builder()
                .sessionUserId(sessionUserId)
                .businessOwnerUserId(ownerUserId)
                .storeOwnerUserIds(Map.of())
                .storeCodes(Set.of(storeCode))
                .build();
    }

    private static BusinessAccessContext contextWithoutStoreScope(Long ownerUserId, Long sessionUserId) {
        return BusinessAccessContext.builder()
                .sessionUserId(sessionUserId)
                .businessOwnerUserId(ownerUserId)
                .storeOwnerUserIds(Map.of())
                .storeCodes(Set.of())
                .build();
    }

    private static OperationsSkinSaveRequest saveRequest(String storeCode, String skinName) {
        OperationsSkinSaveRequest request = new OperationsSkinSaveRequest();
        request.setStoreCode(storeCode);
        request.setSkinName(skinName);
        return request;
    }

    private static OperationsSkinStatusRequest statusRequest(String storeCode, String status) {
        OperationsSkinStatusRequest request = new OperationsSkinStatusRequest();
        request.setStoreCode(storeCode);
        request.setStatus(status);
        return request;
    }

    private static OperationsSkinAssetView asset(String assetType, String imageUrl, String caption, Integer sortOrder) {
        OperationsSkinAssetView asset = new OperationsSkinAssetView();
        asset.setAssetType(assetType);
        asset.setImageUrl(imageUrl);
        asset.setCaption(caption);
        asset.setSortOrder(sortOrder);
        return asset;
    }

    private static OperationsSkinComponentView component(
            String templateRole,
            String componentKey,
            String imageUrl,
            Integer x,
            Integer y,
            Integer width,
            Integer height,
            Integer zIndex
    ) {
        OperationsSkinComponentView component = new OperationsSkinComponentView();
        component.setTemplateRole(templateRole);
        component.setComponentKey(componentKey);
        component.setImageUrl(imageUrl);
        component.setX(x);
        component.setY(y);
        component.setWidth(width);
        component.setHeight(height);
        component.setZIndex(zIndex);
        component.setRequired(true);
        component.setLocked(true);
        component.setStyleJson("{}");
        return component;
    }

    private static final class FakeOperationsSkinMapper implements OperationsSkinMapper {
        private final Map<Long, OperationsSkinRecord> skins = new LinkedHashMap<>();
        private final Map<Long, List<OperationsSkinAssetRecord>> assetsBySkinId = new LinkedHashMap<>();
        private final Map<Long, List<OperationsSkinComponentRecord>> componentsBySkinId = new LinkedHashMap<>();
        private final Map<String, Set<String>> logicalStoreSites = new LinkedHashMap<>();
        private long nextSkinId = 1000L;
        private long nextAssetId = 5000L;
        private long nextComponentId = 8000L;

        private void linkLogicalStoreSites(String... storeCodes) {
            Set<String> linkedSites = Set.of(storeCodes);
            for (String storeCode : storeCodes) {
                logicalStoreSites.put(storeCode, linkedSites);
            }
        }

        @Override
        public Long insertSkin(OperationsSkinRecord record) {
            record.setId(nextSkinId++);
            skins.put(record.getId(), copy(record));
            return record.getId();
        }

        @Override
        public int updateSkin(OperationsSkinRecord record) {
            OperationsSkinRecord existing = skins.get(record.getId());
            if (!matchesScope(existing, record.getOwnerUserId(), record.getStoreCode())) {
                return 0;
            }
            existing.setSkinName(record.getSkinName());
            existing.setStatus(record.getStatus());
            existing.setCoverImageUrl(record.getCoverImageUrl());
            existing.setStyleDescription(record.getStyleDescription());
            existing.setRemark(record.getRemark());
            existing.setSortOrder(record.getSortOrder());
            existing.setUpdatedBy(record.getUpdatedBy());
            existing.setUpdatedAt(record.getUpdatedAt());
            return 1;
        }

        @Override
        public int updateStatus(Long id, Long ownerUserId, String storeCode, String status, Long updatedBy) {
            OperationsSkinRecord existing = skins.get(id);
            if (!matchesScope(existing, ownerUserId, storeCode)) {
                return 0;
            }
            existing.setStatus(status);
            existing.setUpdatedBy(updatedBy);
            existing.setUpdatedAt(LocalDateTime.of(2026, 6, 11, 10, 0));
            return 1;
        }

        @Override
        public int touchSkin(Long id, Long ownerUserId, String storeCode, Long updatedBy) {
            OperationsSkinRecord existing = skins.get(id);
            if (!matchesScope(existing, ownerUserId, storeCode)) {
                return 0;
            }
            existing.setUpdatedBy(updatedBy);
            existing.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 10, 0));
            return 1;
        }

        @Override
        public int softDeleteSkin(Long id, Long ownerUserId, String storeCode, Long updatedBy) {
            OperationsSkinRecord existing = skins.get(id);
            if (!matchesScope(existing, ownerUserId, storeCode)) {
                return 0;
            }
            existing.setDeleted(true);
            existing.setUpdatedBy(updatedBy);
            existing.setUpdatedAt(LocalDateTime.of(2026, 6, 11, 10, 0));
            return 1;
        }

        @Override
        public List<OperationsSkinRecord> selectSkins(Long ownerUserId, String storeCode, String keyword, String status) {
            String trimmedKeyword = keyword == null ? null : keyword.trim();
            return skins.values().stream()
                    .filter(item -> matchesRequestedScope(item, ownerUserId, storeCode))
                    .filter(item -> status == null || status.equals(item.getStatus()))
                    .filter(item -> trimmedKeyword == null
                            || item.getSkinName().contains(trimmedKeyword)
                            || (item.getRemark() != null && item.getRemark().contains(trimmedKeyword)))
                    .sorted(Comparator
                            .comparing((OperationsSkinRecord item) -> item.getSortOrder() == null ? 0 : item.getSortOrder())
                            .thenComparing(OperationsSkinRecord::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(OperationsSkinRecord::getId, Comparator.reverseOrder()))
                    .map(this::copyWithAssetCount)
                    .collect(Collectors.toList());
        }

        @Override
        public OperationsSkinRecord selectSkinById(Long id, Long ownerUserId, String storeCode) {
            OperationsSkinRecord record = skins.get(id);
            if (!matchesRequestedScope(record, ownerUserId, storeCode)) {
                return null;
            }
            return copyWithAssetCount(record);
        }

        @Override
        public int countByName(Long ownerUserId, String storeCode, String skinName, Long excludeId) {
            return (int) skins.values().stream()
                    .filter(item -> matchesRequestedScope(item, ownerUserId, storeCode))
                    .filter(item -> Objects.equals(skinName, item.getSkinName()))
                    .filter(item -> excludeId == null || !Objects.equals(excludeId, item.getId()))
                    .count();
        }

        @Override
        public int countLinkedStoreSites(Long ownerUserId, String accessibleStoreCode, String sourceStoreCode) {
            return logicalStoreSites.getOrDefault(accessibleStoreCode, Set.of(accessibleStoreCode))
                    .contains(sourceStoreCode) ? 1 : 0;
        }

        @Override
        public Long insertAsset(OperationsSkinAssetRecord record) {
            record.setId(nextAssetId++);
            OperationsSkinAssetRecord persisted = copy(record);
            persisted.setDeleted(Boolean.TRUE.equals(persisted.getDeleted()));
            assetsBySkinId.computeIfAbsent(record.getSkinId(), ignored -> new ArrayList<>()).add(persisted);
            return record.getId();
        }

        @Override
        public int softDeleteAssets(Long skinId, Long ownerUserId, String storeCode) {
            if (!matchesScope(skins.get(skinId), ownerUserId, storeCode)) {
                return 0;
            }
            int updated = 0;
            for (OperationsSkinAssetRecord asset : assetsBySkinId.getOrDefault(skinId, List.of())) {
                if (!Boolean.TRUE.equals(asset.getDeleted())) {
                    asset.setDeleted(true);
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public List<OperationsSkinAssetRecord> selectAssets(Long skinId, Long ownerUserId, String storeCode) {
            if (!matchesScope(skins.get(skinId), ownerUserId, storeCode)) {
                return List.of();
            }
            return assetsBySkinId.getOrDefault(skinId, List.of()).stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .sorted(Comparator
                            .comparing((OperationsSkinAssetRecord item) -> item.getSortOrder() == null ? 0 : item.getSortOrder())
                            .thenComparing(OperationsSkinAssetRecord::getId))
                    .map(this::copy)
                    .collect(Collectors.toList());
        }

        private List<Boolean> assetDeletedFlags(Long skinId) {
            return assetsBySkinId.getOrDefault(skinId, List.of()).stream()
                    .map(asset -> Boolean.TRUE.equals(asset.getDeleted()))
                    .collect(Collectors.toList());
        }

        private boolean matchesScope(OperationsSkinRecord record, Long ownerUserId, String storeCode) {
            return record != null
                    && !Boolean.TRUE.equals(record.getDeleted())
                    && Objects.equals(ownerUserId, record.getOwnerUserId())
                    && Objects.equals(storeCode, record.getStoreCode());
        }

        private boolean matchesRequestedScope(OperationsSkinRecord record, Long ownerUserId, String requestedStoreCode) {
            if (record == null
                    || Boolean.TRUE.equals(record.getDeleted())
                    || !Objects.equals(ownerUserId, record.getOwnerUserId())) {
                return false;
            }
            Set<String> visibleSites = logicalStoreSites.getOrDefault(requestedStoreCode, Set.of(requestedStoreCode));
            return visibleSites.contains(record.getStoreCode());
        }

        private OperationsSkinRecord copyWithAssetCount(OperationsSkinRecord source) {
            OperationsSkinRecord copy = copy(source);
            copy.setAssetCount(selectAssets(source.getId(), source.getOwnerUserId(), source.getStoreCode()).size());
            copy.setHeroComponentCount((int) selectComponents(source.getId(), source.getOwnerUserId()).stream()
                    .filter(component -> "HERO_MAIN".equals(component.getTemplateRole()))
                    .filter(component -> component.getImageUrl() != null && !component.getImageUrl().isBlank())
                    .count());
            return copy;
        }

        private OperationsSkinRecord copy(OperationsSkinRecord source) {
            OperationsSkinRecord copy = new OperationsSkinRecord();
            copy.setId(source.getId());
            copy.setOwnerUserId(source.getOwnerUserId());
            copy.setStoreCode(source.getStoreCode());
            copy.setSkinName(source.getSkinName());
            copy.setStatus(source.getStatus());
            copy.setCoverImageUrl(source.getCoverImageUrl());
            copy.setStyleDescription(source.getStyleDescription());
            copy.setRemark(source.getRemark());
            copy.setSortOrder(source.getSortOrder());
            copy.setCreatedBy(source.getCreatedBy());
            copy.setUpdatedBy(source.getUpdatedBy());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setUpdatedAt(source.getUpdatedAt());
            copy.setDeleted(source.getDeleted());
            copy.setAssetCount(source.getAssetCount());
            copy.setHeroComponentCount(source.getHeroComponentCount());
            return copy;
        }

        private OperationsSkinAssetRecord copy(OperationsSkinAssetRecord source) {
            OperationsSkinAssetRecord copy = new OperationsSkinAssetRecord();
            copy.setId(source.getId());
            copy.setSkinId(source.getSkinId());
            copy.setAssetType(source.getAssetType());
            copy.setImageUrl(source.getImageUrl());
            copy.setCaption(source.getCaption());
            copy.setSortOrder(source.getSortOrder());
            copy.setDeleted(source.getDeleted());
            return copy;
        }

        @Override
        public Long insertComponent(OperationsSkinComponentRecord record) {
            record.setId(nextComponentId++);
            OperationsSkinComponentRecord persisted = copy(record);
            persisted.setDeleted(Boolean.TRUE.equals(persisted.getDeleted()));
            componentsBySkinId.computeIfAbsent(record.getSkinId(), ignored -> new ArrayList<>()).add(persisted);
            return record.getId();
        }

        @Override
        public int softDeleteComponents(Long skinId, Long ownerUserId, String storeCode, Long updatedBy) {
            if (!matchesScope(skins.get(skinId), ownerUserId, storeCode)) {
                return 0;
            }
            int updated = 0;
            for (OperationsSkinComponentRecord component : componentsBySkinId.getOrDefault(skinId, List.of())) {
                if (!Boolean.TRUE.equals(component.getDeleted())) {
                    component.setDeleted(true);
                    component.setUpdatedBy(updatedBy);
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public List<OperationsSkinComponentRecord> selectComponents(Long skinId, Long ownerUserId) {
            OperationsSkinRecord skin = skins.get(skinId);
            if (skin == null || !Objects.equals(ownerUserId, skin.getOwnerUserId())) {
                return List.of();
            }
            return componentsBySkinId.getOrDefault(skinId, List.of()).stream()
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .sorted(Comparator
                            .comparing((OperationsSkinComponentRecord item) -> item.getTemplateRole() == null ? "" : item.getTemplateRole())
                            .thenComparing(item -> item.getZIndex() == null ? 0 : item.getZIndex())
                            .thenComparing(item -> item.getId() == null ? 0L : item.getId()))
                    .map(this::copy)
                    .collect(Collectors.toList());
        }

        private OperationsSkinComponentRecord copy(OperationsSkinComponentRecord source) {
            OperationsSkinComponentRecord copy = new OperationsSkinComponentRecord();
            copy.setId(source.getId());
            copy.setSkinId(source.getSkinId());
            copy.setTemplateRole(source.getTemplateRole());
            copy.setComponentKey(source.getComponentKey());
            copy.setImageUrl(source.getImageUrl());
            copy.setX(source.getX());
            copy.setY(source.getY());
            copy.setWidth(source.getWidth());
            copy.setHeight(source.getHeight());
            copy.setZIndex(source.getZIndex());
            copy.setRequired(source.getRequired());
            copy.setLocked(source.getLocked());
            copy.setStyleJson(source.getStyleJson());
            copy.setCreatedBy(source.getCreatedBy());
            copy.setUpdatedBy(source.getUpdatedBy());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setUpdatedAt(source.getUpdatedAt());
            copy.setDeleted(source.getDeleted());
            return copy;
        }
    }
}
