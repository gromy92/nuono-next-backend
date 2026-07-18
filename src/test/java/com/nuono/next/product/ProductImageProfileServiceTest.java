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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductImageProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductImageProfileService(
                mapper,
                operationsSkinMapper,
                productPublicDetailMapper,
                aiCapabilityService,
                eventPublisher
        );
    }

    @Test
    void createAiSuiteDraftShouldBlockAndListMissingBasicFields() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setBrand(null);
        profile.setTitleEn(null);
        profile.setSpecSummary(null);
        profile.setProductFactText(null);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 3001L, 10003L)
        );

        assertTrue(exception.getMessage().contains("品牌"));
        assertTrue(exception.getMessage().contains("英文或阿语标题"));
        assertTrue(exception.getMessage().contains("规格摘要"));
        assertTrue(exception.getMessage().contains("商品事实资料"));
        assertTrue(exception.getMessage().contains("基础图片"));
        verify(mapper, never()).insertSuite(any());
    }

    @Test
    void rejectSuiteShouldKeepUnselectedImageAndRegenerateSelectedImage() {
        ProductImageProfileRecord profile = profileRecord();
        ProductImageSuiteRecord source = suiteRecord(9901L, ProductImageSuiteStatus.PENDING_REVIEW);
        source.setProfileId(7001L);
        source.setRevisionNo(1);
        source.setSuiteName("AI 套图");
        source.setSkinId(3001L);
        source.setSkinName("PAPERSAY");
        source.setDraftPromptText("prompt");
        ProductImageSuiteAssetRecord main = suiteAsset(5001L, ProductImageSuiteAssetRole.MAIN, "/main.png");
        ProductImageSuiteAssetRecord size = suiteAsset(5002L, ProductImageSuiteAssetRole.SIZE, "/size.png");
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectSuiteById(9901L, 7001L)).thenReturn(source);
        when(mapper.selectSuiteAssets(9901L)).thenReturn(List.of(main, size));
        when(mapper.reviewSuite(9901L, 7001L, ProductImageSuiteStatus.HISTORICAL, "尺寸不清楚", 10003L)).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageSuiteRecord revision = invocation.getArgument(0);
            revision.setId(9902L);
            return 1;
        }).when(mapper).insertSuite(any());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());
        when(mapper.selectAssets(7001L)).thenReturn(List.of());

        ProductImageReviewRejectRequest request = new ProductImageReviewRejectRequest();
        request.setComment("尺寸不清楚");
        request.setAssetIds(List.of(5002L));
        service.rejectSuite(307L, "STR108065-NAE", 7001L, 9901L, request, 10003L);

        ArgumentCaptor<ProductImageSuiteAssetRecord> retained = ArgumentCaptor.forClass(ProductImageSuiteAssetRecord.class);
        verify(mapper).insertSuiteAsset(retained.capture());
        assertEquals(ProductImageSuiteAssetRole.MAIN, retained.getValue().getImageRole());
        assertEquals("/main.png", retained.getValue().getImageUrl());
        verify(mapper).insertReviewTarget(
                9901L, "IMAGE", 5002L, ProductImageSuiteAssetRole.SIZE, 1, 10003L
        );
        verify(eventPublisher).publishEvent(any(ProductImageGenerationSubmittedEvent.class));
    }

    @Test
    void listShouldInitializeMissingStoreProfileWithFullTitle() {
        ProductImageProductCandidateRecord candidate = candidateRecord();
        candidate.setProductTitle("3-piece white flameless LED candles with remote control and timer for home hotel party decor");
        when(mapper.selectAllProductCandidatesForStore(307L, "STR69486-NSA")).thenReturn(List.of(candidate));
        when(mapper.selectProfileByIdentity(307L, "STR69486-NSA", "SGGRB291", "psku:SGGRB291")).thenReturn(null);
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
        existing.setProductIdentityKey("psku:SGGRB291");
        existing.setTitleAr("٣ قطع شموع LED بدون لهب");
        existing.setTitleEn("3Pcs Flameless LED Candles");
        existing.setProductFactText(null);
        when(mapper.selectAllProductCandidatesForStore(307L, "STR69486-NSA")).thenReturn(List.of(candidate));
        when(mapper.selectProfileByIdentity(307L, "STR69486-NSA", "SGGRB291", "psku:SGGRB291")).thenReturn(existing);
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
    void listSummariesShouldReturnLightweightRowsWithoutDetailAssetQueries() {
        ProductImageProductCandidateRecord candidate = candidateRecord();
        candidate.setProductTitle("Candidate title");
        ProductImageProfileSummaryRecord summary = new ProductImageProfileSummaryRecord();
        summary.setId(7001L);
        summary.setOwnerUserId(307L);
        summary.setStoreCode("STR108065-NAE");
        summary.setPskuCode("PAPERSAYSB024");
        summary.setProductIdentityKey("psku:PAPERSAYSB024");
        summary.setProductMasterId(9001L);
        summary.setProductTitle("Magnetic Whiteboard Markers");
        summary.setBrand("PAPERSAY");
        summary.setCoverImageUrl("https://example.test/cover.jpg");
        summary.setAssetCount(7);
        summary.setSuiteCount(2);
        summary.setHasAdoptedSuite(true);
        when(mapper.selectAllProductCandidatesForStore(307L, "STR108065-NAE")).thenReturn(List.of(candidate));
        when(mapper.selectProfileByIdentity(307L, "STR108065-NAE", "SGGRB291", "psku:SGGRB291")).thenReturn(profileRecord());
        when(mapper.selectProfileSummariesForStore(307L, "STR108065-NAE", null)).thenReturn(List.of(summary));
        when(mapper.selectProductCandidates(307L, "STR108065-NAE", null)).thenReturn(List.of());

        ProductImageProfileListCommand command = new ProductImageProfileListCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(10003L);

        ProductImageProfileSummaryListView view = service.listSummaries(command);

        assertEquals(1, view.getItems().size());
        ProductImageProfileSummaryView item = view.getItems().get(0);
        assertEquals(7001L, item.getId());
        assertEquals("https://example.test/cover.jpg", item.getCoverImageUrl());
        assertEquals(7, item.getAssetCount());
        assertEquals(2, item.getSuiteCount());
        assertTrue(item.getHasAdoptedSuite());
        verify(mapper, never()).selectAssets(any());
        verify(mapper, never()).selectSections(any());
        verify(mapper, never()).selectSuites(any());
    }

    @Test
    void saveShouldCreateProfileWithPskuIdentityAndSections() {
        ProductImageProfileSaveCommand command = new ProductImageProfileSaveCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setPskuCode("PAPERSAYSB024");
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

        when(mapper.selectProfileByIdentity(307L, "STR108065-NAE", "PAPERSAYSB024", "psku:PAPERSAYSB024"))
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
        assertEquals("psku:PAPERSAYSB024", profileCaptor.getValue().getProductIdentityKey());
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
    void saveAndSyncAssetRolesShouldUpsertListingImageRolesByUrl() {
        ProductImageProfileRecord existing = profileRecord();
        existing.setId(7001L);
        ProductImageProfileSaveCommand command = new ProductImageProfileSaveCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NAE");
        command.setPskuCode("PAPERSAYSB024");
        command.setProductIdentityKey("psku:PAPERSAYSB024");
        command.setOperatorUserId(10003L);
        ProductImageAssetRoleUpdateCommand existingRole = new ProductImageAssetRoleUpdateCommand();
        existingRole.setImageUrl("https://example.test/main.jpg");
        existingRole.setImageRole(ProductImageRole.MAIN);
        existingRole.setSortOrder(0);
        ProductImageAssetRoleUpdateCommand newRole = new ProductImageAssetRoleUpdateCommand();
        newRole.setImageUrl("https://example.test/size.jpg");
        newRole.setImageRole(ProductImageRole.SIZE);
        newRole.setSortOrder(1);

        when(mapper.selectProfileByIdentity(307L, "STR108065-NAE", "PAPERSAYSB024", "psku:PAPERSAYSB024"))
                .thenReturn(existing);
        when(mapper.updateProfile(any())).thenReturn(1);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(existing);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());
        when(mapper.updateAssetRoleAndSortOrderByUrl(
                7001L,
                "https://example.test/main.jpg",
                ProductImageRole.MAIN,
                0,
                10003L
        )).thenReturn(1);
        when(mapper.updateAssetRoleAndSortOrderByUrl(
                7001L,
                "https://example.test/size.jpg",
                ProductImageRole.SIZE,
                1,
                10003L
        )).thenReturn(0);

        service.saveAndSyncAssetRoles(command, List.of(existingRole, newRole));

        verify(mapper).updateAssetRoleAndSortOrderByUrl(
                7001L,
                "https://example.test/main.jpg",
                ProductImageRole.MAIN,
                0,
                10003L
        );
        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor =
                ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals("https://example.test/size.jpg", assetCaptor.getValue().getImageUrl());
        assertEquals(ProductImageRole.SIZE, assetCaptor.getValue().getImageRole());
        assertEquals(1, assetCaptor.getValue().getSortOrder());
        assertEquals(ProductImageAssetStatus.ACTIVE, assetCaptor.getValue().getAssetStatus());
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
        ProductImageProfileAssetRecord current = new ProductImageProfileAssetRecord();
        current.setId(62001L);
        current.setImageUrl("https://example.test/current.jpg");
        current.setContentType("image/jpeg");
        current.setWidthPx(1247);
        current.setHeightPx(1706);
        current.setImageRole(ProductImageRole.MAIN);
        current.setSortOrder(0);
        current.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        ProductImageAssetRoleUpdateCommand command = new ProductImageAssetRoleUpdateCommand();
        command.setImageUrl("https://example.test/current.jpg");
        command.setImageRole(ProductImageRole.SCENE);
        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.updateAssetRoleByUrl(7001L, "https://example.test/current.jpg", ProductImageRole.SCENE, 10003L)).thenReturn(0);
        when(mapper.selectCurrentProductImageByUrl(9001L, "https://example.test/current.jpg")).thenReturn(current);
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
        assertEquals(1247, assetCaptor.getValue().getWidthPx());
        assertEquals(1706, assetCaptor.getValue().getHeightPx());
    }

    @Test
    void addAssetUsagesShouldUseImageUrlWhenCurrentAndProfileAssetIdsCollide() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageProfileAssetRecord collision = new ProductImageProfileAssetRecord();
        collision.setId(62001L);
        collision.setProfileId(7001L);
        collision.setImageUrl("https://example.test/different.jpg");
        ProductImageProfileAssetRecord current = new ProductImageProfileAssetRecord();
        current.setId(62001L);
        current.setImageUrl("https://example.test/current.jpg");
        current.setImageRole(ProductImageRole.MAIN);
        current.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        ProductImageAssetUsageCreateCommand command = new ProductImageAssetUsageCreateCommand();
        command.setAssetId(62001L);
        command.setImageUrl("https://example.test/current.jpg");
        command.setSourceRole(ProductImageRole.MAIN);
        command.setImageRoles(List.of(ProductImageRole.PACKAGE));

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(mapper.selectAssetById(7001L, 62001L)).thenReturn(collision);
        when(mapper.selectCurrentProductImageByUrl(9001L, "https://example.test/current.jpg")).thenReturn(current);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.addAssetUsages(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetRecord> assetCaptor =
                ArgumentCaptor.forClass(ProductImageProfileAssetRecord.class);
        verify(mapper).insertAsset(assetCaptor.capture());
        assertEquals("https://example.test/current.jpg", assetCaptor.getValue().getImageUrl());
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
    void addAssetUsagesShouldReuseOnePhysicalAssetAcrossRoles() {
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setId(62001L);
        asset.setProfileId(7001L);
        asset.setImageUrl("https://example.test/product.jpg");
        asset.setImageRole(ProductImageRole.MAIN);
        asset.setSortOrder(0);
        asset.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        ProductImageAssetUsageCreateCommand command = new ProductImageAssetUsageCreateCommand();
        command.setAssetId(62001L);
        command.setSourceRole(ProductImageRole.MAIN);
        command.setImageRoles(List.of(ProductImageRole.PACKAGE));

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssetById(7001L, 62001L)).thenReturn(asset);
        when(mapper.countActiveAssetUsage(7001L, 62001L, ProductImageRole.MAIN)).thenReturn(1);
        when(mapper.countActiveAssetUsage(7001L, 62001L, ProductImageRole.PACKAGE)).thenReturn(0);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.addAssetUsages(307L, "STR108065-NAE", 7001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetUsageRecord> usageCaptor =
                ArgumentCaptor.forClass(ProductImageProfileAssetUsageRecord.class);
        verify(mapper).insertAssetUsage(usageCaptor.capture());
        assertEquals(62001L, usageCaptor.getValue().getAssetId());
        assertEquals(ProductImageRole.PACKAGE, usageCaptor.getValue().getImageRole());
        assertEquals(ProductImageProcessingStatus.PENDING, usageCaptor.getValue().getProcessingStatus());
        verify(mapper, never()).insertAsset(any());
    }

    @Test
    void updateAssetUsageShouldPersistRoleSpecificNoteAndProcessedMarker() {
        ProductImageProfileAssetUsageRecord usage = new ProductImageProfileAssetUsageRecord();
        usage.setId(88001L);
        usage.setProfileId(7001L);
        usage.setAssetId(62001L);
        usage.setImageRole(ProductImageRole.PACKAGE);
        usage.setProcessingStatus(ProductImageProcessingStatus.PENDING);
        ProductImageAssetUsageUpdateCommand command = new ProductImageAssetUsageUpdateCommand();
        command.setImageRole(ProductImageRole.PACKAGE);
        command.setProcessingNote("保留包装并突出 5 件套");
        command.setProcessingStatus(ProductImageProcessingStatus.PROCESSED);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssetUsageById(7001L, 88001L)).thenReturn(usage);
        when(mapper.updateAssetUsage(any())).thenReturn(1);
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        service.updateAssetUsage(307L, "STR108065-NAE", 7001L, 88001L, command, 10003L);

        ArgumentCaptor<ProductImageProfileAssetUsageRecord> usageCaptor =
                ArgumentCaptor.forClass(ProductImageProfileAssetUsageRecord.class);
        verify(mapper).updateAssetUsage(usageCaptor.capture());
        assertEquals("保留包装并突出 5 件套", usageCaptor.getValue().getProcessingNote());
        assertEquals(ProductImageProcessingStatus.PROCESSED, usageCaptor.getValue().getProcessingStatus());
        assertEquals(10003L, usageCaptor.getValue().getProcessedBy());
        assertTrue(usageCaptor.getValue().getProcessedAt() != null);
    }

    @Test
    void detailShouldExposeSameAssetOncePerUsageWithoutSharingProcessingState() {
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setId(62001L);
        asset.setProfileId(7001L);
        asset.setImageUrl("https://example.test/product.jpg");
        asset.setContentType("image/jpeg");
        asset.setWidthPx(1200);
        asset.setHeightPx(1200);
        asset.setImageRole(ProductImageRole.MAIN);
        asset.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        ProductImageProfileAssetUsageRecord main = new ProductImageProfileAssetUsageRecord();
        main.setId(88001L);
        main.setProfileId(7001L);
        main.setAssetId(62001L);
        main.setImageRole(ProductImageRole.MAIN);
        main.setProcessingStatus(ProductImageProcessingStatus.PROCESSED);
        ProductImageProfileAssetUsageRecord packaging = new ProductImageProfileAssetUsageRecord();
        packaging.setId(88002L);
        packaging.setProfileId(7001L);
        packaging.setAssetId(62001L);
        packaging.setImageRole(ProductImageRole.PACKAGE);
        packaging.setProcessingNote("突出包装数量");
        packaging.setProcessingStatus(ProductImageProcessingStatus.PENDING);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profileRecord());
        when(mapper.selectAssets(7001L)).thenReturn(List.of(asset));
        when(mapper.selectAssetUsages(7001L)).thenReturn(List.of(main, packaging));
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        when(mapper.selectSuites(7001L)).thenReturn(List.of());

        ProductImageProfileDetailView view = service.detail(307L, "STR108065-NAE", 7001L);

        assertEquals(2, view.getAssets().size());
        assertEquals(62001L, view.getAssets().get(0).getId());
        assertEquals(62001L, view.getAssets().get(1).getId());
        assertEquals(ProductImageRole.MAIN, view.getAssets().get(0).getImageRole());
        assertEquals(ProductImageRole.PACKAGE, view.getAssets().get(1).getImageRole());
        assertEquals("突出包装数量", view.getAssets().get(1).getProcessingNote());
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
    void createAiSuiteDraftShouldUseSelectedLogicalStoreSkinWithoutEntrySiteScope() {
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
        skin.setStoreCode("STR108065-NSA");
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
        when(operationsSkinMapper.selectSkinById(3001L, 307L, "STR108065-NAE")).thenReturn(skin);
        when(operationsSkinMapper.selectComponents(3001L, 307L)).thenReturn(components);
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

        ProductImageProfileDetailView view = service.createAiSuiteDraft(
                307L,
                "STR108065-NAE",
                7001L,
                3001L,
                10003L
        );

        ArgumentCaptor<ProductImageSuiteRecord> suiteCaptor = ArgumentCaptor.forClass(ProductImageSuiteRecord.class);
        verify(mapper).insertSuite(suiteCaptor.capture());
        ProductImageSuiteRecord suite = suiteCaptor.getValue();
        assertEquals(7001L, suite.getProfileId());
        assertEquals(3001L, suite.getSkinId());
        assertEquals("PAPERSAY 黄框主图皮肤", suite.getSkinName());
        assertEquals(ProductImageSuiteStatus.PENDING_GENERATION, suite.getSuiteStatus());
        assertTrue(suite.getDraftPromptText().contains("图片要求"));
        assertTrue(suite.getDraftPromptText().contains("4.3 x 2.8 in"));
        assertTrue(suite.getDraftPromptText().contains("每张细节图单独成图"));
        assertTrue(suite.getDraftPromptText().contains("局部大图"));
        assertTrue(suite.getDraftPromptText().contains("场景图使用 SCENE_IMAGE"));
        assertTrue(suite.getDraftPackageJson().contains("\"skinId\":3001"));
        assertTrue(suite.getDraftPackageJson().contains("\"componentKey\":\"FRAME\""));
        assertTrue(suite.getDraftPackageJson().contains("\"templateRole\":\"DETAIL_IMAGE\""));
        assertTrue(suite.getDraftPackageJson().contains("\"componentKey\":\"DETAIL_FRAME\""));
        assertTrue(suite.getDraftPackageJson().contains("\"targetCount\":\"2-4\""));
        assertTrue(suite.getDraftPackageJson().contains("每张细节图单独成图"));
        assertTrue(suite.getDraftPackageJson().contains("局部大图"));
        assertTrue(suite.getDraftPromptText().contains("DETAIL_IMAGE/DETAIL_FRAME"));
        assertTrue(suite.getDraftPackageJson().contains("https://example.test/product-main.jpg"));
        assertFalse(suite.getDraftPackageJson().contains("productVariantId"));
        assertEquals(1, view.getSuites().size());
        verify(operationsSkinMapper).selectComponents(3001L, 307L);
    }

    @Test
    void createAiSuiteDraftShouldChooseLatestEffectiveCompleteSkinAcrossLogicalStore() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageProfileAssetRecord currentImage = new ProductImageProfileAssetRecord();
        currentImage.setImageUrl("https://example.test/product-main.jpg");
        currentImage.setImageRole(ProductImageRole.MAIN);
        currentImage.setAssetStatus(ProductImageAssetStatus.ACTIVE);
        currentImage.setSortOrder(0);

        OperationsSkinRecord olderSkin = skinRecord();
        olderSkin.setId(3001L);
        olderSkin.setStoreCode("STR108065-NAE");
        olderSkin.setUpdatedAt(LocalDateTime.of(2026, 7, 15, 13, 25));
        OperationsSkinRecord latestSkin = skinRecord();
        latestSkin.setId(3002L);
        latestSkin.setStoreCode("STR108065-NSA");
        latestSkin.setSkinName("PAPERSAY 最新通用皮肤");
        latestSkin.setUpdatedAt(LocalDateTime.of(2026, 7, 15, 13, 25));

        List<OperationsSkinComponentRecord> olderComponents = heroComponents(
                3001L,
                LocalDateTime.of(2026, 7, 15, 13, 25)
        );
        List<OperationsSkinComponentRecord> latestComponents = heroComponents(
                3002L,
                LocalDateTime.of(2026, 7, 15, 16, 54)
        );

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(operationsSkinMapper.selectSkins(307L, "STR108065-NAE", null, "ACTIVE"))
                .thenReturn(List.of(olderSkin, latestSkin));
        when(operationsSkinMapper.selectComponents(3001L, 307L)).thenReturn(olderComponents);
        when(operationsSkinMapper.selectComponents(3002L, 307L)).thenReturn(latestComponents);
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(currentImage));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageSuiteRecord suite = invocation.getArgument(0);
            suite.setId(9902L);
            return 1;
        }).when(mapper).insertSuite(any());
        when(mapper.selectSuites(7001L)).thenReturn(List.of(suiteRecord(9902L, ProductImageSuiteStatus.DRAFT)));
        when(mapper.selectSuiteAssets(9902L)).thenReturn(List.of());

        service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 10003L);

        ArgumentCaptor<ProductImageSuiteRecord> suiteCaptor = ArgumentCaptor.forClass(ProductImageSuiteRecord.class);
        verify(mapper).insertSuite(suiteCaptor.capture());
        assertEquals(3002L, suiteCaptor.getValue().getSkinId());
        assertEquals("PAPERSAY 最新通用皮肤", suiteCaptor.getValue().getSkinName());
    }

    @Test
    void createAiSuiteDraftShouldFallBackWhenLatestSkinIsIncomplete() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
        ProductImageProfileAssetRecord currentImage = new ProductImageProfileAssetRecord();
        currentImage.setImageUrl("https://example.test/product-main.jpg");
        currentImage.setImageRole(ProductImageRole.MAIN);
        currentImage.setAssetStatus(ProductImageAssetStatus.ACTIVE);

        OperationsSkinRecord completeSkin = skinRecord();
        completeSkin.setId(3001L);
        completeSkin.setUpdatedAt(LocalDateTime.of(2026, 7, 15, 13, 25));
        OperationsSkinRecord incompleteLatestSkin = skinRecord();
        incompleteLatestSkin.setId(3002L);
        incompleteLatestSkin.setStoreCode("STR108065-NSA");
        incompleteLatestSkin.setUpdatedAt(LocalDateTime.of(2026, 7, 15, 16, 54));
        List<OperationsSkinComponentRecord> incompleteComponents = heroComponents(
                3002L,
                LocalDateTime.of(2026, 7, 15, 16, 54)
        ).subList(0, 3);

        when(mapper.selectProfileById(7001L, 307L, "STR108065-NAE")).thenReturn(profile);
        when(operationsSkinMapper.selectSkins(307L, "STR108065-NAE", null, "ACTIVE"))
                .thenReturn(List.of(incompleteLatestSkin, completeSkin));
        when(operationsSkinMapper.selectComponents(3002L, 307L))
                .thenReturn(incompleteComponents);
        when(operationsSkinMapper.selectComponents(3001L, 307L))
                .thenReturn(heroComponents(3001L, LocalDateTime.of(2026, 7, 15, 13, 25)));
        when(mapper.selectCurrentProductImages(9001L)).thenReturn(List.of(currentImage));
        when(mapper.selectAssets(7001L)).thenReturn(List.of());
        when(mapper.selectSections(7001L)).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
            ProductImageSuiteRecord suite = invocation.getArgument(0);
            suite.setId(9903L);
            return 1;
        }).when(mapper).insertSuite(any());
        when(mapper.selectSuites(7001L)).thenReturn(List.of(suiteRecord(9903L, ProductImageSuiteStatus.DRAFT)));
        when(mapper.selectSuiteAssets(9903L)).thenReturn(List.of());

        service.createAiSuiteDraft(307L, "STR108065-NAE", 7001L, 10003L);

        ArgumentCaptor<ProductImageSuiteRecord> suiteCaptor = ArgumentCaptor.forClass(ProductImageSuiteRecord.class);
        verify(mapper).insertSuite(suiteCaptor.capture());
        assertEquals(3001L, suiteCaptor.getValue().getSkinId());
    }

    @Test
    void extractImageFactsShouldReturnAiSuggestionsWithoutSaving() {
        ProductImageProfileRecord profile = profileRecord();
        profile.setProductMasterId(9001L);
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
        record.setProductIdentityKey("psku:PAPERSAYSB024");
        record.setBrand("PAPERSAY");
        record.setTitleEn("Magnetic Whiteboard Markers");
        record.setSpecSummary("Standard product specification");
        record.setProductFactText("Verified product facts");
        return record;
    }

    private ProductImageProductCandidateRecord candidateRecord() {
        ProductImageProductCandidateRecord record = new ProductImageProductCandidateRecord();
        record.setProductMasterId(9001L);
        record.setPskuCode("SGGRB291");
        record.setProductIdentityKey("psku:SGGRB291");
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

    private ProductImageSuiteAssetRecord suiteAsset(Long id, ProductImageSuiteAssetRole role, String imageUrl) {
        ProductImageSuiteAssetRecord record = new ProductImageSuiteAssetRecord();
        record.setId(id);
        record.setSuiteId(9901L);
        record.setImageRole(role);
        record.setRoleOrdinal(1);
        record.setImageUrl(imageUrl);
        record.setSortOrder(role == ProductImageSuiteAssetRole.MAIN ? 10 : 20);
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

    private List<OperationsSkinComponentRecord> heroComponents(Long skinId, LocalDateTime updatedAt) {
        List<OperationsSkinComponentRecord> components = List.of(
                skinComponent("FRAME", "/operations-skins/papersay/frame.png"),
                skinComponent("BRAND_LOCKUP", "/operations-skins/papersay/brand.png"),
                skinComponent("SPEC_BG", "/operations-skins/papersay/spec.png"),
                skinComponent("MAIN_TITLE_BG", "/operations-skins/papersay/title.png")
        );
        components.forEach(component -> {
            component.setSkinId(skinId);
            component.setUpdatedAt(updatedAt);
        });
        return components;
    }
}
