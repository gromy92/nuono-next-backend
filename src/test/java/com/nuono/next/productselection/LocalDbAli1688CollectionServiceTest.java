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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
                new Ali1688CandidateGateService(objectMapper),
                new Ali1688AutoInquiryEligibilityService(objectMapper),
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
    void collectionViewExposesBlockedDetailCompletionAndFieldCompleteness() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        task.rawSearchSnapshotJson = "{"
                + "\"detailCompletionOutcome\":\"blocked_by_captcha\","
                + "\"detailCompletionMessage\":\"1688 详情页受限，详情字段待补充。\","
                + "\"detailCompletionAttemptCount\":10,"
                + "\"detailCompletionCaptchaCount\":10"
                + "}";
        task.candidateCount = 10;

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(86001L)).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(candidatesMissingDetailFields(10));

        Ali1688CollectionView view = service.getCurrentView(86001L);

        assertEquals("blocked_by_captcha", view.detailCompletionStatus);
        assertEquals("1688 详情页受限，详情字段待补充。", view.detailCompletionMessage);
        assertEquals(10, view.fieldCompleteness.candidateCount);
        assertEquals(10, view.fieldCompleteness.nonFallbackTitleCount);
        assertEquals(10, view.fieldCompleteness.supplierNameCount);
        assertEquals(0, view.fieldCompleteness.priceTextCount);
        assertEquals(0, view.fieldCompleteness.moqTextCount);
        assertEquals(0, view.fieldCompleteness.locationTextCount);
        assertEquals(10, view.fieldCompleteness.normalizedDetailUrlCount);
    }

    @Test
    void collectionViewExposesSafeGatewayStatusFromCaptchaBoundary() {
        Ali1688CollectionRecords.TaskRecord task = task("failed");
        task.failureCode = "captcha_required";
        task.rawSearchSnapshotJson = "{"
                + "\"gatewayServiceKind\":\"system_browser_gateway\","
                + "\"sessionState\":\"captcha_required\","
                + "\"runtimeReady\":true,"
                + "\"captchaAutoSolveEnabled\":false"
                + "}";

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(86001L)).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of());

        Ali1688CollectionView view = service.getCurrentView(86001L);

        assertEquals("system_browser_gateway", view.gatewayStatus.gatewayServiceKind);
        assertEquals("captcha_required", view.gatewayStatus.sessionState);
        assertEquals(true, view.gatewayStatus.runtimeReady);
        assertEquals(false, view.gatewayStatus.captchaAutoSolveEnabled);
        assertEquals("blocked_by_captcha", view.gatewayStatus.userFacingStatus);
        assertEquals("1688 访问受限，系统已暂停自动采集。", view.gatewayStatus.userFacingMessage);
    }

    @Test
    void collectionViewExposesListPriceOnlyAsHintAndBlocksAutoInquiryEligibility() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        task.candidateCount = 1;
        task.recommendedCount = 1;
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88201L;
        candidate.taskId = task.id;
        candidate.rankNo = 1;
        candidate.selectedRankNo = 1;
        candidate.level = "recommended";
        candidate.offerId = "88201";
        candidate.title = "适用于摩托罗拉 Edge70 5G 手机壳素材";
        candidate.supplierName = "佛山市南海区二丰手机配件有限公司";
        candidate.candidateUrl = "https://detail.1688.com/offer/88201.html";
        candidate.priceText = "¥ 6 .93 运费4元起 4400+件 50件起批";
        candidate.priceMin = new BigDecimal("4");
        candidate.priceMax = new BigDecimal("4400");
        candidate.moqText = "50件起批";
        candidate.moqValue = 50;
        candidate.locationText = "广东 佛山";
        candidate.mainImageUrl = "https://images.example.com/ali-phone-case.jpg";
        candidate.imageUrlsJson = "[\"https://images.example.com/ali-phone-case.jpg\"]";
        candidate.ruleScore = 23;
        candidate.priceScore = 8;
        candidate.moqScore = 5;
        candidate.supplierScore = 6;
        candidate.deliveryScore = 4;
        candidate.scoreStatus = "partial";
        candidate.aiAssessmentStatus = "pending";

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(86001L)).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of(candidate));

        Ali1688CollectionView view = service.getCurrentView(86001L);

        Ali1688CollectionView.Ali1688CandidatePreview preview = view.candidates.get(0);
        assertEquals("¥ 6 .93 运费4元起 4400+件 50件起批", preview.priceText);
        assertEquals("¥ 6 .93 运费4元起 4400+件 50件起批", preview.listPriceHintText);
        assertEquals("list_hint_only", preview.priceState);
        assertEquals(null, preview.confirmedPriceText);
        assertEquals(false, preview.autoInquiryEligible);
        assertEquals("PRICE_CONFIRMATION_REQUIRED", preview.procurementInquiryStatus);
        assertEquals("ai_pending", preview.gate.state);
        assertEquals("待AI评分", preview.gate.label);
        assertEquals("AI 匹配/规格补分未完成。", preview.gate.reason);
        assertEquals(false, preview.gate.allowsPriceProbe);
        assertEquals(false, preview.gate.allowsAutoInquiry);
    }

    @Test
    void collectionViewExposesInquiryEligibilityOnlyAfterConfirmedRealPrice() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88202L;
        candidate.taskId = task.id;
        candidate.rankNo = 1;
        candidate.selectedRankNo = 1;
        candidate.level = "recommended";
        candidate.title = "AI 与真实价格均通过候选";
        candidate.supplierName = "深圳源头工厂";
        candidate.candidateUrl = "https://detail.1688.com/offer/88202.html";
        candidate.priceText = "¥15.23";
        candidate.mainImageUrl = "https://images.example.com/ali-confirmed.jpg";
        candidate.imageUrlsJson = "[\"https://images.example.com/ali-confirmed.jpg\"]";
        candidate.scoreStatus = "final";
        candidate.aiAssessmentStatus = "success";
        candidate.ruleScore = 29;
        candidate.totalScore = 74;
        candidate.matchScore = 31;
        candidate.specScore = 14;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        Ali1688RealPriceSnapshot snapshot = confirmedPriceSnapshot(candidate.id);

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(86001L)).thenReturn(task);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of(candidate));
        when(ali1688CollectionMapper.selectLatestRealPriceSnapshotByCandidate(candidate.id)).thenReturn(snapshot);

        Ali1688CollectionView view = service.getCurrentView(86001L);

        assertEquals(1, view.inquiryEligibleCount);
        assertEquals(0, view.inquiryBlockedCount);
        Ali1688CollectionView.Ali1688CandidatePreview preview = view.candidates.get(0);
        assertEquals(true, preview.autoInquiryEligible);
        assertEquals("INQUIRY_ELIGIBLE", preview.procurementInquiryStatus);
        assertEquals("eligible", preview.inquiryEligibility.state);
        assertEquals("可询盘", preview.inquiryEligibility.label);
        assertEquals("inquiry_eligible", preview.gate.state);
        assertEquals(true, preview.gate.allowsAutoInquiry);
    }

    @Test
    void collectionViewExposesPluginAssistAvailabilityAndAssignmentWithoutPlainCode() {
        Ali1688CollectionRecords.TaskRecord task = task("queued");
        task.rawSearchSnapshotJson = "{"
                + "\"gatewayServiceKind\":\"system_browser_gateway\","
                + "\"sessionState\":\"captcha_required\","
                + "\"runtimeReady\":true,"
                + "\"captchaAutoSolveEnabled\":false"
                + "}";
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = 90032L;
        assignment.taskId = task.id;
        assignment.sourceCollectionId = task.sourceCollectionId;
        assignment.status = "created";
        assignment.expiresAt = "2026-05-22 15:30:00";
        assignment.taskNo = task.taskNo;
        assignment.taskCurrentTaskKey = task.currentTaskKey;
        assignment.assignmentCodeHash = "hash-only";
        assignment.assignmentCode = "ALI1688-PLUGIN-SHOULD-NOT-LEAK";

        when(ali1688CollectionMapper.selectCurrentTaskBySourceId(86001L)).thenReturn(task);
        when(ali1688CollectionMapper.selectLatestPluginAssignmentByTask(task.id)).thenReturn(assignment);
        when(ali1688CollectionMapper.listCandidatesByTask(task.id)).thenReturn(List.of());

        Ali1688CollectionView view = service.getCurrentView(86001L);

        assertEquals(true, view.pluginAssistAvailable);
        assertEquals("90032", view.pluginAssignment.assignmentId);
        assertEquals("created", view.pluginAssignment.status);
        assertEquals("2026-05-22 15:30", view.pluginAssignment.expiresAt);
        assertEquals(task.taskNo, view.pluginAssignment.taskNo);
        assertEquals(null, view.pluginAssignment.assignmentCode);
    }

    @Test
    void processQueuedTaskPersistsDedupedCandidatesAndMarksPartialSuccess() {
        Ali1688CollectionRecords.TaskRecord task = task("running");
        Ali1688ImageSearchResult searchResult = searchResult();

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenReturn(searchResult);
        when(ali1688CollectionMapper.updateSearchSnapshot(
                eq(task.id),
                anyString(),
                eq("主图图搜"),
                eq(null),
                eq("img-001"),
                eq("[\"img-001\"]"),
                eq(null),
                eq(3)
        )).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L);

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        ArgumentCaptor<Ali1688ImageSearchRequest> requestCaptor =
                ArgumentCaptor.forClass(Ali1688ImageSearchRequest.class);
        verify(imageSearchGateway).search(requestCaptor.capture());
        Ali1688ImageSearchRequest request = requestCaptor.getValue();
        assertEquals(task.id, request.taskId);
        assertEquals(task.sourceCollectionId, request.sourceCollectionId);
        assertEquals("ali1688-task-87001-attempt-2-ali1688", request.requestId);
        assertEquals(2, request.attemptCount);
        assertTrue(request.lockToken.startsWith("ali1688-collection-scheduler-"));
        assertEquals(task.ownerUserId, request.ownerUserId);
        assertEquals(task.logicalStoreId, request.logicalStoreId);
        assertEquals("https://images.example.com/source.jpg", request.sourceImageUrl);
        assertEquals("Artificial Flowers 6 Stems", request.sourceTitle);
        assertEquals(10, request.maxCandidates);
        verify(ali1688CollectionMapper).updateSearchSnapshot(
                eq(task.id),
                eq(request.lockToken),
                eq("主图图搜"),
                eq(null),
                eq("img-001"),
                eq("[\"img-001\"]"),
                eq(null),
                eq(3)
        );
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(2)).insertCandidateForClaimedTask(candidateCaptor.capture(), eq(request.lockToken));
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
                eq(request.lockToken)
        );
        verify(ali1688CollectionMapper, times(2)).updateSelectedRankForClaimedTask(eq(task.id), anyLong(), anyInt(), eq(task.updatedBy), eq(request.lockToken));
        verify(aiAssessmentService).createPendingAssessments(eq(task), anyList(), eq(request.lockToken));
    }

    @Test
    void processQueuedTaskKeepsCandidatesAndCompletesWhenAiAssessmentSchedulingFails() {
        Ali1688CollectionRecords.TaskRecord task = task("running");
        Ali1688ImageSearchResult searchResult = searchResult();

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenReturn(searchResult);
        when(ali1688CollectionMapper.updateSearchSnapshot(
                eq(task.id),
                anyString(),
                eq("主图图搜"),
                eq(null),
                eq("img-001"),
                eq("[\"img-001\"]"),
                eq(null),
                eq(3)
        )).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L);
        doThrow(new IllegalStateException("ai queue unavailable"))
                .when(aiAssessmentService).createPendingAssessments(eq(task), anyList(), anyString());

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        verify(ali1688CollectionMapper, times(2)).insertCandidateForClaimedTask(any(), anyString());
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
        verify(ali1688CollectionMapper, times(2)).markCandidateAiAssessmentFailed(anyLong(), eq(task.updatedBy));
        verify(ali1688CollectionMapper, never()).markTaskFailedByClaimedTask(
                anyLong(),
                eq("ali1688_collect_failed"),
                anyString(),
                anyLong(),
                anyString()
        );
    }

    @Test
    void processQueuedTaskSkipsInvalidCandidatesAndCompletesWhenTenValidCandidatesRemain() {
        Ali1688CollectionRecords.TaskRecord task = task("running");
        Ali1688ImageSearchResult searchResult = searchResultWithInvalidItemsAndElevenValidCandidates();

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenReturn(searchResult);
        when(ali1688CollectionMapper.updateSearchSnapshot(
                eq(task.id),
                anyString(),
                eq("主图图搜"),
                eq(null),
                eq("img-001"),
                eq("[\"img-001\"]"),
                eq(null),
                eq(13)
        )).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(
                88001L, 88002L, 88003L, 88004L, 88005L,
                88006L, 88007L, 88008L, 88009L, 88010L
        );

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(10)).insertCandidateForClaimedTask(candidateCaptor.capture(), anyString());
        List<Ali1688CollectionRecords.CandidateRecord> candidates = candidateCaptor.getAllValues();
        assertEquals("745612345001", candidates.get(0).offerId);
        assertEquals(1, candidates.get(0).rankNo);
        assertEquals("745612345010", candidates.get(9).offerId);
        assertEquals(10, candidates.get(9).rankNo);
        assertTrue(candidates.stream().allMatch(candidate -> candidate.imageUrlsJson.contains("ali-main")));

        verify(ali1688CollectionMapper).markTaskCompleted(
                eq(task.id),
                eq("success"),
                eq(13),
                eq(10),
                eq(5),
                eq(null),
                eq(null),
                eq(task.updatedBy),
                anyString()
        );
        verify(ali1688CollectionMapper, times(5)).updateSelectedRankForClaimedTask(eq(task.id), anyLong(), anyInt(), eq(task.updatedBy), anyString());
        ArgumentCaptor<List<Ali1688CollectionRecords.CandidateRecord>> aiCandidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiAssessmentService).createPendingAssessments(eq(task), aiCandidatesCaptor.capture(), anyString());
        assertEquals(10, aiCandidatesCaptor.getValue().size());
    }

    @Test
    void processQueuedTaskStopsBeforeCandidateWritesWhenClaimFenceIsLost() {
        Ali1688CollectionRecords.TaskRecord task = task("running");

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenReturn(searchResult());
        when(ali1688CollectionMapper.updateSearchSnapshot(
                eq(task.id),
                anyString(),
                eq("主图图搜"),
                eq(null),
                eq("img-001"),
                eq("[\"img-001\"]"),
                eq(null),
                eq(3)
        )).thenReturn(0);

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        verify(ali1688CollectionMapper, never()).softDeleteCandidatesByClaimedTask(anyLong(), anyLong(), anyString());
        verify(ali1688CollectionMapper, never()).insertCandidateForClaimedTask(any(), anyString());
        verify(ali1688CollectionMapper, never()).clearSelectedRanksForClaimedTask(anyLong(), anyLong(), anyString());
        verify(ali1688CollectionMapper, never()).updateSelectedRankForClaimedTask(anyLong(), anyLong(), anyInt(), anyLong(), anyString());
        verify(aiAssessmentService, never()).createPendingAssessments(any(), anyList(), anyString());
        verify(ali1688CollectionMapper, never()).markTaskCompleted(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyLong(),
                anyString()
        );
    }

    @Test
    void processQueuedTaskMapsTypedGatewayErrorWithoutPersistingCandidates() {
        Ali1688CollectionRecords.TaskRecord task = task("running");

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenThrow(new Ali1688GatewayException(
                "captcha_required",
                "1688 图搜出现验证码，需要人工处理。",
                false,
                "{\"blocked\":true}",
                "https://s.1688.com/image/captcha",
                "trace-001"
        ));

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        verify(ali1688CollectionMapper).updateSearchSnapshot(
                eq(task.id),
                anyString(),
                eq("主图图搜"),
                eq("https://s.1688.com/image/captcha"),
                eq(null),
                eq("[]"),
                eq("{\"blocked\":true}"),
                eq(0)
        );
        verify(ali1688CollectionMapper).markTaskFailedByClaimedTask(
                eq(task.id),
                eq("captcha_required"),
                eq("1688 图搜出现验证码，需要人工处理。"),
                eq(task.updatedBy),
                anyString()
        );
        verify(ali1688CollectionMapper, never()).softDeleteCandidatesByClaimedTask(anyLong(), anyLong(), anyString());
        verify(ali1688CollectionMapper, never()).insertCandidateForClaimedTask(any(), anyString());
        verify(ali1688CollectionMapper, never()).markTaskCompleted(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyLong(),
                anyString()
        );
        verify(aiAssessmentService, never()).createPendingAssessments(any(), anyList(), anyString());
    }

    @Test
    void processQueuedTasksStopsBeforeClaimWhenGatewayHealthIsBlockedByCaptcha() {
        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.blocked(
                "system_browser_gateway",
                "captcha_required",
                true,
                false
        ));

        int processed = service.processQueuedTasksOnce();

        assertEquals(0, processed);
        verify(ali1688CollectionMapper, never()).listClaimableTaskIds(anyInt(), anyInt(), anyInt());
        verify(ali1688CollectionMapper, never()).claimTask(anyLong(), anyString(), anyInt(), anyInt());
        verify(imageSearchGateway, never()).search(any());
    }

    @Test
    void processQueuedTasksUsesCooldownAndAutomaticallyResumesAfterReadyHealth() {
        Clock firstTickClock = Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC);
        ReflectionTestUtils.setField(service, "schedulerClock", firstTickClock);
        ReflectionTestUtils.setField(service, "schedulerGatewayCooldownMillis", 1000L);
        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(
                Ali1688GatewayOperationalStatus.blocked("system_browser_gateway", "captcha_required", true, false),
                Ali1688GatewayOperationalStatus.ready("system_browser_gateway", true, false)
        );

        int firstProcessed = service.processQueuedTasksOnce();
        int secondProcessed = service.processQueuedTasksOnce();
        ReflectionTestUtils.setField(
                service,
                "schedulerClock",
                Clock.fixed(Instant.parse("2026-05-21T00:00:02Z"), ZoneOffset.UTC)
        );
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of());
        int thirdProcessed = service.processQueuedTasksOnce();

        assertEquals(0, firstProcessed);
        assertEquals(0, secondProcessed);
        assertEquals(0, thirdProcessed);
        verify(imageSearchGateway, times(2)).getOperationalStatus();
        verify(ali1688CollectionMapper, times(1)).listClaimableTaskIds(3, 10, 3);
        verify(ali1688CollectionMapper, never()).claimTask(anyLong(), anyString(), anyInt(), anyInt());
    }

    @Test
    void processQueuedTaskStartsCooldownAfterCaptchaTypedErrorFromSearch() {
        Ali1688CollectionRecords.TaskRecord task = task("running");

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id), List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenThrow(new Ali1688GatewayException(
                "captcha_required",
                "1688 图搜出现验证码，需要人工处理。",
                false,
                "{\"sessionState\":\"captcha_required\"}",
                "https://s.1688.com/image/captcha",
                "trace-001"
        ));

        int firstProcessed = service.processQueuedTasksOnce();
        int secondProcessed = service.processQueuedTasksOnce();

        assertEquals(1, firstProcessed);
        assertEquals(0, secondProcessed);
        verify(imageSearchGateway, times(1)).getOperationalStatus();
        verify(ali1688CollectionMapper, times(1)).claimTask(eq(task.id), anyString(), eq(3), eq(10));
        verify(imageSearchGateway, times(1)).search(any());
    }

    @Test
    void processQueuedTaskRequeuesRetryableTypedGatewayErrorWhenAttemptsRemain() {
        Ali1688CollectionRecords.TaskRecord task = task("running");
        task.attemptCount = 1;

        when(ali1688CollectionMapper.listExpiredOverRetryTaskIds(3, 10, 3)).thenReturn(List.of());
        when(imageSearchGatewayProvider.getIfAvailable()).thenReturn(imageSearchGateway);
        when(imageSearchGateway.getOperationalStatus()).thenReturn(Ali1688GatewayOperationalStatus.ready(
                "system_browser_gateway",
                true,
                false
        ));
        when(ali1688CollectionMapper.listClaimableTaskIds(3, 10, 3)).thenReturn(List.of(task.id));
        when(ali1688CollectionMapper.claimTask(eq(task.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId)).thenReturn(sourceCollection("success"));
        when(imageSearchGateway.search(any(Ali1688ImageSearchRequest.class))).thenThrow(new Ali1688GatewayException(
                "gateway_timeout",
                "1688 图搜 HTTP gateway 调用超时。",
                true,
                null,
                null,
                "trace-timeout-001"
        ));

        int processed = service.processQueuedTasksOnce();

        assertEquals(1, processed);
        verify(ali1688CollectionMapper).markTaskRetryableFailure(
                eq(task.id),
                eq("gateway_timeout"),
                eq("1688 图搜 HTTP gateway 调用超时。"),
                eq(task.updatedBy),
                anyString()
        );
        verify(ali1688CollectionMapper, never()).markTaskFailedByClaimedTask(anyLong(), anyString(), anyString(), any(), anyString());
        verify(ali1688CollectionMapper, never()).insertCandidateForClaimedTask(any(), anyString());
        verify(aiAssessmentService, never()).createPendingAssessments(any(), anyList(), anyString());
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
        task.attemptCount = 2;
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688RealPriceSnapshot confirmedPriceSnapshot(Long candidateId) {
        Ali1688RealPriceSnapshot snapshot = new Ali1688RealPriceSnapshot();
        snapshot.id = 99001L;
        snapshot.taskId = 87001L;
        snapshot.candidateId = candidateId;
        snapshot.status = "confirmed";
        snapshot.safetyMode = "preview_only";
        snapshot.sideEffectPolicy = "no_payment_no_order_no_message";
        snapshot.source = "order_preview";
        snapshot.totalPrice = new BigDecimal("38.46");
        snapshot.currency = "CNY";
        return snapshot;
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

    private Ali1688ImageSearchResult searchResultWithInvalidItemsAndElevenValidCandidates() {
        Ali1688ImageSearchResult result = new Ali1688ImageSearchResult();
        result.searchMode = "主图图搜";
        result.searchImageId = "img-001";
        result.searchImageIds = List.of("img-001");
        result.candidates.add(candidate("745612345001", "https://detail.1688.com/offer/745612345001.html", "仿真花束 1", 2));
        result.candidates.add(null);
        result.candidates.add(candidate(null, null, "无 offer 和 URL 的无效候选", 2));
        for (int index = 2; index <= 11; index++) {
            String offerId = String.format("745612345%03d", index);
            result.candidates.add(candidate(offerId, "https://detail.1688.com/offer/" + offerId + ".html", "仿真花束 " + index, 2));
        }
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

    private List<Ali1688CollectionRecords.CandidateRecord> candidatesMissingDetailFields(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
                    candidate.id = 88000L + index;
                    candidate.taskId = 87001L;
                    candidate.sourceCollectionId = 86001L;
                    candidate.rankNo = index;
                    candidate.selectedRankNo = index <= 5 ? index : null;
                    candidate.level = index <= 5 ? "recommended" : "review";
                    candidate.offerId = String.format("745612345%03d", index);
                    candidate.candidateUrl = "https://detail.1688.com/offer/" + candidate.offerId + ".html";
                    candidate.title = "候选商品 " + index;
                    candidate.supplierName = "义乌源头工厂";
                    candidate.mainImageUrl = "https://images.example.com/ali-" + index + ".jpg";
                    candidate.imageUrlsJson = "[\"https://images.example.com/ali-" + index + ".jpg\"]";
                    candidate.scoreStatus = "partial";
                    return candidate;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private ProductSelectionUserContext activeUser(Long userId) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setStatus(1);
        user.setLevel(1);
        return user;
    }
}
