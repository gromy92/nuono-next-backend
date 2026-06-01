package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class Ali1688PluginExecutionAssignmentServiceTest {

    @Mock
    private Ali1688CollectionMapper ali1688CollectionMapper;

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ObjectProvider<Ali1688CandidateAiAssessmentService> aiAssessmentServiceProvider;

    @Mock
    private Ali1688CandidateAiAssessmentService aiAssessmentService;

    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private Ali1688PluginExecutionAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new Ali1688PluginExecutionAssignmentService(
                ali1688CollectionMapper,
                productSelectionMapper,
                permissionGuard,
                new ObjectMapper(),
                ali1688CollectionService,
                aiAssessmentServiceProvider
        );
    }

    @Test
    void createAssignmentDerivesScopeFromTaskAndStoresTypedCandidateScope() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = candidate(task.id);
        Ali1688CollectionRecords.PluginAssignmentRecord inserted = assignment(task, candidate.id, "DETAIL_ENRICHMENT", "created");
        Ali1688PluginExecutionAssignmentCreateCommand command = new Ali1688PluginExecutionAssignmentCreateCommand();
        command.setTaskId(String.valueOf(task.id));
        command.setCandidateId(String.valueOf(candidate.id));
        command.setAssignmentType("DETAIL_ENRICHMENT");

        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(inserted.id);
        when(ali1688CollectionMapper.selectPluginAssignmentById(inserted.id)).thenReturn(inserted);

        Ali1688PluginExecutionAssignmentView view = service.createAssignment(command, 307L);

        ArgumentCaptor<Ali1688CollectionRecords.PluginAssignmentRecord> assignmentCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.PluginAssignmentRecord.class);
        verify(ali1688CollectionMapper).insertPluginAssignment(assignmentCaptor.capture());
        Ali1688CollectionRecords.PluginAssignmentRecord row = assignmentCaptor.getValue();
        assertEquals("DETAIL_ENRICHMENT", row.assignmentType);
        assertEquals(task.id, row.taskId);
        assertEquals(candidate.id, row.candidateId);
        assertEquals(task.ownerUserId, row.ownerUserId);
        assertEquals(task.logicalStoreId, row.logicalStoreId);
        assertEquals("created", row.status);
        assertTrue(row.assignmentCode.startsWith("ALI1688-PLUGIN-"));
        assertTrue(row.currentAssignmentKey.contains("DETAIL_ENRICHMENT:87001:88001"));
        assertEquals("DETAIL_ENRICHMENT", view.assignmentType);
        assertEquals("88001", view.candidateId);
    }

    @Test
    void listAssignmentsBackfillsCandidateCollectionForQueuedTask() {
        ProductSelectionStoreScope scope = storeScope();
        Ali1688CollectionRecords.TaskRecord task = task("queued");
        Ali1688CollectionRecords.PluginAssignmentRecord inserted = assignment(task, null, "CANDIDATE_COLLECTION", "created");

        when(permissionGuard.requireReadableStore(307L, null)).thenReturn(scope);
        when(ali1688CollectionMapper.listCurrentTasks(scope.getLogicalStoreId(), null, 80)).thenReturn(List.of(task));
        when(ali1688CollectionMapper.selectLatestPluginAssignmentByTaskAndType(task.id, "CANDIDATE_COLLECTION")).thenReturn(null);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(inserted.id);
        when(ali1688CollectionMapper.selectPluginAssignmentById(inserted.id)).thenReturn(inserted);
        when(ali1688CollectionMapper.listCurrentPluginAssignments(scope.getLogicalStoreId(), 80)).thenReturn(List.of(inserted));

        Ali1688PluginExecutionAssignmentListView view = service.listAssignments(307L);

        ArgumentCaptor<Ali1688CollectionRecords.PluginAssignmentRecord> assignmentCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.PluginAssignmentRecord.class);
        verify(ali1688CollectionMapper).insertPluginAssignment(assignmentCaptor.capture());
        assertEquals("CANDIDATE_COLLECTION", assignmentCaptor.getValue().assignmentType);
        assertEquals(task.id, assignmentCaptor.getValue().taskId);
        verify(ali1688CollectionMapper, never()).supersedeCurrentPluginAssignments(any(), any());
        assertEquals(1, view.items.size());
        assertEquals("CANDIDATE_COLLECTION", view.items.get(0).assignmentType);
    }

    @Test
    void listAssignmentsReturnsExistingAssignmentWhenBackfillInsertHitsCurrentKeyRace() {
        ProductSelectionStoreScope scope = storeScope();
        Ali1688CollectionRecords.TaskRecord task = task("queued");
        Ali1688CollectionRecords.PluginAssignmentRecord existing = assignment(task, null, "CANDIDATE_COLLECTION", "created");

        when(permissionGuard.requireReadableStore(307L, null)).thenReturn(scope);
        when(ali1688CollectionMapper.listCurrentTasks(scope.getLogicalStoreId(), null, 80)).thenReturn(List.of(task));
        when(ali1688CollectionMapper.selectLatestPluginAssignmentByTaskAndType(task.id, "CANDIDATE_COLLECTION"))
                .thenReturn(null, existing);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90002L);
        doThrow(new DuplicateKeyException("duplicate current assignment"))
                .when(ali1688CollectionMapper)
                .insertPluginAssignment(any());
        when(ali1688CollectionMapper.listCurrentPluginAssignments(scope.getLogicalStoreId(), 80)).thenReturn(List.of(existing));

        Ali1688PluginExecutionAssignmentListView view = service.listAssignments(307L);

        assertEquals(1, view.items.size());
        assertEquals("90001", view.items.get(0).assignmentId);
        assertEquals("CANDIDATE_COLLECTION", view.items.get(0).assignmentType);
        verify(ali1688CollectionMapper, never()).supersedeCurrentPluginAssignments(any(), any());
    }

    @Test
    void issueCandidateCollectionAssignmentRejectsNonQueuedTask() {
        Ali1688CollectionRecords.TaskRecord task = task("failed");
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.issueCandidateCollectionAssignmentForTask(task.id, 307L)
        );

        assertEquals("只有排队中的 1688 采集任务可以派发插件候选采集。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPluginAssignment(any());
    }

    @Test
    void createPricePreviewAssignmentRejectsAiPendingCandidate() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = pricePreviewCandidate();
        candidate.aiAssessmentStatus = "pending";
        candidate.scoreStatus = "partial";
        Ali1688PluginExecutionAssignmentCreateCommand command = pricePreviewCreateCommand(task.id, candidate.id);

        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAssignment(command, 307L)
        );

        assertEquals("候选 AI 尚未通过，不能创建价格预览任务。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPluginAssignment(any());
    }

    @Test
    void createPricePreviewAssignmentRejectsHighRiskCandidate() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = pricePreviewCandidate();
        candidate.scoreDetailJson = "{\"riskLevel\":\"high\"}";
        Ali1688PluginExecutionAssignmentCreateCommand command = pricePreviewCreateCommand(task.id, candidate.id);

        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAssignment(command, 307L)
        );

        assertEquals("候选 AI 风险过高，不能创建价格预览任务。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPluginAssignment(any());
    }

    @Test
    void createPricePreviewAssignmentRejectsCandidateOutsideTopFive() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.CandidateRecord candidate = pricePreviewCandidate();
        candidate.selectedRankNo = null;
        Ali1688PluginExecutionAssignmentCreateCommand command = pricePreviewCreateCommand(task.id, candidate.id);

        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(ali1688CollectionMapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.createAssignment(command, 307L)
        );

        assertEquals("候选尚未进入 Top5，不能创建价格预览任务。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPluginAssignment(any());
    }

    @Test
    void submitResultWithSameIdempotencyKeyReturnsExistingAcceptedAssignmentWithoutMutatingAgain() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 88001L, "PRICE_PREVIEW", "accepted");
        accepted.idempotencyKey = "price-001";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setIdempotencyKey("price-001");
        command.setCandidateId("88001");
        command.setResultSnapshot(Map.of("totalPrice", "38.46"));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(accepted);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginExecutionAssignmentView view = service.submitResult("90001", command, 307L);

        assertEquals("accepted", view.status);
        assertEquals("price-001", view.idempotencyKey);
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitResultRejectsAcceptedAssignmentWhenIdempotencyKeyDiffers() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 88001L, "PRICE_PREVIEW", "accepted");
        accepted.idempotencyKey = "price-001";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setIdempotencyKey("price-002");
        command.setCandidateId("88001");
        command.setResultSnapshot(Map.of("totalPrice", "39.00"));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(accepted);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("插件任务已接收过结果，不能用新的幂等键重复提交。", error.getMessage());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitResultRejectsExpiredAssignmentBeforeMutation() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord expired = assignment(task, 88001L, "PRICE_PREVIEW", "expired");
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setIdempotencyKey("price-003");
        command.setCandidateId("88001");
        command.setResultSnapshot(Map.of("totalPrice", "39.00"));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(expired);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("插件任务已过期或已结束。", error.getMessage());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void startAssignmentRejectsCreatedAssignmentWhenExpiresAtAlreadyPassed() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord created = assignment(task, 88001L, "PRICE_PREVIEW", "created");
        created.expiresAt = "2000-01-01 00:00";

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(created);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.startAssignment("90001", 307L)
        );

        assertEquals("插件任务已过期或已结束。", error.getMessage());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentRunning(any(), any());
    }

    @Test
    void submitResultRejectsRunningAssignmentWhenExpiresAtAlreadyPassed() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        running.expiresAt = "2000-01-01 00:00";
        Ali1688PluginExecutionAssignmentSubmitCommand command = safePricePreviewSubmitCommand();

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("插件任务已过期或已结束。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPricePreviewSnapshot(any());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitResultRejectsWrongCandidateForCandidateScopedAssignment() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setIdempotencyKey("price-002");
        command.setCandidateId("88002");
        command.setResultSnapshot(Map.of("totalPrice", "39.00"));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("插件任务候选范围不匹配。", error.getMessage());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitPricePreviewPersistsPreviewSnapshotBeforeAcceptingAssignment() throws Exception {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 88001L, "PRICE_PREVIEW", "accepted");
        Ali1688CollectionRecords.CandidateRecord candidate = pricePreviewCandidate();
        accepted.idempotencyKey = "price-001";
        accepted.resultStatus = "success";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setAssignmentType("PRICE_PREVIEW");
        command.setCandidateId("88001");
        command.setIdempotencyKey("price-001");
        command.setResultStatus("success");
        command.setResultSnapshot(Map.ofEntries(
                Map.entry("source", "browser-extension"),
                Map.entry("collectedAt", "2026-05-28T10:30:00+08:00"),
                Map.entry("skuOptions", List.of(Map.of("name", "颜色", "value", "透明"))),
                Map.entry("quantity", 2),
                Map.entry("unitPriceText", "¥15.20"),
                Map.entry("shippingText", "¥8.00"),
                Map.entry("discountText", "¥0.00"),
                Map.entry("totalPriceText", "¥38.40"),
                Map.entry("currency", "CNY"),
                Map.entry("regionText", "浙江 -> 广东深圳"),
                Map.entry("safetyMode", "preview_only"),
                Map.entry("sideEffectPolicy", "no_payment_no_order_no_message")
        ));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(ali1688CollectionMapper.selectCandidateById(88001L)).thenReturn(candidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPricePreviewSnapshotId()).thenReturn(92001L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(running.id)).thenReturn(accepted);

        Ali1688PluginExecutionAssignmentView view = service.submitResult("90001", command, 307L);

        ArgumentCaptor<Ali1688CollectionRecords.PricePreviewSnapshotRecord> snapshotCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.PricePreviewSnapshotRecord.class);
        InOrder inOrder = inOrder(ali1688CollectionMapper);
        inOrder.verify(ali1688CollectionMapper).insertPricePreviewSnapshot(snapshotCaptor.capture());
        inOrder.verify(ali1688CollectionMapper).markPluginAssignmentAccepted(
                eq(running.id),
                eq("price-001"),
                eq("success"),
                any(),
                eq(1),
                eq(1),
                eq(0),
                eq(307L)
        );
        Ali1688CollectionRecords.PricePreviewSnapshotRecord row = snapshotCaptor.getValue();
        assertEquals(92001L, row.id);
        assertEquals(running.id, row.assignmentId);
        assertEquals(88001L, row.candidateId);
        assertEquals("browser-extension", row.snapshotSource);
        assertEquals("preview_only", row.safetyMode);
        assertEquals("no_payment_no_order_no_message", row.sideEffectPolicy);
        assertEquals("¥15.20", row.unitPriceText);
        assertEquals("¥38.40", row.totalPriceText);
        assertEquals("CNY", row.currency);
        assertEquals("浙江 -> 广东深圳", row.regionText);
        assertEquals("accepted", view.status);
    }

    @Test
    void submitCandidateCollectionPersistsCandidatesBeforeAcceptingAssignment() {
        Ali1688CollectionRecords.TaskRecord task = task("queued");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, null, "CANDIDATE_COLLECTION", "running");
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, null, "CANDIDATE_COLLECTION", "accepted");
        accepted.idempotencyKey = "candidate-001";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setIdempotencyKey("candidate-001");
        command.setSourcePageUrl("https://s.1688.com/youyuan/index.htm?tab=imageSearch");
        command.setRawSnapshot(Map.of(
                "source", "browser-extension",
                "scannedCount", 12,
                "extractionStatus", "success"
        ));
        command.setCandidates(List.of(
                Map.ofEntries(
                        Map.entry("offerId", "745612345001"),
                        Map.entry("candidateUrl", "https://detail.1688.com/offer/745612345001.html"),
                        Map.entry("title", "透明手机壳"),
                        Map.entry("supplierName", "深圳手机壳工厂"),
                        Map.entry("priceText", "¥3.20"),
                        Map.entry("moqText", "2件起批"),
                        Map.entry("locationText", "广东 深圳"),
                        Map.entry("mainImageUrl", "https://images.example.com/case.jpg"),
                        Map.entry("imageUrls", List.of("https://images.example.com/case.jpg")),
                        Map.entry("badges", Map.of("values", List.of("48小时发货"))),
                        Map.entry("skuSnapshot", Map.of("source", "plugin_extractor")),
                        Map.entry("supplierSnapshot", Map.of("supplierName", "深圳手机壳工厂")),
                        Map.entry("logisticsSnapshot", Map.of("locationText", "广东 深圳"))
                )
        ));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.selectPluginAssignmentById(running.id)).thenReturn(accepted);

        Ali1688PluginExecutionAssignmentView view = service.submitResult("90001", command, 307L);

        InOrder inOrder = inOrder(ali1688CollectionMapper, ali1688CollectionService);
        inOrder.verify(ali1688CollectionService).acceptPluginCandidateCollection(running, command, 307L);
        inOrder.verify(ali1688CollectionMapper).markPluginAssignmentAccepted(
                eq(running.id),
                eq("candidate-001"),
                eq("success"),
                any(),
                eq(1),
                eq(1),
                eq(0),
                eq(307L)
        );
        assertEquals("accepted", view.status);
        assertEquals("CANDIDATE_COLLECTION", view.assignmentType);
    }

    @Test
    void submitPricePreviewRejectsUnsafeSnapshotBeforePersistingIt() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        Ali1688PluginExecutionAssignmentSubmitCommand command = safePricePreviewSubmitCommand();
        command.setResultSnapshot(Map.ofEntries(
                Map.entry("totalPriceText", "¥38.40"),
                Map.entry("quantity", 2),
                Map.entry("safetyMode", "live_order"),
                Map.entry("sideEffectPolicy", "may_submit_order")
        ));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(ali1688CollectionMapper.selectCandidateById(88001L)).thenReturn(pricePreviewCandidate());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("价格预览结果安全边界不匹配。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPricePreviewSnapshot(any());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitPricePreviewRejectsIncompleteSnapshotBeforePersistingIt() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        Ali1688PluginExecutionAssignmentSubmitCommand command = safePricePreviewSubmitCommand();
        command.setResultSnapshot(Map.of(
                "quantity", 2,
                "safetyMode", "preview_only",
                "sideEffectPolicy", "no_payment_no_order_no_message"
        ));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(ali1688CollectionMapper.selectCandidateById(88001L)).thenReturn(pricePreviewCandidate());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("价格预览结果缺少真实总价。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPricePreviewSnapshot(any());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitPricePreviewRechecksCandidateGateBeforePersistingSnapshot() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "PRICE_PREVIEW", "running");
        Ali1688CollectionRecords.CandidateRecord staleCandidate = pricePreviewCandidate();
        staleCandidate.matchScore = 8;
        Ali1688PluginExecutionAssignmentSubmitCommand command = safePricePreviewSubmitCommand();

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(ali1688CollectionMapper.selectCandidateById(88001L)).thenReturn(staleCandidate);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("候选 AI 判定为 mismatch，不能创建价格预览任务。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertPricePreviewSnapshot(any());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void submitDetailEnrichmentPersistsSnapshotBeforeAcceptingAssignment() throws Exception {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, 88001L, "DETAIL_ENRICHMENT", "running");
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 88001L, "DETAIL_ENRICHMENT", "accepted");
        accepted.idempotencyKey = "detail-001";
        accepted.resultStatus = "success";
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setAssignmentType("DETAIL_ENRICHMENT");
        command.setCandidateId("88001");
        command.setIdempotencyKey("detail-001");
        command.setResultStatus("success");
        command.setResultSnapshot(Map.ofEntries(
                Map.entry("source", "browser-extension"),
                Map.entry("collectedAt", "2026-05-28T09:30:00+08:00"),
                Map.entry("pageUrl", "https://detail.1688.com/offer/745612345001.html"),
                Map.entry("title", "户外便携折叠椅 批发定制"),
                Map.entry("mainImageUrls", List.of("https://cbu01.alicdn.com/img/ibank/main.jpg")),
                Map.entry("detailImageUrls", List.of("https://cbu01.alicdn.com/img/ibank/detail.jpg")),
                Map.entry("skuOptions", List.of(Map.of("name", "颜色", "values", List.of("黑色", "卡其色")))),
                Map.entry("moqText", "200件起批"),
                Map.entry("supplierName", "宁波优选户外用品有限公司"),
                Map.entry("locationText", "浙江 宁波"),
                Map.entry("listPriceText", "¥18.80 - ¥25.30"),
                Map.entry("serviceLabels", List.of("7天无理由", "48小时发货")),
                Map.entry("rawEvidenceSnippets", List.of("户外便携折叠椅 批发定制", "200件起批"))
        ));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextDetailEnrichmentSnapshotId()).thenReturn(91001L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(running.id)).thenReturn(accepted);
        when(aiAssessmentServiceProvider.getIfAvailable()).thenReturn(aiAssessmentService);

        Ali1688PluginExecutionAssignmentView view = service.submitResult("90001", command, 307L);

        ArgumentCaptor<Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord> snapshotCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord.class);
        InOrder inOrder = inOrder(ali1688CollectionMapper);
        inOrder.verify(ali1688CollectionMapper).insertDetailEnrichmentSnapshot(snapshotCaptor.capture());
        inOrder.verify(ali1688CollectionMapper).markPluginAssignmentAccepted(
                eq(running.id),
                eq("detail-001"),
                eq("success"),
                any(),
                eq(1),
                eq(1),
                eq(0),
                eq(307L)
        );
        Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord row = snapshotCaptor.getValue();
        assertEquals(91001L, row.id);
        assertEquals(running.id, row.assignmentId);
        assertEquals(task.id, row.taskId);
        assertEquals(88001L, row.candidateId);
        assertEquals(task.sourceCollectionId, row.sourceCollectionId);
        assertEquals(task.ownerUserId, row.ownerUserId);
        assertEquals(task.logicalStoreId, row.logicalStoreId);
        assertEquals("browser-extension", row.snapshotSource);
        assertEquals("https://detail.1688.com/offer/745612345001.html", row.pageUrl);
        assertEquals("户外便携折叠椅 批发定制", row.detailTitle);
        assertEquals("200件起批", row.moqText);
        assertEquals("宁波优选户外用品有限公司", row.supplierName);
        assertEquals("浙江 宁波", row.locationText);
        assertEquals("¥18.80 - ¥25.30", row.listPriceText);
        assertEquals(307L, row.createdBy);
        JsonNode raw = new ObjectMapper().readTree(row.rawSnapshotJson);
        assertEquals("户外便携折叠椅 批发定制", raw.path("title").asText());
        assertEquals("黑色", raw.path("skuOptions").get(0).path("values").get(0).asText());
        assertEquals("accepted", view.status);
        assertEquals("DETAIL_ENRICHMENT", view.assignmentType);
        verify(aiAssessmentService).refreshAssessmentForCandidate(task.id, 88001L, 307L);
    }

    @Test
    void submitDetailEnrichmentRejectsTaskScopedAssignmentWithoutCandidate() {
        Ali1688CollectionRecords.TaskRecord task = task("success");
        Ali1688CollectionRecords.PluginAssignmentRecord running = assignment(task, null, "DETAIL_ENRICHMENT", "running");
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setAssignmentType("DETAIL_ENRICHMENT");
        command.setIdempotencyKey("detail-002");
        command.setResultSnapshot(Map.of("title", "无候选范围详情"));

        when(ali1688CollectionMapper.selectPluginAssignmentByLocator("90001")).thenReturn(running);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.submitResult("90001", command, 307L)
        );

        assertEquals("详情补全插件任务必须绑定一个 1688 候选。", error.getMessage());
        verify(ali1688CollectionMapper, never()).insertDetailEnrichmentSnapshot(any());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
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
        task.sourceImageUrl = "https://images.example.com/source.jpg";
        task.sourceTitle = "Razr Fold 双卡 5G";
        task.sourceTitleCn = "Razr Fold 手机";
        task.sourceUrl = "https://noon.example.com/razr";
        task.pageUrl = "https://noon.example.com/razr";
        task.storeName = "Xingyao";
        task.storeCode = "XINGYAO";
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688CollectionRecords.CandidateRecord candidate(Long taskId) {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = taskId;
        candidate.sourceCollectionId = 86001L;
        candidate.ownerUserId = 307L;
        candidate.logicalStoreId = 301L;
        candidate.offerId = "745612345001";
        candidate.candidateUrl = "https://detail.1688.com/offer/745612345001.html";
        candidate.title = "Razr Fold 手机壳";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord pricePreviewCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = candidate(87001L);
        candidate.selectedRankNo = 1;
        candidate.aiAssessmentStatus = "success";
        candidate.scoreStatus = "final";
        candidate.matchScore = 31;
        candidate.specScore = 17;
        candidate.totalScore = 93;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        return candidate;
    }

    private Ali1688PluginExecutionAssignmentCreateCommand pricePreviewCreateCommand(Long taskId, Long candidateId) {
        Ali1688PluginExecutionAssignmentCreateCommand command = new Ali1688PluginExecutionAssignmentCreateCommand();
        command.setTaskId(String.valueOf(taskId));
        command.setCandidateId(String.valueOf(candidateId));
        command.setAssignmentType("PRICE_PREVIEW");
        return command;
    }

    private Ali1688PluginExecutionAssignmentSubmitCommand safePricePreviewSubmitCommand() {
        Ali1688PluginExecutionAssignmentSubmitCommand command = new Ali1688PluginExecutionAssignmentSubmitCommand();
        command.setAssignmentType("PRICE_PREVIEW");
        command.setCandidateId("88001");
        command.setIdempotencyKey("price-safe-001");
        command.setResultStatus("success");
        command.setResultSnapshot(Map.of(
                "totalPriceText", "¥38.40",
                "quantity", 2,
                "safetyMode", "preview_only",
                "sideEffectPolicy", "no_payment_no_order_no_message"
        ));
        return command;
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord assignment(
            Ali1688CollectionRecords.TaskRecord task,
            Long candidateId,
            String type,
            String status
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = 90001L;
        assignment.assignmentCode = "ALI1688-PLUGIN-90001-ABCDEF";
        assignment.assignmentType = type;
        assignment.taskId = task.id;
        assignment.candidateId = candidateId;
        assignment.sourceCollectionId = task.sourceCollectionId;
        assignment.ownerUserId = task.ownerUserId;
        assignment.logicalStoreId = task.logicalStoreId;
        assignment.currentAssignmentKey = type + ":" + task.id + ":" + (candidateId == null ? "task" : candidateId);
        assignment.status = status;
        assignment.sourceImageUrl = task.sourceImageUrl;
        assignment.sourceTitle = task.sourceTitle;
        assignment.sourceTitleCn = task.sourceTitleCn;
        assignment.sourceUrl = task.sourceUrl;
        assignment.pageUrl = task.pageUrl;
        assignment.storeName = task.storeName;
        assignment.storeCode = task.storeCode;
        return assignment;
    }

    private ProductSelectionUserContext activeUser(Long userId) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setStatus(1);
        user.setLevel(1);
        return user;
    }

    private ProductSelectionStoreScope storeScope() {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(301L);
        scope.setOperatorUserId(307L);
        scope.setStoreCode("XINGYAO");
        return scope;
    }
}
