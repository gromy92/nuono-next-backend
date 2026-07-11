package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.operationsskin.OperationsSkinComponentRecord;
import com.nuono.next.operationsskin.OperationsSkinRecord;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductImageProfileServiceTest {

    @Mock
    private ProductImageProfileMapper mapper;
    @Mock
    private OperationsSkinMapper operationsSkinMapper;
    @Mock
    private ProductPublicDetailMapper productPublicDetailMapper;
    @Mock
    private AiCapabilityService aiCapabilityService;

    private ProductImageProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductImageProfileService(
                mapper,
                operationsSkinMapper,
                productPublicDetailMapper,
                aiCapabilityService
        );
    }

    @Test
    void listShouldInitializeMissingStoreProfileWithFullTitle() {
        ProductImageProductCandidateRecord candidate = candidateRecord();
        candidate.setProductTitle("3-piece white flameless LED candles with remote control and timer for home hotel party decor");
        when(mapper.selectAllProductCandidatesForStore(307L, "STR69486-NSA")).thenReturn(List.of(candidate));
        when(mapper.selectProfileByIdentity(307L, "STR69486-NSA", "SGGRB291", "variant:92001")).thenReturn(null);
        when(mapper.selectProfilesForStore(307L, "STR69486-NSA", null)).thenReturn(List.of());
        when(mapper.selectProductCandidates(307L, "STR69486-NSA", null)).thenReturn(List.of());

        ProductImageProfileListCommand command = new ProductImageProfileListCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR69486-NSA");
        command.setOperatorUserId(10003L);

        service.list(command);

        ArgumentCaptor<ProductImageProfileRecord> profileCaptor = ArgumentCaptor.forClass(ProductImageProfileRecord.class);
        verify(mapper).insertProfile(profileCaptor.capture());
        assertEquals("SGGRB291", profileCaptor.getValue().getPskuCode());
        assertEquals(
                "3-piece white flameless LED candles with remote control and timer for home hotel party decor",
                profileCaptor.getValue().getTitleEn()
        );
        assertEquals("ACTIVE", profileCaptor.getValue().getProfileStatus());
    }

    @Test
    void listShouldRefreshStoredShortTitleToFullTitleWithoutClearingArabicTitle() {
        ProductImageProductCandidateRecord candidate = candidateRecord();
        candidate.setProductTitle("3-piece white flameless LED candles with remote control and timer for home hotel party decor");
        ProductImageProfileRecord existing = profileRecord();
        existing.setId(7101L);
        existing.setStoreCode("STR69486-NSA");
        existing.setPskuCode("SGGRB291");
        existing.setProductIdentityKey("variant:92001");
        existing.setTitleAr("٣ قطع شموع LED بدون لهب");
        existing.setTitleEn("3Pcs Flameless LED Candles");
        existing.setProductFactText(null);
        when(mapper.selectAllProductCandidatesForStore(307L, "STR69486-NSA")).thenReturn(List.of(candidate));
        when(mapper.selectProfileByIdentity(307L, "STR69486-NSA", "SGGRB291", "variant:92001")).thenReturn(existing);
        when(mapper.selectProfilesForStore(307L, "STR69486-NSA", null)).thenReturn(List.of());
        when(mapper.selectProductCandidates(307L, "STR69486-NSA", null)).thenReturn(List.of());

        ProductImageProfileListCommand command = new ProductImageProfileListCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR69486-NSA");
        command.setOperatorUserId(10003L);

        service.list(command);

        ArgumentCaptor<ProductImageProfileRecord> profileCaptor = ArgumentCaptor.forClass(ProductImageProfileRecord.class);
        verify(mapper).updateProfile(profileCaptor.capture());
        assertEquals("٣ قطع شموع LED بدون لهب", profileCaptor.getValue().getTitleAr());
        assertEquals(
                "3-piece white flameless LED candles with remote control and timer for home hotel party decor",
                profileCaptor.getValue().getTitleEn()
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                profileCaptor.getValue().getProductFactText().contains("英文完整标题：3-piece white flameless LED candles")
        );
    }

    @Test
    void saveShouldCreateProfileWithProductIdentityAndSections() {
        ProductImageProfileSaveCommand command = new ProductImageProfileSaveCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setPskuCode("PAPERSAYSB024");
        command.setProductIdentityKey("variant:53001");
        command.setProductVariantId(53001L);
        command.setBrand("PAPERSAY");
        command.setTitleAr("أقلام سبورة مغناطيسية");
        command.setTitleEn("Magnetic Whiteboard Markers");
        command.setSpecSummary("8 Colors");
        command.setHeroSellingPoints(List.of("8 colors", "magnetic cap"));
        ProductImageSectionCommand section = new ProductImageSectionCommand();
        section.setSectionType(ProductImageSectionType.CORE_FEATURE);
        section.setTitleEn("Magnetic Cap");
        section.setSortOrder(1);
        command.setSections(List.of(section));

        when(mapper.selectProfileByIdentity(307L, "STR108065-NAE", "PAPERSAYSB024", "variant:53001"))
                .thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageProfileRecord record = invocation.getArgument(0);
            record.setId(7001L);
            return 1;
        }).when(mapper).insertProfile(any());
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of(sectionRecord()));
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.save(command);

        ArgumentCaptor<ProductImageProfileRecord> profileCaptor = ArgumentCaptor.forClass(ProductImageProfileRecord.class);
        verify(mapper).insertProfile(profileCaptor.capture());
        assertEquals("variant:53001", profileCaptor.getValue().getProductIdentityKey());
        verify(mapper).replaceSectionsAsDeleted(7001L);
        verify(mapper).insertSection(any());
        assertEquals(7001L, view.getId());
    }

    @Test
    void removeAssetShouldMarkBasicImageRemovedWithoutDeletingProfile() {
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.updateAssetStatus(7001L, 31L, ProductImageAssetStatus.REMOVED, 10003L)).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.removeAsset(307L, "STR108065-NAE", 7001L, 31L, 10003L);

        verify(mapper).updateAssetStatus(7001L, 31L, ProductImageAssetStatus.REMOVED, 10003L);
    }

    @Test
    void removeAssetsShouldCreateRemovalMarkerForCurrentProductImage() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageAssetRemoveItemCommand item = new ProductImageAssetRemoveItemCommand();
        item.setImageUrl("https://example.test/current.jpg");
        ProductImageAssetBatchRemoveCommand command = new ProductImageAssetBatchRemoveCommand();
        command.setAssets(List.of(item));
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.countProfileAssetByUrl(7001L, "https://example.test/current.jpg")).thenReturn(0);
        when(mapper.countCurrentProductImageByUrl(9001L, "https://example.test/current.jpg")).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.removeAssets(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor = ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals(7001L, assetCaptor.getValue().getProfileId());
        assertEquals("https://example.test/current.jpg", assetCaptor.getValue().getImageUrl());
        assertEquals(ProductImageAssetStatus.REMOVED, assetCaptor.getValue().getAssetStatus());
    }

    @Test
    void addAssetShouldPersistImageMetadata() {
        ProductImageAssetCreateCommand command = new ProductImageAssetCreateCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setProfileId(7001L);
        command.setImageUrl("/api/product-images/assets/STR108065-NAE/sample.jpg");
        command.setContentType("image/jpeg");
        command.setSizeBytes(102400L);
        command.setWidthPx(1200);
        command.setHeightPx(1600);
        command.setImageRole(ProductImageRole.SIZE);
        command.setOperatorUserId(10003L);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.addAsset(command);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor = ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals("image/jpeg", assetCaptor.getValue().getContentType());
        assertEquals(102400L, assetCaptor.getValue().getSizeBytes());
        assertEquals(1200, assetCaptor.getValue().getWidthPx());
        assertEquals(1600, assetCaptor.getValue().getHeightPx());
        assertEquals(ProductImageRole.SIZE, assetCaptor.getValue().getImageRole());
    }

    @Test
    void addAssetsFromUrlsShouldPersistUniqueHttpUrlsWithDefaultRole() {
        ProductImageAssetUrlImportCommand command = new ProductImageAssetUrlImportCommand();
        command.setImageUrls(List.of(
                " https://example.test/a.jpg ",
                "https://example.test/a.jpg",
                "http://example.test/b.webp"
        ));
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.addAssetsFromUrls(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor = ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper, times(2)).insertAsset(assetCaptor.capture());
        assertEquals("https://example.test/a.jpg", assetCaptor.getAllValues().get(0).getImageUrl());
        assertEquals("http://example.test/b.webp", assetCaptor.getAllValues().get(1).getImageUrl());
        assertEquals(ProductImageRole.MAIN, assetCaptor.getAllValues().get(0).getImageRole());
        assertEquals(ProductImageAssetStatus.ACTIVE, assetCaptor.getAllValues().get(0).getAssetStatus());
    }

    @Test
    void addAssetsFromUrlsShouldPersistRequestedSplitRole() {
        ProductImageAssetUrlImportCommand command = new ProductImageAssetUrlImportCommand();
        command.setImageUrls(List.of("https://example.test/scene.jpg"));
        command.setImageRole(ProductImageRole.SCENE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.addAssetsFromUrls(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor = ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals(ProductImageRole.SCENE, assetCaptor.getValue().getImageRole());
    }

    @Test
    void updateAssetRoleShouldPersistProfileAssetRole() {
        ProductImageAssetRoleUpdateCommand command = new ProductImageAssetRoleUpdateCommand();
        command.setAssetId(62001L);
        command.setImageRole(ProductImageRole.PACKAGE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.updateAssetRole(7001L, 62001L, ProductImageRole.PACKAGE, 10003L)).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.updateAssetRole(307L, "STR108065-NAE", 7001L, command, 10003L);

        verify(mapper).updateAssetRole(7001L, 62001L, ProductImageRole.PACKAGE, 10003L);
    }

    @Test
    void updateAssetRoleShouldCreateOverlayForCurrentProductImageUrl() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageAssetRoleUpdateCommand command = new ProductImageAssetRoleUpdateCommand();
        command.setImageUrl("https://example.test/current.jpg");
        command.setImageRole(ProductImageRole.SCENE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.updateAssetRoleByUrl(7001L, "https://example.test/current.jpg", ProductImageRole.SCENE, 10003L)).thenReturn(0);
        when(mapper.countCurrentProductImageByUrl(9001L, "https://example.test/current.jpg")).thenReturn(1);
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.updateAssetRole(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor = ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals("https://example.test/current.jpg", assetCaptor.getValue().getImageUrl());
        assertEquals(ProductImageRole.SCENE, assetCaptor.getValue().getImageRole());
        assertEquals(ProductImageAssetStatus.ACTIVE, assetCaptor.getValue().getAssetStatus());
    }

    @Test
    void addAssetsFromUrlsShouldRejectEmptyUrlList() {
        ProductImageAssetUrlImportCommand command = new ProductImageAssetUrlImportCommand();
        command.setImageUrls(List.of(" ", ""));
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.addAssetsFromUrls(307L, "STR108065-NAE", 7001L, command, 10003L)
        );

        assertEquals("图片链接不能为空。", error.getMessage());
    }

    @Test
    void addAssetsFromUrlsShouldRejectUnsupportedProtocol() {
        ProductImageAssetUrlImportCommand command = new ProductImageAssetUrlImportCommand();
        command.setImageUrls(List.of("file:///tmp/a.jpg"));
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.addAssetsFromUrls(307L, "STR108065-NAE", 7001L, command, 10003L)
        );

        assertEquals("图片链接只支持 HTTP 或 HTTPS。", error.getMessage());
    }

    @Test
    void detailShouldExposeCurrentProductImageMetadata() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setId(62001L);
        asset.setImageUrl("https://example.test/product.jpg");
        asset.setContentType("image/jpeg");
        asset.setSizeBytes(345678L);
        asset.setWidthPx(1247);
        asset.setHeightPx(1700);
        asset.setImageRole(ProductImageRole.MAIN);
        asset.setSortOrder(0);
        asset.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(asset));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.detail(307L, "STR108065-NAE", 7001L);

        assertEquals(345678L, view.getAssets().get(0).getSizeBytes());
        assertEquals(1247, view.getAssets().get(0).getWidthPx());
        assertEquals(1700, view.getAssets().get(0).getHeightPx());
        assertEquals("image/jpeg", view.getAssets().get(0).getContentType());
    }

    @Test
    void detailShouldExcludeCurrentProductImagesWithRemovalMarkers() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setId(62001L);
        asset.setImageUrl("https://example.test/current.jpg");
        asset.setImageRole(ProductImageRole.MAIN);
        asset.setSortOrder(0);
        asset.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectRemovedAssetUrls(7001L)).thenReturn(List.of("https://example.test/current.jpg"));
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(asset));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.detail(307L, "STR108065-NAE", 7001L);

        assertEquals(0, view.getAssets().size());
    }

    @Test
    void detailShouldHideDiscardedSuites() {
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of(
                suiteRecord(99L, ProductImageSuiteStatus.DISCARDED),
                suiteRecord(100L, ProductImageSuiteStatus.DRAFT)
        ));
        when(mapper.selectSuiteAssets(100L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.detail(307L, "STR108065-NAE", 7001L);

        assertEquals(1, view.getSuites().size());
        assertEquals(100L, view.getSuites().get(0).getId());
        verify(mapper, never()).selectSuiteAssets(99L);
    }

    @Test
    void adoptSuiteShouldMovePreviousAdoptedSuiteToHistorical() {
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectSuiteById(99L, 7001L)).thenReturn(suiteRecord(99L, ProductImageSuiteStatus.DRAFT));
        when(mapper.markAdoptedSuitesHistorical(7001L, 10003L)).thenReturn(1);
        when(mapper.updateSuiteStatus(99L, 7001L, ProductImageSuiteStatus.ADOPTED, 10003L)).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of(suiteRecord(99L, ProductImageSuiteStatus.ADOPTED)));
        when(mapper.selectSuiteAssets(99L)).thenReturn(List.of());

        service.adoptSuite(307L, "STR108065-NAE", 7001L, 99L, 10003L);

        verify(mapper).markAdoptedSuitesHistorical(7001L, 10003L);
        verify(mapper).updateSuiteStatus(99L, 7001L, ProductImageSuiteStatus.ADOPTED, 10003L);
    }

    @Test
    void deleteSuiteShouldSoftDeleteInsteadOfDiscarding() {
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectSuiteById(99L, 7001L)).thenReturn(suiteRecord(99L, ProductImageSuiteStatus.ADOPTED));
        when(mapper.softDeleteSuite(99L, 7001L, 10003L)).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.deleteSuite(307L, "STR108065-NAE", 7001L, 99L, 10003L);

        verify(mapper).softDeleteSuite(99L, 7001L, 10003L);
        verify(mapper, never()).updateSuiteStatus(99L, 7001L, ProductImageSuiteStatus.DISCARDED, 10003L);
    }

    @Test
    void moveSuiteAssetShouldMoveAcrossSuitesAndNormalizeSortOrders() {
        ProductImageSuiteAssetMoveCommand command = new ProductImageSuiteAssetMoveCommand();
        command.setTargetSuiteId(100L);
        command.setTargetIndex(1);
        ProductImageSuiteAssetRecord movedAsset = suiteAssetRecord(501L, 99L, 10);
        ProductImageSuiteAssetRecord remainingAsset = suiteAssetRecord(502L, 99L, 20);
        ProductImageSuiteAssetRecord targetAsset = suiteAssetRecord(600L, 100L, 10);
        ProductImageSuiteAssetRecord movedTargetAsset = suiteAssetRecord(501L, 100L, 30);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectSuiteById(99L, 7001L)).thenReturn(suiteRecord(99L, ProductImageSuiteStatus.DRAFT));
        when(mapper.selectSuiteById(100L, 7001L)).thenReturn(suiteRecord(100L, ProductImageSuiteStatus.DRAFT));
        when(mapper.selectSuiteAssetById(99L, 501L)).thenReturn(movedAsset);
        when(mapper.selectMaxSuiteAssetSortOrder(100L)).thenReturn(20);
        when(mapper.moveSuiteAssetToSuite(99L, 501L, 100L, 30)).thenReturn(1);
        when(mapper.selectSuiteAssets(99L)).thenReturn(List.of(remainingAsset));
        when(mapper.selectSuiteAssets(100L)).thenReturn(List.of(targetAsset, movedTargetAsset));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.moveSuiteAsset(307L, "STR108065-NAE", 7001L, 99L, 501L, command, 10003L);

        verify(mapper).moveSuiteAssetToSuite(99L, 501L, 100L, 30);
        verify(mapper).updateSuiteAssetSortOrder(99L, 502L, 10);
        verify(mapper).updateSuiteAssetSortOrder(100L, 600L, 10);
        verify(mapper).updateSuiteAssetSortOrder(100L, 501L, 20);
        verify(mapper).touchSuite(99L, 7001L, 10003L);
        verify(mapper).touchSuite(100L, 7001L, 10003L);
    }

    @Test
    void createAiSuiteDraftShouldPersistDraftPackageWithCurrentStoreSkin() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        profile.setTitleAr("حامل بطاقة تعريف");
        profile.setSpecSummary("Black");
        profile.setProductFactText("商品事实：badge holder with retractable reel");
        profile.setHeroSellingPointsJson("[\"Retractable reel\",\"Clear ID window\"]");
        ProductImageProfileAssetRecord currentImage = new ProductImageProfileAssetRecord();
        currentImage.setImageUrl("https://example.test/product-main.jpg");
        currentImage.setImageRole(ProductImageRole.MAIN);
        currentImage.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        currentImage.setSortOrder(0);
        ProductImageSectionRecord sizeSection = sectionRecord(ProductImageSectionType.SIZE, "4.3 x 2.8 in");
        ProductImageSectionRecord featureSection = sectionRecord(ProductImageSectionType.CORE_FEATURE, "Retractable reel");
        OperationsSkinRecord skin = skinRecord();
        OperationsSkinComponentRecord detailFrame = skinComponent(
                "DETAIL_IMAGE",
                "DETAIL_FRAME",
                "/operations-skins/papersay/detail-frame.png"
        );
        List<OperationsSkinComponentRecord> components = List.of(
                skinComponent("FRAME", "/operations-skins/papersay/frame.png"),
                skinComponent("BRAND_LOCKUP", "/operations-skins/papersay/brand.png"),
                skinComponent("SPEC_BG", "/operations-skins/papersay/spec.png"),
                skinComponent("MAIN_TITLE_BG", "/operations-skins/papersay/title.png"),
                detailFrame
        );

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(operationsSkinMapper.selectSkins(307L, "STR108065-NAE", null, "ACTIVE")).thenReturn(List.of(skin));
        when(operationsSkinMapper.selectComponents(3001L, 307L, "STR108065-NAE")).thenReturn(components);
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(currentImage));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of(sizeSection, featureSection));
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageSuiteRecord suite = invocation.getArgument(0);
            suite.setId(9901L);
            return 1;
        }).when(mapper).insertSuite(any());
        when(mapper.selectSuites(7001L)).thenReturn(List.of(suiteRecord(9901L, ProductImageSuiteStatus.DRAFT)));
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 10003L);

        ArgumentCaptor<ProductImageSuiteRecord> suiteCaptor = ArgumentCaptor.forClass(ProductImageSuiteRecord.class);
        verify(mapper).insertSuite(suiteCaptor.capture());
        ProductImageSuiteRecord suite = suiteCaptor.getValue();
        assertEquals(7001L, suite.getProfileId());
        assertEquals(3001L, suite.getSkinId());
        assertEquals("PAPERSAY 黄框主图皮肤", suite.getSkinName());
        assertEquals(ProductImageSuiteStatus.DRAFT, suite.getSuiteStatus());
        assertTrue(suite.getDraftPromptText().contains("图片要求"));
        assertTrue(suite.getDraftPromptText().contains("4.3 x 2.8 in"));
        assertTrue(suite.getDraftPackageJson().contains("\"skinId\":3001"));
        assertTrue(suite.getDraftPackageJson().contains("\"componentKey\":\"FRAME\""));
        assertTrue(suite.getDraftPackageJson().contains("\"templateRole\":\"DETAIL_IMAGE\""));
        assertTrue(suite.getDraftPackageJson().contains("\"componentKey\":\"DETAIL_FRAME\""));
        assertTrue(suite.getDraftPromptText().contains("DETAIL_IMAGE/DETAIL_FRAME"));
        assertTrue(suite.getDraftPackageJson().contains("https://example.test/product-main.jpg"));
        assertEquals(1, view.getSuites().size());
    }

    @Test
    void extractImageFactsShouldReturnAiSuggestionsWithoutSaving() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        profile.setProductVariantId(53001L);
        profile.setProductTitle("PAPERSAY 12 Pack Retractable Black Gel Ink Pens");
        profile.setProductFactText("Existing profile fact text");
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setStoreCode("STR108065-NAE");
        snapshot.setSiteCode("AE");
        snapshot.setTitleEn("12 Pack Retractable Black Gel Ink Pens");
        snapshot.setTitleAr("أقلام حبر جل سوداء قابلة للسحب 12 قطعة");
        snapshot.setRawPayloadJson("{\"feature_bullets\":[\"Smooth 0.5mm writing\",\"12 pieces in one pack\"],\"specifications\":{\"Ink Color\":\"Black\"}}");
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        Map<String, Object> parsedJson = new LinkedHashMap<>();
        parsedJson.put("specSummary", "Black · 0.5mm · 12 Pieces");
        parsedJson.put("titleEn", "12 Pack Retractable Black Gel Ink Pens Fine Point 0.5mm");
        parsedJson.put("titleAr", "أقلام حبر جل سوداء قابلة للسحب 12 قطعة");
        parsedJson.put("sizeText", "");
        parsedJson.put("heroSellingPoints", List.of("Smooth 0.5mm writing", "Retractable pen body"));
        parsedJson.put("packageText", "12 black gel ink pens per pack");
        aiResult.setParsedJson(parsedJson);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(307L, "STR108065-NAE", "PAPERSAYSB024"))
                .thenReturn(snapshot);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        ProductImageAiExtractionSuggestionView view = service.extractImageFacts(
                307L,
                "STR108065-NAE",
                7001L,
                10003L
        );

        assertEquals("Black · 0.5mm · 12 Pieces", view.getSpecSummary());
        assertEquals("Retractable Gel Ink Pens Fine Point", view.getTitleEn());
        assertEquals("أقلام حبر جل سوداء قابلة للسحب", view.getTitleAr());
        assertEquals("", view.getSizeText());
        assertEquals(List.of("Smooth 0.5mm writing", "Retractable pen body"), view.getHeroSellingPoints());
        assertEquals("12 black gel ink pens per pack", view.getPackageText());
        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        assertTrue(commandCaptor.getValue().getPrompt().contains("feature_bullets"));
        assertTrue(commandCaptor.getValue().getInstructions().contains("避免图片里重复展示"));
        assertEquals(false, commandCaptor.getValue().getSchema().get("additionalProperties"));
        verify(productPublicDetailMapper, never()).selectLatestSnapshot(any(), any(), any(), any(), any());
        verify(mapper, never()).updateProfile(any());
        verify(mapper, never()).replaceSectionsAsDeleted(any());
    }

    @Test
    void extractImageFactsShouldOmitSiblingSiteRawPublicDetailPayload() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        profile.setProductVariantId(53001L);
        profile.setProductTitle("PAPERSAY 12 Pack Retractable Black Gel Ink Pens");
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setStoreCode("STR108065-NSA");
        snapshot.setSiteCode("SA");
        snapshot.setTitleEn("Sibling public title");
        snapshot.setBrand("PAPERSAY");
        snapshot.setRawPayloadJson("{\"price\":\"88.50\",\"currency\":\"SAR\",\"url\":\"https://www.noon.com/saudi-en/Z/p/\",\"availability\":\"SA only\"}");
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        Map<String, Object> parsedJson = new LinkedHashMap<>();
        parsedJson.put("specSummary", "");
        parsedJson.put("titleEn", "Sibling public title");
        parsedJson.put("titleAr", "");
        parsedJson.put("sizeText", "");
        parsedJson.put("heroSellingPoints", List.of());
        parsedJson.put("packageText", "");
        aiResult.setParsedJson(parsedJson);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(307L, "STR108065-NAE", "PAPERSAYSB024"))
                .thenReturn(snapshot);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        service.extractImageFacts(307L, "STR108065-NAE", 7001L, 10003L);

        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        String prompt = commandCaptor.getValue().getPrompt();
        assertTrue(prompt.contains("Sibling public title"));
        assertTrue(prompt.contains("该快照来自同店铺的兄弟站点"));
        assertFalse(prompt.contains("\"price\""));
        assertFalse(prompt.contains("SAR"));
        assertFalse(prompt.contains("saudi-en"));
        assertFalse(prompt.contains("SA only"));
    }

    private ProductImageProfileRecord profileRecord() {
        ProductImageProfileRecord record = new ProductImageProfileRecord();
        record.setId(7001L);
        record.setOwnerUserId(307L);
        record.setStoreCode("STR108065-NAE");
        record.setPskuCode("PAPERSAYSB024");
        record.setProductIdentityKey("variant:53001");
        record.setBrand("PAPERSAY");
        record.setTitleEn("Magnetic Whiteboard Markers");
        return record;
    }

    private ProductImageProductCandidateRecord candidateRecord() {
        ProductImageProductCandidateRecord record = new ProductImageProductCandidateRecord();
        record.setProductMasterId(9001L);
        record.setProductVariantId(92001L);
        record.setPskuCode("SGGRB291");
        record.setProductIdentityKey("variant:92001");
        record.setBrand("Yalla Pick");
        return record;
    }

    private ProductImageSectionRecord sectionRecord() {
        return sectionRecord(ProductImageSectionType.CORE_FEATURE, "Magnetic Cap");
    }

    private ProductImageSectionRecord sectionRecord(ProductImageSectionType sectionType, String titleEn) {
        ProductImageSectionRecord record = new ProductImageSectionRecord();
        record.setId(8001L);
        record.setProfileId(7001L);
        record.setSectionType(sectionType);
        record.setTitleEn(titleEn);
        record.setSortOrder(1);
        record.setEnabled(true);
        return record;
    }

    private ProductImageSuiteRecord suiteRecord(Long id, ProductImageSuiteStatus status) {
        ProductImageSuiteRecord record = new ProductImageSuiteRecord();
        record.setId(id);
        record.setProfileId(7001L);
        record.setSuiteName("候选套图");
        record.setSuiteStatus(status);
        return record;
    }

    private ProductImageSuiteAssetRecord suiteAssetRecord(Long id, Long suiteId, Integer sortOrder) {
        ProductImageSuiteAssetRecord record = new ProductImageSuiteAssetRecord();
        record.setId(id);
        record.setSuiteId(suiteId);
        record.setImageRole(ProductImageSuiteAssetRole.MAIN);
        record.setImageUrl("https://example.test/suite-" + suiteId + "-" + id + ".png");
        record.setSortOrder(sortOrder);
        return record;
    }

    private OperationsSkinRecord skinRecord() {
        OperationsSkinRecord record = new OperationsSkinRecord();
        record.setId(3001L);
        record.setOwnerUserId(307L);
        record.setStoreCode("STR108065-NAE");
        record.setSkinName("PAPERSAY 黄框主图皮肤");
        record.setStatus("ACTIVE");
        record.setHeroComponentCount(4);
        return record;
    }

    private OperationsSkinComponentRecord skinComponent(String componentKey, String imageUrl) {
        return skinComponent("HERO_MAIN", componentKey, imageUrl);
    }

    private OperationsSkinComponentRecord skinComponent(String templateRole, String componentKey, String imageUrl) {
        OperationsSkinComponentRecord record = new OperationsSkinComponentRecord();
        record.setSkinId(3001L);
        record.setTemplateRole(templateRole);
        record.setComponentKey(componentKey);
        record.setImageUrl(imageUrl);
        record.setX(0);
        record.setY(0);
        record.setWidth(100);
        record.setHeight(100);
        record.setZIndex(1);
        record.setRequired(true);
        record.setLocked(true);
        record.setStyleJson("{}");
        return record;
    }
}
