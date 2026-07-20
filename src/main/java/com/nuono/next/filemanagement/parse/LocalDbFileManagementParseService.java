package com.nuono.next.filemanagement.parse;

import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local-db")
public class LocalDbFileManagementParseService {

    static final long FILE_MANAGEMENT_MENU_ID = 9301L;
    private static final DateTimeFormatter TASK_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_AI_CHUNK_SOURCE_ROWS = 30;
    private static final int MAX_AI_CHUNK_TEXT_LENGTH = 18_000;
    private static final int AI_CHUNK_CONTEXT_SOURCE_ROWS = 5;
    private static final Set<String> FILE_INPUT_TYPES = Set.of("file", "excel", "pdf", "image");
    private static final Set<String> TEXT_INPUT_TYPES = Set.of("manual_text", "ocr_text");
    private static final Set<String> INPUT_ROLES = Set.of("primary_source", "parsed_file", "supplement", "reference");
    private final FileManagementParseMapper fileManagementParseMapper;
    private final FileParseStorageProperties storageProperties;
    private final FileParseUploadArchiveService uploadArchiveService;
    private final FileParseInputExtractionService inputExtractionService;
    private final FileParseStructuredAiService structuredAiService;
    private final FileParseResultDiffService resultDiffService;
    private final FileParseResultPersistenceService resultPersistenceService;
    private final FileParseItemReviewService itemReviewService;
    private final FileParseQueryViewService queryViewService;
    private final FileParsePublishService publishService;
    private final FileParseLogisticsChannelActivationService logisticsChannelActivationService;
    private final FileParseSourceLineagePolicy sourceLineagePolicy = new FileParseSourceLineagePolicy();
    private final FileParseWorkflowStatePolicy workflowStatePolicy = new FileParseWorkflowStatePolicy();
    private final FileParseCommissionSourceScopePolicy commissionSourceScopePolicy = new FileParseCommissionSourceScopePolicy();
    private final FileParseOutboundFeeSourceScopePolicy outboundFeeSourceScopePolicy = new FileParseOutboundFeeSourceScopePolicy();
    private final FileParseLogisticsCoverageValidator logisticsCoverageValidator = new FileParseLogisticsCoverageValidator();

    @Value("${nuono.file-management.parse.retry-scheduler.enabled:true}")
    private boolean retrySchedulerEnabled;

    @Value("${nuono.file-management.parse.retry-scheduler.stale-timeout-seconds:900}")
    private int retrySchedulerStaleTimeoutSeconds;

    @Value("${nuono.file-management.parse.retry-scheduler.max-attempts:5}")
    private int retrySchedulerMaxAttempts;

    @Value("${nuono.file-management.parse.retry-scheduler.retry-delay-seconds:60}")
    private int retrySchedulerRetryDelaySeconds;

    @Value("${nuono.file-management.parse.retry-scheduler.max-items-per-tick:2}")
    private int retrySchedulerMaxItemsPerTick;

    @Value("${nuono.file-management.parse.retry-scheduler.system-operator-user-id:10003}")
    private long retrySchedulerSystemOperatorUserId;

    public LocalDbFileManagementParseService(
            FileManagementParseMapper fileManagementParseMapper,
            FileParseStorageProperties storageProperties,
            FileParseUploadArchiveService uploadArchiveService,
            FileParseInputExtractionService inputExtractionService,
            FileParseStructuredAiService structuredAiService,
            FileParseResultDiffService resultDiffService,
            FileParseResultPersistenceService resultPersistenceService,
            FileParseItemReviewService itemReviewService,
            FileParseQueryViewService queryViewService,
            FileParsePublishService publishService,
            FileParseLogisticsChannelActivationService logisticsChannelActivationService
    ) {
        this.fileManagementParseMapper = fileManagementParseMapper;
        this.storageProperties = storageProperties;
        this.uploadArchiveService = uploadArchiveService;
        this.inputExtractionService = inputExtractionService;
        this.structuredAiService = structuredAiService;
        this.resultDiffService = resultDiffService;
        this.resultPersistenceService = resultPersistenceService;
        this.itemReviewService = itemReviewService;
        this.queryViewService = queryViewService;
        this.publishService = publishService;
        this.logisticsChannelActivationService = logisticsChannelActivationService;
    }

    public List<FileParseTargetPlanSummary> listTargetPlans(AuthenticatedSession session) {
        FileParseUserContext user = requireFileManagementUser(session);
        boolean systemAdmin = isSystemAdmin(user);
        return fileManagementParseMapper.selectVisibleTargetPlans(user.getRoleLevel(), systemAdmin).stream()
                .map(row -> toSummary(row, user))
                .collect(Collectors.toList());
    }

    public FileParseTaskListView listTasks(
            AuthenticatedSession session,
            String keyword,
            Long targetPlanId,
            String status,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        boolean systemAdmin = isSystemAdmin(user);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        String normalizedStatus = normalizeOptionalKeyword(status);
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 20);
        int offset = (page - 1) * pageSize;

        int total = fileManagementParseMapper.countTasks(
                normalizedKeyword,
                targetPlanId,
                normalizedStatus,
                user.getRoleLevel(),
                systemAdmin
        );
        List<FileParseTaskListItemView> items = fileManagementParseMapper
                .selectTasks(
                        normalizedKeyword,
                        targetPlanId,
                        normalizedStatus,
                        user.getRoleLevel(),
                        systemAdmin,
                        pageSize,
                        offset
                )
                .stream()
                .peek(item -> item.setAvailableActions(resolveActions(toTargetPlanRow(item), user)))
                .collect(Collectors.toList());
        enrichTaskListInputs(items);

