package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalDbAli1688CollectionServiceTest {

    @Mock
    private Ali1688CollectionMapper ali1688CollectionMapper;

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ObjectProvider<Ali1688ImageSearchGateway> imageSearchGatewayProvider;

    @Mock
    private Ali1688ImageSearchGateway imageSearchGateway;

    @Mock
    private Ali1688CandidateAiAssessmentService aiAssessmentService;

    private LocalDbAli1688CollectionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new LocalDbAli1688CollectionService(
                ali1688CollectionMapper,
                productSelectionMapper,
                permissionGuard,
                imageSearchGatewayProvider,
                new Ali1688CandidateScoringService(objectMapper),
                aiAssessmentService,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "schedulerMaxItems", 3);
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 10);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
    }

    @Test
    void ensureTaskCreatesQueuedTaskWhenSourceCollectionAlreadySucceeded() {
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        Ali1688CollectionRecords.TaskRecord task = task("queued");

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(source.getId())).thenReturn(null);
        when(ali1688CollectionMapper.nextTaskId()).thenReturn(task.id);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of());

        Ali1688CollectionView view = service.ensureTaskForSourceCollection(source, 307L);

        ArgumentCaptor<Ali1688CollectionRecords.TaskRecord> taskCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.TaskRecord.class);
        verify(ali1688CollectionMapper).insertTask(taskCaptor.capture());
        assertEquals("queued", taskCaptor.getValue().status);
        assertEquals("86001", taskCaptor.getValue().currentTaskKey);
        assertEquals(5, taskCaptor.getValue().progressPercent);
        assertEquals("queued", view.status);
    }

    @Test
    void processQueuedTaskPersistsDedupedCandidatesAndMarksPartialSuccess() {
        Ali1688CollectionRecords.TaskRecord task = task("running");
        Ali1688ImageSearchResult searchResult = searchResult();

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(ProductSelectionSourceCollectionRow.class))).thenReturn(searchResult);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L);

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(2)).insertCandidate(candidateCaptor.capture());
        List<Ali1688CollectionRecords.CandidateRecord> candidates = candidateCaptor.getAllValues();
        assertEquals("745612345678", candidates.get(0).offerId);
        assertEquals("87001:745612345678", candidates.get(0).activeCandidateKey);
        assertEquals("recommended", candidates.get(1).level);
        assertTrue(candidates.get(1).activeCandidateKey.startsWith("87001:"));
        assertEquals(44, candidates.get(0).ruleScore);

        verify(ali1688CollectionMapper).markTaskCompleted(
                eq(task.id),
                eq("partial_success"),
                eq(3),
                eq(2),
                eq(2),
                eq("candidate_count_less_than_10"),
                anyString(),
                eq(task.updatedBy),
                anyString()
        );
        verify(ali1688CollectionMapper, times(2)).updateSelectedRank(eq(task.id), anyLong(), anyInt(), eq(task.updatedBy));
        verify(aiAssessmentService).createPendingAssessments(eq(task), anyList());
    }

    @Test
    void acceptPluginCandidateCollectionPersistsPluginCandidatesAndCompletesTask() {
        Ali1688CollectionRecords.TaskRecord task = task("queued");
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = 90001L;
        assignment.taskId = task.id;
        assignment.assignmentType = "CANDIDATE_COLLECTION";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setSourcePageUrl("https://s.1688.com/youyuan/index.htm?tab=imageSearch");
        command.setRawSnapshot(Map.of(
                "pageUrl", "https://s.1688.com/youyuan/index.htm?tab=imageSearch",
                "searchImageId", "plugin-img-001",
                "searchImageIds", List.of("plugin-img-001"),
                "extractionStatus", "success"
        ));
        command.setCandidates(List.of(
                Map.ofEntries(
                        Map.entry("offerId", "745612345001"),
                        Map.entry("candidateUrl", "https://detail.1688.com/offer/745612345001.html"),
                        Map.entry("title", "透明手机保护壳"),
                        Map.entry("supplierName", "深圳手机壳工厂"),
                        Map.entry("priceText", "¥3.20"),
                        Map.entry("moqText", "2件起批"),
                        Map.entry("moqValue", 2),
                        Map.entry("locationText", "广东 深圳"),
                        Map.entry("mainImageUrl", "https://images.example.com/case-a.jpg"),
                        Map.entry("imageUrls", List.of("https://images.example.com/case-a.jpg")),
                        Map.entry("badges", Map.of("ship", "48小时发货")),
                        Map.entry("skuSnapshot", Map.of("source", "plugin_extractor")),
                        Map.entry("supplierSnapshot", Map.of("supplierName", "深圳手机壳工厂")),
                        Map.entry("logisticsSnapshot", Map.of("locationText", "广东 深圳"))
                ),
                Map.ofEntries(
                        Map.entry("offerId", "745612345002"),
                        Map.entry("candidateUrl", "https://detail.1688.com/offer/745612345002.html"),
                        Map.entry("title", "磨砂手机保护壳"),
                        Map.entry("supplierName", "东莞配件工厂"),
                        Map.entry("priceText", "¥4.80"),
                        Map.entry("moqText", "5件起批"),
                        Map.entry("moqValue", 5),
                        Map.entry("locationText", "广东 东莞"),
                        Map.entry("mainImageUrl", "https://images.example.com/case-b.jpg"),
                        Map.entry("imageUrls", List.of("https://images.example.com/case-b.jpg")),
                        Map.entry("badges", Map.of("ship", "现货")),
                        Map.entry("skuSnapshot", Map.of("source", "plugin_extractor")),
                        Map.entry("supplierSnapshot", Map.of("supplierName", "东莞配件工厂")),
                        Map.entry("logisticsSnapshot", Map.of("locationText", "广东 东莞"))
                )
        ));

        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L);

        service.acceptPluginCandidateCollection(assignment, command, 307L);

        verify(ali1688CollectionMapper).updateSearchSnapshotFromPlugin(
                eq(task.id),
                eq("插件图搜"),
                eq("https://s.1688.com/youyuan/index.htm?tab=imageSearch"),
                eq("plugin-img-001"),
                anyString(),
                anyString(),
                eq(2),
                eq(task.updatedBy)
        );
        verify(ali1688CollectionMapper).softDeleteCandidatesByTask(task.id, task.updatedBy);
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(2)).insertCandidate(candidateCaptor.capture());
        List<Ali1688CollectionRecords.CandidateRecord> inserted = candidateCaptor.getAllValues();
        assertEquals("745612345001", inserted.get(0).offerId);
        assertEquals("透明手机保护壳", inserted.get(0).title);
        assertEquals("87001:745612345001", inserted.get(0).activeCandidateKey);
        assertEquals("pending", inserted.get(0).aiAssessmentStatus);
        assertEquals("745612345002", inserted.get(1).offerId);
        verify(ali1688CollectionMapper).clearSelectedRanks(task.id, 307L);
        verify(ali1688CollectionMapper, times(2)).updateSelectedRank(eq(task.id), anyLong(), anyInt(), eq(307L));
        verify(aiAssessmentService).createPendingAssessments(eq(task), anyList());
        verify(ali1688CollectionMapper).markTaskCompletedFromPlugin(
                eq(task.id),
                eq("partial_success"),
                eq(2),
                eq(2),
                eq(2),
                eq("candidate_count_less_than_10"),
                anyString(),
                eq(307L)
        );
    }

    @Test
    void collectionViewExposesDetailEnrichmentStateFromLatestAssignmentAndSnapshot() {
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = candidateRecord();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = detailAssignment(task, candidate.id, "accepted");
        Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord snapshot = detailSnapshot(candidate.id);

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(source.getId())).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of(candidate));
        when(ali1688CollectionMapper.selectLatestPluginAssignmentByCandidateAndType(candidate.id, "DETAIL_ENRICHMENT")).thenReturn(assignment);
        when(ali1688CollectionMapper.selectLatestDetailEnrichmentSnapshotByCandidateId(candidate.id)).thenReturn(snapshot);

        Ali1688CollectionView view = service.ensureTaskForSourceCollection(source, 307L);

        Ali1688CollectionView.Ali1688CandidatePreview preview = view.candidates.get(0);
        assertEquals("completed", preview.detailEnrichmentStatus);
        assertEquals("Razr Fold 手机壳保护套详情", preview.detailTitle);
        assertEquals(List.of("https://images.example.com/detail-1.jpg"), preview.detailImageUrls);
    }

    @Test
    void collectionViewKeepsListPriceHintSeparateFromConfirmedPriceAndInquiryGate() {
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = candidateRecord();
        candidate.priceText = "¥6.93 运费4元起 4400+件 50件起批";
        Ali1688CollectionRecords.PricePreviewSnapshotRecord priceSnapshot = pricePreviewSnapshot(candidate.id, "success");
        priceSnapshot.totalPriceText = "¥38.40";
        priceSnapshot.unitPriceText = "¥15.20";
        priceSnapshot.shippingText = "¥8.00";

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(source.getId())).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of(candidate));
        when(ali1688CollectionMapper.selectLatestPricePreviewSnapshotByCandidateId(candidate.id)).thenReturn(priceSnapshot);

        Ali1688CollectionView view = service.ensureTaskForSourceCollection(source, 307L);

        Ali1688CollectionView.Ali1688CandidatePreview preview = view.candidates.get(0);
        assertEquals("¥6.93 运费4元起 4400+件 50件起批", preview.priceText);
        assertEquals("price_confirmed", preview.pricePreviewStatus);
        assertEquals("¥38.40", preview.confirmedRealPriceText);
        assertEquals("preview_only", preview.pricePreviewSafetyMode);
        assertEquals("inquiry_eligible", preview.candidateGateStatus);
        assertEquals(Boolean.TRUE, preview.autoInquiryEligible);
        assertEquals("IN_POOL_WAITING_SEND", preview.procurementInquiryStatus);
    }

    @Test
    void collectionViewBlocksInquiryWhenLatestPricePreviewFailed() {
        ProductSelectionSourceCollectionRow source = sourceCollection("success");
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = candidateRecord();
        Ali1688CollectionRecords.PricePreviewSnapshotRecord priceSnapshot = pricePreviewSnapshot(candidate.id, "failed");
        priceSnapshot.failureCode = "shipping_unavailable";
        priceSnapshot.failureMessage = "当前地区无法计算运费。";

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(source.getId())).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of(candidate));
        when(ali1688CollectionMapper.selectLatestPricePreviewSnapshotByCandidateId(candidate.id)).thenReturn(priceSnapshot);

        Ali1688CollectionView view = service.ensureTaskForSourceCollection(source, 307L);

        Ali1688CollectionView.Ali1688CandidatePreview preview = view.candidates.get(0);
        assertEquals("price_probe_failed", preview.pricePreviewStatus);
        assertEquals("shipping_unavailable", preview.pricePreviewFailureCode);
        assertEquals("当前地区无法计算运费。", preview.pricePreviewFailureMessage);
        assertEquals("price_probe_failed", preview.candidateGateStatus);
        assertEquals(Boolean.FALSE, preview.autoInquiryEligible);
        assertTrue(preview.autoInquiryBlockReasons.contains("真实价格预览失败：当前地区无法计算运费。"));
        assertEquals("BACKUP_POOL", preview.procurementInquiryStatus);
    }

    @Test
    void retryRejectsSupersededTaskBeforeMutatingIt() {
        Ali1688CollectionRecords.TaskRecord task = task("failed");
        task.currentTaskKey = null;
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () -> service.retryAli1688Collection(String.valueOf(task.id), 307L));

        verify(ali1688CollectionMapper, never()).retryFailedTask(anyLong(), anyLong());
    }

    private ProductSelectionSourceCollectionRow sourceCollection(String status) {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86001L);
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setCollectionNo("PSC-20260519-001");
        row.setSourceType("marketplace-url");
        row.setSourcePlatform("Amazon");
        row.setSourceTitle("Artificial Flowers 6 Stems");
        row.setSourceTitleCn("仿真花束");
        row.setSourceImageUrl("https://images.example.com/source.jpg");
        row.setImageUrlsJson("[\"https://images.example.com/source.jpg\"]");
        row.setStatus(status);
        row.setUpdatedBy(307L);
        return row;
    }

    private Ali1688CollectionRecords.TaskRecord task(String status) {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.currentTaskKey = "86001";
        task.ownerUserId = 307L;
        task.logicalStoreId = 301L;
        task.taskNo = "ALI1688-87001";
        task.status = status;
        task.progressPercent = "running".equals(status) ? 20 : 5;
        task.searchMode = "主图图搜";
        task.sourceImageUrl = "https://images.example.com/source.jpg";
        task.lockedBy = "ali1688-collection-scheduler-test";
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688ImageSearchResult searchResult() {
        Ali1688ImageSearchResult result = new Ali1688ImageSearchResult();
        result.searchMode = "主图图搜";
        result.searchImageId = "img-001";
        result.searchImageIds = List.of("img-001");
        result.candidates = List.of(
                candidate("745612345678", "https://detail.1688.com/offer/745612345678.html", "仿真花束 A", 2),
                candidate("745612345678", "https://detail.1688.com/offer/745612345678.html?spm=dup", "仿真花束 A duplicate", 2),
                candidate(null, "https://detail.1688.com/offer/999999999999.html?spm=test", "仿真花束 B", 20)
        );
        return result;
    }

    private Ali1688ImageSearchResult.Candidate candidate(String offerId, String url, String title, Integer moq) {
        Ali1688ImageSearchResult.Candidate candidate = new Ali1688ImageSearchResult.Candidate();
        candidate.offerId = offerId;
        candidate.candidateUrl = url;
        candidate.title = title;
        candidate.supplierName = "义乌诚信通源头工厂";
        candidate.priceText = "¥12.80-18.60";
        candidate.priceMin = new BigDecimal("12.80");
        candidate.priceMax = new BigDecimal("18.60");
        candidate.moqText = moq + " 件起批";
        candidate.moqValue = moq;
        candidate.locationText = "浙江 义乌";
        candidate.mainImageUrl = "https://images.example.com/ali-main.jpg";
        candidate.imageUrls = List.of("https://images.example.com/ali-main.jpg");
        candidate.badges = Map.of("stock", "现货", "ship", "48小时发货");
        candidate.supplierSnapshot = Map.of("verified", true, "factory", true, "response", "高");
        candidate.logisticsSnapshot = Map.of("shipFrom", "浙江义乌", "stock", "现货");
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord candidateRecord() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = 87001L;
        candidate.rankNo = 1;
        candidate.selectedRankNo = 1;
        candidate.level = "recommended";
        candidate.offerId = "745612345001";
        candidate.candidateUrl = "https://detail.1688.com/offer/745612345001.html";
        candidate.title = "Razr Fold 手机保护膜";
        candidate.supplierName = "深圳屏幕配件工厂";
        candidate.locationText = "广东 深圳";
        candidate.mainImageUrl = "https://images.example.com/list-main.jpg";
        candidate.imageUrlsJson = "[\"https://images.example.com/list-main.jpg\"]";
        candidate.aiAssessmentStatus = "success";
        candidate.scoreStatus = "final";
        candidate.matchScore = 31;
        candidate.specScore = 17;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        return candidate;
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord detailAssignment(
            Ali1688CollectionRecords.TaskRecord task,
            Long candidateId,
            String status
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = 90001L;
        assignment.assignmentType = "DETAIL_ENRICHMENT";
        assignment.taskId = task.id;
        assignment.candidateId = candidateId;
        assignment.status = status;
        return assignment;
    }

    private Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord detailSnapshot(Long candidateId) {
        Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord snapshot = new Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord();
        snapshot.id = 91001L;
        snapshot.candidateId = candidateId;
        snapshot.detailTitle = "Razr Fold 手机壳保护套详情";
        snapshot.detailImageUrlsJson = "[\"https://images.example.com/detail-1.jpg\"]";
        return snapshot;
    }

    private Ali1688CollectionRecords.PricePreviewSnapshotRecord pricePreviewSnapshot(Long candidateId, String status) {
        Ali1688CollectionRecords.PricePreviewSnapshotRecord snapshot = new Ali1688CollectionRecords.PricePreviewSnapshotRecord();
        snapshot.id = 92001L;
        snapshot.assignmentId = 90001L;
        snapshot.taskId = 87001L;
        snapshot.candidateId = candidateId;
        snapshot.snapshotSource = "browser-extension";
        snapshot.resultStatus = status;
        snapshot.quantity = 2;
        snapshot.currency = "CNY";
        snapshot.regionText = "浙江 -> 广东深圳";
        snapshot.safetyMode = "preview_only";
        snapshot.sideEffectPolicy = "no_payment_no_order_no_message";
        return snapshot;
    }

    private ProductSelectionUserContext activeUser(Long userId) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setStatus(1);
        user.setLevel(1);
        return user;
    }
}
