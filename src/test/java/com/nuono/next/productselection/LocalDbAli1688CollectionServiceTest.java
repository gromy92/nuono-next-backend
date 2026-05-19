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

    private ProductSelectionUserContext activeUser(Long userId) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setStatus(1);
        user.setLevel(1);
        return user;
    }
}
