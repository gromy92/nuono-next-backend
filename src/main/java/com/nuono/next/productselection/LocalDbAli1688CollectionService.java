package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
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
    private final Ali1688CandidateGateService candidateGateService;
    private final Ali1688AutoInquiryEligibilityService autoInquiryEligibilityService;
    private final ObjectMapper objectMapper;

    @Value("${nuono.product-selection.ali1688.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-selection.ali1688.scheduler.max-items-per-tick:3}")
    private int schedulerMaxItems;

    @Value("${nuono.product-selection.ali1688.scheduler.lock-timeout-minutes:10}")
    private int lockTimeoutMinutes;

    @Value("${nuono.product-selection.ali1688.scheduler.max-attempts:3}")
    private int maxAttempts;

    @Value("${nuono.product-selection.ali1688.scheduler.gateway-cooldown-ms:60000}")
    private long schedulerGatewayCooldownMillis;

    private Clock schedulerClock = Clock.systemUTC();

    private SchedulerGatewayGateState schedulerGatewayGateState = SchedulerGatewayGateState.open();

    public LocalDbAli1688CollectionService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ObjectProvider<Ali1688ImageSearchGateway> imageSearchGatewayProvider,
            Ali1688CandidateScoringService scoringService,
            Ali1688CandidateAiAssessmentService aiAssessmentService,
            Ali1688CandidateGateService candidateGateService,
            Ali1688AutoInquiryEligibilityService autoInquiryEligibilityService,
            ObjectMapper objectMapper
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.imageSearchGatewayProvider = imageSearchGatewayProvider;
        this.scoringService = scoringService;
        this.aiAssessmentService = aiAssessmentService;
        this.candidateGateService = candidateGateService;
        this.autoInquiryEligibilityService = autoInquiryEligibilityService;
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
        if (!schedulerGatewayGateAllowsClaim()) {
            return 0;
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
            ali1688CollectionMapper.markTaskFailedByClaimedTask(
                    taskId,
                    "ali1688_gateway_not_configured",
                    "1688 图搜网关未配置，无法执行真实候选采集。",
                    task.updatedBy,
                    lockOwner
            );
            return;
        }
        ProductSelectionSourceCollectionRow sourceCollection = productSelectionMapper.selectSourceCollectionById(task.sourceCollectionId);
        try {
            Ali1688ImageSearchRequest request = Ali1688ImageSearchRequest.fromTask(lockOwner, task, sourceCollection, MAX_CANDIDATES);
            Ali1688ImageSearchResult result = gateway.search(request);
            List<Ali1688CollectionRecords.CandidateRecord> candidates = persistCandidates(task, lockOwner, result);
            if (candidates == null) {
                return;
            }
            selectTopFive(task.id, candidates, task.updatedBy, lockOwner);
            createPendingAiAssessmentsWithoutBlockingTask(task, candidates, lockOwner);
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
        } catch (Ali1688GatewayException exception) {
            recordSchedulerGatewayBoundary(exception);
            updateFailureSnapshot(task, lockOwner, exception);
            String failureMessage = shrink(defaultText(exception.getGatewayMessage(), "1688 候选采集失败。"), 480);
            if (exception.isRetryable() && (task.attemptCount == null || task.attemptCount < maxAttempts)) {
                ali1688CollectionMapper.markTaskRetryableFailure(
                        task.id,
                        exception.getErrorCode(),
                        failureMessage,
                        task.updatedBy,
                        lockOwner
                );
                return;
            }
            ali1688CollectionMapper.markTaskFailedByClaimedTask(task.id, exception.getErrorCode(), failureMessage, task.updatedBy, lockOwner);
        } catch (Exception exception) {
            ali1688CollectionMapper.markTaskFailedByClaimedTask(
                    task.id,
                    "ali1688_collect_failed",
                    shrink(defaultText(exception.getMessage(), "1688 候选采集失败。"), 480),
                    task.updatedBy,
                    lockOwner
            );
        }
    }

    private void createPendingAiAssessmentsWithoutBlockingTask(
            Ali1688CollectionRecords.TaskRecord task,
            List<Ali1688CollectionRecords.CandidateRecord> candidates,
            String lockOwner
    ) {
        try {
            aiAssessmentService.createPendingAssessments(task, candidates, lockOwner);
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
                // AI 补分是异步增强，失败不能覆盖已经成功的候选采集结果。
            }
        }
    }

    private boolean schedulerGatewayGateAllowsClaim() {
        Instant now = schedulerClock.instant();
        SchedulerGatewayGateState currentGate = schedulerGatewayGateSnapshot();
        if (currentGate.isCooldownActive(now)) {
            return false;
        }
        Ali1688ImageSearchGateway gateway = imageSearchGatewayProvider.getIfAvailable();
        if (gateway == null) {
            recordSchedulerGatewayStatus(Ali1688GatewayOperationalStatus.unavailable(
                    "system_browser_gateway",
                    "ali1688_gateway_not_configured",
                    false,
                    false
            ));
            return false;
        }
        Ali1688GatewayOperationalStatus status;
        try {
            status = gateway.getOperationalStatus();
        } catch (Exception exception) {
            status = Ali1688GatewayOperationalStatus.unavailable(
                    "system_browser_gateway",
                    "unexpected_response",
                    false,
                    false
            );
        }
        if (status == null) {
            status = Ali1688GatewayOperationalStatus.unavailable(
                    "system_browser_gateway",
                    "unexpected_response",
                    false,
                    false
            );
        }
        if (status.isReadyForClaim()) {
            clearSchedulerGatewayGate(status);
            return true;
        }
        recordSchedulerGatewayStatus(status);
        return false;
    }

    private void recordSchedulerGatewayBoundary(Ali1688GatewayException exception) {
        String errorCode = normalizeGatewayBoundaryCode(exception == null ? null : exception.getErrorCode());
        if (!StringUtils.hasText(errorCode)) {
            return;
        }
        recordSchedulerGatewayStatus(readGatewayStatusFromRaw(
                exception.getRawSnapshotJson(),
                errorCode
        ));
    }

    private void updateFailureSnapshot(Ali1688CollectionRecords.TaskRecord task, String lockOwner, Ali1688GatewayException exception) {
        if (!StringUtils.hasText(exception.getRawSnapshotJson()) && !StringUtils.hasText(exception.getOfficialSearchUrl())) {
            return;
        }
        ali1688CollectionMapper.updateSearchSnapshot(
                task.id,
                lockOwner,
                defaultText(task.searchMode, "主图图搜"),
                exception.getOfficialSearchUrl(),
                null,
                "[]",
                exception.getRawSnapshotJson(),
                0
        );
    }

    private List<Ali1688CollectionRecords.CandidateRecord> persistCandidates(
            Ali1688CollectionRecords.TaskRecord task,
            String lockOwner,
            Ali1688ImageSearchResult result
    ) {
        int snapshotUpdated = ali1688CollectionMapper.updateSearchSnapshot(
                task.id,
                lockOwner,
                defaultText(result == null ? null : result.searchMode, "主图图搜"),
                result == null ? null : result.officialSearchUrl,
                result == null ? null : result.searchImageId,
                writeJson(result == null ? List.of() : result.searchImageIds),
                result == null ? null : result.rawSnapshotJson,
                result == null || result.candidates == null ? 0 : result.candidates.size()
        );
        if (snapshotUpdated <= 0) {
            return null;
        }
        ali1688CollectionMapper.softDeleteCandidatesByClaimedTask(task.id, task.updatedBy, lockOwner);
        List<Ali1688ImageSearchResult.Candidate> rawCandidates = result == null || result.candidates == null ? List.of() : result.candidates;
        Set<String> seen = new LinkedHashSet<>();
        List<Ali1688CollectionRecords.CandidateRecord> persisted = new ArrayList<>();
        int rank = 1;
        for (Ali1688ImageSearchResult.Candidate raw : rawCandidates) {
            if (persisted.size() >= MAX_CANDIDATES) {
                break;
            }
            if (raw == null) {
                continue;
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
            ali1688CollectionMapper.insertCandidateForClaimedTask(candidate, lockOwner);
            persisted.add(candidate);
        }
        return persisted;
    }

    private void selectTopFive(
            Long taskId,
            List<Ali1688CollectionRecords.CandidateRecord> candidates,
            Long updatedBy,
            String lockOwner
    ) {
        ali1688CollectionMapper.clearSelectedRanksForClaimedTask(taskId, updatedBy, lockOwner);
        List<Ali1688CollectionRecords.CandidateRecord> selected = candidates.stream()
                .sorted(Comparator.comparing((Ali1688CollectionRecords.CandidateRecord item) -> item.ruleScore == null ? 0 : item.ruleScore).reversed()
                        .thenComparing(item -> item.rankNo == null ? Integer.MAX_VALUE : item.rankNo))
                .limit(TOP_FIVE_LIMIT)
                .collect(Collectors.toList());
        int selectedRank = 1;
        for (Ali1688CollectionRecords.CandidateRecord candidate : selected) {
            candidate.selectedRankNo = selectedRank;
            candidate.level = "recommended";
            ali1688CollectionMapper.updateSelectedRankForClaimedTask(taskId, candidate.id, selectedRank, updatedBy, lockOwner);
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
        DetailCompletionState detailCompletion = readDetailCompletionState(task.rawSearchSnapshotJson);
        view.detailCompletionStatus = detailCompletion.status;
        view.detailCompletionMessage = detailCompletion.message;
        Ali1688GatewayOperationalStatus gatewayStatus = resolveGatewayStatus(task);
        view.gatewayStatus = toGatewayStatusView(gatewayStatus);
        view.pluginAssistAvailable = canUsePluginAssist(task, gatewayStatus);
        view.pluginAssignment = toPluginAssignmentView(ali1688CollectionMapper.selectLatestPluginAssignmentByTask(task.id));
        List<Ali1688CollectionRecords.CandidateRecord> candidates = ali1688CollectionMapper.listCandidatesByTask(task.id);
        view.fieldCompleteness = buildFieldCompleteness(candidates);
        view.candidates = candidates.stream()
                .map(this::toCandidatePreview)
                .collect(Collectors.toList());
        view.inquiryEligibleCount = (int) view.candidates.stream()
                .filter(candidate -> Boolean.TRUE.equals(candidate.autoInquiryEligible))
                .count();
        view.inquiryBlockedCount = Math.max(0, view.candidates.size() - view.inquiryEligibleCount);
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
        Ali1688RealPriceSnapshot priceSnapshot = ali1688CollectionMapper.selectLatestRealPriceSnapshotByCandidate(candidate.id);
        Ali1688InquiryEligibilityView inquiryEligibility = autoInquiryEligibilityService.resolve(candidate, priceSnapshot);
        preview.listPriceHintText = candidate.priceText;
        preview.realPriceSnapshot = priceSnapshot;
        preview.priceState = resolvePriceState(priceSnapshot);
        preview.confirmedPriceText = confirmedPriceText(priceSnapshot);
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
        preview.inquiryEligibility = inquiryEligibility;
        preview.gate = candidateGateService.resolve(
                candidate,
                preview.priceState,
                inquiryEligibility.eligible,
                priceSnapshot == null ? null : priceSnapshot.failureCode
        );
        preview.autoInquiryEligible = inquiryEligibility.eligible;
        preview.procurementInquiryStatus = resolveProcurementInquiryStatus(inquiryEligibility, preview.priceState);
        return preview;
    }

    private String resolveProcurementInquiryStatus(Ali1688InquiryEligibilityView inquiryEligibility, String priceState) {
        if ("list_hint_only".equals(priceState) || "price_probe_pending".equals(priceState)) {
            return "PRICE_CONFIRMATION_REQUIRED";
        }
        if ("price_probe_failed".equals(priceState)) {
            return "PRICE_CONFIRMATION_FAILED";
        }
        if (inquiryEligibility == null || !StringUtils.hasText(inquiryEligibility.state)) {
            return "INQUIRY_NOT_ELIGIBLE";
        }
        if (Boolean.TRUE.equals(inquiryEligibility.eligible)) {
            return "INQUIRY_ELIGIBLE";
        }
        if ("rejected_missing_real_price".equals(inquiryEligibility.state)) {
            return "PRICE_CONFIRMATION_REQUIRED";
        }
        if ("rejected_price_failed".equals(inquiryEligibility.state)) {
            return "PRICE_CONFIRMATION_FAILED";
        }
        return "INQUIRY_NOT_ELIGIBLE";
    }

    private String resolvePriceState(Ali1688RealPriceSnapshot snapshot) {
        if (snapshot == null) {
            return "list_hint_only";
        }
        if ("confirmed".equals(snapshot.status)) {
            return "price_confirmed";
        }
        if ("failed".equals(snapshot.status)) {
            return "price_probe_failed";
        }
        return "price_probe_pending";
    }

    private String confirmedPriceText(Ali1688RealPriceSnapshot snapshot) {
        if (snapshot == null || !"confirmed".equals(snapshot.status) || snapshot.totalPrice == null) {
            return null;
        }
        String currency = StringUtils.hasText(snapshot.currency) ? snapshot.currency : "CNY";
        String amount = snapshot.totalPrice.stripTrailingZeros().toPlainString();
        return "CNY".equals(currency) ? "¥" + amount : currency + " " + amount;
    }

    private Ali1688CollectionView emptyView(ProductSelectionSourceCollectionRow sourceCollection) {
        Ali1688CollectionView view = new Ali1688CollectionView();
        view.status = "not_started";
        view.progressPercent = 0;
        view.searchMode = "主图图搜";
        view.candidateCount = 0;
        view.recommendedCount = 0;
        view.message = "暂无真实1688候选采集任务。";
        view.detailCompletionStatus = "not_attempted";
        view.detailCompletionMessage = "未执行详情页补全。";
        view.fieldCompleteness = new Ali1688CollectionView.FieldCompleteness();
        view.gatewayStatus = toGatewayStatusView(schedulerGatewayGateSnapshot().status);
        view.pluginAssistAvailable = false;
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

    private boolean canUsePluginAssist(
            Ali1688CollectionRecords.TaskRecord task,
            Ali1688GatewayOperationalStatus gatewayStatus
    ) {
        if (task == null || task.id == null || !StringUtils.hasText(task.currentTaskKey) || gatewayStatus == null) {
            return false;
        }
        return "blocked_by_captcha".equals(gatewayStatus.userFacingStatus)
                || "login_required".equals(gatewayStatus.userFacingStatus)
                || "cooling_down".equals(gatewayStatus.userFacingStatus)
                || "unavailable".equals(gatewayStatus.userFacingStatus);
    }

    private Ali1688PluginAssignmentView toPluginAssignmentView(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
        if (assignment == null) {
            return null;
        }
        Ali1688PluginAssignmentView view = new Ali1688PluginAssignmentView();
        view.assignmentId = assignment.id == null ? null : String.valueOf(assignment.id);
        view.taskId = assignment.taskId == null ? null : String.valueOf(assignment.taskId);
        view.sourceCollectionId = assignment.sourceCollectionId == null ? null : String.valueOf(assignment.sourceCollectionId);
        view.taskNo = assignment.taskNo;
        view.status = defaultText(assignment.status, "created");
        view.sourceImageUrl = assignment.sourceImageUrl;
        view.sourceTitle = assignment.sourceTitle;
        view.sourceTitleCn = assignment.sourceTitleCn;
        view.sourceUrl = assignment.sourceUrl;
        view.pageUrl = assignment.pageUrl;
        view.storeId = assignment.logicalStoreId == null ? null : String.valueOf(assignment.logicalStoreId);
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
        view.message = resolvePluginAssignmentMessage(assignment);
        return view;
    }

    private String resolvePluginAssignmentMessage(Ali1688CollectionRecords.PluginAssignmentRecord assignment) {
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

    private String toMinute(String value) {
        String text = trim(value);
        return text != null && text.length() >= 16 ? text.substring(0, 16) : text;
    }

    private Ali1688GatewayOperationalStatus resolveGatewayStatus(Ali1688CollectionRecords.TaskRecord task) {
        Ali1688GatewayOperationalStatus rawStatus = readGatewayStatusFromRaw(
                task == null ? null : task.rawSearchSnapshotJson,
                task == null ? null : task.failureCode
        );
        SchedulerGatewayGateState gate = schedulerGatewayGateSnapshot();
        if (gate.isActive()
                && task != null
                && ("queued".equals(task.status) || "running".equals(task.status) || "waiting_source".equals(task.status))) {
            return gate.status;
        }
        return rawStatus;
    }

    private Ali1688GatewayOperationalStatus readGatewayStatusFromRaw(String rawSearchSnapshotJson, String fallbackCode) {
        String serviceKind = "unknown";
        String sessionState = normalizeGatewayBoundaryCode(fallbackCode);
        Boolean runtimeReady = null;
        Boolean captchaAutoSolveEnabled = null;
        if (StringUtils.hasText(rawSearchSnapshotJson)) {
            try {
                JsonNode root = objectMapper.readTree(rawSearchSnapshotJson);
                serviceKind = defaultText(trim(root.path("gatewayServiceKind").asText(null)), serviceKind);
                String rawSessionState = trim(root.path("sessionState").asText(null));
                if (!StringUtils.hasText(sessionState) && StringUtils.hasText(rawSessionState)) {
                    sessionState = rawSessionState;
                }
                if (root.has("runtimeReady") && !root.path("runtimeReady").isNull()) {
                    runtimeReady = root.path("runtimeReady").asBoolean();
                }
                if (root.has("captchaAutoSolveEnabled") && !root.path("captchaAutoSolveEnabled").isNull()) {
                    captchaAutoSolveEnabled = root.path("captchaAutoSolveEnabled").asBoolean();
                }
            } catch (JsonProcessingException exception) {
                sessionState = "unexpected_response";
            }
        }
        if (!StringUtils.hasText(sessionState)) {
            sessionState = "unknown";
        }
        return Ali1688GatewayOperationalStatus.from(serviceKind, sessionState, runtimeReady, captchaAutoSolveEnabled);
    }

    private Ali1688CollectionView.GatewayStatus toGatewayStatusView(Ali1688GatewayOperationalStatus status) {
        Ali1688GatewayOperationalStatus safeStatus = status == null
                ? Ali1688GatewayOperationalStatus.unavailable("unknown", "unknown", null, null)
                : status;
        Ali1688CollectionView.GatewayStatus viewStatus = new Ali1688CollectionView.GatewayStatus();
        viewStatus.gatewayServiceKind = safeStatus.gatewayServiceKind;
        viewStatus.sessionState = safeStatus.sessionState;
        viewStatus.runtimeReady = safeStatus.runtimeReady;
        viewStatus.captchaAutoSolveEnabled = safeStatus.captchaAutoSolveEnabled;
        viewStatus.userFacingStatus = safeStatus.userFacingStatus;
        viewStatus.userFacingMessage = safeStatus.userFacingMessage;
        return viewStatus;
    }

    private String normalizeGatewayBoundaryCode(String value) {
        String code = trim(value);
        if ("captcha_required".equals(code) || "login_required".equals(code) || "rate_limited".equals(code)) {
            return code;
        }
        return null;
    }

    private DetailCompletionState readDetailCompletionState(String rawSearchSnapshotJson) {
        String status = "not_attempted";
        String message = "未执行详情页补全。";
        if (StringUtils.hasText(rawSearchSnapshotJson)) {
            try {
                JsonNode root = objectMapper.readTree(rawSearchSnapshotJson);
                String rawStatus = trim(root.path("detailCompletionOutcome").asText(null));
                String rawMessage = trim(root.path("detailCompletionMessage").asText(null));
                if (StringUtils.hasText(rawStatus)) {
                    status = rawStatus;
                    message = StringUtils.hasText(rawMessage) ? rawMessage : defaultDetailCompletionMessage(status);
                }
            } catch (JsonProcessingException exception) {
                status = "unknown";
                message = "详情页补全诊断解析失败。";
            }
        }
        return new DetailCompletionState(status, message);
    }

    private Ali1688CollectionView.FieldCompleteness buildFieldCompleteness(List<Ali1688CollectionRecords.CandidateRecord> candidates) {
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

    private String defaultDetailCompletionMessage(String status) {
        if ("blocked_by_captcha".equals(status)) {
            return "1688 详情页受限，详情字段待补充。";
        }
        if ("completed".equals(status)) {
            return "详情页补全完成。";
        }
        if ("partial_enriched".equals(status)) {
            return "详情页部分补全，仍有字段待补充。";
        }
        if ("failed".equals(status)) {
            return "详情页补全失败，详情字段待补充。";
        }
        return "未执行详情页补全。";
    }

    private static class DetailCompletionState {
        private final String status;
        private final String message;

        private DetailCompletionState(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private synchronized SchedulerGatewayGateState schedulerGatewayGateSnapshot() {
        return schedulerGatewayGateState;
    }

    private synchronized void recordSchedulerGatewayStatus(Ali1688GatewayOperationalStatus status) {
        Ali1688GatewayOperationalStatus safeStatus = status == null
                ? Ali1688GatewayOperationalStatus.unavailable("unknown", "unknown", false, false)
                : status;
        long cooldownMillis = Math.max(1L, schedulerGatewayCooldownMillis);
        Instant cooldownUntil = safeStatus.shouldCooldown()
                ? schedulerClock.instant().plusMillis(cooldownMillis)
                : schedulerClock.instant();
        schedulerGatewayGateState = SchedulerGatewayGateState.blocked(safeStatus, cooldownUntil);
    }

    private synchronized void clearSchedulerGatewayGate(Ali1688GatewayOperationalStatus status) {
        schedulerGatewayGateState = SchedulerGatewayGateState.ready(status);
    }

    private static class SchedulerGatewayGateState {
        private final Ali1688GatewayOperationalStatus status;
        private final Instant cooldownUntil;

        private SchedulerGatewayGateState(Ali1688GatewayOperationalStatus status, Instant cooldownUntil) {
            this.status = status;
            this.cooldownUntil = cooldownUntil;
        }

        private static SchedulerGatewayGateState open() {
            return new SchedulerGatewayGateState(
                    Ali1688GatewayOperationalStatus.unavailable("unknown", "unknown", null, null),
                    null
            );
        }

        private static SchedulerGatewayGateState ready(Ali1688GatewayOperationalStatus status) {
            return new SchedulerGatewayGateState(status, null);
        }

        private static SchedulerGatewayGateState blocked(Ali1688GatewayOperationalStatus status, Instant cooldownUntil) {
            return new SchedulerGatewayGateState(status, cooldownUntil);
        }

        private boolean isActive() {
            return status != null && !"available".equals(status.userFacingStatus) && !"unknown".equals(status.sessionState);
        }

        private boolean isCooldownActive(Instant now) {
            return isActive() && cooldownUntil != null && now != null && now.isBefore(cooldownUntil);
        }
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
