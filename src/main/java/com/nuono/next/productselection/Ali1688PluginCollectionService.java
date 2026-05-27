package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688PluginCollectionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DB_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(BUSINESS_ZONE);
    private static final String ASSIGNMENT_ISSUE_EXPLICIT_CREATE = "explicit_create";
    private static final String ASSIGNMENT_ISSUE_QUEUE_AUTO = "queue_auto_issue";
    private static final Set<String> FAILURE_CODES = Set.of(
            "captcha_required",
            "login_required",
            "rate_limited",
            "gateway_timeout",
            "no_candidates",
            "unexpected_response",
            "plugin_collect_failed"
    );
    private static final Set<String> RETRYABLE_FAILURE_CODES = Set.of(
            "captcha_required",
            "login_required",
            "rate_limited",
            "gateway_timeout",
            "no_candidates"
    );

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final Ali1688PluginSubmissionNormalizer submissionNormalizer;
    private final Ali1688CandidateScoringService scoringService;
    private final Ali1688CandidateAiAssessmentService aiAssessmentService;
    private final ObjectMapper objectMapper;
    private Ali1688OfferDetailGateway offerDetailGateway = Ali1688OfferDetailGateway.disabled();

    @Value("${nuono.product-selection.ali1688.plugin-assignment.ttl-minutes:30}")
    private int assignmentTtlMinutes;

    private Clock clock = Clock.systemUTC();

    public Ali1688PluginCollectionService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            Ali1688PluginSubmissionNormalizer submissionNormalizer,
            Ali1688CandidateScoringService scoringService,
            Ali1688CandidateAiAssessmentService aiAssessmentService,
            ObjectMapper objectMapper
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.submissionNormalizer = submissionNormalizer;
        this.scoringService = scoringService;
        this.aiAssessmentService = aiAssessmentService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setOfferDetailGateway(Ali1688OfferDetailGateway offerDetailGateway) {
        if (offerDetailGateway != null) {
            this.offerDetailGateway = offerDetailGateway;
        }
    }

    @Transactional
    public Ali1688PluginAssignmentView createAssignment(String taskId, Long operatorUserId) {
        Long parsedTaskId = parseLongId(taskId, "1688 采集任务不存在或已被删除。");
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(parsedTaskId);
        requireCurrentVisibleTask(operatorUserId, task);

        CreatedPluginAssignment created = createPluginAssignmentForTask(
                task,
                operatorUserId,
                ASSIGNMENT_ISSUE_EXPLICIT_CREATE
        );
        Ali1688PluginAssignmentView view = toView(created.assignment);
        view.assignmentCode = created.plainAssignmentCode;
        return view;
    }

    public Ali1688PluginAssignmentView getAssignment(String assignmentCode, AuthenticatedSession session) {
        return toView(requireUsableAssignment(assignmentCode, session));
    }

    @Transactional
    public Ali1688PluginAssignmentListView listCurrentAssignments(AuthenticatedSession session) {
        if (session == null || session.getUserId() == null) {
            throw new ProductSelectionAccessDeniedException("插件接口缺少有效登录态。");
        }
        Long operatorUserId = session.getUserId();
        Ali1688PluginAssignmentListView result = new Ali1688PluginAssignmentListView();
        result.refreshedAt = formatDateTime(clock.instant());
        List<Ali1688CollectionRecords.PluginAssignmentRecord> currentAssignments = safeList(
                ali1688CollectionMapper.listCurrentPluginAssignmentsByCreatedBy(operatorUserId, 20)
        );
        List<Ali1688CollectionRecords.PluginAssignmentRecord> latestAssignments = safeList(
                ali1688CollectionMapper.listLatestPluginAssignmentsByCreatedBy(operatorUserId, 80)
        );
        Map<Long, Ali1688CollectionRecords.PluginAssignmentRecord> latestClosedAssignmentsByTaskId = latestAssignments.stream()
                .filter(assignment -> assignment != null && assignment.taskId != null && shouldSurfaceClosedAssignment(assignment))
                .collect(Collectors.toMap(
                        assignment -> assignment.taskId,
                        assignment -> assignment,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Ali1688CollectionRecords.PluginAssignmentRecord> currentAssignmentsByTaskId = currentAssignments.stream()
                .filter(assignment -> assignment != null && assignment.taskId != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.taskId,
                        assignment -> assignment,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (Ali1688CollectionRecords.PluginAssignmentRecord assignment : currentAssignments) {
            if (assignment == null) {
                continue;
            }
            if (!isVisibleLogicalStore(operatorUserId, assignment.logicalStoreId)) {
                continue;
            }
            result.items.add(toView(resolveVisibleAssignment(assignment, latestClosedAssignmentsByTaskId)));
            if (result.items.size() >= 20) {
                finalizeListView(result, currentAssignments, List.of(), List.of(), null);
                return result;
            }
        }
        for (Ali1688CollectionRecords.PluginAssignmentRecord assignment : latestAssignments) {
            if (assignment == null || assignment.taskId == null || !isAcceptedPluginAssignment(assignment)) {
                continue;
            }
            if (currentAssignmentsByTaskId.containsKey(assignment.taskId)) {
                continue;
            }
            if (!isVisibleLogicalStore(operatorUserId, assignment.logicalStoreId)) {
                continue;
            }
            result.items.add(toView(assignment));
            currentAssignmentsByTaskId.put(assignment.taskId, assignment);
            if (result.items.size() >= 20) {
                finalizeListView(result, currentAssignments, latestAssignments, List.of(), null);
                return result;
            }
        }
        Map<Long, Ali1688CollectionRecords.PluginAssignmentRecord> latestAssignmentsByTaskId = latestAssignments.stream()
                .filter(assignment -> assignment != null && assignment.taskId != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.taskId,
                        assignment -> assignment,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        List<Ali1688CollectionRecords.TaskRecord> assignableTasks = isAdminUser(user)
                ? ali1688CollectionMapper.listCurrentPluginAssignableTasks(80)
                : ali1688CollectionMapper.listCurrentPluginAssignableTasksByOperator(operatorUserId, 80);
        int visibleTaskCount = 0;
        int assignableTaskCount = 0;
        int missingSourceImageCount = 0;
        String assignmentIssueMessage = null;
        for (Ali1688CollectionRecords.TaskRecord task : safeList(assignableTasks)) {
            if (task == null || task.id == null || currentAssignmentsByTaskId.containsKey(task.id)) {
                continue;
            }
            visibleTaskCount++;
            if (!isVisibleLogicalStore(operatorUserId, task.logicalStoreId)) {
                continue;
            }
            if (!StringUtils.hasText(task.sourceImageUrl)) {
                missingSourceImageCount++;
                continue;
            }
            Ali1688CollectionRecords.PluginAssignmentRecord latestAssignment = latestAssignmentsByTaskId.get(task.id);
            if (shouldSurfaceClosedAssignment(latestAssignment) && !shouldAutoReissueClosedAssignment(latestAssignment)) {
                result.items.add(toView(latestAssignment));
                currentAssignmentsByTaskId.put(task.id, latestAssignment);
                if (result.items.size() >= 20) {
                    break;
                }
                continue;
            }
            assignableTaskCount++;
            try {
                CreatedPluginAssignment created = createPluginAssignmentForTask(
                        task,
                        operatorUserId,
                        ASSIGNMENT_ISSUE_QUEUE_AUTO
                );
                result.items.add(toView(created.assignment));
                currentAssignmentsByTaskId.put(task.id, created.assignment);
            } catch (RuntimeException exception) {
                assignmentIssueMessage = "系统暂时无法签发插件采集任务。";
            }
            if (result.items.size() >= 20) {
                break;
            }
        }
        result.diagnostics.visibleTaskCount = visibleTaskCount;
        result.diagnostics.assignableTaskCount = assignableTaskCount;
        result.diagnostics.missingSourceImageCount = missingSourceImageCount;
        finalizeListView(result, currentAssignments, latestAssignments, safeList(assignableTasks), assignmentIssueMessage);
        return result;
    }

    private void finalizeListView(
            Ali1688PluginAssignmentListView result,
            List<Ali1688CollectionRecords.PluginAssignmentRecord> currentAssignments,
            List<Ali1688CollectionRecords.PluginAssignmentRecord> latestAssignments,
            List<Ali1688CollectionRecords.TaskRecord> assignableTasks,
            String assignmentIssueMessage
    ) {
        result.summary.total = result.items.size();
        for (Ali1688PluginAssignmentView item : result.items) {
            if ("created".equals(item.status)) {
                result.summary.pending++;
            } else if ("running".equals(item.status)) {
                result.summary.running++;
            } else if ("accepted".equals(item.status)) {
                result.summary.synced++;
            } else if ("failed".equals(item.status) || "expired".equals(item.status) || "cancelled".equals(item.status)) {
                result.summary.blockedOrFailed++;
            }
        }

        result.diagnostics.issuedAssignmentCount = Math.max(
                result.items.size(),
                Math.max(safeList(currentAssignments).size(), safeList(latestAssignments).size())
        );
        result.diagnostics.expiredAssignmentCount = (int) safeList(latestAssignments).stream()
                .filter(assignment -> assignment != null && "expired".equals(assignment.status))
                .count();

        if (!result.items.isEmpty()) {
            result.emptyReason = null;
            result.message = "已加载系统插件采集队列。";
            return;
        }

        if (StringUtils.hasText(assignmentIssueMessage) && result.diagnostics.assignableTaskCount > 0) {
            result.emptyReason = "assignment_not_issued";
            result.message = assignmentIssueMessage;
            return;
        }

        if (result.diagnostics.visibleTaskCount > 0
                && result.diagnostics.missingSourceImageCount == result.diagnostics.visibleTaskCount) {
            result.emptyReason = "task_missing_source_image";
            result.message = "系统任务缺少源头图片，暂不能派发给插件。";
            return;
        }

        if (allLatestAssignmentsInStatus(latestAssignments, "accepted")) {
            result.emptyReason = "all_tasks_done";
            result.message = "当前账号的插件采集任务已同步完成。";
            return;
        }

        if (allLatestAssignmentsInStatus(latestAssignments, "expired")) {
            result.emptyReason = "all_tasks_expired";
            result.message = "当前账号的插件采集任务已过期。";
            return;
        }

        if (!safeList(assignableTasks).isEmpty() && result.diagnostics.assignableTaskCount == 0) {
            result.emptyReason = "no_authorized_task";
            result.message = "当前账号无可处理任务。";
            return;
        }

        result.emptyReason = "no_visible_task";
        result.message = "当前没有可处理的系统采集任务。";
    }

    private boolean isClosedPluginAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        return assignment != null
                && Set.of("failed", "accepted", "expired", "cancelled").contains(assignment.status);
    }

    private boolean shouldSurfaceClosedAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        return assignment != null
                && Set.of("failed", "accepted", "cancelled").contains(assignment.status);
    }

    private boolean isAcceptedPluginAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        return assignment != null && "accepted".equals(assignment.status);
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord resolveVisibleAssignment(
            Ali1688CollectionRecords.PluginAssignmentRecord current,
            Map<Long, Ali1688CollectionRecords.PluginAssignmentRecord> latestClosedAssignmentsByTaskId
    ) {
        if (!isUnstartedCreatedAssignment(current)) {
            return current;
        }
        if (isExplicitCreateAssignment(current)) {
            return current;
        }
        Ali1688CollectionRecords.PluginAssignmentRecord latestClosed = latestClosedAssignmentsByTaskId.get(current.taskId);
        return latestClosed == null || shouldAutoReissueClosedAssignment(latestClosed) ? current : latestClosed;
    }

    private boolean isExplicitCreateAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        String snapshot = assignment == null ? null : assignment.rawAssignmentSnapshotJson;
        return StringUtils.hasText(snapshot)
                && snapshot.contains("\"issueSource\":\"" + ASSIGNMENT_ISSUE_EXPLICIT_CREATE + "\"");
    }

    private boolean isUnstartedCreatedAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        return assignment != null
                && "created".equals(assignment.status)
                && !StringUtils.hasText(assignment.startedAt)
                && defaultInt(assignment.submittedCandidateCount) == 0
                && defaultInt(assignment.acceptedCandidateCount) == 0
                && defaultInt(assignment.rejectedCandidateCount) == 0;
    }

    private boolean shouldAutoReissueClosedAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        return assignment != null
                && "failed".equals(assignment.status)
                && RETRYABLE_FAILURE_CODES.contains(trim(assignment.failureCode));
    }

    private boolean allLatestAssignmentsInStatus(
            List<Ali1688CollectionRecords.PluginAssignmentRecord> latestAssignments,
            String status
    ) {
        List<Ali1688CollectionRecords.PluginAssignmentRecord> assignments = safeList(latestAssignments);
        return !assignments.isEmpty() && assignments.stream()
                .filter(assignment -> assignment != null)
                .allMatch(assignment -> status.equals(assignment.status));
    }

    @Transactional
    public Ali1688PluginAssignmentView startAssignment(String assignmentCode, AuthenticatedSession session) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireUsableAssignment(assignmentCode, session);
        if (!"created".equals(assignment.status) && !"running".equals(assignment.status)) {
            throw assignmentError("assignment_not_startable", "当前插件采集任务不能开始。");
        }
        ali1688CollectionMapper.markPluginAssignmentRunning(assignment.id, session.getUserId());
        assignment.status = "running";
        Ali1688CollectionRecords.PluginAssignmentRecord saved = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(saved == null ? assignment : saved);
    }

    @Transactional
    public Ali1688PluginAssignmentView failAssignment(
            String assignmentCode,
            AuthenticatedSession session,
            Ali1688PluginAssignmentFailureCommand command
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireUsableAssignment(assignmentCode, session);
        String failureCode = normalizeFailureCode(command == null ? null : command.getFailureCode());
        if (!StringUtils.hasText(failureCode)) {
            throw assignmentError("plugin_failure_code_required", "缺少插件采集失败类型。");
        }
        String failureMessage = shrink(defaultText(command == null ? null : command.getFailureMessage(), defaultFailureMessage(failureCode)), 480);
        ali1688CollectionMapper.markPluginAssignmentFailed(assignment.id, failureCode, failureMessage, session.getUserId());
        assignment.status = "failed";
        assignment.failureCode = failureCode;
        assignment.failureMessage = failureMessage;
        assignment.activeAssignmentKey = null;
        Ali1688CollectionRecords.PluginAssignmentRecord saved = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(saved == null ? assignment : saved);
    }

    @Transactional
    public Ali1688PluginAssignmentView cancelAssignment(String assignmentCode, Long operatorUserId) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = findAssignmentByLocator(assignmentCode);
        if (assignment == null) {
            throw assignmentError("assignment_not_found", "插件采集任务不存在或已失效。");
        }
        requireVisibleLogicalStore(operatorUserId, assignment.logicalStoreId);
        ali1688CollectionMapper.cancelPluginAssignment(assignment.id, operatorUserId);
        assignment.status = "cancelled";
        assignment.activeAssignmentKey = null;
        Ali1688CollectionRecords.PluginAssignmentRecord saved = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(saved == null ? assignment : saved);
    }

    @Transactional
    public Ali1688PluginSubmissionView submitCandidates(
            String assignmentCode,
            AuthenticatedSession session,
            Ali1688PluginSubmissionCommand command
    ) {
        Ali1688PluginSubmissionCommand safeCommand = command == null ? new Ali1688PluginSubmissionCommand() : command;
        String idempotencyKey = trim(safeCommand.getIdempotencyKey());
        if (!StringUtils.hasText(idempotencyKey)) {
            throw assignmentError("idempotency_key_required", "缺少插件提交幂等键。");
        }
        if (safeCommand.hasIdentityFields()) {
            throw assignmentError("plugin_identity_fields_rejected", "插件提交归属由后端会话和 assignment 推导，不能由插件传入。");
        }

        Ali1688CollectionRecords.PluginAssignmentRecord assignment = loadAssignmentForSubmission(assignmentCode, session, idempotencyKey);
        if ("accepted".equals(assignment.status)) {
            return toSubmissionView(assignment, assignment.taskStatus, assignment.failureCode, assignment.failureMessage);
        }

        Ali1688PluginSubmissionNormalizer.Result normalized = submissionNormalizer.normalize(safeCommand);
        if (normalized.getAcceptedCandidateCount() <= 0) {
            throw assignmentError("plugin_no_valid_candidates", "插件提交中没有有效 1688 候选。");
        }
        Ali1688OfferDetailCompletionResult detailCompletion = enrichKnownOfferDetails(normalized.getCandidates());
        String auditSnapshotJson = appendDetailCompletion(
                normalized.getAuditSnapshotJson(),
                detailCompletion
        );

        Long operatorUserId = session.getUserId();
        Ali1688CollectionRecords.TaskRecord task = toTaskRecord(assignment, operatorUserId);
        ali1688CollectionMapper.softDeleteCandidatesByCurrentPluginAssignment(task.id, operatorUserId, assignment.id);
        List<Ali1688CollectionRecords.CandidateRecord> candidates = persistSubmittedCandidates(
                task,
                assignment.id,
                operatorUserId,
                normalized.getCandidates()
        );
        if (candidates.size() != normalized.getAcceptedCandidateCount()) {
            throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
        }

        selectTopFiveForPlugin(task.id, candidates, operatorUserId, assignment.id);
        String taskStatus = candidates.size() >= 10 ? "success" : "partial_success";
        String failureCode = candidates.size() >= 10 ? null : "plugin_candidate_count_less_than_10";
        String failureMessage = candidates.size() >= 10 ? null : "插件提交候选不足 10 个，已展示可用候选。";
        int recommendedCount = Math.min(candidates.size(), 5);
        int taskUpdated = ali1688CollectionMapper.markTaskCompletedFromPluginSubmission(
                task.id,
                taskStatus,
                normalized.getSubmittedCandidateCount(),
                candidates.size(),
                recommendedCount,
                failureCode,
                failureMessage,
                auditSnapshotJson,
                operatorUserId,
                assignment.id
        );
        if (taskUpdated <= 0) {
            throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
        }
        int assignmentUpdated = ali1688CollectionMapper.markPluginAssignmentAccepted(
                assignment.id,
                idempotencyKey,
                candidates.size(),
                normalized.getRejectedCandidateCount(),
                auditSnapshotJson,
                operatorUserId
        );
        if (assignmentUpdated <= 0) {
            throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
        }
        createPendingAiAssessmentsWithoutBlockingSubmission(task, candidates);

        assignment.status = "accepted";
        assignment.activeAssignmentKey = null;
        assignment.submissionIdempotencyKey = idempotencyKey;
        assignment.submittedCandidateCount = normalized.getSubmittedCandidateCount();
        assignment.acceptedCandidateCount = candidates.size();
        assignment.rejectedCandidateCount = normalized.getRejectedCandidateCount();
        assignment.taskStatus = taskStatus;
        assignment.taskCandidateCount = candidates.size();
        assignment.taskRecommendedCount = recommendedCount;
        assignment.failureCode = failureCode;
        assignment.failureMessage = failureMessage;
        return toSubmissionView(assignment, taskStatus, failureCode, failureMessage, detailCompletion, candidates);
    }

    private void createPendingAiAssessmentsWithoutBlockingSubmission(
            Ali1688CollectionRecords.TaskRecord task,
            List<Ali1688CollectionRecords.CandidateRecord> candidates
    ) {
        try {
            aiAssessmentService.createPendingAssessmentsForCurrentTask(task, candidates);
        } catch (RuntimeException exception) {
            markCandidatesAiAssessmentFailed(candidates, task == null ? null : task.updatedBy);
        }
    }

    private void markCandidatesAiAssessmentFailed(
            List<Ali1688CollectionRecords.CandidateRecord> candidates,
            Long updatedBy
    ) {
        for (Ali1688CollectionRecords.CandidateRecord candidate : candidates == null ? List.<Ali1688CollectionRecords.CandidateRecord>of() : candidates) {
            if (candidate == null || candidate.id == null) {
                continue;
            }
            try {
                ali1688CollectionMapper.markCandidateAiAssessmentFailed(candidate.id, updatedBy);
                candidate.aiAssessmentStatus = "failed";
            } catch (RuntimeException ignored) {
                // AI 补分是异步增强，失败不能覆盖已经成功的插件候选提交结果。
            }
        }
    }

    private Ali1688OfferDetailCompletionResult enrichKnownOfferDetails(
            List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> candidates
    ) {
        try {
            return offerDetailGateway.enrich(candidates);
        } catch (RuntimeException exception) {
            return Ali1688OfferDetailCompletionResult.failed(
                    0,
                    candidates == null ? 0 : candidates.size(),
                    "Known-offer detail enrichment failed: " + defaultText(exception.getMessage(), exception.getClass().getSimpleName())
            );
        }
    }

    private String appendDetailCompletion(
            String auditSnapshotJson,
            Ali1688OfferDetailCompletionResult detailCompletion
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (StringUtils.hasText(auditSnapshotJson)) {
            try {
                snapshot.putAll(objectMapper.readValue(auditSnapshotJson, new TypeReference<Map<String, Object>>() {
                }));
            } catch (Exception ignored) {
                snapshot.put("rawAuditSnapshotJson", auditSnapshotJson);
            }
        }
        Ali1688OfferDetailCompletionResult result = detailCompletion == null
                ? Ali1688OfferDetailCompletionResult.notAttempted("Known-offer detail enrichment did not run.")
                : detailCompletion;
        snapshot.put("detailCompletionOutcome", result.getOutcome());
        snapshot.put("detailCompletionMessage", result.getMessage());
        snapshot.put("detailCompletionAttemptCount", result.getAttemptCount());
        snapshot.put("detailCompletionEnrichedCount", result.getEnrichedCount());
        snapshot.put("detailCompletionFailedCount", result.getFailedCount());
        return writeJson(snapshot);
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord loadAssignmentForSubmission(
            String assignmentCode,
            AuthenticatedSession session,
            String idempotencyKey
    ) {
        if (session == null || session.getUserId() == null) {
            throw new ProductSelectionAccessDeniedException("插件接口缺少有效登录态。");
        }
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = findAssignmentByLocator(assignmentCode);
        if (assignment == null) {
            throw assignmentError("assignment_not_found", "插件采集任务不存在或已失效。");
        }
        requireVisibleLogicalStore(session.getUserId(), assignment.logicalStoreId);
        if ("accepted".equals(assignment.status)) {
            if (!StringUtils.hasText(assignment.taskCurrentTaskKey)) {
                throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
            }
            if (idempotencyKey.equals(assignment.submissionIdempotencyKey)) {
                return assignment;
            }
            throw assignmentError("assignment_closed", "插件采集任务已结束。");
        }
        if (isExpired(assignment.expiresAt)) {
            ali1688CollectionMapper.expirePluginAssignment(assignment.id, session.getUserId());
            throw assignmentError("assignment_expired", "插件采集任务已过期。");
        }
        if (!StringUtils.hasText(assignment.activeAssignmentKey)
                || !String.valueOf(assignment.taskId).equals(assignment.activeAssignmentKey)
                || !StringUtils.hasText(assignment.taskCurrentTaskKey)) {
            throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
        }
        if ("cancelled".equals(assignment.status)) {
            throw assignmentError("assignment_cancelled", "插件采集任务已取消。");
        }
        if ("expired".equals(assignment.status)) {
            throw assignmentError("assignment_expired", "插件采集任务已过期。");
        }
        if ("failed".equals(assignment.status)) {
            throw assignmentError("assignment_closed", "插件采集任务已结束。");
        }
        return assignment;
    }

    private List<Ali1688CollectionRecords.CandidateRecord> persistSubmittedCandidates(
            Ali1688CollectionRecords.TaskRecord task,
            Long assignmentId,
            Long operatorUserId,
            List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> normalizedCandidates
    ) {
        List<Ali1688CollectionRecords.CandidateRecord> persisted = new java.util.ArrayList<>();
        int rank = 1;
        for (Ali1688PluginSubmissionNormalizer.NormalizedCandidate normalized : normalizedCandidates) {
            Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
            candidate.id = ali1688CollectionMapper.nextCandidateId();
            candidate.taskId = task.id;
            candidate.sourceCollectionId = task.sourceCollectionId;
            candidate.ownerUserId = task.ownerUserId;
            candidate.logicalStoreId = task.logicalStoreId;
            candidate.rankNo = rank++;
            candidate.level = "review";
            candidate.offerId = normalized.offerId;
            candidate.candidateUrl = normalized.candidateUrl;
            candidate.candidateUrlHash = normalized.candidateUrlHash;
            candidate.activeCandidateKey = task.id + ":" + (StringUtils.hasText(candidate.offerId)
                    ? candidate.offerId
                    : candidate.candidateUrlHash);
            candidate.title = normalized.title;
            candidate.supplierName = normalized.supplierName;
            candidate.priceText = normalized.priceText;
            candidate.priceMin = normalized.priceMin;
            candidate.priceMax = normalized.priceMax;
            candidate.moqText = normalized.moqText;
            candidate.moqValue = normalized.moqValue;
            candidate.locationText = normalized.locationText;
            candidate.mainImageUrl = normalized.mainImageUrl;
            candidate.imageUrlsJson = writeJson(normalized.imageUrls);
            candidate.badgesJson = writeJson(normalized.badges);
            candidate.skuSnapshotJson = writeJson(normalized.skuSnapshot);
            candidate.supplierSnapshotJson = writeJson(normalized.supplierSnapshot);
            candidate.logisticsSnapshotJson = writeJson(normalized.logisticsSnapshot);
            candidate.aiAssessmentStatus = "pending";
            candidate.createdBy = operatorUserId;
            candidate.updatedBy = operatorUserId;
            scoringService.score(candidate);
            int inserted = ali1688CollectionMapper.insertCandidateForCurrentPluginAssignment(candidate, assignmentId);
            if (inserted <= 0) {
                break;
            }
            persisted.add(candidate);
        }
        return persisted;
    }

    private void selectTopFiveForPlugin(
            Long taskId,
            List<Ali1688CollectionRecords.CandidateRecord> candidates,
            Long updatedBy,
            Long assignmentId
    ) {
        ali1688CollectionMapper.clearSelectedRanksForCurrentPluginAssignment(taskId, updatedBy, assignmentId);
        List<Ali1688CollectionRecords.CandidateRecord> selected = candidates.stream()
                .sorted(Comparator.comparing((Ali1688CollectionRecords.CandidateRecord item) -> item.ruleScore == null ? 0 : item.ruleScore).reversed()
                        .thenComparing(item -> item.rankNo == null ? Integer.MAX_VALUE : item.rankNo))
                .limit(5)
                .collect(Collectors.toList());
        int selectedRank = 1;
        for (Ali1688CollectionRecords.CandidateRecord candidate : selected) {
            candidate.selectedRankNo = selectedRank;
            candidate.level = "recommended";
            ali1688CollectionMapper.updateSelectedRankForCurrentPluginAssignment(
                    taskId,
                    candidate.id,
                    selectedRank,
                    updatedBy,
                    assignmentId
            );
            selectedRank++;
        }
    }

    private Ali1688CollectionRecords.TaskRecord toTaskRecord(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Long operatorUserId
    ) {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = assignment.taskId;
        task.sourceCollectionId = assignment.sourceCollectionId;
        task.currentTaskKey = assignment.taskCurrentTaskKey;
        task.ownerUserId = assignment.ownerUserId;
        task.logicalStoreId = assignment.logicalStoreId;
        task.taskNo = assignment.taskNo;
        task.status = assignment.taskStatus;
        task.sourceImageUrl = assignment.sourceImageUrl;
        task.sourceTitle = assignment.sourceTitle;
        task.sourceTitleCn = assignment.sourceTitleCn;
        task.sourceUrl = assignment.sourceUrl;
        task.pageUrl = assignment.pageUrl;
        task.updatedBy = operatorUserId;
        return task;
    }

    private Ali1688PluginSubmissionView toSubmissionView(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            String taskStatus,
            String failureCode,
            String failureMessage
    ) {
        return toSubmissionView(assignment, taskStatus, failureCode, failureMessage, null, null);
    }

    private Ali1688PluginSubmissionView toSubmissionView(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            String taskStatus,
            String failureCode,
            String failureMessage,
            Ali1688OfferDetailCompletionResult detailCompletion,
            List<Ali1688CollectionRecords.CandidateRecord> candidates
    ) {
        Ali1688PluginSubmissionView view = new Ali1688PluginSubmissionView();
        view.assignmentId = stringId(assignment.id);
        view.taskId = stringId(assignment.taskId);
        view.sourceCollectionId = stringId(assignment.sourceCollectionId);
        view.status = assignment.status;
        view.submittedCandidateCount = defaultInt(assignment.submittedCandidateCount);
        view.acceptedCandidateCount = defaultInt(assignment.acceptedCandidateCount);
        view.rejectedCandidateCount = defaultInt(assignment.rejectedCandidateCount);
        view.candidateCount = defaultInt(assignment.taskCandidateCount);
        view.recommendedCount = defaultInt(assignment.taskRecommendedCount);
        view.taskStatus = taskStatus;
        view.failureCode = failureCode;
        view.failureMessage = failureMessage;
        view.message = "插件提交已接收，候选已进入 1688 采集结果。";
        view.resultSource = "plugin_assisted";
        view.pluginPathPassed = defaultInt(view.acceptedCandidateCount) > 0;
        Ali1688OfferDetailCompletionResult safeDetailCompletion = detailCompletion == null
                ? Ali1688OfferDetailCompletionResult.notAttempted("Known-offer detail enrichment did not run.")
                : detailCompletion;
        view.detailCompletionStatus = safeDetailCompletion.getOutcome();
        view.detailCompletionMessage = safeDetailCompletion.getMessage();
        view.fieldCompleteness = buildFieldCompleteness(candidates);
        view.fieldCompletenessPassed = isFieldCompletenessPassed(view.fieldCompleteness);
        return view;
    }

    private Ali1688CollectionView.FieldCompleteness buildFieldCompleteness(
            List<Ali1688CollectionRecords.CandidateRecord> candidates
    ) {
        Ali1688CollectionView.FieldCompleteness completeness = new Ali1688CollectionView.FieldCompleteness();
        List<Ali1688CollectionRecords.CandidateRecord> safeCandidates = candidates == null ? List.of() : candidates;
        completeness.candidateCount = safeCandidates.size();
        completeness.nonFallbackTitleCount = (int) safeCandidates.stream()
                .filter(candidate -> isNonFallbackTitle(candidate.title, candidate.supplierName))
                .count();
        completeness.supplierNameCount = (int) safeCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.supplierName))
                .count();
        completeness.priceTextCount = (int) safeCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.priceText))
                .count();
        completeness.moqTextCount = (int) safeCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.moqText))
                .count();
        completeness.locationTextCount = (int) safeCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.locationText))
                .count();
        completeness.normalizedDetailUrlCount = (int) safeCandidates.stream()
                .filter(candidate -> isNormalizedDetailUrl(candidate.candidateUrl))
                .count();
        return completeness;
    }

    private boolean isFieldCompletenessPassed(Ali1688CollectionView.FieldCompleteness completeness) {
        return completeness != null
                && completeness.candidateCount > 0
                && completeness.priceTextCount == completeness.candidateCount
                && completeness.moqTextCount == completeness.candidateCount
                && completeness.locationTextCount == completeness.candidateCount
                && completeness.normalizedDetailUrlCount == completeness.candidateCount;
    }

    private boolean isNonFallbackTitle(String title, String supplierName) {
        String value = defaultText(title, "");
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (value.matches("^1688候选\\s*\\d+$")) {
            return false;
        }
        if ("旺旺在线".equals(value) || value.contains("点此可以直接和卖家交流")) {
            return false;
        }
        return !StringUtils.hasText(supplierName) || !value.equals(supplierName.trim());
    }

    private boolean isNormalizedDetailUrl(String candidateUrl) {
        return defaultText(candidateUrl, "").matches("^https://detail\\.1688\\.com/offer/\\d+\\.html$");
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord requireUsableAssignment(
            String assignmentCode,
            AuthenticatedSession session
    ) {
        if (session == null || session.getUserId() == null) {
            throw new ProductSelectionAccessDeniedException("插件接口缺少有效登录态。");
        }
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = findAssignmentByLocator(assignmentCode);
        if (assignment == null) {
            throw assignmentError("assignment_not_found", "插件采集任务不存在或已失效。");
        }
        requireVisibleLogicalStore(session.getUserId(), assignment.logicalStoreId);
        if (isExpired(assignment.expiresAt)) {
            ali1688CollectionMapper.expirePluginAssignment(assignment.id, session.getUserId());
            throw assignmentError("assignment_expired", "插件采集任务已过期。");
        }
        if (!StringUtils.hasText(assignment.activeAssignmentKey)
                || !String.valueOf(assignment.taskId).equals(assignment.activeAssignmentKey)
                || !StringUtils.hasText(assignment.taskCurrentTaskKey)) {
            throw assignmentError("assignment_not_current", "插件采集任务已不是当前任务。");
        }
        if ("cancelled".equals(assignment.status)) {
            throw assignmentError("assignment_cancelled", "插件采集任务已取消。");
        }
        if ("expired".equals(assignment.status)) {
            throw assignmentError("assignment_expired", "插件采集任务已过期。");
        }
        if ("failed".equals(assignment.status) || "accepted".equals(assignment.status)) {
            throw assignmentError("assignment_closed", "插件采集任务已结束。");
        }
        return assignment;
    }

    private void requireCurrentVisibleTask(Long operatorUserId, Ali1688CollectionRecords.TaskRecord task) {
        if (task == null) {
            throw new IllegalArgumentException("1688 采集任务不存在或已被删除。");
        }
        if (!StringUtils.hasText(task.currentTaskKey)) {
            throw new IllegalArgumentException("只有当前 1688 采集任务可以创建插件采集任务。");
        }
        requireVisibleLogicalStore(operatorUserId, task.logicalStoreId);
    }

    private void requireVisibleLogicalStore(Long operatorUserId, Long logicalStoreId) {
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (isAdminUser(user)) {
            return;
        }
        if (logicalStoreId == null || productSelectionMapper.countVisibleLogicalStoreSites(operatorUserId, logicalStoreId) <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该插件采集任务。");
        }
    }

    private boolean isAdminUser(ProductSelectionUserContext user) {
        return user != null && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
    }

    private boolean isVisibleLogicalStore(Long operatorUserId, Long logicalStoreId) {
        try {
            requireVisibleLogicalStore(operatorUserId, logicalStoreId);
            return true;
        } catch (ProductSelectionAccessDeniedException exception) {
            return false;
        }
    }

    private CreatedPluginAssignment createPluginAssignmentForTask(
            Ali1688CollectionRecords.TaskRecord task,
            Long operatorUserId,
            String issueSource
    ) {
        ali1688CollectionMapper.expireCurrentPluginAssignmentsByTask(task.id, operatorUserId);

        Long assignmentId = ali1688CollectionMapper.nextPluginAssignmentId();
        String assignmentCode = generateAssignmentCode(assignmentId);
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = assignmentId;
        assignment.taskId = task.id;
        assignment.sourceCollectionId = task.sourceCollectionId;
        assignment.ownerUserId = task.ownerUserId;
        assignment.logicalStoreId = task.logicalStoreId;
        assignment.activeAssignmentKey = String.valueOf(task.id);
        assignment.assignmentCode = assignmentCode;
        assignment.assignmentCodeHash = sha256Hex(assignmentCode);
        assignment.status = "created";
        assignment.expiresAt = formatDateTime(clock.instant().plusSeconds(Math.max(1, assignmentTtlMinutes) * 60L));
        assignment.rawAssignmentSnapshotJson = pluginAssignmentIssueSnapshot(issueSource);
        assignment.createdBy = operatorUserId;
        assignment.updatedBy = operatorUserId;
        ali1688CollectionMapper.insertPluginAssignment(assignment);

        Ali1688CollectionRecords.PluginAssignmentRecord saved = ali1688CollectionMapper.selectPluginAssignmentById(assignmentId);
        return new CreatedPluginAssignment(saved == null ? mergeTaskFields(assignment, task) : saved, assignmentCode);
    }

    private String pluginAssignmentIssueSnapshot(String issueSource) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "nuono-system");
        snapshot.put("issueSource", StringUtils.hasText(issueSource) ? issueSource : ASSIGNMENT_ISSUE_QUEUE_AUTO);
        snapshot.put("issuedAt", formatDateTime(clock.instant()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{\"source\":\"nuono-system\",\"issueSource\":\"" + ASSIGNMENT_ISSUE_QUEUE_AUTO + "\"}";
        }
    }

    private Ali1688PluginAssignmentView toView(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        Ali1688PluginAssignmentView view = new Ali1688PluginAssignmentView();
        if (assignment == null) {
            return view;
        }
        view.assignmentId = stringId(assignment.id);
        view.taskId = stringId(assignment.taskId);
        view.sourceCollectionId = stringId(assignment.sourceCollectionId);
        view.taskNo = assignment.taskNo;
        view.status = defaultText(assignment.status, "created");
        view.sourceImageUrl = assignment.sourceImageUrl;
        view.sourceTitle = assignment.sourceTitle;
        view.sourceTitleCn = assignment.sourceTitleCn;
        view.sourceUrl = assignment.sourceUrl;
        view.pageUrl = assignment.pageUrl;
        view.storeId = stringId(assignment.logicalStoreId);
        view.storeName = assignment.storeName;
        view.storeCode = assignment.storeCode;
        view.createdAt = toMinute(assignment.createdAt);
        view.expiresAt = toMinute(assignment.expiresAt);
        view.startedAt = toMinute(assignment.startedAt);
        view.finishedAt = toMinute(assignment.finishedAt);
        view.failureCode = assignment.failureCode;
        view.failureMessage = assignment.failureMessage;
        view.submittedCandidateCount = assignment.submittedCandidateCount;
        view.acceptedCandidateCount = assignment.acceptedCandidateCount;
        view.rejectedCandidateCount = assignment.rejectedCandidateCount;
        view.current = StringUtils.hasText(assignment.activeAssignmentKey)
                && String.valueOf(assignment.taskId).equals(assignment.activeAssignmentKey)
                && StringUtils.hasText(assignment.taskCurrentTaskKey);
        view.message = resolveMessage(assignment);
        return view;
    }

    private String resolveMessage(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        if (assignment == null) {
            return "";
        }
        if (StringUtils.hasText(assignment.failureMessage)) {
            return assignment.failureMessage;
        }
        if ("created".equals(assignment.status)) {
            return "插件采集任务已创建，等待插件领取。";
        }
        if ("running".equals(assignment.status)) {
            return "插件采集中。";
        }
        if ("accepted".equals(assignment.status)) {
            return "插件候选已接收。";
        }
        if ("failed".equals(assignment.status)) {
            return "插件采集失败。";
        }
        if ("expired".equals(assignment.status)) {
            return "插件采集任务已过期。";
        }
        if ("cancelled".equals(assignment.status)) {
            return "插件采集任务已取消。";
        }
        return "";
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord mergeTaskFields(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Ali1688CollectionRecords.TaskRecord task
    ) {
        assignment.taskCurrentTaskKey = task.currentTaskKey;
        assignment.taskNo = task.taskNo;
        assignment.taskStatus = task.status;
        assignment.sourceImageUrl = task.sourceImageUrl;
        assignment.sourceCollectionNo = task.sourceCollectionNo;
        assignment.sourcePlatform = task.sourcePlatform;
        assignment.sourceTitle = task.sourceTitle;
        assignment.sourceTitleCn = task.sourceTitleCn;
        assignment.sourceUrl = task.sourceUrl;
        assignment.pageUrl = task.pageUrl;
        assignment.storeName = task.storeName;
        assignment.storeCode = task.storeCode;
        return assignment;
    }

    private String generateAssignmentCode(Long assignmentId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "ALI1688-PLUGIN-" + assignmentId + "-" + suffix;
    }

    private String requiredLocator(String value) {
        String locator = trim(value);
        if (!StringUtils.hasText(locator)) {
            throw assignmentError("assignment_locator_required", "缺少插件采集任务定位码。");
        }
        return locator;
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord findAssignmentByLocator(String value) {
        String locator = requiredLocator(value);
        if (locator.matches("\\d+")) {
            return ali1688CollectionMapper.selectPluginAssignmentById(Long.valueOf(locator));
        }
        return ali1688CollectionMapper.selectPluginAssignmentByCodeHash(sha256Hex(locator));
    }

    private String normalizeFailureCode(String value) {
        String code = trim(value);
        return FAILURE_CODES.contains(code) ? code : null;
    }

    private String defaultFailureMessage(String failureCode) {
        if ("captcha_required".equals(failureCode)) {
            return "1688 页面出现验证码。";
        }
        if ("login_required".equals(failureCode)) {
            return "1688 页面需要登录。";
        }
        if ("rate_limited".equals(failureCode)) {
            return "1688 访问频率受限。";
        }
        if ("no_candidates".equals(failureCode)) {
            return "插件未采集到有效候选。";
        }
        return "插件采集失败。";
    }

    private boolean isExpired(String expiresAt) {
        Instant expiresInstant = parseDateTime(expiresAt);
        return expiresInstant != null && !clock.instant().isBefore(expiresInstant);
    }

    private Instant parseDateTime(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            String normalized = text.length() == 16 ? text + ":00" : text;
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return localDateTime.atZone(BUSINESS_ZONE).toInstant();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String formatDateTime(Instant value) {
        return DB_DATE_TIME_FORMATTER.format(value);
    }

    private String toMinute(String value) {
        String text = trim(value);
        if (text != null && text.length() >= 16) {
            return text.substring(0, 16);
        }
        return text;
    }

    private Long parseLongId(String value, String errorMessage) {
        try {
            return Long.valueOf(requiredText(value, errorMessage));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private String requiredText(String value, String errorMessage) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成插件采集任务定位码摘要。", exception);
        }
    }

    private Ali1688PluginAssignmentException assignmentError(String errorCode, String message) {
        return new Ali1688PluginAssignmentException(errorCode, message);
    }

    private String stringId(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String shrink(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static class CreatedPluginAssignment {
        private final Ali1688CollectionRecords.PluginAssignmentRecord assignment;
        private final String plainAssignmentCode;

        private CreatedPluginAssignment(
                Ali1688CollectionRecords.PluginAssignmentRecord assignment,
                String plainAssignmentCode
        ) {
            this.assignment = assignment;
            this.plainAssignmentCode = plainAssignmentCode;
        }
    }
}
