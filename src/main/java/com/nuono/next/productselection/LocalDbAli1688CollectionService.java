package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbAli1688CollectionService {

    private static final String WORKER_NAME = "ali1688-collection-scheduler";
    private static final int TOP_FIVE_LIMIT = 5;
    private static final int MAX_CANDIDATES = 10;

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ObjectProvider<Ali1688ImageSearchGateway> imageSearchGatewayProvider;
    private final Ali1688CandidateScoringService scoringService;
    private final Ali1688CandidateAiAssessmentService aiAssessmentService;
    private final ObjectMapper objectMapper;

    @Value("${nuono.product-selection.ali1688.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-selection.ali1688.scheduler.max-items-per-tick:3}")
    private int schedulerMaxItems;

    @Value("${nuono.product-selection.ali1688.scheduler.lock-timeout-minutes:10}")
    private int lockTimeoutMinutes;

    @Value("${nuono.product-selection.ali1688.scheduler.max-attempts:3}")
    private int maxAttempts;

    public LocalDbAli1688CollectionService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ObjectProvider<Ali1688ImageSearchGateway> imageSearchGatewayProvider,
            Ali1688CandidateScoringService scoringService,
            Ali1688CandidateAiAssessmentService aiAssessmentService,
            ObjectMapper objectMapper
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.imageSearchGatewayProvider = imageSearchGatewayProvider;
        this.scoringService = scoringService;
        this.aiAssessmentService = aiAssessmentService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Ali1688CollectionView ensureTaskForSourceCollection(ProductSelectionSourceCollectionRow sourceCollection, Long operatorUserId) {
        if (sourceCollection == null || sourceCollection.getId() == null) {
            return emptyView(null);
        }
        Ali1688CollectionRecords.TaskRecord existing = ali1688CollectionMapper.selectCurrentTaskBySourceId(sourceCollection.getId());
        if (existing != null) {
            return toCollectionView(existing);
        }
        return toCollectionView(createTask(sourceCollection, operatorUserId));
    }

    @Transactional
    public Ali1688CollectionView resetTaskForSourceRecollect(ProductSelectionSourceCollectionRow sourceCollection, Long operatorUserId) {
        if (sourceCollection == null || sourceCollection.getId() == null) {
            return emptyView(null);
        }
        ali1688CollectionMapper.supersedeCurrentTask(sourceCollection.getId(), operatorUserId);
        return toCollectionView(createTask(sourceCollection, operatorUserId));
    }

    @Transactional
    public void markSourceCollectionSucceeded(ProductSelectionSourceCollectionRow sourceCollection) {
        if (sourceCollection == null || sourceCollection.getId() == null) {
            return;
        }
        ali1688CollectionMapper.markCurrentTaskQueued(
                sourceCollection.getId(),
                sourceCollection.getSourceImageUrl(),
                readStringListJson(sourceCollection.getImageUrlsJson()).size(),
                sourceCollection.getUpdatedBy()
        );
    }

    @Transactional
    public void markSourceCollectionFailed(Long sourceCollectionId, String failureMessage, Long updatedBy) {
        if (sourceCollectionId == null) {
            return;
        }
        ali1688CollectionMapper.markCurrentTaskFailed(
                sourceCollectionId,
                "source_collection_failed",
                shrink(defaultText(failureMessage, "源头商品采集失败，1688 候选采集未启动。"), 480),
                updatedBy
        );
    }

    public List<Ali1688CollectionView> listCollections(String storeName, String storeCode, String status, Long operatorUserId) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        String rawStatus = "not_started".equals(status) ? "waiting_source" : status;
        return ali1688CollectionMapper.listCurrentTasks(scope.getLogicalStoreId(), rawStatus, 80).stream()
                .map(this::toCollectionView)
                .collect(Collectors.toList());
    }

    public Ali1688CollectionView getCurrentView(Long sourceCollectionId) {
        if (sourceCollectionId == null) {
            return emptyView(null);
        }
        return toCollectionView(ali1688CollectionMapper.selectCurrentTaskBySourceId(sourceCollectionId));
    }

    public Ali1688CollectionView getSourceCollectionAli1688(String collectionId, Long operatorUserId) {
        ProductSelectionSourceCollectionRow sourceCollection = requireVisibleSourceCollection(collectionId, operatorUserId);
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectCurrentTaskBySourceId(sourceCollection.getId());
        return task == null ? emptyView(sourceCollection) : toCollectionView(task);
    }

    @Transactional
    public Ali1688CollectionView recollectSourceCollectionAli1688(String collectionId, Long operatorUserId) {
        ProductSelectionSourceCollectionRow sourceCollection = requireVisibleSourceCollection(collectionId, operatorUserId);
        Ali1688CollectionRecords.TaskRecord current = ali1688CollectionMapper.selectCurrentTaskBySourceId(sourceCollection.getId());
        if (current != null && "running".equals(current.status)) {
            throw new IllegalArgumentException("1688 候选采集正在执行，不能重跑。");
        }
        ali1688CollectionMapper.supersedeCurrentTask(sourceCollection.getId(), operatorUserId);
        return toCollectionView(createTask(sourceCollection, operatorUserId));
    }

    @Transactional
    public Ali1688CollectionView retryAli1688Collection(String taskId, Long operatorUserId) {
        Long id = parseLongId(taskId, "1688 采集任务不存在或已被删除。");
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(id);
        requireVisibleTask(operatorUserId, task);
        if (task.currentTaskKey == null || !"failed".equals(task.status)) {
            throw new IllegalArgumentException("只有当前失败的 1688 采集任务可以重试。");
        }
        ali1688CollectionMapper.retryFailedTask(id, operatorUserId);
        return toCollectionView(ali1688CollectionMapper.selectTaskById(id));
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-selection.ali1688.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nuono.product-selection.ali1688.scheduler.initial-delay-ms:3000}"
    )
    public void processQueuedTasks() {
        if (!schedulerEnabled) {
            return;
        }
        processQueuedTasksOnce();
    }

    public int processQueuedTasksOnce() {
        int limit = Math.max(1, Math.min(schedulerMaxItems, 10));
        for (Long taskId : ali1688CollectionMapper.listExpiredOverRetryTaskIds(maxAttempts, lockTimeoutMinutes, limit)) {
            ali1688CollectionMapper.markTaskFailed(taskId, "max_attempts_exceeded", "1688 候选采集超过最大重试次数。", null);
        }
        int processed = 0;
        for (Long taskId : ali1688CollectionMapper.listClaimableTaskIds(maxAttempts, lockTimeoutMinutes, limit)) {
            String lockOwner = WORKER_NAME + "-" + UUID.randomUUID();
            if (ali1688CollectionMapper.claimTask(taskId, lockOwner, maxAttempts, lockTimeoutMinutes) <= 0) {
                continue;
            }
            runTask(taskId, lockOwner);
            processed++;
        }
        return processed;
    }

    private void runTask(Long taskId, String lockOwner) {
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(taskId);
        if (task == null) {
            return;
        }
        Ali1688ImageSearchGateway gateway = imageSearchGatewayProvider.getIfAvailable();
        if (gateway == null) {
            ali1688CollectionMapper.markTaskFailed(taskId, "ali1688_gateway_not_configured", "1688 图搜网关未配置，无法执行真实候选采集。", task.updatedBy);
            return;
        }
        ProductSelectionSourceCollectionRow sourceCollection = productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId);
        try {
            Ali1688ImageSearchResult result = gateway.search(sourceCollection);
            List<Ali1688CollectionRecords.CandidateRecord> candidates = persistCandidates(task, result);
            selectTopFive(task.id, candidates, task.updatedBy);
            aiAssessmentService.createPendingAssessments(task, candidates);
            String status = candidates.size() >= MAX_CANDIDATES ? "success" : candidates.isEmpty() ? "failed" : "partial_success";
            String failureCode = candidates.isEmpty() ? "no_valid_candidate" : candidates.size() < MAX_CANDIDATES ? "candidate_count_less_than_10" : null;
            String failureMessage = candidates.isEmpty() ? "1688 图搜未回收有效候选。"
                    : candidates.size() < MAX_CANDIDATES ? "1688 图搜候选不足 10 个，已展示可用候选。" : null;
            ali1688CollectionMapper.markTaskCompleted(
                    task.id,
                    status,
                    result == null || result.candidates == null ? 0 : result.candidates.size(),
                    candidates.size(),
                    Math.min(candidates.size(), TOP_FIVE_LIMIT),
                    failureCode,
                    failureMessage,
                    task.updatedBy,
                    lockOwner
            );
        } catch (Exception exception) {
            ali1688CollectionMapper.markTaskFailed(task.id, "ali1688_collect_failed", shrink(defaultText(exception.getMessage(), "1688 候选采集失败。"), 480), task.updatedBy);
        }
    }

    private List<Ali1688CollectionRecords.CandidateRecord> persistCandidates(Ali1688CollectionRecords.TaskRecord task, Ali1688ImageSearchResult result) {
        ali1688CollectionMapper.updateSearchSnapshot(
                task.id,
                task.lockedBy,
                defaultText(result == null ? null : result.searchMode, "主图图搜"),
                result == null ? null : result.officialSearchUrl,
                result == null ? null : result.searchImageId,
                writeJson(result == null ? List.of() : result.searchImageIds),
                result == null ? null : result.rawSnapshotJson,
                result == null || result.candidates == null ? 0 : result.candidates.size()
        );
        ali1688CollectionMapper.softDeleteCandidatesByTask(task.id, task.updatedBy);
        List<Ali1688ImageSearchResult.Candidate> rawCandidates = result == null || result.candidates == null ? List.of() : result.candidates;
        Set<String> seen = new LinkedHashSet<>();
        List<Ali1688CollectionRecords.CandidateRecord> persisted = new ArrayList<>();
        int rank = 1;
        for (Ali1688ImageSearchResult.Candidate raw : rawCandidates) {
            if (raw == null || persisted.size() >= MAX_CANDIDATES) {
                break;
            }
            String urlHash = urlHash(raw.candidateUrl);
            String dedupeKey = StringUtils.hasText(raw.offerId) ? "offer:" + raw.offerId.trim() : "url:" + urlHash;
            if (!StringUtils.hasText(urlHash) && !StringUtils.hasText(raw.offerId)) {
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
            candidate.offerId = trim(raw.offerId);
            candidate.candidateUrl = trim(raw.candidateUrl);
            candidate.candidateUrlHash = urlHash;
            candidate.activeCandidateKey = task.id + ":" + (StringUtils.hasText(candidate.offerId) ? candidate.offerId : urlHash);
            candidate.title = trim(raw.title);
            candidate.supplierName = trim(raw.supplierName);
            candidate.priceText = trim(raw.priceText);
            candidate.priceMin = raw.priceMin;
            candidate.priceMax = raw.priceMax;
            candidate.moqText = trim(raw.moqText);
            candidate.moqValue = raw.moqValue;
            candidate.locationText = trim(raw.locationText);
            candidate.mainImageUrl = trim(raw.mainImageUrl);
            candidate.imageUrlsJson = writeJson(normalizeImageUrls(raw.imageUrls, raw.mainImageUrl));
            candidate.badgesJson = writeJson(raw.badges);
            candidate.skuSnapshotJson = writeJson(raw.skuSnapshot);
            candidate.supplierSnapshotJson = writeJson(raw.supplierSnapshot);
            candidate.logisticsSnapshotJson = writeJson(raw.logisticsSnapshot);
            candidate.aiAssessmentStatus = "pending";
            candidate.createdBy = task.updatedBy;
            candidate.updatedBy = task.updatedBy;
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
            candidate.selectedRankNo = selectedRank;
            candidate.level = "recommended";
            ali1688CollectionMapper.updateSelectedRank(taskId, candidate.id, selectedRank, updatedBy);
            selectedRank++;
        }
    }

    private Ali1688CollectionRecords.TaskRecord createTask(ProductSelectionSourceCollectionRow sourceCollection, Long operatorUserId) {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = ali1688CollectionMapper.nextTaskId();
        task.sourceCollectionId = sourceCollection.getId();
        task.currentTaskKey = String.valueOf(sourceCollection.getId());
        task.ownerUserId = sourceCollection.getOwnerUserId();
        task.logicalStoreId = sourceCollection.getLogicalStoreId();
        task.taskNo = "ALI1688-" + task.id;
        task.status = "success".equals(sourceCollection.getStatus()) ? "queued" : "waiting_source";
        task.progressPercent = "queued".equals(task.status) ? 5 : 0;
        task.searchMode = "主图图搜";
        task.sourceImageUrl = sourceCollection.getSourceImageUrl();
        task.selectedImageCount = readStringListJson(sourceCollection.getImageUrlsJson()).size();
        task.scannedCount = 0;
        task.candidateCount = 0;
        task.recommendedCount = 0;
        task.createdBy = operatorUserId;
        task.updatedBy = operatorUserId;
        ali1688CollectionMapper.insertTask(task);
        return ali1688CollectionMapper.selectTaskById(task.id);
    }

    private Ali1688CollectionView toCollectionView(Ali1688CollectionRecords.TaskRecord task) {
        if (task == null) {
            return emptyView(null);
        }
        Ali1688CollectionView view = new Ali1688CollectionView();
        view.id = String.valueOf(task.id);
        view.taskId = String.valueOf(task.id);
        view.sourceCollectionId = String.valueOf(task.sourceCollectionId);
        view.sourceCollectionNo = task.sourceCollectionNo;
        view.storeId = task.logicalStoreId == null ? null : String.valueOf(task.logicalStoreId);
        view.storeName = task.storeName;
        view.storeCode = task.storeCode;
        view.sourcePlatform = task.sourcePlatform;
        view.sourceTitle = task.sourceTitle;
        view.sourceTitleCn = task.sourceTitleCn;
        view.sourceUrl = task.sourceUrl;
        view.pageUrl = task.pageUrl;
        view.status = toViewStatus(task.status);
        view.progressPercent = task.progressPercent == null ? 0 : task.progressPercent;
        view.searchMode = task.searchMode;
        view.sourceImageUrl = task.sourceImageUrl;
        view.selectedImageCount = task.selectedImageCount;
        view.scannedCount = task.scannedCount;
        view.candidateCount = task.candidateCount;
        view.recommendedCount = task.recommendedCount;
        view.failureCode = task.failureCode;
        view.failureMessage = task.failureMessage;
        view.startedAt = task.startedAt;
        view.finishedAt = task.finishedAt;
        view.message = resolveMessage(task);
        view.canGenerateProcurementOrder = false;
        view.candidates = ali1688CollectionMapper.listCandidatesByTask(task.id).stream()
                .map(this::toCandidatePreview)
                .collect(Collectors.toList());
        return view;
    }

    private Ali1688CollectionView.Ali1688CandidatePreview toCandidatePreview(Ali1688CollectionRecords.CandidateRecord candidate) {
        Ali1688CollectionView.Ali1688CandidatePreview preview = new Ali1688CollectionView.Ali1688CandidatePreview();
        preview.id = String.valueOf(candidate.id);
        preview.rankNo = candidate.rankNo;
        preview.selectedRankNo = candidate.selectedRankNo;
        preview.level = defaultText(candidate.level, "review");
        preview.offerId = candidate.offerId;
        preview.title = defaultText(candidate.title, "未命名 1688 候选");
        preview.supplierName = defaultText(candidate.supplierName, "供应商待解析");
        preview.candidateUrl = candidate.candidateUrl;
        preview.priceText = candidate.priceText;
        preview.moqText = candidate.moqText;
        preview.locationText = candidate.locationText;
        preview.imageUrl = candidate.mainImageUrl;
        preview.imageUrls = readStringListJson(candidate.imageUrlsJson);
        preview.ruleScore = candidate.ruleScore;
        preview.totalScore = candidate.totalScore;
        preview.scoreStatus = candidate.scoreStatus;
        preview.scoreBreakdown.matchScore = candidate.matchScore;
        preview.scoreBreakdown.specScore = candidate.specScore;
        preview.scoreBreakdown.priceScore = candidate.priceScore;
        preview.scoreBreakdown.moqScore = candidate.moqScore;
        preview.scoreBreakdown.supplierScore = candidate.supplierScore;
        preview.scoreBreakdown.deliveryScore = candidate.deliveryScore;
        preview.aiAssessmentStatus = candidate.aiAssessmentStatus;
        preview.procurementInquiryStatus = candidate.selectedRankNo == null ? "BACKUP_POOL" : "IN_POOL_WAITING_SEND";
        return preview;
    }

    private Ali1688CollectionView emptyView(ProductSelectionSourceCollectionRow sourceCollection) {
        Ali1688CollectionView view = new Ali1688CollectionView();
        view.status = "not_started";
        view.progressPercent = 0;
        view.searchMode = "主图图搜";
        view.candidateCount = 0;
        view.recommendedCount = 0;
        view.message = "暂无真实1688候选采集任务。";
        view.canGenerateProcurementOrder = false;
        if (sourceCollection != null) {
            view.sourceCollectionId = sourceCollection.getId() == null ? null : String.valueOf(sourceCollection.getId());
            view.sourceCollectionNo = sourceCollection.getCollectionNo();
            view.storeId = sourceCollection.getLogicalStoreId() == null ? null : String.valueOf(sourceCollection.getLogicalStoreId());
            view.storeName = sourceCollection.getStoreName();
            view.storeCode = sourceCollection.getStoreCode();
            view.sourcePlatform = sourceCollection.getSourcePlatform();
            view.sourceTitle = sourceCollection.getSourceTitle();
            view.sourceTitleCn = sourceCollection.getSourceTitleCn();
            view.sourceUrl = sourceCollection.getSourceUrl();
            view.pageUrl = sourceCollection.getPageUrl();
            view.sourceImageUrl = sourceCollection.getSourceImageUrl();
        }
        return view;
    }

    private ProductSelectionSourceCollectionRow requireVisibleSourceCollection(String collectionId, Long operatorUserId) {
        Long sourceCollectionId = parseLongId(collectionId, "采集记录不存在或已被删除。");
        ProductSelectionSourceCollectionRow row = productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
        if (row == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        requireVisibleLogicalStore(operatorUserId, row.getLogicalStoreId());
        return row;
    }

    private void requireVisibleTask(Long operatorUserId, Ali1688CollectionRecords.TaskRecord task) {
        if (task == null) {
            throw new IllegalArgumentException("1688 采集任务不存在或已被删除。");
        }
        requireVisibleLogicalStore(operatorUserId, task.logicalStoreId);
    }

    private void requireVisibleLogicalStore(Long operatorUserId, Long logicalStoreId) {
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (user != null && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()))) {
            return;
        }
        if (logicalStoreId == null || productSelectionMapper.countVisibleLogicalStoreSites(operatorUserId, logicalStoreId) <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该 1688 采集任务。");
        }
    }

    private String resolveMessage(Ali1688CollectionRecords.TaskRecord task) {
        if (StringUtils.hasText(task.failureMessage)) {
            return task.failureMessage;
        }
        if ("waiting_source".equals(task.status)) {
            return "源头商品仍在采集中，1688 候选采集等待源头成功。";
        }
        if ("queued".equals(task.status)) {
            return "1688 候选采集已排队。";
        }
        if ("running".equals(task.status)) {
            return "1688 候选采集中。";
        }
        if ("partial_success".equals(task.status)) {
            return "1688 候选已部分回收。";
        }
        if ("success".equals(task.status)) {
            return "1688 候选采集完成。";
        }
        return "";
    }

    private String toViewStatus(String status) {
        if ("waiting_source".equals(status) || "superseded".equals(status)) {
            return "not_started";
        }
        return defaultText(status, "not_started");
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

    private String urlHash(String url) {
        String normalized = normalizeUrl(url);
        return StringUtils.hasText(normalized) ? sha256(normalized) : null;
    }

    private String normalizeUrl(String url) {
        String value = defaultText(url, "").trim().toLowerCase();
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

    private List<String> readStringListJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            }).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
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

    private Long parseLongId(String id, String message) {
        try {
            return Long.parseLong(defaultText(id, ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String shrink(String value, int maxLength) {
        String text = defaultText(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