        FileParseTaskListView view = new FileParseTaskListView();
        view.setTotal(total);
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setItems(items);
        return view;
    }

    private void enrichTaskListInputs(List<FileParseTaskListItemView> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> taskIds = items.stream()
                .map(FileParseTaskListItemView::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, List<FileParseTaskInputView>> inputItemsByTaskId = fileManagementParseMapper
                .selectTaskInputsByTaskIds(taskIds)
                .stream()
                .filter(row -> row.getTaskId() != null)
                .collect(Collectors.groupingBy(
                        FileParseTaskInputRow::getTaskId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toTaskInputView, Collectors.toList())
                ));
        for (FileParseTaskListItemView item : items) {
            item.setInputItems(inputItemsByTaskId.getOrDefault(item.getId(), List.of()));
        }
    }

    public FileParseTaskDetailView getTask(AuthenticatedSession session, Long taskId) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        return toTaskDetailView(task, targetPlan, fileManagementParseMapper.selectTaskInputs(task.getId()));
    }

    @Transactional
    public void deleteTask(AuthenticatedSession session, Long taskId) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireCanCreateTask(targetPlan, user);
        List<Long> versionIds = fileManagementParseMapper.selectVersionIdsBySourceTask(task.getId());
        deletePublishedBusinessResults(versionIds, user.getUserId());
        fileManagementParseMapper.deleteCurrentResultByTask(task.getId());
        fileManagementParseMapper.deleteResultItemSourcesByTask(task.getId());
        fileManagementParseMapper.deleteAiChunksByTask(task.getId());
        fileManagementParseMapper.softDeleteValidationIssuesByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.softDeleteSourceRowsByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.softDeleteItemReviewsByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.softDeleteResultItemsByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.markResultsDeletedByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.softDeleteTaskInputsByTask(task.getId(), user.getUserId());
        fileManagementParseMapper.softDeleteFileAssetsByTask(task.getId(), user.getUserId());
        int affectedRows = fileManagementParseMapper.softDeleteTask(task.getId(), user.getUserId());
        if (affectedRows <= 0) {
            throw new IllegalArgumentException("解析文档不存在或已删除。");
        }
    }

    private void deletePublishedBusinessResults(List<Long> versionIds, Long operatorUserId) {
        if (versionIds == null || versionIds.isEmpty()) {
            return;
        }
        fileManagementParseMapper.softDeleteActiveVersionsByVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsServiceLinesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsCargoCategoriesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsPriceRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsSurchargeRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsBillingRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsWarehouseFeeRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markLogisticsRestrictionRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markOfficialOutboundSizeClassificationRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markOfficialOutboundFeeWeightSlabRulesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.markOfficialOutboundFeeCalculationPoliciesDeletedBySourceVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.softDeleteLogisticsChannelActivationsByVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.softDeleteVersionItemsByVersionIds(versionIds, operatorUserId);
        fileManagementParseMapper.softDeleteVersionsByIds(versionIds, operatorUserId);
    }

    @Transactional
    public FileParseUploadView uploadFile(AuthenticatedSession session, Long targetPlanId, MultipartFile file) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, targetPlanId);
        requireCanCreateTask(targetPlan, user);
        return uploadArchiveService.archive(targetPlan, user.getUserId(), file);
    }

    public FileParseArchivedFile resolveArchivedFile(AuthenticatedSession session, Long fileId) {
        FileParseUserContext user = requireFileManagementUser(session);
        return uploadArchiveService.resolve(fileId, user.getUserId(), isSystemAdmin(user));
    }

    @Transactional
    public FileParseTaskDetailView createTask(
            AuthenticatedSession session,
            FileParseCreateTaskCommand command,
            String idempotencyKey
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        validateCreateTaskCommand(command);
        FileParseTaskRow parentTask = resolveParentTask(user, command);
        Long targetPlanId = command.getTargetPlanId() == null ? parentTask.getTargetPlanId() : command.getTargetPlanId();
        if (parentTask != null && !parentTask.getTargetPlanId().equals(targetPlanId)) {
            throw new IllegalArgumentException("更新源文件必须使用原解析文档的目标输出方案。");
        }
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, targetPlanId);
        requireCanCreateTask(targetPlan, user);

        Long taskId = fileManagementParseMapper.nextTaskId();
        String taskNo = "TASK-" + LocalDate.now().format(TASK_DATE_FORMATTER) + "-" + taskId;
        String documentTitle = trimToLength(command.getDocumentTitle(), 200, "文档名称");
        String remark = trimOptional(command.getRemark(), 1000, "备注");
        String normalizedIdempotencyKey = trimOptional(idempotencyKey, 180, "幂等键");
        Long documentGroupId = resolveDocumentGroupId(parentTask, taskId);
        Integer iterationNo = resolveIterationNo(parentTask, documentGroupId);
        Long parentTaskId = parentTask == null ? null : parentTask.getId();
        String requestHash = sha256Text(documentTitle + "|" + targetPlan.getId() + "|" + parentTaskId + "|" + command.getInputItems().size());

        int inserted = fileManagementParseMapper.insertTask(
                taskId,
                taskNo,
                documentTitle,
                targetPlan.getId(),
                targetPlan.getStandardVersionId(),
                targetPlan.getCurrentVersionId(),
                documentGroupId,
                parentTaskId,
                iterationNo,
                remark,
                normalizedIdempotencyKey,
                requestHash,
                user.getUserId()
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析文档创建失败。");
        }

        List<FileParseTaskInputView> inputViews = new ArrayList<>();
        for (int i = 0; i < command.getInputItems().size(); i++) {
            FileParseTaskInputCommand inputCommand = command.getInputItems().get(i);
            FileParseTaskInputView inputView = createTaskInput(taskId, targetPlan, user, inputCommand, i + 1);
            inputViews.add(inputView);
        }

        FileParseTaskDetailView view = new FileParseTaskDetailView();
        view.setId(taskId);
        view.setTaskNo(taskNo);
        view.setDocumentTitle(documentTitle);
        view.setTargetPlanId(targetPlan.getId());
        view.setTargetPlanCode(targetPlan.getCode());
        view.setTargetPlanLabel(targetPlan.getLabel());
        view.setDocumentType(targetPlan.getDocumentType());
        view.setDocumentName(targetPlan.getDocumentName());
        view.setStandardVersion(targetPlan.getStandardVersion());
        view.setCurrentVersion(targetPlan.getCurrentVersion());
        view.setStatus("reading");
        view.setResultId(null);
        view.setDataScopeType("global");
        view.setDataScopeKey("global:*");
        view.setDocumentGroupId(documentGroupId);
        view.setParentTaskId(parentTaskId);
        view.setIterationNo(iterationNo);
        view.setRemark(remark);
        view.setMessage(parentTaskId == null
                ? "解析文档已创建，文件和文本输入已完成归档，等待后续 AI 解析执行。"
                : "源文件更新任务已创建，本次解析会基于当前生效版本重新对比。");
        view.setInputItems(inputViews);
        return view;
    }

    public FileParseTaskRunView startParseTask(AuthenticatedSession session, Long taskId) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireCanProcess(targetPlan, user);

        if (!"reading".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
            throw new IllegalArgumentException("当前解析文档状态不能发起解析：" + task.getStatus());
        }

        String lockOwner = "file-parse-" + UUID.randomUUID();
        int locked = fileManagementParseMapper.markTaskParsing(task.getId(), lockOwner, user.getUserId());
        if (locked != 1) {
            throw new IllegalStateException("解析任务已被占用或状态已变化，请刷新后重试。");
        }

        Long currentAiChunkId = null;
        try {
            List<FileParseTaskInputRow> inputs = fileManagementParseMapper.selectTaskInputs(task.getId());
            touchTaskParsingLock(task.getId(), lockOwner, user.getUserId());
            fileManagementParseMapper.markOpenAiChunksFailedByTask(task.getId(), user.getUserId());
            fileManagementParseMapper.deleteAiChunksByTask(task.getId());
            fileManagementParseMapper.softDeleteValidationIssuesByTask(task.getId(), user.getUserId());
            fileManagementParseMapper.softDeleteSourceRowsByTask(task.getId(), user.getUserId());
            FileParseExtractionResult extractionResult = inputExtractionService.extract(inputs, storageRoot());
            touchTaskParsingLock(task.getId(), lockOwner, user.getUserId());
            List<FileParsePersistedSourceRow> sourceRows = persistSourceRows(task, extractionResult, user.getUserId());
            if (extractionResult.isRequiresFileAiAdapter()) {
                fileManagementParseMapper.releaseTaskParsingLock(task.getId(), lockOwner, user.getUserId());
                FileParseTaskRunView view = toRunView(task, "parsing", extractionResult.getSummaries());
                view.setMessage("任务已进入解析中，存在需要 AI 文件解析适配器处理的文件输入。");
                return view;
            }

            FileParseStandardVersionRow standardVersion = requireStandardVersion(task.getStandardVersionId());
            List<FileParseItemStandardRow> itemStandards = fileManagementParseMapper.selectItemStandards(standardVersion.getId());
            List<FileParseAiChunkDraft> chunkDrafts = buildAiChunkDrafts(targetPlan, extractionResult, sourceRows);
            List<FileParseAiChunkRun> chunkRuns = new ArrayList<>();
            for (FileParseAiChunkDraft chunkDraft : chunkDrafts) {
                touchTaskParsingLock(task.getId(), lockOwner, user.getUserId());
                currentAiChunkId = createAiChunk(task, chunkDraft, user.getUserId());
                FileParseStructuredAiResult chunkResult = structuredAiService.parse(
                        task,
                        targetPlan,
                        standardVersion,
                        itemStandards,
                        chunkDraft.toExtractionResult(),
                        user.getUserId()
                );
                touchTaskParsingLock(task.getId(), lockOwner, user.getUserId());
                sourceLineagePolicy.attachFallbackSourceRows(chunkResult, chunkDraft.getSourceRowIds());
                chunkRuns.add(new FileParseAiChunkRun(currentAiChunkId, chunkDraft, chunkResult));
            }
            FileParseStructuredAiResult structuredResult = structuredAiService.combineChunkResults(
                    chunkRuns.stream().map(FileParseAiChunkRun::getResult).collect(Collectors.toList())
            );
            List<FileParsePersistedSourceRow> lineageSourceRows = sourceRowsForChunkDrafts(sourceRows, chunkDrafts);
            structuredResult = structuredAiService.stabilizeWithSourceContext(
                    structuredResult,
                    targetPlan,
                    itemStandards,
                    buildChunkText(lineageSourceRows)
            );
            resultDiffService.applyDiff(task, itemStandards, structuredResult);
            touchTaskParsingLock(task.getId(), lockOwner, user.getUserId());
            FileParsePersistedResult persistedResult = resultPersistenceService.persist(
                    task,
                    structuredResult,
                    lockOwner,
                    user.getUserId()
            );
            for (FileParseAiChunkRun chunkRun : chunkRuns) {
                fileManagementParseMapper.markAiChunkSucceeded(
                        chunkRun.getAiChunkId(),
                        task.getId(),
                        persistedResult.getResultId(),
                        chunkRun.getResult().getItems().size(),
                        sha256Text(nullToEmpty(chunkRun.getResult().getRawResultJson())),
                        chunkRun.getResult().getRawResultJson(),
                        user.getUserId()
                );
            }
            persistLineageAndValidationIssues(
                    task,
                    targetPlan,
                    persistedResult.getResultId(),
                    chunkRuns,
                    structuredResult,
                    lineageSourceRows,
                    user.getUserId()
            );
            fileManagementParseMapper.updateTaskStatusAfterReviewFromItems(task.getId(), user.getUserId());
            FileParseTaskRunView view = toRunView(task, "review_required", extractionResult.getSummaries());
            view.setResultId(persistedResult.getResultId());
            view.setResultNo(persistedResult.getResultNo());
            view.setResultItemCount(persistedResult.getItemCount());
            view.setMessage("AI 结构化解析已生成不可变解析结果，等待人工处理。");
            return view;
        } catch (IllegalArgumentException | IOException error) {
            String message = trimFailureMessage(error.getMessage());
            fileManagementParseMapper.markTaskFailed(task.getId(), "INPUT_EXTRACTION_FAILED", message, lockOwner, user.getUserId());
            FileParseTaskRunView view = toRunView(task, "failed", List.of());
            view.setMessage(message);
            return view;
        } catch (FileParseAiParseException error) {
            String message = trimFailureMessage(error.getMessage());
            markAiChunkFailedIfNeeded(currentAiChunkId, task.getId(), error.getCode(), message, user.getUserId());
            fileManagementParseMapper.markOpenAiChunksFailedByTask(task.getId(), user.getUserId());
            if (shouldScheduleRetry(task, error.getCode(), message)) {
                fileManagementParseMapper.markTaskFailedRetryable(
                        task.getId(),
                        error.getCode(),
                        message,
                        lockOwner,
                        retryDelaySeconds(),
                        user.getUserId()
                );
                FileParseTaskRunView view = toRunView(task, "retry_waiting", List.of());
                view.setMessage("AI 服务临时异常，系统会自动重试。");
                return view;
            } else {
                fileManagementParseMapper.markTaskFailed(task.getId(), error.getCode(), message, lockOwner, user.getUserId());
            }
            FileParseTaskRunView view = toRunView(task, "failed", List.of());
            view.setMessage(message);
            return view;
        } catch (RuntimeException error) {
            String message = trimFailureMessage(error.getMessage());
            markAiChunkFailedIfNeeded(currentAiChunkId, task.getId(), "PARSE_START_FAILED", message, user.getUserId());
            fileManagementParseMapper.markOpenAiChunksFailedByTask(task.getId(), user.getUserId());
            fileManagementParseMapper.markTaskFailed(task.getId(), "PARSE_START_FAILED", message, lockOwner, user.getUserId());
            FileParseTaskRunView view = toRunView(task, "failed", List.of());
            view.setMessage(message);
            return view;
        }
    }

    private void touchTaskParsingLock(Long taskId, String lockOwner, Long operatorUserId) {
        if (taskId == null || !StringUtils.hasText(lockOwner)) {
            return;
        }
        fileManagementParseMapper.touchTaskParsingLock(taskId, lockOwner, operatorUserId);
    }

    @Scheduled(
            fixedDelayString = "${nuono.file-management.parse.retry-scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${nuono.file-management.parse.retry-scheduler.initial-delay-ms:30000}"
    )
    public void recoverStaleParsingTasksOnSchedule() {
        if (!retrySchedulerEnabled) {
            return;
        }
        recoverStaleParsingTasks(
                retrySchedulerMaxItemsPerTick,
                retrySchedulerStaleTimeoutSeconds,
                retrySchedulerMaxAttempts,
                retrySchedulerSystemOperatorUserId
        );
    }

    int recoverStaleParsingTasks(
            int maxItems,
            int staleTimeoutSeconds,
            int maxAttempts,
            long operatorUserId
    ) {
        int normalizedMaxItems = Math.max(1, Math.min(maxItems, 20));
        int normalizedStaleTimeoutSeconds = Math.max(60, staleTimeoutSeconds);
        int normalizedMaxAttempts = Math.max(1, maxAttempts);
        Long normalizedOperatorUserId = operatorUserId > 0 ? operatorUserId : null;
        if (normalizedOperatorUserId == null) {
            return 0;
        }

        List<FileParseTaskRow> staleTasks = fileManagementParseMapper.selectStaleParsingTasks(
                normalizedStaleTimeoutSeconds,
                normalizedMaxItems
        );
        int recovered = 0;
        if (staleTasks == null) {
            staleTasks = List.of();
        }
        for (FileParseTaskRow staleTask : staleTasks) {
            if (recoverOneStaleParsingTask(
                    staleTask,
                    normalizedStaleTimeoutSeconds,
                    normalizedMaxAttempts,
                    normalizedOperatorUserId
            )) {
                recovered++;
            }
        }
        int remaining = normalizedMaxItems - recovered;
        if (remaining <= 0) {
            return recovered;
        }
        List<FileParseTaskRow> retryableTasks = fileManagementParseMapper.selectRetryableFailedParseTasks(remaining);
        if (retryableTasks == null) {
            return recovered;
        }
        for (FileParseTaskRow retryableTask : retryableTasks) {
            if (recoverOneRetryableFailedTask(
                    retryableTask,
                    normalizedMaxAttempts,
                    normalizedOperatorUserId
            )) {
                recovered++;
                if (recovered >= normalizedMaxItems) {
                    break;
                }
            }
        }
        return recovered;
    }

    private boolean recoverOneStaleParsingTask(
            FileParseTaskRow staleTask,
            int staleTimeoutSeconds,
            int maxAttempts,
            Long operatorUserId
    ) {
        if (staleTask == null || staleTask.getId() == null) {
            return false;
        }
        FileParseWorkflowStatePolicy.StaleRecoveryDecision decision = workflowStatePolicy.staleRecoveryDecision(
                staleTask.getParseAttemptCount(),
                maxAttempts,
                staleTimeoutSeconds
        );
        fileManagementParseMapper.markOpenAiChunksFailedByTask(staleTask.getId(), operatorUserId);
        if (decision.isFinalFailure()) {
            return fileManagementParseMapper.markStaleParsingTaskFinalFailed(
                    staleTask.getId(),
                    staleTimeoutSeconds,
                    decision.getFailureCode(),
                    decision.getFailureMessage(),
                    operatorUserId
            ) == 1;
        }

        int reset = fileManagementParseMapper.resetStaleParsingTaskForRetry(
                staleTask.getId(),
                staleTimeoutSeconds,
                decision.getFailureCode(),
                decision.getFailureMessage(),
                operatorUserId
        );
        if (reset != 1) {
            return false;
        }

        try {
            FileParseTaskRunView runView = startParseTask(new AuthenticatedSession(operatorUserId, 1L, 0), staleTask.getId());
            return runView == null || !"failed".equals(runView.getStatus());
        } catch (RuntimeException error) {
            fileManagementParseMapper.markAutoRetryDispatchFailed(
                    staleTask.getId(),
                    "PARSE_AUTO_RETRY_DISPATCH_FAILED",
                    trimFailureMessage(error.getMessage()),
                    operatorUserId
            );
            return false;
        }
    }

    private boolean recoverOneRetryableFailedTask(
            FileParseTaskRow retryableTask,
            int maxAttempts,
            Long operatorUserId
    ) {
        if (retryableTask == null || retryableTask.getId() == null) {
            return false;
        }
        FileParseWorkflowStatePolicy.RetryableFailureRecoveryDecision decision = workflowStatePolicy.retryableFailureDecision(
                retryableTask.getParseAttemptCount(),
                maxAttempts,
                retryableTask.getFailureMessage()
        );
        if (decision.isFinalFailure()) {
            return fileManagementParseMapper.markRetryableParseTaskFinalFailed(
                    retryableTask.getId(),
                    decision.getFailureCode(),
                    decision.getFailureMessage(),
                    operatorUserId
            ) == 1;
        }

        try {
            FileParseTaskRunView runView = startParseTask(new AuthenticatedSession(operatorUserId, 1L, 0), retryableTask.getId());
            return runView == null || !"failed".equals(runView.getStatus());
        } catch (RuntimeException error) {
            fileManagementParseMapper.markAutoRetryDispatchFailed(
                    retryableTask.getId(),
                    "PARSE_AUTO_RETRY_DISPATCH_FAILED",
                    trimFailureMessage(error.getMessage()),
                    operatorUserId
            );
            return false;
        }
    }

    private boolean shouldScheduleRetry(FileParseTaskRow task, String failureCode, String failureMessage) {
        Integer previousAttempts = task == null ? null : task.getParseAttemptCount();
        return workflowStatePolicy.shouldScheduleRetry(
                previousAttempts,
                failureCode,
                failureMessage,
                retrySchedulerMaxAttempts
        );
    }

    private int retryDelaySeconds() {
        return workflowStatePolicy.retryDelaySeconds(retrySchedulerRetryDelaySeconds);
    }

    private List<FileParsePersistedSourceRow> persistSourceRows(
            FileParseTaskRow task,
            FileParseExtractionResult extractionResult,
            Long operatorUserId
    ) {
        List<FileParsePersistedSourceRow> sourceRows = new ArrayList<>();
        if (extractionResult == null) {
            return sourceRows;
        }
        for (FileParseSourceRowDraft sourceRow : extractionResult.getSourceRows()) {
            Long sourceRowId = fileManagementParseMapper.nextSourceRowId();
            int inserted = fileManagementParseMapper.insertSourceRow(sourceRowId, task.getId(), sourceRow, operatorUserId);
            if (inserted != 1) {
                throw new IllegalStateException("源内容行写入失败。");
            }
            sourceRows.add(new FileParsePersistedSourceRow(sourceRowId, sourceRow));
        }
        return sourceRows;
    }

    private Long createAiChunk(
            FileParseTaskRow task,
            FileParseAiChunkDraft chunkDraft,
            Long operatorUserId
    ) {
        Long aiChunkId = fileManagementParseMapper.nextAiChunkId();
        String inputText = chunkDraft == null ? "" : nullToEmpty(chunkDraft.getCombinedText());
        int attachmentCount = chunkDraft == null ? 0 : chunkDraft.getAttachments().size();
        int sourceRowCount = chunkDraft == null ? 0 : chunkDraft.getSourceRowIds().size();
        int inserted = fileManagementParseMapper.insertAiChunk(
                aiChunkId,
                task.getId(),
                chunkDraft == null ? 1 : chunkDraft.getChunkNo(),
                chunkDraft == null ? "source_rows" : chunkDraft.getChunkType(),
                longListJson(chunkDraft == null ? List.of() : chunkDraft.getSourceRowIds()),
                sourceRowCount,
                sha256Text(task.getTargetPlanId() + "|" + task.getStandardVersionId() + "|" + task.getDocumentTitle() + "|chunk=" + (chunkDraft == null ? 1 : chunkDraft.getChunkNo())),
                sha256Text(inputText + "|attachments=" + attachmentCount),
                "openai-compatible",
                structuredAiService.getConfiguredModel(),
                operatorUserId
        );
        if (inserted != 1) {
            throw new IllegalStateException("AI 分块记录写入失败。");
        }
        return aiChunkId;
    }

    private List<FileParseAiChunkDraft> buildAiChunkDrafts(
            FileParseTargetPlanRow targetPlan,
            FileParseExtractionResult extractionResult,
            List<FileParsePersistedSourceRow> sourceRows
    ) {
        List<FileParseInputAttachment> attachments = extractionResult == null ? List.of() : extractionResult.getAttachments();
        List<FileParsePersistedSourceRow> scopedSourceRows = scopeSourceRowsForTargetPlan(targetPlan, sourceRows);
        if (scopedSourceRows == null || scopedSourceRows.isEmpty()) {
            return List.of(new FileParseAiChunkDraft(
                    1,
                    attachments.isEmpty() ? "raw_text" : "attachment",
                    extractionResult == null ? "" : nullToEmpty(extractionResult.getCombinedText()),
                    List.of(),
                    attachments
            ));
        }
        if (!attachments.isEmpty()) {
            return List.of(new FileParseAiChunkDraft(
                    1,
                    "source_rows_with_attachments",
                    buildChunkText(scopedSourceRows),
                    scopedSourceRows.stream().map(FileParsePersistedSourceRow::getId).collect(Collectors.toList()),
                    attachments
            ));
        }

        List<FileParseAiChunkDraft> chunks = new ArrayList<>();
        List<FileParsePersistedSourceRow> buffer = new ArrayList<>();
        List<FileParsePersistedSourceRow> contextRows = List.of();
        int charCount = 0;
        for (FileParsePersistedSourceRow sourceRow : scopedSourceRows) {
            String rowText = buildSourceRowText(sourceRow);
            boolean shouldFlush = !buffer.isEmpty()
                    && (buffer.size() >= MAX_AI_CHUNK_SOURCE_ROWS
                    || charCount + rowText.length() > MAX_AI_CHUNK_TEXT_LENGTH);
            if (shouldFlush) {
                chunks.add(toChunkDraft(chunks.size() + 1, contextRows, buffer));
                contextRows = trailingSourceRows(buffer, AI_CHUNK_CONTEXT_SOURCE_ROWS);
                buffer = new ArrayList<>();
                charCount = 0;
            }
            buffer.add(sourceRow);
            charCount += rowText.length();
        }
        if (!buffer.isEmpty()) {
            chunks.add(toChunkDraft(chunks.size() + 1, contextRows, buffer));
        }
        return chunks;
    }

    private List<FileParsePersistedSourceRow> scopeSourceRowsForTargetPlan(
            FileParseTargetPlanRow targetPlan,
            List<FileParsePersistedSourceRow> sourceRows
    ) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return sourceRows;
        }
        if (isOutboundFeePlan(targetPlan)) {
            return outboundFeeSourceScopePolicy.outboundFeeRows(
                    targetPlan,
                    sourceRows,
                    sourceRow -> sourceRow == null || sourceRow.getRow() == null ? "" : nullToEmpty(sourceRow.getRow().getRawText()),
                    sourceRow -> sourceRow == null || sourceRow.getRow() == null ? "" : nullToEmpty(sourceRow.getRow().getSourceType()),
                    sourceRow -> sourceRow == null || sourceRow.getRow() == null ? "" : nullToEmpty(sourceRow.getRow().getSheetName())
            );
        }
        if (!isCommissionPlan(targetPlan)) {
            return sourceRows;
        }
        List<FileParsePersistedSourceRow> referralFeeRows = commissionSourceScopePolicy.referralFeeSectionRows(
                sourceRows,
                sourceRow -> sourceRow == null || sourceRow.getRow() == null ? "" : nullToEmpty(sourceRow.getRow().getRawText())
        );
        return referralFeeRows.isEmpty() ? sourceRows : referralFeeRows;
    }

    private List<FileParsePersistedSourceRow> sourceRowsForChunkDrafts(
            List<FileParsePersistedSourceRow> sourceRows,
            List<FileParseAiChunkDraft> chunkDrafts
    ) {
        if (sourceRows == null || sourceRows.isEmpty() || chunkDrafts == null || chunkDrafts.isEmpty()) {
            return sourceRows;
        }
        Set<Long> chunkSourceRowIds = chunkDrafts.stream()
                .flatMap(chunk -> chunk.getSourceRowIds().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (chunkSourceRowIds.isEmpty()) {
            return sourceRows;
        }
        return sourceRows.stream()
                .filter(sourceRow -> sourceRow != null && chunkSourceRowIds.contains(sourceRow.getId()))
                .collect(Collectors.toList());
    }

    private List<FileParsePersistedSourceRow> trailingSourceRows(List<FileParsePersistedSourceRow> sourceRows, int maxRows) {
        if (sourceRows == null || sourceRows.isEmpty() || maxRows <= 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, sourceRows.size() - maxRows);
        return new ArrayList<>(sourceRows.subList(fromIndex, sourceRows.size()));
    }

    private FileParseAiChunkDraft toChunkDraft(
            int chunkNo,
            List<FileParsePersistedSourceRow> contextRows,
            List<FileParsePersistedSourceRow> sourceRows
    ) {
        List<FileParsePersistedSourceRow> combinedRows = new ArrayList<>();
        if (contextRows != null && !contextRows.isEmpty()) {
            combinedRows.addAll(contextRows);
        }
        combinedRows.addAll(sourceRows);
        return new FileParseAiChunkDraft(
                chunkNo,
                "source_rows",
                buildChunkText(combinedRows),
                sourceRows.stream().map(FileParsePersistedSourceRow::getId).collect(Collectors.toList()),
                List.of()
        );
    }

    private String buildChunkText(List<FileParsePersistedSourceRow> sourceRows) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return "";
        }
        return sourceRows.stream()
                .map(this::buildSourceRowText)
                .collect(Collectors.joining("\n"));
    }

    private String buildSourceRowText(FileParsePersistedSourceRow sourceRow) {
        FileParseSourceRowDraft row = sourceRow.getRow();
        String locator = StringUtils.hasText(row.getSourceLocator()) ? row.getSourceLocator() : "sort=" + row.getSortNo();
        return "[[SOURCE_ROW_ID=" + sourceRow.getId()
                + ";TYPE=" + nullToEmpty(row.getSourceType())
                + ";LOC=" + locator + "]]\n"
                + nullToEmpty(row.getRawText());
    }

    private void persistLineageAndValidationIssues(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            Long resultId,
            List<FileParseAiChunkRun> chunkRuns,
            FileParseStructuredAiResult structuredResult,
            List<FileParsePersistedSourceRow> sourceRows,
            Long operatorUserId
    ) {
        List<FileParseResultItemRow> resultItems = fileManagementParseMapper.selectResultItems(
                resultId,
                null,
                null,
                10_000,
                0
        );
        Map<Integer, List<Long>> sourceRowIdsBySortNo = new LinkedHashMap<>();
        if (structuredResult != null) {
            for (FileParseStructuredItem item : structuredResult.getItems()) {
                sourceRowIdsBySortNo.put(item.getSortNo(), item.getSourceRowIds());
            }
        }
        Map<Long, Long> aiChunkIdBySourceRowId = sourceLineagePolicy.aiChunkIdBySourceRowId(
                chunkRuns == null
                        ? List.of()
                        : chunkRuns.stream()
                        .map(chunkRun -> new FileParseSourceLineagePolicy.AiChunkSourceRows(
                                chunkRun.getAiChunkId(),
                                chunkRun.getChunkDraft().getSourceRowIds()
                        ))
                        .collect(Collectors.toList())
        );
        Set<Long> persistedSourceRowIds = sourceRows == null
                ? Set.of()
                : sourceRows.stream().map(FileParsePersistedSourceRow::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!persistedSourceRowIds.isEmpty()) {
            for (FileParseResultItemRow item : resultItems) {
                List<Long> itemSourceRowIds = sourceRowIdsBySortNo
                        .getOrDefault(item.getSortNo(), List.of())
                        .stream()
                        .filter(persistedSourceRowIds::contains)
                        .distinct()
                        .collect(Collectors.toList());
                for (Long sourceRowId : itemSourceRowIds) {
                    if (sourceRowId == null) {
                        continue;
                    }
                    String evidence = trimOptional(item.getNaturalKey(), 1000, "来源证据");
                    fileManagementParseMapper.insertResultItemSource(
                            fileManagementParseMapper.nextResultItemSourceId(),
                            task.getId(),
                            resultId,
                            item.getId(),
                            sourceRowId,
                            aiChunkIdBySourceRowId.get(sourceRowId),
                            "primary",
                            item.getConfidence(),
                            evidence,
                            operatorUserId
                    );
                }
            }
        }
        int sourceRowCount = sourceRows == null ? 0 : sourceRows.size();
        int aiOutputItemCount = chunkRuns == null
                ? resultItems.size()
                : chunkRuns.stream().mapToInt(chunk -> chunk.getResult().getItems().size()).sum();
        Long firstAiChunkId = chunkRuns == null || chunkRuns.isEmpty() ? null : chunkRuns.get(0).getAiChunkId();
        persistCoverageIssueIfNeeded(task, targetPlan, resultId, firstAiChunkId, sourceRowCount, aiOutputItemCount, operatorUserId);
        persistLogisticsCoverageIssues(
                task,
                targetPlan,
                resultId,
                structuredResult,
                sourceRows,
                aiChunkIdBySourceRowId,
                firstAiChunkId,
                operatorUserId
        );
        for (FileParseResultItemRow item : resultItems) {
            if ("hard_error".equals(item.getValidationStatus()) || StringUtils.hasText(item.getValidationErrorJson())) {
                fileManagementParseMapper.insertValidationIssue(
                        fileManagementParseMapper.nextValidationIssueId(),
                        task.getId(),
                        resultId,
                        item.getId(),
                        null,
                        sourceLineagePolicy.resolveAiChunkIdForSourceRows(
                                sourceRowIdsBySortNo.getOrDefault(item.getSortNo(), List.of()),
                                aiChunkIdBySourceRowId
                        ),
                        "schema_error",
                        "hard_error".equals(item.getValidationStatus()) ? "hard_error" : "warning",
                        null,
                        "解析结果行存在校验问题，请在解析处理页修正。",
                        item.getValidationErrorJson(),
                        operatorUserId
                );
            }
        }
    }

    private void persistLogisticsCoverageIssues(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            Long resultId,
            FileParseStructuredAiResult structuredResult,
            List<FileParsePersistedSourceRow> sourceRows,
            Map<Long, Long> aiChunkIdBySourceRowId,
            Long fallbackAiChunkId,
            Long operatorUserId
    ) {
        List<FileParseLogisticsCoverageValidator.CoverageIssue> issues = logisticsCoverageValidator.validate(
                targetPlan,
                structuredResult == null ? List.of() : structuredResult.getItems(),
                toLogisticsSourceEvidence(sourceRows)
        );
        for (FileParseLogisticsCoverageValidator.CoverageIssue issue : issues) {
            Long sourceRowId = issue.getSourceRowId();
            Long aiChunkId = sourceRowId == null ? fallbackAiChunkId : aiChunkIdBySourceRowId.getOrDefault(sourceRowId, fallbackAiChunkId);
            fileManagementParseMapper.insertValidationIssue(
                    fileManagementParseMapper.nextValidationIssueId(),
                    task.getId(),
                    resultId,
                    null,
                    sourceRowId,
                    aiChunkId,
                    issue.getCode(),
                    issue.getSeverity(),
                    null,
                    issue.getMessage(),
                    issue.getDetailsJson(),
                    operatorUserId
            );
        }
    }

    private List<FileParseLogisticsCoverageValidator.SourceRowEvidence> toLogisticsSourceEvidence(
            List<FileParsePersistedSourceRow> sourceRows
    ) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }
        List<FileParseLogisticsCoverageValidator.SourceRowEvidence> evidence = new ArrayList<>();
        for (FileParsePersistedSourceRow sourceRow : sourceRows) {
            if (sourceRow == null || sourceRow.getRow() == null) {
                continue;
            }
            evidence.add(new FileParseLogisticsCoverageValidator.SourceRowEvidence(
                    sourceRow.getId(),
                    sourceRow.getRow().getSourceType(),
                    sourceRow.getRow().getRawText()
            ));
        }
        return evidence;
    }

    private void persistCoverageIssueIfNeeded(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            Long resultId,
            Long aiChunkId,
            int sourceRowCount,
            int resultItemCount,
            Long operatorUserId
    ) {
        if (sourceRowCount <= 0) {
            return;
        }
        if (resultItemCount <= 0) {
            fileManagementParseMapper.insertValidationIssue(
                    fileManagementParseMapper.nextValidationIssueId(),
                    task.getId(),
                    resultId,
                    null,
                    null,
                    aiChunkId,
                    "coverage_missing",
                    "hard_error",
                    null,
                    "源内容已抽取，但 AI 未返回任何可落库结果。",
                    "{\"sourceRows\":" + sourceRowCount + ",\"resultItems\":" + resultItemCount + "}",
                    operatorUserId
            );
            return;
        }
        int divisor = isCommissionPlan(targetPlan) ? 6 : 2;
        if (resultItemCount < Math.max(1, sourceRowCount / divisor)) {
            fileManagementParseMapper.insertValidationIssue(
                    fileManagementParseMapper.nextValidationIssueId(),
                    task.getId(),
                    resultId,
                    null,
                    null,
                    aiChunkId,
                    "coverage_missing",
                    "warning",
                    null,
                    "AI 输出数量明显少于源内容行，请检查是否存在漏读。",
                    "{\"sourceRows\":" + sourceRowCount + ",\"resultItems\":" + resultItemCount + "}",
                    operatorUserId
            );
        }
    }

    private void markAiChunkFailedIfNeeded(
            Long aiChunkId,
            Long taskId,
            String failureCode,
            String failureMessage,
            Long operatorUserId
    ) {
        if (aiChunkId == null) {
            return;
        }
        fileManagementParseMapper.markAiChunkFailed(
                aiChunkId,
                taskId,
                trimOptional(failureCode, 100, "失败码"),
                trimOptional(failureMessage, 1000, "失败说明"),
                operatorUserId
        );
    }

    public FileParseProcessingItemsView listProcessingItems(
            AuthenticatedSession session,
            Long taskId,
            String reviewStatus,
            String changeType,
            Integer page,
            Integer pageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        return itemReviewService.listProcessingItems(
                task,
                loadItemStandards(task),
                reviewStatus,
                changeType,
                page,
                pageSize
        );
    }

    public FileParseSourceRowsView listSourceRows(
            AuthenticatedSession session,
            Long taskId,
            Long inputId,
            String sourceType,
            String keyword,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 100);
        int offset = (page - 1) * pageSize;
        String normalizedSourceType = normalizeOptionalKeyword(sourceType);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        FileParseSourceRowsView view = new FileParseSourceRowsView();
        view.setTaskId(task.getId());
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setTotal(fileManagementParseMapper.countSourceRows(task.getId(), inputId, normalizedSourceType, normalizedKeyword));
        view.setItems(fileManagementParseMapper.selectSourceRows(
                task.getId(),
                inputId,
                normalizedSourceType,
                normalizedKeyword,
                pageSize,
                offset
        ));
        return view;
    }

    public FileParseWorkflowView getWorkflow(AuthenticatedSession session, Long taskId) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        int extractedSourceRows = fileManagementParseMapper.countSourceRowsByTask(task.getId());
        int aiInputSourceRows = fileManagementParseMapper.sumAiChunkSourceRows(task.getId());
        int sourceRows = isCommissionPlan(targetPlan) && aiInputSourceRows > 0
                ? aiInputSourceRows
                : extractedSourceRows;
        int aiOutputItems = fileManagementParseMapper.sumAiChunkOutputItems(task.getId());
        int processedSourceRows = fileManagementParseMapper.sumSucceededAiChunkSourceRows(task.getId());
        int hardIssues = fileManagementParseMapper.countOpenHardValidationIssues(task.getId());
        int resultItems = task.getCurrentResultId() == null
                ? 0
                : fileManagementParseMapper.countResultItems(task.getCurrentResultId(), null, null);

        FileParseWorkflowCoverageView coverage = new FileParseWorkflowCoverageView();
        coverage.setSourceRows(sourceRows);
        coverage.setProcessedSourceRows(Math.min(sourceRows, Math.max(processedSourceRows, Math.max(aiOutputItems, resultItems))));
        coverage.setUnprocessedSourceRows(Math.max(0, sourceRows - coverage.getProcessedSourceRows()));
        coverage.setResultItems(resultItems);
        coverage.setHardErrors(hardIssues);

        FileParseWorkflowView view = new FileParseWorkflowView();
        view.setTaskId(task.getId());
        view.setStatus(task.getStatus());
        view.setCoverage(coverage);
        view.setSteps(List.of(
                new FileParseWorkflowStepView("file_reading", "文件读取", stepStatus(task, "reading", extractedSourceRows > 0), extractedSourceRows),
                new FileParseWorkflowStepView("source_extract", "源内容抽取", extractedSourceRows > 0 ? "succeeded" : stepStatus(task, "parsing", false), extractedSourceRows),
                new FileParseWorkflowStepView("ai_parse", "AI解析", aiOutputItems > 0 ? "succeeded" : stepStatus(task, "parsing", false), aiOutputItems),
                new FileParseWorkflowStepView("validation", "规则校验", hardIssues > 0 ? "hard_error" : (resultItems > 0 ? "succeeded" : "pending"), hardIssues),
                new FileParseWorkflowStepView("diff", "版本对比", resultItems > 0 ? "succeeded" : "pending", resultItems)
        ));
        return view;
    }

    public FileParseAiChunksView listAiChunks(
            AuthenticatedSession session,
            Long taskId,
            String status,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireSystemAdminForAiChunks(user);
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 50);
        int offset = (page - 1) * pageSize;
        String normalizedStatus = normalizeOptionalKeyword(status);
        FileParseAiChunksView view = new FileParseAiChunksView();
        view.setTaskId(task.getId());
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setTotal(fileManagementParseMapper.countAiChunks(task.getId(), normalizedStatus));
        view.setItems(fileManagementParseMapper.selectAiChunks(task.getId(), normalizedStatus, pageSize, offset));
        return view;
    }

    public FileParseValidationIssuesView listValidationIssues(
            AuthenticatedSession session,
            Long taskId,
            String severity,
            String issueType,
            String resolvedStatus,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 100);
        int offset = (page - 1) * pageSize;
        String normalizedSeverity = normalizeOptionalKeyword(severity);
        String normalizedIssueType = normalizeOptionalKeyword(issueType);
        String normalizedResolvedStatus = normalizeOptionalKeyword(resolvedStatus);
        FileParseValidationIssuesView view = new FileParseValidationIssuesView();
        view.setTaskId(task.getId());
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setTotal(fileManagementParseMapper.countValidationIssues(
                task.getId(),
                normalizedSeverity,
                normalizedIssueType,
                normalizedResolvedStatus
        ));
        view.setItems(fileManagementParseMapper.selectValidationIssues(
                task.getId(),
                normalizedSeverity,
                normalizedIssueType,
                normalizedResolvedStatus,
                pageSize,
                offset
        ));
        return view;
    }

    public FileParseItemCompareView compareResultItem(
            AuthenticatedSession session,
            Long taskId,
            Long itemId
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        return itemReviewService.compareItem(task, itemId);
    }

    public FileParseProcessingItemView reviewResultItem(
            AuthenticatedSession session,
            Long taskId,
            Long itemId,
            String action,
            FileParseReviewCommand command,
            String idempotencyKey
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireCanProcess(targetPlan, user);
        return itemReviewService.reviewItem(
                task,
                loadItemStandards(task),
                itemId,
                action,
                command,
                idempotencyKey,
                user.getUserId()
        );
    }

    public FileParseBatchReviewView batchAcceptResultItems(
            AuthenticatedSession session,
            Long taskId,
            FileParseBatchReviewCommand command,
            String idempotencyKey
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireCanProcess(targetPlan, user);
        return itemReviewService.acceptItems(
                task,
                loadItemStandards(task),
                command,
                idempotencyKey,
                user.getUserId()
        );
    }

    public FileParseOverviewItemsView listOverviewItems(
            AuthenticatedSession session,
            Long taskId,
            Integer page,
            Integer pageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        return queryViewService.listOverviewItems(task, loadItemStandards(task), page, pageSize);
    }

    public FileParseExportFile exportOverviewItems(
            AuthenticatedSession session,
            Long taskId
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        requireVisibleTargetPlan(user, task.getTargetPlanId());
        FileParseExportFile exportFile = queryViewService.exportOverviewItems(task, loadItemStandards(task));
        Long auditId = fileManagementParseMapper.nextAuditLogId();
        fileManagementParseMapper.insertAuditLog(
                auditId,
                task.getId(),
                task.getTargetPlanId(),
                null,
                "export_overview_items",
                "导出解析总览：" + exportFile.getFileName(),
                null,
                null,
                user.getUserId()
        );
        return exportFile;
    }

    public FileParseVersionListView listVersions(
            AuthenticatedSession session,
            Long targetPlanId,
            Integer page,
            Integer pageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        requireVisibleTargetPlan(user, targetPlanId);
        return queryViewService.listVersions(targetPlanId, page, pageSize);
    }

    public FileParseVersionItemsView listVersionItems(
            AuthenticatedSession session,
            Long versionId,
            Integer page,
            Integer pageSize
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseVersionSummaryRow version = requireVersion(versionId);
        requireVisibleTargetPlan(user, version.getTargetPlanId());
        List<FileParseItemStandardRow> itemStandards = fileManagementParseMapper.selectItemStandards(version.getStandardVersionId());
        return queryViewService.listVersionItems(version, itemStandards, page, pageSize);
    }

    public FileParsePublishView publishTask(
            AuthenticatedSession session,
            Long taskId,
            FileParsePublishCommand command,
            String idempotencyKey
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTaskRow task = requireTask(taskId);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, task.getTargetPlanId());
        requireCanPublish(targetPlan, user);
        return publishService.publish(
                task,
                targetPlan,
                loadItemStandards(task),
                command,
                idempotencyKey,
                user.getUserId()
        );
    }

    public FileParseLogisticsChannelActivationView listLogisticsChannelActivations(
            AuthenticatedSession session,
            Long targetPlanId,
            Long versionId
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, targetPlanId);
        requireLogisticsTargetPlan(targetPlan);
        FileParseVersionSummaryRow version = requireLogisticsVersion(targetPlan, versionId);
        return logisticsChannelActivationService.listActivations(
                targetPlan,
                version,
                resolveLogisticsActivationOwner(user)
        );
    }

    @Transactional
    public FileParseLogisticsChannelActivationView saveLogisticsChannelActivations(
            AuthenticatedSession session,
            FileParseLogisticsChannelActivationCommand command
    ) {
        FileParseUserContext user = requireFileManagementUser(session);
        validateLogisticsActivationCommand(command);
        FileParseTargetPlanRow targetPlan = requireVisibleTargetPlan(user, command.getTargetPlanId());
        requireLogisticsTargetPlan(targetPlan);
        requireCanActivateLogisticsChannels(targetPlan, user);
        FileParseVersionSummaryRow version = requireLogisticsVersion(targetPlan, command.getVersionId());
        FileParseLogisticsChannelActivationView view = logisticsChannelActivationService.saveActivations(
                targetPlan,
                version,
                resolveLogisticsActivationOwner(user),
                command.getSelectedChannelKeys(),
                user.getUserId()
        );
        Long auditId = fileManagementParseMapper.nextAuditLogId();
        fileManagementParseMapper.insertAuditLog(
                auditId,
                null,
                targetPlan.getId(),
                version.getId(),
                "save_logistics_channel_activation",
                "保存物流渠道生效选择：" + view.getSelectedChannelKeys().size() + " 个渠道",
                null,
                null,
                user.getUserId()
        );
        return view;
    }

    private FileParseTaskInputView createTaskInput(
            Long taskId,
            FileParseTargetPlanRow targetPlan,
            FileParseUserContext user,
            FileParseTaskInputCommand inputCommand,
            int defaultSortNo
    ) {
        if (inputCommand == null) {
            throw new IllegalArgumentException("输入项不能为空。");
        }

        FileParseFileAssetRow asset = null;
        String inputType = normalizeInputType(inputCommand);
        if (FILE_INPUT_TYPES.contains(inputType)) {
            asset = requireUsableInputFile(user, targetPlan, taskId, inputCommand.getFileAssetId());
            inputType = normalizeFileInputType(inputType, asset.getFileExtension());
        } else if (TEXT_INPUT_TYPES.contains(inputType)) {
            if (!StringUtils.hasText(inputCommand.getTextContent())) {
                throw new IllegalArgumentException("文本输入内容不能为空。");
            }
        } else {
            throw new IllegalArgumentException("不支持的输入类型：" + inputType);
        }

        String inputRole = normalizeInputRole(inputCommand.getInputRole());
        String textContent = TEXT_INPUT_TYPES.contains(inputType)
                ? trimToLength(inputCommand.getTextContent(), 20000, "文本输入内容")
                : trimOptional(inputCommand.getTextContent(), 20000, "文本输入内容");
        String displayName = normalizeDisplayName(inputCommand, asset);
        Integer sortNo = inputCommand.getSortNo() == null ? defaultSortNo : Math.max(inputCommand.getSortNo(), 0);
        Long inputId = fileManagementParseMapper.nextTaskInputId();

        int inserted = fileManagementParseMapper.insertTaskInput(
                inputId,
                taskId,
                inputType,
                inputRole,
                asset == null ? null : asset.getId(),
                textContent,
                displayName,
                sortNo,
                user.getUserId()
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析文档输入项创建失败。");
        }

        FileParseTaskInputView view = new FileParseTaskInputView();
        view.setId(inputId);
        view.setInputType(inputType);
        view.setInputRole(inputRole);
        view.setFileAssetId(asset == null ? null : asset.getId());
        view.setDisplayName(displayName);
        view.setDownloadUrl(asset == null ? null : uploadArchiveService.downloadUrl(asset.getId()));
        view.setSortNo(sortNo);
        return view;
    }

    private FileParseFileAssetRow requireUsableInputFile(
            FileParseUserContext user,
            FileParseTargetPlanRow targetPlan,
            Long taskId,
            Long fileAssetId
    ) {
        if (fileAssetId == null) {
            throw new IllegalArgumentException("文件输入项必须先上传文件。");
        }
        FileParseFileAssetRow asset = fileManagementParseMapper.selectFileAsset(fileAssetId);
        if (asset == null) {
            throw new IllegalArgumentException("上传文件不存在或已删除。");
        }
        if (!targetPlan.getId().equals(asset.getTargetPlanId())) {
            throw new IllegalArgumentException("上传文件与目标输出方案不匹配。");
        }
        if (!isSystemAdmin(user) && !user.getUserId().equals(asset.getUploadedBy())) {
            throw new FileParseAccessDeniedException("当前账号不能使用该上传文件。");
        }
        if (asset.getExpiresAt() != null && asset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("上传文件已过期，请重新上传。");
        }
        if (asset.getBoundTaskId() != null && !taskId.equals(asset.getBoundTaskId())) {
            throw new IllegalArgumentException("上传文件已被其它解析文档使用。");
        }
        int updated = fileManagementParseMapper.bindFileAssetToTask(asset.getId(), taskId, user.getUserId());
        if (updated != 1) {
            throw new IllegalArgumentException("上传文件已被其它解析文档使用。");
        }
        return asset;
    }

    private FileParseUserContext requireFileManagementUser(AuthenticatedSession session) {
        FileParseUserContext user = requireActiveUser(session);
        if (!isSystemAdmin(user) && fileManagementParseMapper.countActiveUserMenu(user.getUserId(), FILE_MANAGEMENT_MENU_ID) <= 0) {
            throw new FileParseAccessDeniedException("当前账号没有文件管理入口权限。");
        }
        return user;
    }

    private FileParseUserContext requireActiveUser(AuthenticatedSession session) {
        if (session == null || session.getUserId() == null) {
            throw new FileParseAccessDeniedException("请先登录后再继续操作。");
        }
        FileParseUserContext user = fileManagementParseMapper.selectUserContext(session.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new FileParseAccessDeniedException("当前账号不存在或已停用。");
        }
        return user;
    }

    private FileParseTaskRow requireTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("解析文档 ID 不能为空。");
        }
        FileParseTaskRow task = fileManagementParseMapper.selectTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("解析文档不存在或已删除。");
        }
        return task;
    }

    private FileParseTaskRow resolveParentTask(FileParseUserContext user, FileParseCreateTaskCommand command) {
        if (command.getParentTaskId() == null) {
            return null;
        }
        FileParseTaskRow parentTask = requireTask(command.getParentTaskId());
        requireVisibleTargetPlan(user, parentTask.getTargetPlanId());
        if ("reading".equals(parentTask.getStatus()) || "parsing".equals(parentTask.getStatus())) {
            throw new IllegalArgumentException("原解析文档仍在解析中，不能更新源文件。");
        }
        return parentTask;
    }

    private Long resolveDocumentGroupId(FileParseTaskRow parentTask, Long taskId) {
        if (parentTask == null) {
            return taskId;
        }
        return parentTask.getDocumentGroupId() == null ? parentTask.getId() : parentTask.getDocumentGroupId();
    }

    private Integer resolveIterationNo(FileParseTaskRow parentTask, Long documentGroupId) {
        if (parentTask == null) {
            return 1;
        }
        return fileManagementParseMapper.selectMaxIterationNo(documentGroupId) + 1;
    }

    private FileParseStandardVersionRow requireStandardVersion(Long standardVersionId) {
        FileParseStandardVersionRow standardVersion = fileManagementParseMapper.selectStandardVersion(standardVersionId);
        if (standardVersion == null) {
            throw new FileParseAiParseException("STANDARD_VERSION_MISSING", "目标输出方案缺少可用的生效标准版本。");
        }
        return standardVersion;
    }

    private FileParseVersionSummaryRow requireVersion(Long versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("版本 ID 不能为空。");
        }
        FileParseVersionSummaryRow version = fileManagementParseMapper.selectVersion(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在或已删除。");
        }
        return version;
    }

    private FileParseVersionSummaryRow requireLogisticsVersion(FileParseTargetPlanRow targetPlan, Long versionId) {
        Long resolvedVersionId = versionId == null ? targetPlan.getCurrentVersionId() : versionId;
        FileParseVersionSummaryRow version = requireVersion(resolvedVersionId);
        if (!targetPlan.getId().equals(version.getTargetPlanId())) {
            throw new IllegalArgumentException("版本不属于该目标输出方案。");
        }
        if (!isUsablePublishedVersion(version)) {
            throw new IllegalArgumentException("物流渠道生效只能选择可用的已发布版本。");
        }
        return version;
    }

    private boolean isUsablePublishedVersion(FileParseVersionSummaryRow version) {
        String status = version == null ? null : version.getVersionStatus();
        return "active".equalsIgnoreCase(nullToEmpty(status))
                || "history".equalsIgnoreCase(nullToEmpty(status));
    }

    private List<FileParseItemStandardRow> loadItemStandards(FileParseTaskRow task) {
        requireStandardVersion(task.getStandardVersionId());
        List<FileParseItemStandardRow> itemStandards = fileManagementParseMapper.selectItemStandards(task.getStandardVersionId());
        if (itemStandards == null || itemStandards.isEmpty()) {
            throw new IllegalArgumentException("目标输出方案缺少结果行标准。");
        }
        return itemStandards;
    }

    private FileParseTargetPlanRow requireVisibleTargetPlan(FileParseUserContext user, Long targetPlanId) {
        if (targetPlanId == null) {
            throw new IllegalArgumentException("目标输出方案不能为空。");
        }
        boolean systemAdmin = isSystemAdmin(user);
        FileParseTargetPlanRow row = fileManagementParseMapper.selectVisibleTargetPlan(
                targetPlanId,
                user.getRoleLevel(),
                systemAdmin
        );
        if (row == null) {
            throw new FileParseAccessDeniedException("当前账号不能使用该目标输出方案。");
        }
        return row;
    }

    private void requireCanCreateTask(FileParseTargetPlanRow targetPlan, FileParseUserContext user) {
        if (!resolveActions(targetPlan, user).isCanCreateTask()) {
            throw new FileParseAccessDeniedException("当前账号没有发起解析权限。");
        }
    }

    private void requireCanProcess(FileParseTargetPlanRow targetPlan, FileParseUserContext user) {
        if (!resolveActions(targetPlan, user).isCanProcess()) {
            throw new FileParseAccessDeniedException("当前账号没有解析处理权限。");
        }
    }

    private void requireCanPublish(FileParseTargetPlanRow targetPlan, FileParseUserContext user) {
        if (!resolveActions(targetPlan, user).isCanPublish()) {
            throw new FileParseAccessDeniedException("当前账号没有发布版本权限。");
        }
    }

    private void requireCanActivateLogisticsChannels(FileParseTargetPlanRow targetPlan, FileParseUserContext user) {
        if (!resolveActions(targetPlan, user).isCanActivateLogisticsChannels()) {
            throw new FileParseAccessDeniedException("当前账号没有物流渠道生效选择权限。");
        }
    }

    private void requireLogisticsTargetPlan(FileParseTargetPlanRow targetPlan) {
        if (!isLogisticsPlan(targetPlan)) {
            throw new IllegalArgumentException("只有物流目标输出方案支持渠道生效选择。");
        }
    }

    private void validateCreateTaskCommand(FileParseCreateTaskCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("解析文档请求不能为空。");
        }
        if (!StringUtils.hasText(command.getDocumentTitle())) {
            throw new IllegalArgumentException("文档名称不能为空。");
        }
        if (command.getTargetPlanId() == null && command.getParentTaskId() == null) {
            throw new IllegalArgumentException("目标输出方案不能为空。");
        }
        if (command.getInputItems() == null || command.getInputItems().isEmpty()) {
            throw new IllegalArgumentException("至少需要一个输入项。");
        }
        if (command.getInputItems().size() > 20) {
            throw new IllegalArgumentException("单个解析文档最多允许 20 个输入项。");
        }
    }

    private void validateLogisticsActivationCommand(FileParseLogisticsChannelActivationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("物流渠道生效选择请求不能为空。");
        }
        if (command.getTargetPlanId() == null) {
            throw new IllegalArgumentException("目标输出方案不能为空。");
        }
        if (command.getVersionId() == null) {
            throw new IllegalArgumentException("版本 ID 不能为空。");
        }
    }

    private Long resolveLogisticsActivationOwner(FileParseUserContext user) {
        return user.getUserId();
    }

    private FileParseTargetPlanSummary toSummary(FileParseTargetPlanRow row, FileParseUserContext user) {
        FileParseTargetPlanSummary summary = new FileParseTargetPlanSummary();
        summary.setId(row.getId());
        summary.setCode(row.getCode());
        summary.setLabel(row.getLabel());
        summary.setDocumentType(row.getDocumentType());
        summary.setDocumentName(row.getDocumentName());
        summary.setStandardVersion(row.getStandardVersion());
        summary.setCurrentVersion(row.getCurrentVersion());
        summary.setDescription(row.getDescription());
        summary.setAvailableActions(resolveActions(row, user));
        summary.setItemTypes(toTargetPlanItemTypes(row.getStandardVersionId()));
        return summary;
    }

    private List<FileParseTargetPlanItemTypeView> toTargetPlanItemTypes(Long standardVersionId) {
        if (standardVersionId == null) {
            return List.of();
        }
        List<FileParseItemStandardRow> itemStandards = fileManagementParseMapper.selectItemStandards(standardVersionId);
        if (itemStandards == null || itemStandards.isEmpty()) {
            return List.of();
        }
        return itemStandards.stream()
                .map(this::toTargetPlanItemType)
                .collect(Collectors.toList());
    }

    private FileParseTargetPlanItemTypeView toTargetPlanItemType(FileParseItemStandardRow row) {
        FileParseTargetPlanItemTypeView view = new FileParseTargetPlanItemTypeView();
        view.setValue(row.getItemType());
        view.setLabel(StringUtils.hasText(row.getItemLabel()) ? row.getItemLabel() : row.getItemType());
        return view;
    }

    private FileParseTaskDetailView toTaskDetailView(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            List<FileParseTaskInputRow> inputRows
    ) {
        FileParseTaskDetailView view = new FileParseTaskDetailView();
        view.setId(task.getId());
        view.setTaskNo(task.getTaskNo());
        view.setDocumentTitle(task.getDocumentTitle());
        view.setTargetPlanId(targetPlan.getId());
        view.setTargetPlanCode(targetPlan.getCode());
        view.setTargetPlanLabel(targetPlan.getLabel());
        view.setDocumentType(targetPlan.getDocumentType());
        view.setDocumentName(targetPlan.getDocumentName());
        view.setStandardVersion(targetPlan.getStandardVersion());
        view.setCurrentVersion(targetPlan.getCurrentVersion());
        view.setStatus(task.getStatus());
        view.setResultId(task.getCurrentResultId());
        view.setFailureCode(task.getFailureCode());
        view.setFailureMessage(task.getFailureMessage());
        view.setNextRunAt(task.getNextRunAt());
        view.setDataScopeType(task.getDataScopeType());
        view.setDataScopeKey(task.getDataScopeKey());
        view.setDocumentGroupId(task.getDocumentGroupId() == null ? task.getId() : task.getDocumentGroupId());
        view.setParentTaskId(task.getParentTaskId());
        view.setIterationNo(task.getIterationNo() == null ? 1 : task.getIterationNo());
        view.setMessage(task.getFailureMessage());
        view.setInputItems(inputRows == null ? List.of() : inputRows.stream()
                .map(this::toTaskInputView)
                .collect(Collectors.toList()));
        return view;
    }

    private FileParseTaskInputView toTaskInputView(FileParseTaskInputRow row) {
        FileParseTaskInputView view = new FileParseTaskInputView();
        view.setId(row.getId());
        view.setInputType(row.getInputType());
        view.setInputRole(row.getInputRole());
        view.setFileAssetId(row.getFileAssetId());
        view.setDisplayName(row.getDisplayName());
        view.setDownloadUrl(row.getFileAssetId() == null
                ? null
                : uploadArchiveService.downloadUrl(row.getFileAssetId()));
        view.setSortNo(row.getSortNo());
        return view;
    }

    private FileParseTargetPlanRow toTargetPlanRow(FileParseTaskListItemView item) {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(item.getTargetPlanId());
        row.setCode(item.getTargetPlanCode());
        row.setLabel(item.getTargetPlanLabel());
        row.setDocumentType(item.getDocumentType());
        row.setDocumentName(item.getDocumentName());
        row.setStandardVersion(item.getStandardVersion());
        row.setCurrentVersion(item.getCurrentVersion());
        return row;
    }

    private FileParseAvailableActions resolveActions(FileParseTargetPlanRow row, FileParseUserContext user) {
        boolean systemAdmin = isSystemAdmin(user);
        boolean boss = isBoss(user);
        boolean opsManager = isOpsManager(user);
        boolean logisticsPlan = isLogisticsPlan(row);

        FileParseAvailableActions actions = new FileParseAvailableActions();
        actions.setCanCreateTask(systemAdmin || boss || opsManager);
        actions.setCanProcess(systemAdmin || boss || opsManager);
        actions.setCanPublish(systemAdmin);
        actions.setCanManageStandard(systemAdmin);
        actions.setCanActivateLogisticsChannels(logisticsPlan && (systemAdmin || boss));
        return actions;
    }

    private FileParseTaskRunView toRunView(
            FileParseTaskRow task,
            String status,
            List<FileParseExtractionSummary> summaries
    ) {
        FileParseTaskRunView view = new FileParseTaskRunView();
        view.setTaskId(task.getId());
        view.setTaskNo(task.getTaskNo());
        view.setDocumentTitle(task.getDocumentTitle());
        view.setStatus(status);
        int currentAttemptCount = task.getParseAttemptCount() == null ? 0 : task.getParseAttemptCount();
        view.setParseAttemptCount(currentAttemptCount + 1);
        view.setExtractions(summaries);
        return view;
    }

    private String normalizeInputType(FileParseTaskInputCommand inputCommand) {
        String inputType = trimLowercase(inputCommand.getInputType());
        if (StringUtils.hasText(inputType)) {
            return inputType;
        }
        return inputCommand.getFileAssetId() == null ? "manual_text" : "file";
    }

    private String normalizeFileInputType(String inputType, String extension) {
        if (!"file".equals(inputType)) {
            return inputType;
        }
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if ("xlsx".equals(extension) || "xls".equals(extension) || "csv".equals(extension)) {
            return "excel";
        }
        if ("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension) || "webp".equals(extension)) {
            return "image";
        }
        return "file";
    }

    private String normalizeInputRole(String inputRole) {
        String role = trimLowercase(inputRole);
        if (!StringUtils.hasText(role)) {
            return "primary_source";
        }
        if (!INPUT_ROLES.contains(role)) {
            throw new IllegalArgumentException("不支持的输入角色：" + role);
        }
        return role;
    }

    private String normalizeDisplayName(FileParseTaskInputCommand inputCommand, FileParseFileAssetRow asset) {
        String displayName = trimOptional(inputCommand.getDisplayName(), 300, "展示名称");
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        if (asset != null) {
            return asset.getOriginalFileName();
        }
        return "文本输入";
    }

    private String normalizeOptionalKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String stepStatus(FileParseTaskRow task, String activeStatus, boolean completed) {
        return workflowStatePolicy.stepStatus(task == null ? null : task.getStatus(), activeStatus, completed);
    }

    private void requireSystemAdminForAiChunks(FileParseUserContext user) {
        if (!isSystemAdmin(user)) {
            throw new FileParseAccessDeniedException("当前账号不能查看 AI 分块明细。");
        }
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize, int defaultPageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        return Math.min(pageSize, 100);
    }

    private String trimLowercase(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToLength(String value, int maxLength, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(label + "不能为空。");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符。");
        }
        return trimmed;
    }

    private String trimOptional(String value, int maxLength, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符。");
        }
        return trimmed;
    }

    private Path storageRoot() {
        return storageProperties.getRootDir().toAbsolutePath().normalize();
    }

    private String sha256Text(String value) {
        MessageDigest digest = sha256Digest();
        digest.update(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private String longListJson(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return "[" + values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String trimFailureMessage(String message) {
        String fallback = "解析任务启动失败。";
        String value = StringUtils.hasText(message) ? message.trim() : fallback;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private boolean isSystemAdmin(FileParseUserContext user) {
        return hasRoleLevel(user, 0)
                || "SYSTEM_ADMIN".equalsIgnoreCase(user.getRoleCode())
                || "系统管理员".equals(user.getRoleName());
    }

    private boolean isBoss(FileParseUserContext user) {
        return hasRoleLevel(user, 1)
                || "BOSS".equalsIgnoreCase(user.getRoleCode())
                || "老板".equals(user.getRoleName());
    }

    private boolean isOpsManager(FileParseUserContext user) {
        return hasRoleLevel(user, 2)
                || "OPS_MANAGER".equalsIgnoreCase(user.getRoleCode())
                || "运营主管".equals(user.getRoleName());
    }

    private boolean hasRoleLevel(FileParseUserContext user, int level) {
        return user != null && user.getRoleLevel() != null && user.getRoleLevel() == level;
    }

    private boolean isLogisticsPlan(FileParseTargetPlanRow row) {
        if (row == null) {
            return false;
        }
        return startsWithIgnoreCase(row.getCode(), "logistics")
                || startsWithIgnoreCase(row.getDocumentType(), "logistics");
    }

    private boolean isCommissionPlan(FileParseTargetPlanRow row) {
        if (row == null) {
            return false;
        }
        return startsWithIgnoreCase(row.getCode(), "commission")
                || "official_commission".equalsIgnoreCase(row.getDocumentType());
    }

    private boolean isOutboundFeePlan(FileParseTargetPlanRow row) {
        if (row == null) {
            return false;
        }
        return startsWithIgnoreCase(row.getCode(), "outbound_fee")
                || "official_outbound_fee".equalsIgnoreCase(row.getDocumentType());
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private static class FileParsePersistedSourceRow {

        private final Long id;
        private final FileParseSourceRowDraft row;

        private FileParsePersistedSourceRow(Long id, FileParseSourceRowDraft row) {
            this.id = id;
            this.row = row;
        }

        private Long getId() {
            return id;
        }

        private FileParseSourceRowDraft getRow() {
            return row;
        }
    }

    private static class FileParseAiChunkDraft {

        private final Integer chunkNo;
        private final String chunkType;
        private final String combinedText;
        private final List<Long> sourceRowIds;
        private final List<FileParseInputAttachment> attachments;

        private FileParseAiChunkDraft(
                Integer chunkNo,
                String chunkType,
                String combinedText,
                List<Long> sourceRowIds,
                List<FileParseInputAttachment> attachments
        ) {
            this.chunkNo = chunkNo;
            this.chunkType = chunkType;
            this.combinedText = combinedText;
            this.sourceRowIds = sourceRowIds == null ? List.of() : new ArrayList<>(sourceRowIds);
            this.attachments = attachments == null ? List.of() : new ArrayList<>(attachments);
        }

        private Integer getChunkNo() {
            return chunkNo;
        }

        private String getChunkType() {
            return chunkType;
        }

        private String getCombinedText() {
            return combinedText;
        }

        private List<Long> getSourceRowIds() {
            return new ArrayList<>(sourceRowIds);
        }

        private List<FileParseInputAttachment> getAttachments() {
            return new ArrayList<>(attachments);
        }

        private FileParseExtractionResult toExtractionResult() {
            return new FileParseExtractionResult(List.of(), combinedText, false, attachments);
        }
    }

    private static class FileParseAiChunkRun {

        private final Long aiChunkId;
        private final FileParseAiChunkDraft chunkDraft;
        private final FileParseStructuredAiResult result;

        private FileParseAiChunkRun(Long aiChunkId, FileParseAiChunkDraft chunkDraft, FileParseStructuredAiResult result) {
            this.aiChunkId = aiChunkId;
            this.chunkDraft = chunkDraft;
            this.result = result;
        }

        private Long getAiChunkId() {
            return aiChunkId;
        }

        private FileParseAiChunkDraft getChunkDraft() {
            return chunkDraft;
        }

        private FileParseStructuredAiResult getResult() {
            return result;
        }
    }
}
