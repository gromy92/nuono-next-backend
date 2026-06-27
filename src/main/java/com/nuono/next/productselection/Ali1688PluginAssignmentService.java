package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688PluginAssignmentService {

    private static final int MAX_CANDIDATES = 10;
    private static final int TOP_FIVE_LIMIT = 5;
    private static final String ASSIGNMENT_PREFIX = "ALI1688-PLUGIN-";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final Ali1688CandidateScoringService scoringService;
    private final Ali1688CandidateAiAssessmentService aiAssessmentService;
    private final ObjectMapper objectMapper;

    public Ali1688PluginAssignmentService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            Ali1688CandidateScoringService scoringService,
            Ali1688CandidateAiAssessmentService aiAssessmentService,
            ObjectMapper objectMapper
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.scoringService = scoringService;
        this.aiAssessmentService = aiAssessmentService;
        this.objectMapper = objectMapper;
    }

    public Ali1688PluginAssignmentListView listAssignments(BusinessAccessContext access) {
        List<Ali1688CollectionRecords.TaskRecord> tasks = ali1688CollectionMapper.listPluginAssignmentTasks(
                access.isSystemAdmin() ? null : access.getBusinessOwnerUserId(),
                new ArrayList<>(access.getStoreCodes()),
                50
        );
        Ali1688PluginAssignmentListView view = new Ali1688PluginAssignmentListView();
        view.items = tasks.stream()
                .map(this::toAssignmentView)
                .collect(Collectors.toCollection(ArrayList::new));
        view.summary.total = view.items.size();
        view.summary.pending = (int) view.items.stream().filter(item -> "created".equals(item.status)).count();
        view.summary.running = (int) view.items.stream().filter(item -> "running".equals(item.status)).count();
        view.summary.synced = (int) view.items.stream().filter(item -> "accepted".equals(item.status)).count();
        view.summary.blockedOrFailed = (int) view.items.stream()
                .filter(item -> "failed".equals(item.status) || "expired".equals(item.status) || "cancelled".equals(item.status))
                .count();
        view.diagnostics.visibleTaskCount = view.items.size();
        view.diagnostics.assignableTaskCount = view.summary.pending + view.summary.running;
        view.diagnostics.issuedAssignmentCount = view.items.size();
        view.diagnostics.missingSourceImageCount = (int) view.items.stream()
                .filter(item -> !StringUtils.hasText(item.sourceImageUrl))
                .count();
        view.emptyReason = view.items.isEmpty() ? "no_visible_task" : null;
        view.message = view.items.isEmpty() ? "当前没有待插件自动采集的系统任务。" : "已加载系统插件采集队列。";
        view.refreshedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return view;
    }

    public Ali1688PluginAssignmentView getAssignment(BusinessAccessContext access, String locator) {
        return toAssignmentView(requireVisibleTask(access, locator));
    }

    @Transactional
    public Ali1688PluginAssignmentView startAssignment(BusinessAccessContext access, String locator) {
        Ali1688CollectionRecords.TaskRecord task = requireVisibleTask(access, locator);
        if (!"queued".equals(task.status) && !"running".equals(task.status)) {
            throw new IllegalArgumentException("当前 1688 采集任务状态不能开始采集。");
        }
        ali1688CollectionMapper.markTaskRunningForPlugin(task.id, lockOwner(access, task.id), access.getSessionUserId());
        return toAssignmentView(ali1688CollectionMapper.selectTaskById(task.id));
    }

    @Transactional
    public Ali1688PluginAssignmentView failAssignment(
            BusinessAccessContext access,
            String locator,
            Ali1688PluginCommands.FailureCommand command
    ) {
        Ali1688CollectionRecords.TaskRecord task = requireVisibleTask(access, locator);
        ali1688CollectionMapper.markTaskFailed(
                task.id,
                trim(command == null ? null : command.failureCode, "plugin_collect_failed"),
                shrink(trim(command == null ? null : command.failureMessage, "插件采集失败。"), 480),
                access.getSessionUserId()
        );
        return toAssignmentView(ali1688CollectionMapper.selectTaskById(task.id));
    }

    @Transactional
    public Ali1688PluginSubmissionView submitCandidates(
            BusinessAccessContext access,
            String locator,
            Ali1688PluginCommands.CandidateSubmissionCommand command
    ) {
        Ali1688CollectionRecords.TaskRecord task = requireVisibleTask(access, locator);
        Ali1688PluginCommands.CandidateSubmissionCommand effectiveCommand = command == null
                ? new Ali1688PluginCommands.CandidateSubmissionCommand()
                : command;
        List<Ali1688PluginCommands.CandidatePayload> payloadCandidates = effectiveCommand.candidates == null
                ? List.of()
                : effectiveCommand.candidates;
        if (payloadCandidates.isEmpty()) {
            throw new IllegalArgumentException("当前 1688 页面没有可同步候选。");
        }

        String lockedBy = lockOwner(access, task.id);
        ali1688CollectionMapper.markTaskRunningForPlugin(task.id, lockedBy, access.getSessionUserId());
        Ali1688CollectionRecords.TaskRecord runningTask = ali1688CollectionMapper.selectTaskById(task.id);
        ali1688CollectionMapper.updateSearchSnapshot(
                task.id,
                lockedBy,
                "插件图搜",
                trim(effectiveCommand.sourcePageUrl, null),
                null,
                writeJson(List.of()),
                writeJson(Map.of(
                        "source", "browser-extension",
                        "rawSnapshot", effectiveCommand.rawSnapshot == null ? Map.of() : effectiveCommand.rawSnapshot,
                        "idempotencyKey", trim(effectiveCommand.idempotencyKey, "")
                )),
                effectiveCommand.rawSnapshot == null || effectiveCommand.rawSnapshot.scannedCount == null
                        ? payloadCandidates.size()
                        : effectiveCommand.rawSnapshot.scannedCount
        );
        ali1688CollectionMapper.softDeleteCandidatesByTask(task.id, access.getSessionUserId());

        List<Ali1688CollectionRecords.CandidateRecord> persisted = persistPluginCandidates(
                runningTask == null ? task : runningTask,
                payloadCandidates,
                access.getSessionUserId()
        );
        selectTopFive(task.id, persisted, access.getSessionUserId());
        aiAssessmentService.createPendingAssessments(runningTask == null ? task : runningTask, persisted);

        String finalStatus = persisted.size() >= MAX_CANDIDATES ? "success" : persisted.isEmpty() ? "failed" : "partial_success";
        String failureCode = persisted.isEmpty() ? "no_valid_candidate" : persisted.size() < MAX_CANDIDATES ? "candidate_count_less_than_10" : null;
        String failureMessage = persisted.isEmpty() ? "插件未同步有效 1688 候选。"
                : persisted.size() < MAX_CANDIDATES ? "1688 图搜候选不足 10 个，已展示可用候选。" : null;
        ali1688CollectionMapper.markTaskCompleted(
                task.id,
                finalStatus,
                effectiveCommand.rawSnapshot == null || effectiveCommand.rawSnapshot.scannedCount == null
                        ? payloadCandidates.size()
                        : effectiveCommand.rawSnapshot.scannedCount,
                persisted.size(),
                Math.min(persisted.size(), TOP_FIVE_LIMIT),
                failureCode,
                failureMessage,
                access.getSessionUserId(),
                lockedBy
        );

        Ali1688CollectionRecords.TaskRecord updated = ali1688CollectionMapper.selectTaskById(task.id);
        Ali1688PluginSubmissionView view = new Ali1688PluginSubmissionView();
        view.assignmentId = assignmentId(task.id);
        view.taskId = String.valueOf(task.id);
        view.sourceCollectionId = String.valueOf(task.sourceCollectionId);
        view.status = "accepted";
        view.submittedCandidateCount = payloadCandidates.size();
        view.acceptedCandidateCount = persisted.size();
        view.rejectedCandidateCount = Math.max(0, payloadCandidates.size() - persisted.size());
        view.candidateCount = persisted.size();
        view.recommendedCount = Math.min(persisted.size(), TOP_FIVE_LIMIT);
        view.taskStatus = updated == null ? finalStatus : updated.status;
        view.failureCode = updated == null ? failureCode : updated.failureCode;
        view.failureMessage = updated == null ? failureMessage : updated.failureMessage;
        view.message = persisted.isEmpty() ? view.failureMessage : "候选已同步到系统。";
        return view;
    }

    public Ali1688PluginAssignmentView submitAssignmentResult(
            BusinessAccessContext access,
            String locator,
            Ali1688PluginCommands.AssignmentResultCommand command
    ) {
        // Purchase-order collection currently issues only CANDIDATE_COLLECTION assignments.
        // Returning the current assignment keeps newer extension clients from failing if they call this path accidentally.
        return getAssignment(access, locator);
    }

    public Ali1688PluginAssignmentView createAssignment(
            BusinessAccessContext access,
            Ali1688PluginCommands.CreateAssignmentCommand command
    ) {
        if (command == null || !StringUtils.hasText(command.taskId)) {
            throw new IllegalArgumentException("缺少 1688 任务，不能创建插件 assignment。");
        }
        return getAssignment(access, command.taskId);
    }

    private List<Ali1688CollectionRecords.CandidateRecord> persistPluginCandidates(
            Ali1688CollectionRecords.TaskRecord task,
            List<Ali1688PluginCommands.CandidatePayload> payloadCandidates,
            Long operatorUserId
    ) {
        Set<String> seen = new LinkedHashSet<>();
        List<Ali1688CollectionRecords.CandidateRecord> persisted = new ArrayList<>();
        int rank = 1;
        for (Ali1688PluginCommands.CandidatePayload raw : payloadCandidates) {
            if (raw == null || persisted.size() >= MAX_CANDIDATES) {
                break;
            }
            String candidateUrl = trim(raw.candidateUrl, null);
            String offerId = trim(raw.offerId, null);
            String urlHash = urlHash(candidateUrl);
            String dedupeKey = StringUtils.hasText(offerId) ? "offer:" + offerId : "url:" + urlHash;
            if (!StringUtils.hasText(offerId) && !StringUtils.hasText(urlHash)) {
                continue;
            }
            if (!seen.add(dedupeKey)) {
                continue;
            }
            Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
            candidate.id = ali1688CollectionMapper.nextCandidateId();
            candidate.taskId = task.id;
            candidate.sourceCollectionId = task.sourceCollectionId;
            candidate.ownerUserId = task.ownerUserId;
            candidate.logicalStoreId = task.logicalStoreId;
            candidate.rankNo = rank++;
            candidate.level = "review";
            candidate.offerId = offerId;
            candidate.candidateUrl = candidateUrl;
            candidate.candidateUrlHash = urlHash;
            candidate.activeCandidateKey = task.id + ":" + (StringUtils.hasText(offerId) ? offerId : urlHash);
            candidate.title = trim(raw.title, null);
            candidate.supplierName = trim(raw.supplierName, null);
            candidate.priceText = trim(raw.priceText, null);
            candidate.priceMin = safePrice(raw.priceMin);
            candidate.priceMax = safePrice(raw.priceMax);
            candidate.moqText = trim(raw.moqText, null);
            candidate.moqValue = raw.moqValue;
            candidate.locationText = trim(raw.locationText, null);
            candidate.mainImageUrl = trim(raw.mainImageUrl, null);
            candidate.imageUrlsJson = writeJson(normalizeImageUrls(raw.imageUrls, raw.mainImageUrl));
            candidate.badgesJson = writeJson(raw.badges);
            candidate.skuSnapshotJson = writeJson(raw.skuSnapshot);
            candidate.supplierSnapshotJson = writeJson(raw.supplierSnapshot);
            candidate.logisticsSnapshotJson = writeJson(raw.logisticsSnapshot);
            candidate.aiAssessmentStatus = "pending";
            candidate.createdBy = operatorUserId;
            candidate.updatedBy = operatorUserId;
            scoringService.score(candidate);
            ali1688CollectionMapper.insertCandidate(candidate);
            persisted.add(candidate);
        }
        return persisted;
    }

    private void selectTopFive(Long taskId, List<Ali1688CollectionRecords.CandidateRecord> candidates, Long updatedBy) {
        ali1688CollectionMapper.clearSelectedRanks(taskId, updatedBy);
        List<Ali1688CollectionRecords.CandidateRecord> selected = candidates.stream()
                .sorted(Comparator.comparing((Ali1688CollectionRecords.CandidateRecord item) -> item.ruleScore == null ? 0 : item.ruleScore).reversed()
                        .thenComparing(item -> item.rankNo == null ? Integer.MAX_VALUE : item.rankNo))
                .limit(TOP_FIVE_LIMIT)
                .collect(Collectors.toList());
        int selectedRank = 1;
        for (Ali1688CollectionRecords.CandidateRecord candidate : selected) {
            ali1688CollectionMapper.updateSelectedRank(taskId, candidate.id, selectedRank, updatedBy);
            selectedRank++;
        }
    }

    private Ali1688CollectionRecords.TaskRecord requireVisibleTask(BusinessAccessContext access, String locator) {
        Long taskId = parseTaskId(locator);
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(taskId);
        if (task == null || task.id == null) {
            throw new IllegalArgumentException("1688 插件任务不存在或已失效。");
        }
        if (!access.isSystemAdmin()) {
            if (access.getBusinessOwnerUserId() != null && task.ownerUserId != null
                    && !access.getBusinessOwnerUserId().equals(task.ownerUserId)) {
                throw new ProductSelectionAccessDeniedException("当前账号不能访问该 1688 插件任务。");
            }
            if (!access.getStoreCodes().isEmpty() && !access.canAccessStore(task.storeCode)) {
                throw new ProductSelectionAccessDeniedException("当前账号不能访问该 1688 插件任务。");
            }
        }
        return task;
    }

    private Ali1688PluginAssignmentView toAssignmentView(Ali1688CollectionRecords.TaskRecord task) {
        Ali1688PluginAssignmentView view = new Ali1688PluginAssignmentView();
        view.assignmentId = assignmentId(task.id);
        view.assignmentCode = assignmentId(task.id);
        view.assignmentType = "CANDIDATE_COLLECTION";
        view.taskId = String.valueOf(task.id);
        view.sourceCollectionId = String.valueOf(task.sourceCollectionId);
        view.taskNo = task.taskNo;
        view.status = toAssignmentStatus(task.status);
        view.resultStatus = toResultStatus(task.status);
        view.idempotencyKey = task.currentTaskKey;
        view.sourceImageUrl = NoonImageUrlNormalizer.normalize(task.sourceImageUrl);
        view.sourceTitle = task.sourceTitle;
        view.sourceTitleCn = task.sourceTitleCn;
        view.sourceUrl = task.sourceUrl;
        view.pageUrl = task.pageUrl;
        view.targetSkuSelection = buildTargetSkuSelection(task);
        view.storeId = task.logicalStoreId == null ? null : String.valueOf(task.logicalStoreId);
        view.storeName = task.storeName;
        view.storeCode = task.storeCode;
        view.startedAt = task.startedAt;
        view.finishedAt = task.finishedAt;
        view.failureCode = task.failureCode;
        view.failureMessage = task.failureMessage;
        view.submittedCandidateCount = task.scannedCount;
        view.acceptedCandidateCount = task.candidateCount;
        view.rejectedCandidateCount = task.scannedCount == null || task.candidateCount == null
                ? 0
                : Math.max(0, task.scannedCount - task.candidateCount);
        view.message = StringUtils.hasText(task.failureMessage) ? task.failureMessage : defaultMessage(task.status);
        return view;
    }

    private Map<String, Object> buildTargetSkuSelection(Ali1688CollectionRecords.TaskRecord task) {
        List<String> specHints = readStringList(task.sourceSpecHintsJson);
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("status", hasActionableSpecHint(specHints) ? "target_spec_available" : "missing_target_spec");
        target.put("sourceSku", trim(task.sourceSelectedText, null));
        target.put("specAttrs", String.join("; ", specHints));
        List<Map<String, String>> options = new ArrayList<>();
        for (String hint : specHints) {
            Map<String, String> option = splitSpecHint(hint);
            if (!option.isEmpty()) {
                options.add(option);
            }
        }
        target.put("options", options);
        target.put("reason", hasActionableSpecHint(specHints)
                ? "系统任务已带采购采集要求，插件应按规格、尺寸或颜色优先核对候选。"
                : "商品档案缺少尺寸/规格，插件只能按图片采集并提示人工确认。");
        return target;
    }

    private boolean hasActionableSpecHint(List<String> specHints) {
        return specHints.stream().anyMatch(hint -> {
            String lower = hint == null ? "" : hint.toLowerCase(Locale.ROOT);
            return lower.startsWith("size:")
                    || lower.startsWith("size ar:")
                    || lower.startsWith("规格:")
                    || lower.startsWith("尺寸:")
                    || lower.startsWith("颜色:")
                    || lower.startsWith("product dimensions:")
                    || lower.startsWith("carton dimensions:");
        });
    }

    private Map<String, String> splitSpecHint(String hint) {
        String value = trim(hint, null);
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator >= value.length() - 1) {
            return Map.of("name", "规格", "value", value);
        }
        return Map.of(
                "name", value.substring(0, separator).trim(),
                "value", value.substring(separator + 1).trim()
        );
    }

    private String toAssignmentStatus(String taskStatus) {
        String normalized = normalize(taskStatus);
        switch (normalized) {
            case "queued":
                return "created";
            case "running":
                return "running";
            case "success":
            case "partial_success":
                return "accepted";
            case "failed":
                return "failed";
            default:
                return "cancelled";
        }
    }

    private String toResultStatus(String taskStatus) {
        String normalized = normalize(taskStatus);
        if ("success".equals(normalized) || "partial_success".equals(normalized)) {
            return "success";
        }
        if ("failed".equals(normalized)) {
            return "failed";
        }
        return null;
    }

    private String defaultMessage(String taskStatus) {
        String normalized = normalize(taskStatus);
        if ("queued".equals(normalized)) {
            return "1688 候选采集已排队，等待插件执行。";
        }
        if ("running".equals(normalized)) {
            return "1688 候选采集中。";
        }
        if ("success".equals(normalized)) {
            return "1688 候选采集完成。";
        }
        if ("partial_success".equals(normalized)) {
            return "1688 候选已部分回收。";
        }
        return "";
    }

    private Long parseTaskId(String locator) {
        String value = trim(locator, "");
        if (value.startsWith(ASSIGNMENT_PREFIX)) {
            value = value.substring(ASSIGNMENT_PREFIX.length());
        }
        if (value.toUpperCase(Locale.ROOT).startsWith("ALI1688-")) {
            value = value.substring("ALI1688-".length());
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("1688 插件任务定位信息无效。");
        }
    }

    private String assignmentId(Long taskId) {
        return ASSIGNMENT_PREFIX + taskId;
    }

    private String lockOwner(BusinessAccessContext access, Long taskId) {
        return "ali1688-plugin:" + access.getSessionUserId() + ":" + taskId;
    }

    private List<String> normalizeImageUrls(List<String> imageUrls, String mainImageUrl) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.hasText(mainImageUrl)) {
            values.add(mainImageUrl.trim());
        }
        if (imageUrls != null) {
            imageUrls.stream().filter(StringUtils::hasText).map(String::trim).forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private BigDecimal safePrice(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
    }

    private String urlHash(String url) {
        String normalized = normalizeUrl(url);
        return StringUtils.hasText(normalized) ? sha256(normalized) : null;
    }

    private String normalizeUrl(String url) {
        String value = trim(url, "").toLowerCase(Locale.ROOT);
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0) {
            value = value.substring(0, hashIndex);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST_TYPE);
            return values == null
                    ? List.of()
                    : values.stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .collect(Collectors.toList());
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String trim(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String shrink(String value, int maxLength) {
        String text = trim(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
