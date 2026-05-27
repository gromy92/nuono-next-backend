package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LocalDbSourceCollectionServiceTest {

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ProductSelectionSourceCollectionCollector sourceCollectionCollector;

    @Mock
    private ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;

    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private LocalDbSourceCollectionService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbSourceCollectionService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                new SourceCollectionCompletenessCalculator(),
                ali1688CollectionService,
                new ObjectMapper()
        );
    }

    @Test
    void listSourceCollectionsKeepsSourceRowsWhenAli1688TablesAreMissing() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("success");

        when(permissionGuard.requireReadableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.listSourceCollections(50005L, 50)).thenReturn(List.of(source));
        when(ali1688CollectionService.getCurrentView(86001L)).thenThrow(missingAli1688Table());

        List<ProductSelectionSourceCollectionView> views =
                service.listSourceCollections("canman", "STR108065-NAE", 307L);

        assertEquals(1, views.size());
        ProductSelectionSourceCollectionView view = views.get(0);
        assertEquals("86001", view.getId());
        assertEquals("Amazon", view.getSourcePlatform());
        assertNotNull(view.getAli1688Collection());
        assertEquals("not_started", view.getAli1688Collection().status);
        assertEquals("1688采集暂不可用，不影响源头采集记录。", view.getAli1688Collection().message);
    }

    @Test
    void listSourceCollectionsPageReturnsTotalAndUsesServerSideFilters() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        source.setId(86051L);
        source.setSourcePlatform("Noon");
        source.setSourceTitle("Premium Sherpa Fleece Women's Robe");
        source.setSourceTitleCn("高级羊羔绒女士睡袍");
        ProductSelectionSourceCollectionListQuery query = new ProductSelectionSourceCollectionListQuery();
        query.setPage(2);
        query.setPageSize(50);
        query.setSourcePlatform("Noon");
        query.setSourceTitle("Sherpa");
        query.setSourceTitleCn("睡袍");
        query.setStatus("success");

        when(permissionGuard.requireReadableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.countSourceCollections(50005L, "Noon", "Sherpa", "睡袍", "success"))
                .thenReturn(75);
        when(productSelectionMapper.listSourceCollectionsPage(
                50005L,
                "Noon",
                "Sherpa",
                "睡袍",
                "success",
                50,
                50
        )).thenReturn(List.of(source));
        when(ali1688CollectionService.getCurrentView(86051L)).thenThrow(missingAli1688Table());

        ProductSelectionSourceCollectionPageView page =
                service.listSourceCollectionsPage("canman", "STR108065-NAE", 307L, query);

        assertEquals(75, page.getTotal());
        assertEquals(2, page.getPage());
        assertEquals(50, page.getPageSize());
        assertEquals(1, page.getItems().size());
        assertEquals("86051", page.getItems().get(0).getId());
    }

    @Test
    void listSourceCollectionsExposesCollectionTimingForDurationDisplay() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        source.setCollectionStartedAt("2026-05-14 10:19:57");
        source.setCollectionFinishedAt("2026-05-14 10:30:00");
        source.setCollectionDurationSeconds(603);

        when(permissionGuard.requireReadableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.listSourceCollections(50005L, 50)).thenReturn(List.of(source));
        when(ali1688CollectionService.getCurrentView(86001L)).thenThrow(missingAli1688Table());

        List<ProductSelectionSourceCollectionView> views =
                service.listSourceCollections("canman", "STR108065-NAE", 307L);

        ProductSelectionSourceCollectionView view = views.get(0);
        assertEquals("2026-05-14 10:19:57", view.getCollectionStartedAt());
        assertEquals("2026-05-14 10:30:00", view.getCollectionFinishedAt());
        assertEquals(603, view.getCollectionDurationSeconds());
    }

    @Test
    void createSourceCollectionPersistsSourceRowWhenAli1688TaskBootstrapFails() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("running");
        ProductSelectionSourceCollectionCommand command = new ProductSelectionSourceCollectionCommand();
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(307L);
        command.setSourceType("marketplace-url");
        command.setPageUrl("https://www.amazon.com/dp/B0C7RHFC3F");

        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86001L);
        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(source);
        when(ali1688CollectionService.ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L)))
                .thenThrow(missingAli1688Table());
        when(ali1688CollectionService.getCurrentView(86001L)).thenThrow(missingAli1688Table());

        ProductSelectionSourceCollectionView view = service.createSourceCollection(command);

        assertEquals("86001", view.getId());
        assertEquals("running", view.getStatus());
        assertEquals("Amazon", view.getSourcePlatform());
        assertEquals("not_started", view.getAli1688Collection().status);
        verify(productSelectionMapper).insertSourceCollection(any(ProductSelectionSourceCollectionRow.class));
    }

    @Test
    void createSourceCollectionDropsBatchRunnerSampleNotesBeforePersisting() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("running");
        ProductSelectionSourceCollectionCommand command = new ProductSelectionSourceCollectionCommand();
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(307L);
        command.setSourceType("marketplace-url");
        command.setPageUrl("https://www.amazon.com/dp/B0C7RHFC3F");
        command.setNotes("batch-runner sample: Noon / Egypt / pet-supplies / expected EGP / old-system-catalog-api");

        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86001L);
        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(source);
        when(ali1688CollectionService.ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L)))
                .thenThrow(missingAli1688Table());
        when(ali1688CollectionService.getCurrentView(86001L)).thenThrow(missingAli1688Table());

        service.createSourceCollection(command);

        ArgumentCaptor<ProductSelectionSourceCollectionRow> captor =
                ArgumentCaptor.forClass(ProductSelectionSourceCollectionRow.class);
        verify(productSelectionMapper).insertSourceCollection(captor.capture());
        assertEquals("", captor.getValue().getNotes());
    }

    @Test
    void createSourceCollectionDefersAli1688AccessUntilSourceTransactionCommits() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionRow source = sourceCollection("running");
        ProductSelectionSourceCollectionCommand command = new ProductSelectionSourceCollectionCommand();
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(307L);
        command.setSourceType("marketplace-url");
        command.setPageUrl("https://www.amazon.com/dp/B0C7RHFC3F");

        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(scope);
        when(productSelectionMapper.nextSourceCollectionId()).thenReturn(86001L);
        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(source);

        TransactionSynchronizationManager.initSynchronization();
        try {
            ProductSelectionSourceCollectionView view = service.createSourceCollection(command);

            assertEquals("86001", view.getId());
            assertNotNull(view.getAli1688Collection());
            assertEquals("not_started", view.getAli1688Collection().status);
            verify(ali1688CollectionService, never())
                    .ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L));
            verify(ali1688CollectionService, never()).getCurrentView(86001L);

            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());

            synchronizations.get(0).afterCommit();

            verify(ali1688CollectionService)
                    .ensureTaskForSourceCollection(any(ProductSelectionSourceCollectionRow.class), eq(307L));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createSourceCollectionRejectsSheinWhileCompleteCollectionIsPaused() {
        ProductSelectionStoreScope scope = storeScope();
        ProductSelectionSourceCollectionCommand command = new ProductSelectionSourceCollectionCommand();
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(307L);
        command.setSourceType("marketplace-url");
        command.setPageUrl("https://us.shein.com/Artificial-Poppy-Flowers-p-55110364.html?mallCode=1");

        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(scope);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createSourceCollection(command)
        );

        assertEquals("SHEIN 完整采集暂缓，当前仅验收 Amazon / Noon。", exception.getMessage());
        verify(productSelectionMapper, never()).nextSourceCollectionId();
        verify(productSelectionMapper, never()).insertSourceCollection(any(ProductSelectionSourceCollectionRow.class));
    }

    @Test
    void recollectSourceCollectionRejectsExistingSheinRecordWhilePaused() {
        ProductSelectionSourceCollectionRow source = sourceCollection("failed");
        source.setSourcePlatform("SHEIN");
        source.setSourceUrl("https://ar.shein.com/Product-p-33710994.html?mallCode=1");
        source.setPageUrl("https://ar.shein.com/Product-p-33710994.html?mallCode=1");
        ProductSelectionSourceCollectionCommand command = new ProductSelectionSourceCollectionCommand();
        command.setStoreCode("STR108065-NAE");
        command.setOperatorUserId(307L);

        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(307L);
        user.setLevel(2);
        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(source);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(user);
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, 50005L)).thenReturn(1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.recollectSourceCollection("86001", command)
        );

        assertEquals("SHEIN 完整采集暂缓，当前仅验收 Amazon / Noon。", exception.getMessage());
        verify(productSelectionMapper, never()).markSourceCollectionRunning(eq(86001L), eq(307L));
    }

    @Test
    void schedulerMarksSourceCollectionFailedWhenCollectorThrowsLinkageError() {
        ProductSelectionSourceCollectionRow source = sourceCollection("running");
        ReflectionTestUtils.setField(service, "sourceCollectionSchedulerEnabled", true);
        ReflectionTestUtils.setField(service, "sourceCollectionSchedulerMaxItems", 1);

        when(productSelectionMapper.listRunningSourceCollections(1)).thenReturn(List.of(source));
        when(productSelectionMapper.claimSourceCollection(eq(86001L), anyString())).thenReturn(1);
        when(productSelectionMapper.selectSourceCollectionById(86001L)).thenReturn(source);
        when(sourceCollectionCollector.collect(source)).thenThrow(new NoClassDefFoundError("noon payload parser"));

        service.processRunningSourceCollections();

        verify(productSelectionMapper).markSourceCollectionFailed(
                eq(86001L),
                eq("source_collect_failed"),
                contains("noon payload parser"),
                eq(307L),
                anyString()
        );
    }

    private static ProductSelectionStoreScope storeScope() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(201L);
        scope.setLogicalStoreId(50005L);
        scope.setStoreCode("STR108065-NAE");
        scope.setProjectName("canman");
        scope.setAuthorized(true);
        return scope;
    }

    private static ProductSelectionSourceCollectionRow sourceCollection(String status) {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86001L);
        row.setOwnerUserId(201L);
        row.setLogicalStoreId(50005L);
        row.setCollectionNo("PSC-86001");
        row.setSourceType("marketplace-url");
        row.setSourcePlatform("Amazon");
        row.setSourceUrl("https://www.amazon.com/dp/B0C7RHFC3F");
        row.setPageUrl("https://www.amazon.com/dp/B0C7RHFC3F");
        row.setSourceTitle("Artificial Flowers 6 Stems");
        row.setSourceTitleAr("زهور صناعية");
        row.setSourceImageUrl("https://images.example.com/source.jpg");
        row.setImageUrlsJson("[\"https://images.example.com/source.jpg\"]");
        row.setPriceSummary("$14.99");
        row.setBrandName("DUYONE");
        row.setUnitCount("6");
        row.setColorName("Orange Pink");
        row.setSpecHintsJson("[\"Brand: DUYONE\",\"Unit Count: 6\",\"Color: Orange Pink\"]");
        row.setSourceDescriptionEn("Long description");
        row.setSourceDescriptionAr("وصف طويل");
        row.setSourceSellingPointsEnJson("[\"Point one\"]");
        row.setSourceSellingPointsArJson("[\"نقطة واحدة\"]");
        row.setStatus(status);
        row.setCreatedBy(307L);
        row.setUpdatedBy(307L);
        row.setStoreName("canman");
        row.setStoreCode("STR108065-NAE");
        return row;
    }

    private static InvalidDataAccessResourceUsageException missingAli1688Table() {
        return new InvalidDataAccessResourceUsageException("Table 'product_selection_ali1688_collection_task' doesn't exist");
    }
}
