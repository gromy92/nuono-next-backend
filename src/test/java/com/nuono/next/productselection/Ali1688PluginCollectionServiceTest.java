package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.auth.AuthenticatedSession;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Ali1688PluginCollectionServiceTest {

    @Mock
    private Ali1688CollectionMapper ali1688CollectionMapper;

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private Ali1688CandidateAiAssessmentService aiAssessmentService;

    private ObjectMapper objectMapper;
    private Ali1688PluginCollectionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new Ali1688PluginCollectionService(
                ali1688CollectionMapper,
                productSelectionMapper,
                permissionGuard,
                new Ali1688PluginSubmissionNormalizer(objectMapper),
                new Ali1688CandidateScoringService(objectMapper),
                aiAssessmentService,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "assignmentTtlMinutes", 30);
        ReflectionTestUtils.setField(
                service,
                "clock",
                Clock.fixed(Instant.parse("2026-05-22T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createAssignmentUsesCurrentTaskScopeAndExpiresPreviousCurrentAssignment() {
        Ali1688CollectionRecords.TaskRecord task = task();
        when(ali1688CollectionMapper.selectTaskById(task.id)).thenReturn(task);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90001L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90001L)).thenReturn(assignment(task, 90001L, "created"));

        Ali1688PluginAssignmentView view = service.createAssignment(String.valueOf(task.id), 307L);

        verify(ali1688CollectionMapper).expireCurrentPluginAssignmentsByTask(task.id, 307L);
        ArgumentCaptor<Ali1688CollectionRecords.PluginAssignmentRecord> captor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.PluginAssignmentRecord.class);
        verify(ali1688CollectionMapper).insertPluginAssignment(captor.capture());
        Ali1688CollectionRecords.PluginAssignmentRecord inserted = captor.getValue();
        assertEquals(90001L, inserted.id);
        assertEquals(task.id, inserted.taskId);
        assertEquals(task.sourceCollectionId, inserted.sourceCollectionId);
        assertEquals(task.ownerUserId, inserted.ownerUserId);
        assertEquals(task.logicalStoreId, inserted.logicalStoreId);
        assertEquals("87001", inserted.activeAssignmentKey);
        assertEquals("created", inserted.status);
        assertEquals("2026-05-22 11:30:00", inserted.expiresAt);
        assertEquals(307L, inserted.createdBy);
        assertTrue(inserted.assignmentCode.startsWith("ALI1688-PLUGIN-90001-"));
        assertEquals(64, inserted.assignmentCodeHash.length());

        assertEquals("90001", view.assignmentId);
        assertEquals("ALI1688-87001", view.taskNo);
        assertEquals("created", view.status);
        assertEquals(task.sourceImageUrl, view.sourceImageUrl);
        assertTrue(view.assignmentCode.startsWith("ALI1688-PLUGIN-90001-"));
        assertFalse(view.toString().contains(inserted.assignmentCodeHash));
        verify(ali1688CollectionMapper, never()).insertCandidate(any());
        verify(ali1688CollectionMapper, never()).insertCandidateForClaimedTask(any(), any());
    }

    @Test
    void pluginFetchRequiresBearerAuthorizedCurrentAssignmentAndDoesNotReturnCodeHash() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "created");
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(any())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentView view = service.getAssignment("ALI1688-PLUGIN-90001-ABCDEF", new AuthenticatedSession(307L, 2L, 3));

        assertEquals("90001", view.assignmentId);
        assertEquals("87001", view.taskId);
        assertEquals("created", view.status);
        assertEquals(task.sourceImageUrl, view.sourceImageUrl);
        assertEquals(task.sourceTitle, view.sourceTitle);
        assertEquals("2026-05-22 12:00", view.expiresAt);
        assertEquals(null, view.assignmentCode);
        assertFalse(view.toString().contains("code-hash"));
    }

    @Test
    void listCurrentAssignmentsReturnsVisibleSystemTasksWithoutPlainCodes() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "created");
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of(assignment));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.total);
        assertEquals(1, result.summary.pending);
        assertEquals(1, result.diagnostics.issuedAssignmentCount);
        assertEquals(null, result.emptyReason);
        Ali1688PluginAssignmentView view = result.items.get(0);
        assertEquals("90001", view.assignmentId);
        assertEquals(null, view.assignmentCode);
        assertEquals("87001", view.taskId);
        assertEquals("ALI1688-87001", view.taskNo);
        assertEquals("仿真花束", view.sourceTitleCn);
        assertEquals("Xingyao", view.storeName);
        assertEquals("created", view.status);
        assertEquals(true, view.current);
    }

    @Test
    void listCurrentAssignmentsAutoIssuesAssignmentForVisibleSystemTasks() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "created");
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of());
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90001L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90001L)).thenReturn(assignment);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.total);
        assertEquals(1, result.summary.pending);
        assertEquals(1, result.diagnostics.visibleTaskCount);
        assertEquals(1, result.diagnostics.assignableTaskCount);
        assertEquals(1, result.diagnostics.issuedAssignmentCount);
        assertEquals(null, result.emptyReason);
        Ali1688PluginAssignmentView view = result.items.get(0);
        assertEquals("90001", view.assignmentId);
        assertEquals(null, view.assignmentCode);
        assertEquals("87001", view.taskId);
        assertEquals("ALI1688-87001", view.taskNo);
        assertEquals("created", view.status);
        assertEquals(true, view.current);
        verify(ali1688CollectionMapper).expireCurrentPluginAssignmentsByTask(task.id, 307L);
        verify(ali1688CollectionMapper).insertPluginAssignment(any());
    }

    @Test
    void listCurrentAssignmentsReissuesExpiredAssignmentForStillAssignableTask() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord expired = assignment(task, 90001L, "expired");
        expired.activeAssignmentKey = null;
        Ali1688CollectionRecords.PluginAssignmentRecord fresh = assignment(task, 90002L, "created");
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(expired));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90002L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90002L)).thenReturn(fresh);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.pending);
        assertEquals(0, result.summary.blockedOrFailed);
        assertEquals(1, result.diagnostics.expiredAssignmentCount);
        assertEquals("90002", result.items.get(0).assignmentId);
        assertEquals("created", result.items.get(0).status);
        assertEquals(true, result.items.get(0).current);
        verify(ali1688CollectionMapper).expireCurrentPluginAssignmentsByTask(task.id, 307L);
        verify(ali1688CollectionMapper).insertPluginAssignment(any());
    }

    @Test
    void listCurrentAssignmentsReissuesRetryableFailedAssignmentForStillAssignableTask() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord failed = assignment(task, 90001L, "failed");
        failed.activeAssignmentKey = null;
        failed.failureCode = "captcha_required";
        failed.failureMessage = "1688 页面需要验证";
        Ali1688CollectionRecords.PluginAssignmentRecord fresh = assignment(task, 90002L, "created");
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(failed));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90002L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90002L)).thenReturn(fresh);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(0, result.summary.blockedOrFailed);
        assertEquals(1, result.summary.pending);
        assertEquals("90002", result.items.get(0).assignmentId);
        assertEquals("created", result.items.get(0).status);
        assertEquals(null, result.emptyReason);
        verify(ali1688CollectionMapper).insertPluginAssignment(any());
        verify(ali1688CollectionMapper).expireCurrentPluginAssignmentsByTask(task.id, 307L);
    }

    @Test
    void listCurrentAssignmentsReissuesGatewayTimeoutFailureForStillAssignableTask() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord failed = assignment(task, 90001L, "failed");
        failed.activeAssignmentKey = null;
        failed.failureCode = "gateway_timeout";
        failed.failureMessage = "1688 图搜响应超时";
        Ali1688CollectionRecords.PluginAssignmentRecord fresh = assignment(task, 90002L, "created");
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(failed));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenReturn(90002L);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90002L)).thenReturn(fresh);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(0, result.summary.blockedOrFailed);
        assertEquals(1, result.summary.pending);
        assertEquals("90002", result.items.get(0).assignmentId);
        assertEquals("created", result.items.get(0).status);
        verify(ali1688CollectionMapper).insertPluginAssignment(any());
        verify(ali1688CollectionMapper).expireCurrentPluginAssignmentsByTask(task.id, 307L);
    }

    @Test
    void listCurrentAssignmentsKeepsAutoReissuedCreatedAssignmentVisibleForRetryableFailure() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord autoReissued = assignment(task, 90002L, "created");
        autoReissued.startedAt = null;
        autoReissued.submittedCandidateCount = 0;
        autoReissued.acceptedCandidateCount = 0;
        Ali1688CollectionRecords.PluginAssignmentRecord failed = assignment(task, 90001L, "failed");
        failed.activeAssignmentKey = null;
        failed.failureCode = "captcha_required";
        failed.failureMessage = "1688 页面需要验证";
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of(autoReissued));
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(autoReissued, failed));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.pending);
        assertEquals(0, result.summary.blockedOrFailed);
        assertEquals("90002", result.items.get(0).assignmentId);
        assertEquals("created", result.items.get(0).status);
    }

    @Test
    void listCurrentAssignmentsKeepsExplicitlyReissuedCreatedAssignmentVisible() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "queued";
        Ali1688CollectionRecords.PluginAssignmentRecord explicitReissue = assignment(task, 90003L, "created");
        explicitReissue.startedAt = null;
        explicitReissue.submittedCandidateCount = 0;
        explicitReissue.acceptedCandidateCount = 0;
        explicitReissue.rawAssignmentSnapshotJson = "{\"issueSource\":\"explicit_create\"}";
        Ali1688CollectionRecords.PluginAssignmentRecord previousFailure = assignment(task, 90001L, "failed");
        previousFailure.activeAssignmentKey = null;
        previousFailure.failureCode = "captcha_required";
        previousFailure.failureMessage = "1688 页面需要验证";
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of(explicitReissue));
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(explicitReissue, previousFailure));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.pending);
        assertEquals(0, result.summary.blockedOrFailed);
        assertEquals("90003", result.items.get(0).assignmentId);
        assertEquals("created", result.items.get(0).status);
    }

    @Test
    void listCurrentAssignmentsKeepsRecentlyAcceptedAssignmentsVisibleForCompletedTasks() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.status = "success";
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 90004L, "accepted");
        accepted.activeAssignmentKey = null;
        accepted.submissionIdempotencyKey = "submit-90004";
        accepted.submittedCandidateCount = 10;
        accepted.acceptedCandidateCount = 10;
        accepted.taskCandidateCount = 10;
        accepted.taskRecommendedCount = 5;
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(accepted));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(1, result.items.size());
        assertEquals(1, result.summary.total);
        assertEquals(1, result.summary.synced);
        assertEquals(0, result.summary.pending);
        assertEquals(null, result.emptyReason);
        assertEquals("90004", result.items.get(0).assignmentId);
        assertEquals("accepted", result.items.get(0).status);
        assertEquals(10, result.items.get(0).acceptedCandidateCount);
    }

    @Test
    void listCurrentAssignmentsExplainsNoVisibleTask() {
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of());

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals(0, result.summary.total);
        assertEquals("no_visible_task", result.emptyReason);
        assertEquals(0, result.diagnostics.visibleTaskCount);
        assertEquals(0, result.diagnostics.assignableTaskCount);
        assertEquals(0, result.diagnostics.issuedAssignmentCount);
    }

    @Test
    void listCurrentAssignmentsExplainsNoAuthorizedTaskWhenOnlyInvisibleTasksExist() {
        Ali1688CollectionRecords.TaskRecord task = task();
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(0);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals("no_authorized_task", result.emptyReason);
        assertEquals(1, result.diagnostics.visibleTaskCount);
        assertEquals(0, result.diagnostics.assignableTaskCount);
        assertEquals(0, result.diagnostics.issuedAssignmentCount);
    }

    @Test
    void listCurrentAssignmentsExplainsTaskMissingSourceImage() {
        Ali1688CollectionRecords.TaskRecord task = task();
        task.sourceImageUrl = "";
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals("task_missing_source_image", result.emptyReason);
        assertEquals(1, result.diagnostics.visibleTaskCount);
        assertEquals(0, result.diagnostics.assignableTaskCount);
        assertEquals(1, result.diagnostics.missingSourceImageCount);
    }

    @Test
    void listCurrentAssignmentsExplainsAllTasksDone() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord accepted = assignment(task, 90001L, "accepted");
        accepted.activeAssignmentKey = null;
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(accepted));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of());

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals("all_tasks_done", result.emptyReason);
        assertEquals(1, result.diagnostics.issuedAssignmentCount);
    }

    @Test
    void listCurrentAssignmentsExplainsAllTasksExpired() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord expired = assignment(task, 90001L, "expired");
        expired.activeAssignmentKey = null;
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(307L, 80)).thenReturn(List.of(expired));
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of());

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals("all_tasks_expired", result.emptyReason);
        assertEquals(1, result.diagnostics.expiredAssignmentCount);
    }

    @Test
    void listCurrentAssignmentsExplainsAssignmentIssueFailure() {
        Ali1688CollectionRecords.TaskRecord task = task();
        when(ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(307L, 20)).thenReturn(List.of());
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(307L, 80)).thenReturn(List.of(task));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextPluginAssignmentId()).thenThrow(new IllegalStateException("sequence unavailable"));

        Ali1688PluginAssignmentListView result = service.listCurrentAssignments(new AuthenticatedSession(307L, 2L, 3));

        assertEquals(0, result.items.size());
        assertEquals("assignment_not_issued", result.emptyReason);
        assertEquals(1, result.diagnostics.visibleTaskCount);
        assertEquals(1, result.diagnostics.assignableTaskCount);
        assertEquals(0, result.diagnostics.issuedAssignmentCount);
        assertEquals("系统暂时无法签发插件采集任务。", result.message);
    }

    @Test
    void pluginStartCanUseAssignmentIdFromSystemTaskListAsLocator() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "created");
        when(ali1688CollectionMapper.selectPluginAssignmentById(90001L)).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.selectPluginAssignmentById(90001L)).thenReturn(assignment);

        Ali1688PluginAssignmentView view = service.startAssignment("90001", new AuthenticatedSession(307L, 2L, 3));

        assertEquals("90001", view.assignmentId);
        assertEquals("running", view.status);
        verify(ali1688CollectionMapper).markPluginAssignmentRunning(90001L, 307L);
    }

    @Test
    void pluginStartRejectsExpiredAssignmentBeforeMutatingIt() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "created");
        assignment.expiresAt = "2026-05-22 10:59:00";
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(any())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginAssignmentException error = assertThrows(
                Ali1688PluginAssignmentException.class,
                () -> service.startAssignment("ALI1688-PLUGIN-90001-ABCDEF", new AuthenticatedSession(307L, 2L, 3))
        );

        assertEquals("assignment_expired", error.getErrorCode());
        verify(ali1688CollectionMapper).expirePluginAssignment(90001L, 307L);
        verify(ali1688CollectionMapper, never()).markPluginAssignmentRunning(anyLong(), anyLong());
    }

    @Test
    void pluginFailRejectsCrossStoreAssignmentBeforeMutatingIt() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "running");
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(any())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(999L)).thenReturn(activeUser(999L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(999L, task.logicalStoreId)).thenReturn(0);

        Ali1688PluginAssignmentFailureCommand command = new Ali1688PluginAssignmentFailureCommand();
        command.setFailureCode("captcha_required");
        command.setFailureMessage("1688 页面出现验证码。");

        ProductSelectionAccessDeniedException error = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> service.failAssignment("ALI1688-PLUGIN-90001-ABCDEF", new AuthenticatedSession(999L, 2L, 3), command)
        );

        assertEquals("当前账号不能访问该插件采集任务。", error.getMessage());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentFailed(anyLong(), any(), any(), anyLong());
    }

    @Test
    void submitCandidatesPersistsNormalizedCandidatesScoresTopFiveAndCompletesTask() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "running");
        Ali1688PluginSubmissionCommand command = submissionCommand("submit-001", 6);
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(anyString())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L, 88003L, 88004L, 88005L, 88006L);
        when(ali1688CollectionMapper.insertCandidateForCurrentPluginAssignment(any(), eq(90001L))).thenReturn(1);
        when(ali1688CollectionMapper.markTaskCompletedFromPluginSubmission(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyLong()
        )).thenReturn(1);
        when(ali1688CollectionMapper.markPluginAssignmentAccepted(anyLong(), anyString(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(1);

        Ali1688PluginSubmissionView view = service.submitCandidates(
                "ALI1688-PLUGIN-90001-ABCDEF",
                new AuthenticatedSession(307L, 2L, 3),
                command
        );

        assertEquals("accepted", view.status);
        assertEquals(6, view.acceptedCandidateCount);
        assertEquals(0, view.rejectedCandidateCount);
        assertEquals(6, view.candidateCount);
        assertEquals(5, view.recommendedCount);
        assertEquals("plugin_assisted", view.resultSource);
        assertEquals(true, view.pluginPathPassed);
        assertEquals(false, view.fieldCompletenessPassed);
        assertEquals("not_attempted", view.detailCompletionStatus);
        assertEquals("Known-offer detail enrichment is disabled.", view.detailCompletionMessage);
        assertEquals(6, view.fieldCompleteness.candidateCount);
        assertEquals(6, view.fieldCompleteness.nonFallbackTitleCount);
        assertEquals(6, view.fieldCompleteness.supplierNameCount);
        assertEquals(5, view.fieldCompleteness.priceTextCount);
        assertEquals(5, view.fieldCompleteness.moqTextCount);
        assertEquals(5, view.fieldCompleteness.locationTextCount);
        assertEquals(6, view.fieldCompleteness.normalizedDetailUrlCount);
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(6)).insertCandidateForCurrentPluginAssignment(candidateCaptor.capture(), eq(90001L));
        List<Ali1688CollectionRecords.CandidateRecord> candidates = candidateCaptor.getAllValues();
        assertEquals("745612345001", candidates.get(0).offerId);
        assertEquals("https://detail.1688.com/offer/745612345001.html", candidates.get(0).candidateUrl);
        assertEquals("87001:745612345001", candidates.get(0).activeCandidateKey);
        assertEquals(1, candidates.get(0).rankNo);
        assertEquals("partial", candidates.get(0).scoreStatus);
        assertEquals(null, candidates.get(1).priceText);
        assertEquals(null, candidates.get(1).moqText);
        assertEquals(null, candidates.get(1).locationText);
        assertTrue(candidates.get(0).ruleScore > candidates.get(1).ruleScore);

        verify(ali1688CollectionMapper).softDeleteCandidatesByCurrentPluginAssignment(task.id, 307L, 90001L);
        verify(ali1688CollectionMapper).clearSelectedRanksForCurrentPluginAssignment(task.id, 307L, 90001L);
        verify(ali1688CollectionMapper, times(5)).updateSelectedRankForCurrentPluginAssignment(eq(task.id), anyLong(), anyInt(), eq(307L), eq(90001L));
        verify(ali1688CollectionMapper).markTaskCompletedFromPluginSubmission(
                eq(task.id),
                eq("partial_success"),
                eq(6),
                eq(6),
                eq(5),
                eq("plugin_candidate_count_less_than_10"),
                eq("插件提交候选不足 10 个，已展示可用候选。"),
                anyString(),
                eq(307L),
                eq(90001L)
        );
        verify(ali1688CollectionMapper).markPluginAssignmentAccepted(
                eq(90001L),
                eq("submit-001"),
                eq(6),
                eq(0),
                anyString(),
                eq(307L)
        );
        verify(aiAssessmentService).createPendingAssessmentsForCurrentTask(any(), anyList());
    }

    @Test
    void submitCandidatesKeepsTaskAcceptedWhenAiAssessmentSchedulingFails() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "running");
        Ali1688PluginSubmissionCommand command = submissionCommand("submit-ai-schedule-failed", 6);
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(anyString())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L, 88003L, 88004L, 88005L, 88006L);
        when(ali1688CollectionMapper.insertCandidateForCurrentPluginAssignment(any(), eq(90001L))).thenReturn(1);
        when(ali1688CollectionMapper.markTaskCompletedFromPluginSubmission(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyLong()
        )).thenReturn(1);
        when(ali1688CollectionMapper.markPluginAssignmentAccepted(anyLong(), anyString(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(1);
        doThrow(new IllegalStateException("ai queue unavailable"))
                .when(aiAssessmentService).createPendingAssessmentsForCurrentTask(any(), anyList());

        Ali1688PluginSubmissionView view = service.submitCandidates(
                "ALI1688-PLUGIN-90001-ABCDEF",
                new AuthenticatedSession(307L, 2L, 3),
                command
        );

        assertEquals("accepted", view.status);
        assertEquals("partial_success", view.taskStatus);
        verify(ali1688CollectionMapper).markPluginAssignmentAccepted(
                eq(90001L),
                eq("submit-ai-schedule-failed"),
                eq(6),
                eq(0),
                anyString(),
                eq(307L)
        );
        verify(aiAssessmentService).createPendingAssessmentsForCurrentTask(any(), anyList());
        verify(ali1688CollectionMapper, times(6)).markCandidateAiAssessmentFailed(anyLong(), eq(307L));
    }

    @Test
    void submitCandidatesUsesKnownOfferDetailEnrichmentAndRecordsOutcome() throws Exception {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "running");
        Ali1688PluginSubmissionCommand command = submissionCommand("submit-enriched", 2);
        ReflectionTestUtils.setField(service, "offerDetailGateway", (Ali1688OfferDetailGateway) candidates -> {
            assertEquals(2, candidates.size());
            Ali1688PluginSubmissionNormalizer.NormalizedCandidate missingFields = candidates.get(1);
            missingFields.priceText = "¥18.80";
            missingFields.priceMin = new BigDecimal("18.80");
            missingFields.priceMax = new BigDecimal("22.50");
            missingFields.moqText = "2 件起批";
            missingFields.moqValue = 2;
            missingFields.locationText = "浙江 温州";
            return Ali1688OfferDetailCompletionResult.completed(1, 1, "known offer detail enriched");
        });
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(anyString())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(88001L, 88002L);
        when(ali1688CollectionMapper.insertCandidateForCurrentPluginAssignment(any(), eq(90001L))).thenReturn(1);
        when(ali1688CollectionMapper.markTaskCompletedFromPluginSubmission(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyLong()
        )).thenReturn(1);
        when(ali1688CollectionMapper.markPluginAssignmentAccepted(anyLong(), anyString(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(1);

        Ali1688PluginSubmissionView view = service.submitCandidates(
                "ALI1688-PLUGIN-90001-ABCDEF",
                new AuthenticatedSession(307L, 2L, 3),
                command
        );

        assertEquals("accepted", view.status);
        assertEquals("completed", view.detailCompletionStatus);
        assertEquals("known offer detail enriched", view.detailCompletionMessage);
        assertEquals(2, view.fieldCompleteness.priceTextCount);
        assertEquals(2, view.fieldCompleteness.moqTextCount);
        assertEquals(2, view.fieldCompleteness.locationTextCount);
        assertEquals(true, view.fieldCompletenessPassed);
        ArgumentCaptor<Ali1688CollectionRecords.CandidateRecord> candidateCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.CandidateRecord.class);
        verify(ali1688CollectionMapper, times(2)).insertCandidateForCurrentPluginAssignment(candidateCaptor.capture(), eq(90001L));
        Ali1688CollectionRecords.CandidateRecord enriched = candidateCaptor.getAllValues().get(1);
        assertEquals("¥18.80", enriched.priceText);
        assertEquals(new BigDecimal("18.80"), enriched.priceMin);
        assertEquals(new BigDecimal("22.50"), enriched.priceMax);
        assertEquals("2 件起批", enriched.moqText);
        assertEquals(2, enriched.moqValue);
        assertEquals("浙江 温州", enriched.locationText);

        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(ali1688CollectionMapper).markTaskCompletedFromPluginSubmission(
                eq(task.id),
                eq("partial_success"),
                eq(2),
                eq(2),
                eq(2),
                eq("plugin_candidate_count_less_than_10"),
                eq("插件提交候选不足 10 个，已展示可用候选。"),
                snapshotCaptor.capture(),
                eq(307L),
                eq(90001L)
        );
        JsonNode snapshot = objectMapper.readTree(snapshotCaptor.getValue());
        assertEquals("completed", snapshot.path("detailCompletionOutcome").asText());
        assertEquals("known offer detail enriched", snapshot.path("detailCompletionMessage").asText());
        assertEquals(1, snapshot.path("detailCompletionAttemptCount").asInt());
        assertEquals(1, snapshot.path("detailCompletionEnrichedCount").asInt());
        assertNotNull(snapshot.path("sanitizedRawSnapshot"));
    }

    @Test
    void submitCandidatesKeepsTaskSuccessWhenKnownOfferDetailGatewayFails() throws Exception {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "running");
        Ali1688PluginSubmissionCommand command = submissionCommand("submit-detail-failed", 10);
        ReflectionTestUtils.setField(service, "offerDetailGateway", (Ali1688OfferDetailGateway) candidates -> {
            throw new IllegalStateException("detail service down");
        });
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(anyString())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);
        when(ali1688CollectionMapper.nextCandidateId()).thenReturn(
                88001L, 88002L, 88003L, 88004L, 88005L,
                88006L, 88007L, 88008L, 88009L, 88010L
        );
        when(ali1688CollectionMapper.insertCandidateForCurrentPluginAssignment(any(), eq(90001L))).thenReturn(1);
        when(ali1688CollectionMapper.markTaskCompletedFromPluginSubmission(
                anyLong(),
                anyString(),
                anyInt(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                anyString(),
                anyLong(),
                anyLong()
        )).thenReturn(1);
        when(ali1688CollectionMapper.markPluginAssignmentAccepted(anyLong(), anyString(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(1);

        Ali1688PluginSubmissionView view = service.submitCandidates(
                "ALI1688-PLUGIN-90001-ABCDEF",
                new AuthenticatedSession(307L, 2L, 3),
                command
        );

        assertEquals("accepted", view.status);
        assertEquals("success", view.taskStatus);
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(ali1688CollectionMapper).markTaskCompletedFromPluginSubmission(
                eq(task.id),
                eq("success"),
                eq(10),
                eq(10),
                eq(5),
                eq(null),
                eq(null),
                snapshotCaptor.capture(),
                eq(307L),
                eq(90001L)
        );
        JsonNode snapshot = objectMapper.readTree(snapshotCaptor.getValue());
        assertEquals("failed", snapshot.path("detailCompletionOutcome").asText());
        assertTrue(snapshot.path("detailCompletionMessage").asText().contains("detail service down"));
    }

    @Test
    void duplicateSubmissionWithSameIdempotencyKeyDoesNotWriteCandidatesAgain() {
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = assignment(task, 90001L, "accepted");
        assignment.activeAssignmentKey = null;
        assignment.submissionIdempotencyKey = "submit-001";
        assignment.acceptedCandidateCount = 6;
        assignment.rejectedCandidateCount = 0;
        assignment.taskCandidateCount = 6;
        assignment.taskRecommendedCount = 5;
        when(ali1688CollectionMapper.selectPluginAssignmentByCodeHash(anyString())).thenReturn(assignment);
        when(permissionGuard.requireActiveUser(307L)).thenReturn(activeUser(307L));
        when(productSelectionMapper.countVisibleLogicalStoreSites(307L, task.logicalStoreId)).thenReturn(1);

        Ali1688PluginSubmissionView view = service.submitCandidates(
                "ALI1688-PLUGIN-90001-ABCDEF",
                new AuthenticatedSession(307L, 2L, 3),
                submissionCommand("submit-001", 6)
        );

        assertEquals("accepted", view.status);
        assertEquals(6, view.acceptedCandidateCount);
        verify(ali1688CollectionMapper, never()).softDeleteCandidatesByCurrentPluginAssignment(anyLong(), anyLong(), anyLong());
        verify(ali1688CollectionMapper, never()).insertCandidateForCurrentPluginAssignment(any(), anyLong());
        verify(ali1688CollectionMapper, never()).markTaskCompletedFromPluginSubmission(anyLong(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyLong());
        verify(ali1688CollectionMapper, never()).markPluginAssignmentAccepted(anyLong(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void submitRejectsPluginSuppliedIdentityFieldsBeforeAssignmentLookup() {
        Ali1688PluginSubmissionCommand command = submissionCommand("submit-identity", 1);
        command.setOwnerUserId(99999L);

        Ali1688PluginAssignmentException error = assertThrows(
                Ali1688PluginAssignmentException.class,
                () -> service.submitCandidates("ALI1688-PLUGIN-90001-ABCDEF", new AuthenticatedSession(307L, 2L, 3), command)
        );

        assertEquals("plugin_identity_fields_rejected", error.getErrorCode());
        verify(ali1688CollectionMapper, never()).selectPluginAssignmentByCodeHash(anyString());
        verify(ali1688CollectionMapper, never()).insertCandidateForCurrentPluginAssignment(any(), anyLong());
    }

    private Ali1688CollectionRecords.TaskRecord task() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.currentTaskKey = "86001";
        task.ownerUserId = 307L;
        task.logicalStoreId = 301L;
        task.taskNo = "ALI1688-87001";
        task.status = "partial_success";
        task.searchMode = "主图图搜";
        task.sourceImageUrl = "https://images.example.com/source.jpg";
        task.sourceTitle = "Artificial Flowers 6 Stems";
        task.sourceTitleCn = "仿真花束";
        task.sourceUrl = "https://example.com/source";
        task.pageUrl = "https://example.com/source";
        task.storeName = "Xingyao";
        task.storeCode = "STR-XINGYAO";
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord assignment(
            Ali1688CollectionRecords.TaskRecord task,
            Long id,
            String status
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = id;
        assignment.taskId = task.id;
        assignment.sourceCollectionId = task.sourceCollectionId;
        assignment.ownerUserId = task.ownerUserId;
        assignment.logicalStoreId = task.logicalStoreId;
        assignment.assignmentCodeHash = "code-hash";
        assignment.activeAssignmentKey = String.valueOf(task.id);
        assignment.status = status;
        assignment.expiresAt = "2026-05-22 12:00:00";
        assignment.taskCurrentTaskKey = task.currentTaskKey;
        assignment.taskNo = task.taskNo;
        assignment.taskStatus = task.status;
        assignment.sourceImageUrl = task.sourceImageUrl;
        assignment.sourceTitle = task.sourceTitle;
        assignment.sourceTitleCn = task.sourceTitleCn;
        assignment.sourceUrl = task.sourceUrl;
        assignment.pageUrl = task.pageUrl;
        assignment.storeName = task.storeName;
        assignment.storeCode = task.storeCode;
        assignment.createdBy = task.updatedBy;
        assignment.updatedBy = task.updatedBy;
        return assignment;
    }

    private Ali1688PluginSubmissionCommand submissionCommand(String idempotencyKey, int count) {
        Ali1688PluginSubmissionCommand command = new Ali1688PluginSubmissionCommand();
        command.setIdempotencyKey(idempotencyKey);
        command.setSourcePageUrl("https://s.1688.com/image/search");
        command.setRawSnapshot(Map.of("visibleCount", count, "cookie", "secret-cookie"));
        for (int index = 1; index <= count; index++) {
            Ali1688PluginSubmissionCommand.Candidate candidate = new Ali1688PluginSubmissionCommand.Candidate();
            String offerId = String.format("745612345%03d", index);
            candidate.setOfferId(offerId);
            candidate.setCandidateUrl("https://detail.1688.com/offer/" + offerId + ".html?spm=plugin");
            candidate.setTitle("仿真花束 " + index);
            candidate.setSupplierName("义乌诚信通源头工厂");
            if (index != 2) {
                candidate.setPriceText("¥" + (10 + index));
                candidate.setPriceMin(BigDecimal.valueOf(10 + index));
                candidate.setMoqText(index + "件起批");
                candidate.setMoqValue(index);
                candidate.setLocationText("浙江 义乌");
            }
            candidate.setMainImageUrl("https://images.example.com/" + offerId + ".jpg");
            candidate.setImageUrls(List.of(candidate.getMainImageUrl()));
            candidate.setBadges(Map.of("stock", "现货"));
            candidate.setSupplierSnapshot(Map.of("factory", true));
            candidate.setLogisticsSnapshot(Map.of("shipFrom", "浙江义乌"));
            command.getCandidates().add(candidate);
        }
        return command;
    }

    private ProductSelectionUserContext activeUser(Long userId) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setStatus(1);
        user.setLevel(1);
        return user;
    }
}
