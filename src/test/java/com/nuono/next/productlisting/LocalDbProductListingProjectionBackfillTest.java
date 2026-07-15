package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductImageAssetRoleUpdateCommand;
import com.nuono.next.product.ProductImageProfileSaveCommand;
import com.nuono.next.product.ProductImageProfileService;
import com.nuono.next.product.ProductImageRole;
import com.nuono.next.product.ProductMasterIdentityRecord;
import com.nuono.next.infrastructure.mapper.ProductListingProjectionMapper;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LocalDbProductListingProjectionBackfillTest {

    @Mock
    private ProductProjectionPersistenceService projectionPersistenceService;

    @Mock
    private ProductListingProjectionMapper projectionMapper;

    @Mock
    private ProductImageProfileService productImageProfileService;

    @Mock
    private ProductManagementMapper productManagementMapper;

    private LocalDbProductListingProjectionBackfill backfill;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backfill = new LocalDbProductListingProjectionBackfill(
                projectionPersistenceService,
                projectionMapper,
                productImageProfileService,
                productManagementMapper
        );
    }

    @Test
    void shouldPersistDraftSnapshotProjectionWhenDraftHasPsku() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setDraftNo("PLD-10001");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setProductTitleCn("本地草稿中文标题");

        backfill.backfillDraftListing(record, draft);

        ArgumentCaptor<ProductMasterSnapshotView> snapshotCaptor =
                ArgumentCaptor.forClass(ProductMasterSnapshotView.class);
        verify(projectionPersistenceService).persistSnapshotProjection(
                eq(10002L),
                snapshotCaptor.capture(),
                eq("draft"),
                anyString(),
                anyList()
        );
        ProductMasterSnapshotView snapshot = snapshotCaptor.getValue();
        assertEquals("listing-draft", snapshot.getMode());
        assertTrue(snapshot.isReady());
        assertEquals("STR245027-NAE", snapshot.getStoreContext().get("storeCode"));
        assertEquals("PRJ69486", snapshot.getStoreContext().get("projectCode"));
        assertEquals("NN-TEST-PSKU", snapshot.getIdentity().get("partnerSku"));
        assertTrue(String.valueOf(snapshot.getIdentity().get("skuParent")).startsWith("LOCAL-NN-TEST-PSKU"));
        assertEquals("本地草稿中文标题", snapshot.getContent().get("titleCn"));
        assertEquals(List.of("https://example.test/images/sku-main.jpg"), snapshot.getContent().get("images"));
        assertEquals(1, snapshot.getSiteOffers().size());
        Map<String, Object> siteOffer = snapshot.getSiteOffers().get(0);
        assertEquals("STR245027-NAE", siteOffer.get("storeCode"));
        assertEquals("NN-TEST-PSKU", siteOffer.get("partnerSku"));
        assertEquals("49.9", siteOffer.get("price"));
        assertEquals(24, siteOffer.get("idWarranty"));
        assertNotNull(siteOffer.get("fbpStock"));
    }

    @Test
    void shouldDeletePersistedBarcodeWhenDraftBarcodeIsCleared() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        ProductMasterIdentityRecord identity = new ProductMasterIdentityRecord();
        identity.setProductMasterId(88001L);
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "NN-TEST-PSKU"
        )).thenReturn(identity);

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setDraftNo("PLD-10001");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setBarcode(null);
        draft.setKeyAttributes(List.of(Map.of(
                "code", "barcode",
                "commonValue", "",
                "enValue", "",
                "arValue", ""
        )));

        backfill.backfillDraftListing(record, draft);

        verify(productManagementMapper).markProductBarcodesDeletedByProductMasterId(88001L, 10002L);
    }

    @Test
    void shouldRetainOnlyCurrentBarcodeWhenDraftBarcodeChanges() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        ProductMasterIdentityRecord identity = new ProductMasterIdentityRecord();
        identity.setProductMasterId(88001L);
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "NN-TEST-PSKU"
        )).thenReturn(identity);

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setDraftNo("PLD-10001");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setBarcode("6290000000099");

        backfill.backfillDraftListing(record, draft);

        verify(productManagementMapper).markOtherProductBarcodesDeletedByProductMasterId(
                88001L,
                "6290000000099",
                10002L
        );
    }

    @Test
    void shouldCarryDraftVariantSizeIntoProductSnapshotProjection() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);

        ProductListingDraftRecord record = new ProductListingDraftRecord();
        record.setId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setDraftNo("PLD-10001");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        setListingDraftField(draft, "sizeEn", "One Size");
        setListingDraftField(draft, "sizeAr", "مقاس واحد");

        backfill.backfillDraftListing(record, draft);

        ArgumentCaptor<ProductMasterSnapshotView> snapshotCaptor =
                ArgumentCaptor.forClass(ProductMasterSnapshotView.class);
        verify(projectionPersistenceService).persistSnapshotProjection(
                eq(10002L),
                snapshotCaptor.capture(),
                eq("draft"),
                anyString(),
                anyList()
        );
        Map<String, Object> variant = snapshotCaptor.getValue().getVariants().get(0);
        assertEquals("One Size", variant.get("sizeEn"));
        assertEquals("مقاس واحد", variant.get("sizeAr"));
        assertEquals("One Size", variant.get("displaySize"));
    }

    @Test
    void shouldPersistSuccessfulListingWithListingStartedMetadata() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setCompletedAt(LocalDateTime.of(2026, 7, 3, 10, 24, 3));
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZTEST001;pskuCode=PSKU-CODE-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        ProductProjectionPersistenceService.SiteOfferSeed offer = seed.getSiteOffers().get(0);
        assertEquals("2026-07-03 10:24:03", offer.getListingStartedAt());
        assertEquals("product_listing", offer.getListingStartedSource());
    }

    @Test
    void shouldSyncSuccessfulListingImageRolesIntoProductImageProfile() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));
        ProductMasterIdentityRecord identity = new ProductMasterIdentityRecord();
        identity.setProductMasterId(88001L);
        identity.setPartnerSku("NN-TEST-PSKU");
        identity.setPskuCode("PSKU-CODE-1");
        when(productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                10002L,
                "STR245027-NAE",
                "NN-TEST-PSKU"
        )).thenReturn(identity);

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setImageUrls(List.of(
                "https://example.test/images/main.jpg",
                "https://example.test/images/size.jpg",
                "https://example.test/images/package.jpg"
        ));
        draft.setImageRoleAssignments(List.of(
                imageRoleAssignment("https://example.test/images/main.jpg", ProductImageRole.MAIN),
                imageRoleAssignment("https://example.test/images/size.jpg", ProductImageRole.SIZE),
                imageRoleAssignment("https://example.test/images/package.jpg", ProductImageRole.PACKAGE)
        ));
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZTEST001;pskuCode=PSKU-CODE-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        assertEquals(List.of(
                "https://example.test/images/main.jpg",
                "https://example.test/images/size.jpg",
                "https://example.test/images/package.jpg"
        ), seed.getImageUrls());

        ArgumentCaptor<ProductImageProfileSaveCommand> profileCaptor =
                ArgumentCaptor.forClass(ProductImageProfileSaveCommand.class);
        ArgumentCaptor<List> roleCaptor = ArgumentCaptor.forClass(List.class);
        verify(productImageProfileService).saveAndSyncAssetRoles(profileCaptor.capture(), roleCaptor.capture());
        ProductImageProfileSaveCommand profileCommand = profileCaptor.getValue();
        assertEquals(10002L, profileCommand.getOwnerUserId());
        assertEquals("STR245027-NAE", profileCommand.getStoreCode());
        assertEquals("NN-TEST-PSKU", profileCommand.getPskuCode());
        assertEquals("psku:NN-TEST-PSKU", profileCommand.getProductIdentityKey());
        assertEquals(88001L, profileCommand.getProductMasterId());

        @SuppressWarnings("unchecked")
        List<ProductImageAssetRoleUpdateCommand> roleCommands =
                (List<ProductImageAssetRoleUpdateCommand>) roleCaptor.getValue();
        assertEquals(3, roleCommands.size());
        assertEquals(ProductImageRole.MAIN, roleCommands.get(0).getImageRole());
        assertEquals(0, roleCommands.get(0).getSortOrder());
        assertEquals(ProductImageRole.SIZE, roleCommands.get(1).getImageRole());
        assertEquals(ProductImageRole.PACKAGE, roleCommands.get(2).getImageRole());
    }

    @Test
    void shouldCarryDraftVariantSizeIntoSuccessfulListingProjectionSeed() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        setListingDraftField(draft, "sizeEn", "One Size");
        setListingDraftField(draft, "sizeAr", "مقاس واحد");
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZTEST001;pskuCode=PSKU-CODE-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        ProductProjectionPersistenceService.VariantSeed variant = seed.toRepresentativeVariantSeed();
        assertEquals("One Size", variant.getSizeEn());
        assertEquals("مقاس واحد", variant.getSizeAr());
    }

    @Test
    void shouldPreserveInheritedListingStartedMetadataForProductRebuild() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setCompletedAt(LocalDateTime.of(2026, 7, 4, 15, 10, 0));
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setRebuildSourceProductMasterId(64001L);
        draft.setInheritedListingStartedAt("2026-03-12 00:00:00");
        draft.setInheritedListingStartedSource("pv");
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZREBUILD001;pskuCode=PSKU-REBUILD-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        ProductProjectionPersistenceService.SiteOfferSeed offer = seed.getSiteOffers().get(0);
        assertEquals("2026-03-12 00:00:00", offer.getListingStartedAt());
        assertEquals("product_rebuild_inherited:pv", offer.getListingStartedSource());
    }

    @Test
    void shouldFitProductRebuildInheritedListingSourceIntoColumnLimit() {
        ProductListingStoreProjectionContext storeContext = new ProductListingStoreProjectionContext();
        storeContext.setProjectCode("PRJ69486");
        storeContext.setProjectName("xingyao");
        storeContext.setStoreCode("STR245027-NAE");
        storeContext.setSite("SA");
        when(projectionMapper.selectStoreContext(10002L, "STR245027-NAE"))
                .thenReturn(storeContext);
        when(projectionMapper.selectProjectStoreContexts(10002L, "PRJ69486"))
                .thenReturn(List.of(storeContext));

        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        ProductListingDraftCommand draft = ProductListingTestFixtures.validCommand();
        draft.setInheritedListingStartedAt("2026-07-03 10:24:03");
        draft.setInheritedListingStartedSource("product_listing");
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("create_product");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZREBUILD001;pskuCode=PSKU-REBUILD-1");

        backfill.backfillSuccessfulListing(task, draft, ProductListingNoonWriteResult.succeeded(List.of(step)));

        ArgumentCaptor<List> seedCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionPersistenceService).persistInitializationProjection(
                eq(10002L),
                eq("PRJ69486"),
                eq("xingyao"),
                eq("STR245027-NAE"),
                anyList(),
                seedCaptor.capture(),
                anyList(),
                eq(true)
        );
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                (ProductProjectionPersistenceService.ProductMasterSeed) seedCaptor.getValue().get(0);
        ProductProjectionPersistenceService.SiteOfferSeed offer = seed.getSiteOffers().get(0);
        assertEquals("product_rebuild_inherited:listing", offer.getListingStartedSource());
        assertTrue(offer.getListingStartedSource().length() <= 40);
    }

    private void setListingDraftField(ProductListingDraftCommand draft, String fieldName, String value) {
        try {
            Field field = ProductListingDraftCommand.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(draft, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Product listing draft must expose " + fieldName, exception);
        }
    }

    private ProductListingImageRoleAssignment imageRoleAssignment(String imageUrl, ProductImageRole imageRole) {
        ProductListingImageRoleAssignment assignment = new ProductListingImageRoleAssignment();
        assignment.setImageUrl(imageUrl);
        assignment.setImageRole(imageRole);
        return assignment;
    }
}
