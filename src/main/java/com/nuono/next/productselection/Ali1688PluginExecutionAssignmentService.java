package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688PluginExecutionAssignmentService {

    private static final List<DateTimeFormatter> ASSIGNMENT_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "CANDIDATE_COLLECTION",
            "DETAIL_ENRICHMENT",
            "PRICE_PREVIEW",
            "INQUIRY_PREPARE"
    );

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ObjectMapper objectMapper;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectProvider<Ali1688CandidateAiAssessmentService> aiAssessmentServiceProvider;

    public Ali1688PluginExecutionAssignmentService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ObjectMapper objectMapper,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectProvider<Ali1688CandidateAiAssessmentService> aiAssessmentServiceProvider
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.objectMapper = objectMapper;
        this.ali1688CollectionService = ali1688CollectionService;
        this.aiAssessmentServiceProvider = aiAssessmentServiceProvider;
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentListView listAssignments(Long operatorUserId) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, null);
        ali1688CollectionMapper.expireCurrentPluginAssignments(scope.getLogicalStoreId(), operatorUserId);
        reconcileAssignments(scope.getLogicalStoreId(), operatorUserId);
        List<Ali1688PluginExecutionAssignmentView> items = ali1688CollectionMapper
                .listCurrentPluginAssignments(scope.getLogicalStoreId(), 80)
                .stream()
                .map(this::toView)
                .collect(Collectors.toCollection(ArrayList::new));
        Ali1688PluginExecutionAssignmentListView view = new Ali1688PluginExecutionAssignmentListView();
        view.items = items;
        view.summary = summary(items);
        view.diagnostics = diagnostics(items);
        view.emptyReason = items.isEmpty() ? "assignment_not_issued" : null;
        view.message = items.isEmpty() ? "当前没有已派发的 1688 插件执行任务。" : "已加载系统插件执行队列。";
        return view;
    }

    private void reconcileAssignments(Long logicalStoreId, Long operatorUserId) {
        if (logicalStoreId == null) {
            return;
        }
        for (Ali1688CollectionRecords.TaskRecord task : ali1688CollectionMapper.listCurrentTasks(logicalStoreId, null, 80)) {
            if ("queued".equals(task.status) && !hasExistingTaskAssignment(task.id, "CANDIDATE_COLLECTION")) {
                insertAssignment(task, null, "CANDIDATE_COLLECTION", operatorUserId, false);
            }
            if ("success".equals(task.status) || "partial_success".equals(task.status)) {
                issuePricePreviewAssignmentsForEligibleTask(task.id, operatorUserId);
            }
        }
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentView createAssignment(
            Ali1688PluginExecutionAssignmentCreateCommand command,
            Long operatorUserId
    ) {
        Ali1688PluginExecutionAssignmentCreateCommand source = command == null
                ? new Ali1688PluginExecutionAssignmentCreateCommand()
                : command;
        Long taskId = parseLongId(source.getTaskId(), "1688 采集任务不存在或已被删除。");
        String assignmentType = normalizeAssignmentType(source.getAssignmentType());
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(taskId);
        requireVisibleTask(operatorUserId, task);
        Long candidateId = parseOptionalLongId(source.getCandidateId(), "1688 候选不存在或已被删除。");
        Ali1688CollectionRecords.CandidateRecord candidate = null;
        if (candidateId != null) {
            candidate = ali1688CollectionMapper.selectCandidateById(candidateId);
            if (candidate == null || !task.id.equals(candidate.taskId)) {
                throw new IllegalArgumentException("插件任务候选范围不匹配。");
            }
        }
        if ("PRICE_PREVIEW".equals(assignmentType)) {
            requirePricePreviewGate(candidate);
        }

        return toView(insertAssignment(task, candidateId, assignmentType, operatorUserId));
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentView issueCandidateCollectionAssignmentForTask(Long taskId, Long operatorUserId) {
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(taskId);
        if (task == null || task.currentTaskKey == null) {
            throw new IllegalArgumentException("1688 采集任务不存在或已被删除。");
        }
        if (!"queued".equals(task.status)) {
            throw new IllegalArgumentException("只有排队中的 1688 采集任务可以派发插件候选采集。");
        }
        return toView(insertAssignment(task, null, "CANDIDATE_COLLECTION", operatorUserId));
    }

    @Transactional
    public int issuePricePreviewAssignmentsForEligibleTask(Long taskId, Long operatorUserId) {
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(taskId);
        if (task == null || task.currentTaskKey == null) {
            return 0;
        }
        int issued = 0;
        for (Ali1688CollectionRecords.CandidateRecord candidate : ali1688CollectionMapper.listCandidatesByTask(task.id)) {
            if (!isPricePreviewGateSatisfied(candidate)
                    || ali1688CollectionMapper.selectLatestPricePreviewSnapshotByCandidateId(candidate.id) != null
                    || hasExistingTerminalOrCurrentPricePreviewAssignment(candidate.id)) {
                continue;
            }
            insertAssignment(task, candidate.id, "PRICE_PREVIEW", operatorUserId, false);
            issued++;
        }
        return issued;
    }

    public Ali1688PluginExecutionAssignmentView getAssignment(String locator, Long operatorUserId) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireAssignment(locator);
        requireVisibleAssignment(operatorUserId, assignment);
        return toView(assignment);
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentView startAssignment(String locator, Long operatorUserId) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireAssignment(locator);
        requireVisibleAssignment(operatorUserId, assignment);
        if (!isOpenAssignmentStatus(assignment.status)) {
            throw new IllegalArgumentException("插件任务当前状态不能开始执行。");
        }
        requireNotExpired(assignment);
        ali1688CollectionMapper.markPluginAssignmentRunning(assignment.id, operatorUserId);
        Ali1688CollectionRecords.PluginAssignmentRecord updated = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(updated == null ? assignment : updated);
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentView failAssignment(
            String locator,
            Ali1688PluginExecutionAssignmentFailureCommand command,
            Long operatorUserId
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireAssignment(locator);
        requireVisibleAssignment(operatorUserId, assignment);
        requireExecutableAssignment(assignment);
        Ali1688PluginExecutionAssignmentFailureCommand source = command == null
                ? new Ali1688PluginExecutionAssignmentFailureCommand()
                : command;
        if ("PRICE_PREVIEW".equals(assignment.assignmentType) && assignment.candidateId != null) {
            insertPricePreviewFailureSnapshot(assignment, source, operatorUserId);
        }
        if ("CANDIDATE_COLLECTION".equals(assignment.assignmentType)) {
            ali1688CollectionMapper.markTaskFailed(
                    assignment.taskId,
                    shrink(defaultText(source.getFailureCode(), "plugin_execution_failed"), 100),
                    shrink(defaultText(source.getFailureMessage(), "插件执行失败。"), 480),
                    operatorUserId
            );
        }
        ali1688CollectionMapper.markPluginAssignmentFailed(
                assignment.id,
                shrink(defaultText(source.getFailureCode(), "plugin_execution_failed"), 100),
                shrink(defaultText(source.getFailureMessage(), "插件执行失败。"), 480),
                operatorUserId
        );
        Ali1688CollectionRecords.PluginAssignmentRecord updated = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(updated == null ? assignment : updated);
    }

    @Transactional
    public Ali1688PluginExecutionAssignmentView submitResult(
            String locator,
            Ali1688PluginExecutionAssignmentSubmitCommand command,
            Long operatorUserId
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = requireAssignment(locator);
        requireVisibleAssignment(operatorUserId, assignment);
        Ali1688PluginExecutionAssignmentSubmitCommand source = command == null
                ? new Ali1688PluginExecutionAssignmentSubmitCommand()
                : command;
        String idempotencyKey = requiredText(source.getIdempotencyKey(), "插件执行结果缺少幂等键。");
        if ("accepted".equals(assignment.status)) {
            if (idempotencyKey.equals(assignment.idempotencyKey)) {
                return toView(assignment);
            }
            throw new IllegalArgumentException("插件任务已接收过结果，不能用新的幂等键重复提交。");
        }
        requireExecutableAssignment(assignment);
        Long submittedCandidateId = parseOptionalLongId(source.getCandidateId(), "插件任务候选范围不匹配。");
        if (assignment.candidateId != null
                && submittedCandidateId != null
                && !assignment.candidateId.equals(submittedCandidateId)) {
            throw new IllegalArgumentException("插件任务候选范围不匹配。");
        }
        if (StringUtils.hasText(source.getAssignmentType())
                && !assignment.assignmentType.equals(normalizeAssignmentType(source.getAssignmentType()))) {
            throw new IllegalArgumentException("插件任务类型不匹配。");
        }

        boolean candidateCollection = "CANDIDATE_COLLECTION".equals(assignment.assignmentType);
        if (candidateCollection) {
            ali1688CollectionService.acceptPluginCandidateCollection(assignment, source, operatorUserId);
        }
        boolean detailEnrichment = "DETAIL_ENRICHMENT".equals(assignment.assignmentType);
        if (detailEnrichment) {
            insertDetailEnrichmentSnapshot(assignment, source, operatorUserId);
            refreshAiAssessmentAfterDetailEnrichment(assignment, operatorUserId);
        }
        boolean pricePreview = "PRICE_PREVIEW".equals(assignment.assignmentType);
        if (pricePreview) {
            requirePricePreviewSubmitGate(assignment);
            requirePricePreviewResultContract(source);
            insertPricePreviewSnapshot(assignment, source, operatorUserId);
        }
        int submittedCount = detailEnrichment || pricePreview ? 1 : source.getCandidates() == null ? 0 : source.getCandidates().size();
        int acceptedCount = submittedCount;
        int rejectedCount = 0;
        ali1688CollectionMapper.markPluginAssignmentAccepted(
                assignment.id,
                idempotencyKey,
                shrink(defaultText(source.getResultStatus(), "success"), 60),
                writeResultSnapshot(source),
                submittedCount,
                acceptedCount,
                rejectedCount,
                operatorUserId
        );
        Ali1688CollectionRecords.PluginAssignmentRecord updated = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return toView(updated == null ? assignment : updated);
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord insertAssignment(
            Ali1688CollectionRecords.TaskRecord task,
            Long candidateId,
            String assignmentType,
            Long operatorUserId
    ) {
        return insertAssignment(task, candidateId, assignmentType, operatorUserId, true);
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord insertAssignment(
            Ali1688CollectionRecords.TaskRecord task,
            Long candidateId,
            String assignmentType,
            Long operatorUserId,
            boolean supersedeExisting
    ) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = new Ali1688CollectionRecords.PluginAssignmentRecord();
        assignment.id = ali1688CollectionMapper.nextPluginAssignmentId();
        assignment.assignmentCode = "ALI1688-PLUGIN-" + assignment.id + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        assignment.assignmentType = assignmentType;
        assignment.taskId = task.id;
        assignment.candidateId = candidateId;
        assignment.sourceCollectionId = task.sourceCollectionId;
        assignment.ownerUserId = task.ownerUserId;
        assignment.logicalStoreId = task.logicalStoreId;
        assignment.currentAssignmentKey = currentAssignmentKey(assignmentType, task.id, candidateId);
        assignment.status = "created";
        assignment.createdBy = operatorUserId;
        assignment.updatedBy = operatorUserId;

        if (supersedeExisting) {
            ali1688CollectionMapper.supersedeCurrentPluginAssignments(assignment.currentAssignmentKey, operatorUserId);
        }
        try {
            ali1688CollectionMapper.insertPluginAssignment(assignment);
        } catch (DuplicateKeyException exception) {
            Ali1688CollectionRecords.PluginAssignmentRecord existing =
                    selectExistingCurrentAssignment(assignmentType, task.id, candidateId);
            if (existing != null && assignment.currentAssignmentKey.equals(existing.currentAssignmentKey)) {
                return existing;
            }
            throw exception;
        }
        Ali1688CollectionRecords.PluginAssignmentRecord inserted = ali1688CollectionMapper.selectPluginAssignmentById(assignment.id);
        return inserted == null ? assignment : inserted;
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord selectExistingCurrentAssignment(
            String assignmentType,
            Long taskId,
            Long candidateId
    ) {
        if (candidateId == null) {
            return ali1688CollectionMapper.selectLatestPluginAssignmentByTaskAndType(taskId, assignmentType);
        }
        return ali1688CollectionMapper.selectLatestPluginAssignmentByCandidateAndType(candidateId, assignmentType);
    }

    private void requirePricePreviewGate(Ali1688CollectionRecords.CandidateRecord candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("价格预览插件任务必须绑定一个 1688 候选。");
        }
        if (candidate.selectedRankNo == null) {
            throw new IllegalArgumentException("候选尚未进入 Top5，不能创建价格预览任务。");
        }
        if (!"success".equals(candidate.aiAssessmentStatus) || !"final".equals(candidate.scoreStatus)) {
            throw new IllegalArgumentException("候选 AI 尚未通过，不能创建价格预览任务。");
        }
        if (candidate.matchScore == null || candidate.matchScore < 20) {
            throw new IllegalArgumentException("候选 AI 判定为 mismatch，不能创建价格预览任务。");
        }
        if ("high".equalsIgnoreCase(readScoreRiskLevel(candidate.scoreDetailJson))) {
            throw new IllegalArgumentException("候选 AI 风险过高，不能创建价格预览任务。");
        }
        if (candidate.specScore == null || candidate.specScore < 10) {
            throw new IllegalArgumentException("候选规格匹配信息不足，不能创建价格预览任务。");
        }
    }

    private boolean isPricePreviewGateSatisfied(Ali1688CollectionRecords.CandidateRecord candidate) {
        try {
            requirePricePreviewGate(candidate);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean hasExistingTerminalOrCurrentPricePreviewAssignment(Long candidateId) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment =
                ali1688CollectionMapper.selectLatestPluginAssignmentByCandidateAndType(candidateId, "PRICE_PREVIEW");
        if (assignment == null) {
            return false;
        }
        return blocksNewAssignment(assignment);
    }

    private boolean hasExistingTaskAssignment(Long taskId, String assignmentType) {
        Ali1688CollectionRecords.PluginAssignmentRecord assignment =
                ali1688CollectionMapper.selectLatestPluginAssignmentByTaskAndType(taskId, assignmentType);
        if (assignment == null) {
            return false;
        }
        return blocksNewAssignment(assignment);
    }

    private boolean blocksNewAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        String status = defaultText(assignment.status, "");
        if (isOpenAssignmentStatus(status)) {
            return !isAssignmentExpired(assignment);
        }
        return List.of("accepted", "failed").contains(status);
    }

    private void requireExecutableAssignment(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        if (!isOpenAssignmentStatus(assignment.status)) {
            throw new IllegalArgumentException("插件任务已过期或已结束。");
        }
        requireNotExpired(assignment);
    }

    private void requireNotExpired(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        if (isAssignmentExpired(assignment)) {
            throw new IllegalArgumentException("插件任务已过期或已结束。");
        }
    }

    private boolean isOpenAssignmentStatus(String status) {
        return "created".equals(status) || "running".equals(status);
    }

    private boolean isAssignmentExpired(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        LocalDateTime expiresAt = parseAssignmentTime(assignment == null ? null : assignment.expiresAt);
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    private LocalDateTime parseAssignmentTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : ASSIGNMENT_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported database display format.
            }
        }
        return null;
    }

    private String readScoreRiskLevel(String scoreDetailJson) {
        if (!StringUtils.hasText(scoreDetailJson)) {
            return "";
        }
        try {
            Object value = objectMapper.readValue(scoreDetailJson, Map.class).get("riskLevel");
            return value == null ? "" : String.valueOf(value);
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private void requirePricePreviewSubmitGate(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        if (assignment.candidateId == null) {
            throw new IllegalArgumentException("价格预览插件任务必须绑定一个 1688 候选。");
        }
        Ali1688CollectionRecords.CandidateRecord candidate = ali1688CollectionMapper.selectCandidateById(assignment.candidateId);
        if (candidate == null || !assignment.taskId.equals(candidate.taskId)) {
            throw new IllegalArgumentException("插件任务候选范围不匹配。");
        }
        requirePricePreviewGate(candidate);
    }

    private void requirePricePreviewResultContract(Ali1688PluginExecutionAssignmentSubmitCommand command) {
        if (!"success".equals(defaultText(command.getResultStatus(), "success"))) {
            throw new IllegalArgumentException("价格预览成功结果必须通过成功合同提交。");
        }
        Map<String, Object> snapshot = command.getResultSnapshot() == null ? Map.of() : command.getResultSnapshot();
        if (!"preview_only".equals(defaultText(stringValue(snapshot.get("safetyMode")), ""))
                || !"no_payment_no_order_no_message".equals(defaultText(stringValue(snapshot.get("sideEffectPolicy")), ""))) {
            throw new IllegalArgumentException("价格预览结果安全边界不匹配。");
        }
        if (!StringUtils.hasText(stringValue(snapshot.get("totalPriceText")))) {
            throw new IllegalArgumentException("价格预览结果缺少真实总价。");
        }
        if (integerValue(snapshot.get("quantity")) == null || integerValue(snapshot.get("quantity")) <= 0) {
            throw new IllegalArgumentException("价格预览结果缺少有效数量。");
        }
    }

    private void insertDetailEnrichmentSnapshot(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Ali1688PluginExecutionAssignmentSubmitCommand command,
            Long operatorUserId
    ) {
        if (assignment.candidateId == null) {
            throw new IllegalArgumentException("详情补全插件任务必须绑定一个 1688 候选。");
        }

        Map<String, Object> snapshot = command.getResultSnapshot() == null ? Map.of() : command.getResultSnapshot();
        Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord row =
                new Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord();
        row.id = ali1688CollectionMapper.nextDetailEnrichmentSnapshotId();
        row.assignmentId = assignment.id;
        row.taskId = assignment.taskId;
        row.candidateId = assignment.candidateId;
        row.sourceCollectionId = assignment.sourceCollectionId;
        row.ownerUserId = assignment.ownerUserId;
        row.logicalStoreId = assignment.logicalStoreId;
        row.snapshotSource = shrink(defaultText(stringValue(snapshot.get("source")), "browser-extension"), 60);
        row.collectedAt = shrink(defaultText(stringValue(snapshot.get("collectedAt")), ""), 80);
        row.pageUrl = shrink(defaultText(stringValue(snapshot.get("pageUrl")), assignment.candidateUrl), 1000);
        row.detailTitle = shrink(defaultText(stringValue(snapshot.get("title")), ""), 500);
        row.mainImageUrlsJson = writeJsonValue(snapshot.get("mainImageUrls"));
        row.detailImageUrlsJson = writeJsonValue(snapshot.get("detailImageUrls"));
        row.imageUrlsJson = writeJsonValue(snapshot.get("imageUrls"));
        row.skuOptionsJson = writeJsonValue(snapshot.get("skuOptions"));
        row.moqText = shrink(defaultText(stringValue(snapshot.get("moqText")), ""), 200);
        row.supplierName = shrink(defaultText(stringValue(snapshot.get("supplierName")), ""), 300);
        row.locationText = shrink(defaultText(stringValue(snapshot.get("locationText")), ""), 200);
        row.listPriceText = shrink(defaultText(stringValue(snapshot.get("listPriceText")), ""), 200);
        row.serviceLabelsJson = writeJsonValue(snapshot.get("serviceLabels"));
        row.salesLabelsJson = writeJsonValue(snapshot.get("salesLabels"));
        row.rawEvidenceSnippetsJson = writeJsonValue(snapshot.get("rawEvidenceSnippets"));
        row.rawSnapshotJson = writeJsonValue(snapshot);
        // v2：window.context.result.data 结构化详情（仅作 AI 线索/读模型展示，不回写列表候选事实）。
        row.unit = shrink(defaultText(stringValue(snapshot.get("unit")), ""), 60);
        row.variantImageUrlsJson = writeJsonValue(snapshot.get("variantImageUrls"));
        row.attributesJson = writeJsonValue(snapshot.get("attributes"));
        row.skuCombinationsJson = writeJsonValue(snapshot.get("skuCombinations"));
        row.skuCount = integerValue(snapshot.get("skuCount"));
        row.pagePriceHintJson = writeJsonValue(snapshot.get("pagePriceHint"));
        row.supplierProfileJson = writeJsonValue(snapshot.get("supplierProfile"));
        row.shippingSnapshotJson = writeJsonValue(snapshot.get("shippingSnapshot"));
        row.videoJson = writeJsonValue(snapshot.get("video"));
        row.createdBy = operatorUserId;
        row.updatedBy = operatorUserId;
        ali1688CollectionMapper.insertDetailEnrichmentSnapshot(row);
    }

    private void insertPricePreviewSnapshot(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Ali1688PluginExecutionAssignmentSubmitCommand command,
            Long operatorUserId
    ) {
        if (assignment.candidateId == null) {
            throw new IllegalArgumentException("价格预览插件任务必须绑定一个 1688 候选。");
        }

        Map<String, Object> snapshot = command.getResultSnapshot() == null ? Map.of() : command.getResultSnapshot();
        Ali1688CollectionRecords.PricePreviewSnapshotRecord row = basePricePreviewSnapshot(assignment, operatorUserId);
        row.id = ali1688CollectionMapper.nextPricePreviewSnapshotId();
        row.snapshotSource = shrink(defaultText(stringValue(snapshot.get("source")), "browser-extension"), 60);
        row.resultStatus = shrink(defaultText(command.getResultStatus(), "success"), 60);
        row.failureCode = null;
        row.failureMessage = null;
        row.collectedAt = shrink(defaultText(stringValue(snapshot.get("collectedAt")), ""), 80);
        row.skuOptionsJson = writeJsonValue(snapshot.get("skuOptions"));
        row.quantity = integerValue(snapshot.get("quantity"));
        row.unitPriceText = shrink(defaultText(stringValue(snapshot.get("unitPriceText")), ""), 160);
        row.shippingText = shrink(defaultText(stringValue(snapshot.get("shippingText")), ""), 160);
        row.discountText = shrink(defaultText(stringValue(snapshot.get("discountText")), ""), 160);
        row.totalPriceText = shrink(defaultText(stringValue(snapshot.get("totalPriceText")), ""), 160);
        row.currency = shrink(defaultText(stringValue(snapshot.get("currency")), "CNY"), 20);
        row.regionText = shrink(defaultText(stringValue(snapshot.get("regionText")), ""), 300);
        row.safetyMode = shrink(defaultText(stringValue(snapshot.get("safetyMode")), "preview_only"), 60);
        row.sideEffectPolicy = shrink(defaultText(stringValue(snapshot.get("sideEffectPolicy")), "no_payment_no_order_no_message"), 100);
        row.rawSnapshotJson = writeJsonValue(snapshot);
        ali1688CollectionMapper.insertPricePreviewSnapshot(row);
    }

    private void insertPricePreviewFailureSnapshot(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Ali1688PluginExecutionAssignmentFailureCommand command,
            Long operatorUserId
    ) {
        Ali1688CollectionRecords.PricePreviewSnapshotRecord row = basePricePreviewSnapshot(assignment, operatorUserId);
        row.id = ali1688CollectionMapper.nextPricePreviewSnapshotId();
        row.snapshotSource = "browser-extension";
        row.resultStatus = "failed";
        row.failureCode = shrink(defaultText(command.getFailureCode(), "preview_failed"), 100);
        row.failureMessage = shrink(defaultText(command.getFailureMessage(), "价格预览失败。"), 500);
        row.currency = "CNY";
        row.safetyMode = "preview_only";
        row.sideEffectPolicy = "no_payment_no_order_no_message";
        row.rawSnapshotJson = "{}";
        ali1688CollectionMapper.insertPricePreviewSnapshot(row);
    }

    private Ali1688CollectionRecords.PricePreviewSnapshotRecord basePricePreviewSnapshot(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Long operatorUserId
    ) {
        Ali1688CollectionRecords.PricePreviewSnapshotRecord row = new Ali1688CollectionRecords.PricePreviewSnapshotRecord();
        row.assignmentId = assignment.id;
        row.taskId = assignment.taskId;
        row.candidateId = assignment.candidateId;
        row.sourceCollectionId = assignment.sourceCollectionId;
        row.ownerUserId = assignment.ownerUserId;
        row.logicalStoreId = assignment.logicalStoreId;
        row.createdBy = operatorUserId;
        row.updatedBy = operatorUserId;
        return row;
    }

    private void refreshAiAssessmentAfterDetailEnrichment(
            Ali1688CollectionRecords.PluginAssignmentRecord assignment,
            Long operatorUserId
    ) {
        Ali1688CandidateAiAssessmentService aiAssessmentService = aiAssessmentServiceProvider == null
                ? null
                : aiAssessmentServiceProvider.getIfAvailable();
        if (aiAssessmentService == null) {
            return;
        }
        aiAssessmentService.refreshAssessmentForCandidate(assignment.taskId, assignment.candidateId, operatorUserId);
    }

    private Ali1688CollectionRecords.PluginAssignmentRecord requireAssignment(String locator) {
        String value = requiredText(locator, "插件任务不存在或已失效。");
        Ali1688CollectionRecords.PluginAssignmentRecord assignment = ali1688CollectionMapper.selectPluginAssignmentByLocator(value);
        if (assignment == null) {
            throw new IllegalArgumentException("插件任务不存在或已失效。");
        }
        return assignment;
    }

    private void requireVisibleTask(Long operatorUserId, Ali1688CollectionRecords.TaskRecord task) {
        if (task == null || task.currentTaskKey == null) {
            throw new IllegalArgumentException("1688 采集任务不存在或已被删除。");
        }
        requireVisibleLogicalStore(operatorUserId, task.logicalStoreId);
    }

    private void requireVisibleAssignment(Long operatorUserId, Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        requireVisibleLogicalStore(operatorUserId, assignment.logicalStoreId);
    }

    private void requireVisibleLogicalStore(Long operatorUserId, Long logicalStoreId) {
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (user != null && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()))) {
            return;
        }
        if (logicalStoreId == null || productSelectionMapper.countVisibleLogicalStoreSites(operatorUserId, logicalStoreId) <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该 1688 插件任务。");
        }
    }

    private Ali1688PluginExecutionAssignmentView toView(Ali1688CollectionRecords.PluginAssignmentRecord record) {
        Ali1688PluginExecutionAssignmentView view = new Ali1688PluginExecutionAssignmentView();
        if (record == null) {
            view.status = "missing";
            view.current = false;
            view.message = "插件任务不存在或已失效。";
            return view;
        }
        view.assignmentId = record.id == null ? null : String.valueOf(record.id);
        view.assignmentCode = record.assignmentCode;
        view.assignmentType = record.assignmentType;
        view.taskId = record.taskId == null ? null : String.valueOf(record.taskId);
        view.candidateId = record.candidateId == null ? null : String.valueOf(record.candidateId);
        view.sourceCollectionId = record.sourceCollectionId == null ? null : String.valueOf(record.sourceCollectionId);
        view.taskNo = record.taskNo;
        view.status = defaultText(record.status, "created");
        view.resultStatus = record.resultStatus;
        view.idempotencyKey = record.idempotencyKey;
        view.sourceImageUrl = record.sourceImageUrl;
        view.sourceTitle = record.sourceTitle;
        view.sourceTitleCn = record.sourceTitleCn;
        view.sourceUrl = record.sourceUrl;
        view.pageUrl = record.pageUrl;
        view.storeId = record.logicalStoreId == null ? null : String.valueOf(record.logicalStoreId);
        view.storeName = record.storeName;
        view.storeCode = record.storeCode;
        view.candidateTitle = record.candidateTitle;
        view.candidateUrl = record.candidateUrl;
        view.offerId = record.offerId;
        view.createdAt = record.createdAt;
        view.expiresAt = record.expiresAt;
        view.startedAt = record.startedAt;
        view.finishedAt = record.finishedAt;
        view.failureCode = record.failureCode;
        view.failureMessage = record.failureMessage;
        view.submittedCandidateCount = record.submittedCandidateCount;
        view.acceptedCandidateCount = record.acceptedCandidateCount;
        view.rejectedCandidateCount = record.rejectedCandidateCount;
        view.current = StringUtils.hasText(record.currentAssignmentKey);
        view.message = message(view);
        return view;
    }

    private Map<String, Object> summary(List<Ali1688PluginExecutionAssignmentView> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("pending", items.stream().filter(item -> "created".equals(item.status)).count());
        summary.put("running", items.stream().filter(item -> "running".equals(item.status)).count());
        summary.put("synced", items.stream().filter(item -> "accepted".equals(item.status)).count());
        summary.put("blockedOrFailed", items.stream().filter(item -> List.of("failed", "expired", "cancelled").contains(item.status)).count());
        return summary;
    }

    private Map<String, Object> diagnostics(List<Ali1688PluginExecutionAssignmentView> items) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("apiAvailable", true);
        diagnostics.put("bearerSessionValid", true);
        diagnostics.put("visibleTaskCount", items.size());
        diagnostics.put("assignableTaskCount", items.stream().filter(item -> List.of("created", "running").contains(item.status)).count());
        diagnostics.put("issuedAssignmentCount", items.size());
        diagnostics.put("missingSourceImageCount", items.stream().filter(item -> !StringUtils.hasText(item.sourceImageUrl)).count());
        diagnostics.put("expiredAssignmentCount", items.stream().filter(item -> "expired".equals(item.status)).count());
        diagnostics.put("version", "ALI1688_PLUGIN_ASSIGNMENT_LIST_V2");
        return diagnostics;
    }

    private String writeResultSnapshot(Ali1688PluginExecutionAssignmentSubmitCommand command) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourcePageUrl", command.getSourcePageUrl());
        snapshot.put("resultSnapshot", command.getResultSnapshot() == null ? Map.of() : command.getResultSnapshot());
        snapshot.put("rawSnapshot", command.getRawSnapshot() == null ? Map.of() : command.getRawSnapshot());
        snapshot.put("candidates", command.getCandidates() == null ? List.of() : command.getCandidates());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String writeJsonValue(Object value) {
        Object jsonValue = value == null ? List.of() : value;
        try {
            return objectMapper.writeValueAsString(jsonValue);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String normalizeAssignmentType(String value) {
        String type = defaultText(value, "CANDIDATE_COLLECTION").toUpperCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的插件任务类型：" + type);
        }
        return type;
    }

    private String currentAssignmentKey(String assignmentType, Long taskId, Long candidateId) {
        return assignmentType + ":" + taskId + ":" + (candidateId == null ? "task" : candidateId);
    }

    private Long parseLongId(String id, String message) {
        try {
            return Long.valueOf(defaultText(id, ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private Long parseOptionalLongId(String id, String message) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return parseLongId(id, message);
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String stringValue(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String shrink(String value, int maxLength) {
        String text = defaultText(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String message(Ali1688PluginExecutionAssignmentView view) {
        if (StringUtils.hasText(view.failureMessage)) {
            return view.failureMessage;
        }
        if ("running".equals(view.status)) {
            return "插件任务执行中。";
        }
        if ("accepted".equals(view.status)) {
            return "插件任务结果已接收。";
        }
        if ("failed".equals(view.status)) {
            return "插件任务执行失败。";
        }
        return "插件任务已创建，等待插件执行。";
    }
}
