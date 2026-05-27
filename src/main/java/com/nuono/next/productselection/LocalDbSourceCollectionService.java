package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbSourceCollectionService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SOURCE_COLLECTION_WORKER = "source-collection-scheduler";
    private static final Set<String> ACTIVE_MARKETPLACE_URL_PLATFORMS = Set.of("Noon", "Amazon");
    private static final String SHEIN_COLLECTION_PAUSED_MESSAGE = "SHEIN 完整采集暂缓，当前仅验收 Amazon / Noon。";
    private static final String BATCH_RUNNER_SAMPLE_NOTE_PREFIX = "batch-runner sample:";
    private static final int DEFAULT_LIST_PAGE_SIZE = 50;
    private static final int MAX_LIST_PAGE_SIZE = 100;

    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ProductSelectionSourceCollectionCollector sourceCollectionCollector;
    private final ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;
    private final SourceCollectionCompletenessCalculator completenessCalculator;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectMapper objectMapper;

    @Value("${nuono.product-selection.source-collection.scheduler.enabled:false}")
    private boolean sourceCollectionSchedulerEnabled;

    @Value("${nuono.product-selection.source-collection.scheduler.max-items-per-tick:3}")
    private int sourceCollectionSchedulerMaxItems;

    public LocalDbSourceCollectionService(
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ProductSelectionSourceCollectionCollector sourceCollectionCollector,
            ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer,
            SourceCollectionCompletenessCalculator completenessCalculator,
            LocalDbAli1688CollectionService ali1688CollectionService,
            ObjectMapper objectMapper
    ) {
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.sourceCollectionCollector = sourceCollectionCollector;
        this.sourceCollectionLocalizer = sourceCollectionLocalizer;
        this.completenessCalculator = completenessCalculator;
        this.ali1688CollectionService = ali1688CollectionService;
        this.objectMapper = objectMapper;
    }

    public List<ProductSelectionSourceCollectionView> listSourceCollections(
            String storeName,
            String storeCode,
            Long operatorUserId
    ) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        return listSourceCollections(scope.getLogicalStoreId(), 50);
    }

    public List<ProductSelectionSourceCollectionView> listSourceCollections(Long logicalStoreId, int limit) {
        return productSelectionMapper.listSourceCollections(logicalStoreId, limit).stream()
                .map(this::toSourceCollectionView)
                .collect(Collectors.toList());
    }

    public ProductSelectionSourceCollectionPageView listSourceCollectionsPage(
            String storeName,
            String storeCode,
            Long operatorUserId,
            ProductSelectionSourceCollectionListQuery query
    ) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        ProductSelectionSourceCollectionListQuery source = query == null
                ? new ProductSelectionSourceCollectionListQuery()
                : query;
        int page = normalizedPage(source.getPage());
        int pageSize = normalizedPageSize(source.getPageSize());
        int offset = (page - 1) * pageSize;
        String sourcePlatform = normalizedText(source.getSourcePlatform());
        String sourceTitle = normalizedText(source.getSourceTitle());
        String sourceTitleCn = normalizedText(source.getSourceTitleCn());
        String status = normalizedText(source.getStatus());
        int total = productSelectionMapper.countSourceCollections(
                scope.getLogicalStoreId(),
                sourcePlatform,
                sourceTitle,
                sourceTitleCn,
                status
        );
        List<ProductSelectionSourceCollectionView> items = productSelectionMapper.listSourceCollectionsPage(
                        scope.getLogicalStoreId(),
                        sourcePlatform,
                        sourceTitle,
                        sourceTitleCn,
                        status,
                        pageSize,
                        offset
                ).stream()
                .map(this::toSourceCollectionView)
                .collect(Collectors.toList());
        ProductSelectionSourceCollectionPageView pageView = new ProductSelectionSourceCollectionPageView();
        pageView.setItems(items);
        pageView.setTotal(total);
        pageView.setPage(page);
        pageView.setPageSize(pageSize);
        return pageView;
    }

    @Transactional
    public ProductSelectionSourceCollectionView createSourceCollection(ProductSelectionSourceCollectionCommand command) {
        ProductSelectionSourceCollectionCommand source = command == null
                ? new ProductSelectionSourceCollectionCommand()
                : command;
        ProductSelectionStoreScope scope = permissionGuard.requireWritableStore(
                source.getOperatorUserId(),
                source.getStoreCode()
        );
        String sourceType = defaultText(source.getSourceType(), "image-search-source");
        String pageUrl = defaultText(source.getPageUrl(), source.getSourceUrl());
        String sourceUrl = defaultText(source.getSourceUrl(), source.getPageUrl());
        boolean marketplaceUrlCollection = "marketplace-url".equals(sourceType);
        List<String> imageUrls = normalizeList(source.getImageUrls(), 20);
        String mainImageUrl = defaultText(source.getSourceImageUrl(), "");
        if (StringUtils.hasText(mainImageUrl) && !imageUrls.contains(mainImageUrl)) {
            imageUrls.add(0, mainImageUrl);
        }
        if (!marketplaceUrlCollection && imageUrls.isEmpty()) {
            throw new IllegalArgumentException("至少需要一张源头商品图片，才能进入图搜采集。");
        }
        if (marketplaceUrlCollection && !StringUtils.hasText(pageUrl)) {
            throw new IllegalArgumentException("请填写 Noon、Amazon 或 SHEIN 商品页链接。");
        }
        String sourcePlatform = defaultText(
                source.getSourcePlatform(),
                inferSourcePlatform(defaultText(pageUrl, sourceUrl))
        );
        if (marketplaceUrlCollection && isShein(sourcePlatform)) {
            throw new IllegalArgumentException(SHEIN_COLLECTION_PAUSED_MESSAGE);
        }
        if (marketplaceUrlCollection && !isActiveMarketplaceUrlPlatform(sourcePlatform)) {
            throw new IllegalArgumentException("链接自动采集当前只支持 Noon 和 Amazon；SHEIN 完整采集暂缓。");
        }

        Long id = productSelectionMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(scope.getOwnerUserId());
        row.setLogicalStoreId(scope.getLogicalStoreId());
        row.setCollectionNo("PSC-" + id);
        row.setSourceType(sourceType);
        row.setSourceUrl(sourceUrl);
        row.setPageUrl(pageUrl);
        row.setSourcePlatform(sourcePlatform);
        row.setSourceTitle(defaultText(source.getSourceTitle(), marketplaceUrlCollection ? "采集中..." : "未命名源头商品"));
        row.setSourceTitleCn(defaultText(source.getSourceTitleCn(), source.getSelectedText()));
        row.setSourceTitleAr(defaultText(source.getSourceTitleAr(), ""));
        row.setSourceImageUrl(imageUrls.isEmpty() ? "" : imageUrls.get(0));
        row.setImageUrlsJson(writeStringListJson(imageUrls));
        row.setPriceSummary(defaultText(source.getPriceSummary(), ""));
        row.setMoqHint(defaultText(source.getMoqHint(), ""));
        row.setShippingFrom(defaultText(source.getShippingFrom(), ""));
        List<String> specHints = normalizeList(source.getSpecHints(), 20);
        row.setBrandName(completenessCalculator.firstSpecValue(specHints, Set.of("brand", "brand name")));
        row.setUnitCount(completenessCalculator.firstSpecValue(specHints, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS));
        row.setColorName(completenessCalculator.firstSpecValue(specHints, Set.of("color", "colour")));
        row.setSpecHintsJson(writeStringListJson(specHints));
        row.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specHints));
        row.setSourceDescriptionEn(defaultText(source.getSourceDescriptionEn(), ""));
        row.setSourceDescriptionAr(defaultText(source.getSourceDescriptionAr(), source.getSelectedTextAr()));
        row.setSourceSellingPointsEnJson(writeStringListJson(normalizeList(source.getSourceSellingPointsEn(), 12)));
        row.setSourceSellingPointsArJson(writeStringListJson(normalizeList(source.getSourceSellingPointsAr(), 12)));
        row.setSelectedText(defaultText(source.getSelectedText(), ""));
        row.setSelectedTextAr(defaultText(source.getSelectedTextAr(), ""));
        row.setNotes(sanitizeSourceCollectionNotes(source.getNotes()));
        row.setStatus(marketplaceUrlCollection ? "running" : "success");
        row.setCreatedBy(source.getOperatorUserId());
        row.setUpdatedBy(source.getOperatorUserId());
        productSelectionMapper.insertSourceCollection(row);
        ProductSelectionSourceCollectionRow inserted = productSelectionMapper.selectSourceCollectionById(id);
        ProductSelectionSourceCollectionRow effectiveRow = inserted == null ? row : inserted;
        safeEnsureAli1688Task(effectiveRow, source.getOperatorUserId());
        return toSourceCollectionView(effectiveRow);
    }

    @Transactional
    public ProductSelectionSourceCollectionView recollectSourceCollection(
            String collectionId,
            ProductSelectionSourceCollectionCommand command
    ) {
        ProductSelectionSourceCollectionCommand source = command == null
                ? new ProductSelectionSourceCollectionCommand()
                : command;
        Long id = parseLongId(collectionId, "采集记录不存在或已被删除。");
        ProductSelectionSourceCollectionRow row = productSelectionMapper.selectSourceCollectionById(id);
        requireSourceCollectionVisible(source.getOperatorUserId(), row);
        if (!"marketplace-url".equals(defaultText(row.getSourceType(), ""))) {
            throw new IllegalArgumentException("该记录不是链接采集任务，不能重新采集。");
        }
        if (!StringUtils.hasText(defaultText(row.getPageUrl(), row.getSourceUrl()))) {
            throw new IllegalArgumentException("该记录缺少商品页链接，不能重新采集。");
        }
        String sourcePlatform = defaultText(
                row.getSourcePlatform(),
                inferSourcePlatform(defaultText(row.getPageUrl(), row.getSourceUrl()))
        );
        if (isShein(sourcePlatform)) {
            throw new IllegalArgumentException(SHEIN_COLLECTION_PAUSED_MESSAGE);
        }
        if (!isActiveMarketplaceUrlPlatform(sourcePlatform)) {
            throw new IllegalArgumentException("链接自动采集当前只支持 Noon 和 Amazon；SHEIN 完整采集暂缓。");
        }

        productSelectionMapper.markSourceCollectionRunning(id, source.getOperatorUserId());
        ProductSelectionSourceCollectionRow updated = productSelectionMapper.selectSourceCollectionById(id);
        ProductSelectionSourceCollectionRow effectiveRow = updated == null ? row : updated;
        safeResetAli1688Task(effectiveRow, source.getOperatorUserId());
        return toSourceCollectionView(effectiveRow);
    }

    public Ali1688CollectionView getSourceCollectionAli1688(String collectionId, Long operatorUserId) {
        return ali1688CollectionService.getSourceCollectionAli1688(collectionId, operatorUserId);
    }

    public Ali1688CollectionView recollectSourceCollectionAli1688(String collectionId, Long operatorUserId) {
        return ali1688CollectionService.recollectSourceCollectionAli1688(collectionId, operatorUserId);
    }

    public Ali1688CollectionView retryAli1688Collection(String taskId, Long operatorUserId) {
        return ali1688CollectionService.retryAli1688Collection(taskId, operatorUserId);
    }

    public List<Ali1688CollectionView> listAli1688Collections(
            String storeName,
            String storeCode,
            String status,
            Long operatorUserId
    ) {
        return ali1688CollectionService.listCollections(storeName, storeCode, status, operatorUserId);
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-selection.source-collection.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nuono.product-selection.source-collection.scheduler.initial-delay-ms:2000}"
    )
    public void processRunningSourceCollections() {
        if (!sourceCollectionSchedulerEnabled) {
            return;
        }
        int limit = Math.max(1, Math.min(sourceCollectionSchedulerMaxItems, 10));
        for (ProductSelectionSourceCollectionRow row : productSelectionMapper.listRunningSourceCollections(limit)) {
            String lockOwner = SOURCE_COLLECTION_WORKER + "-" + UUID.randomUUID();
            if (row.getId() == null
                    || productSelectionMapper.claimSourceCollection(row.getId(), lockOwner) <= 0) {
                continue;
            }
            ProductSelectionSourceCollectionRow claimedRow = productSelectionMapper.selectSourceCollectionById(row.getId());
            collectSourceCollection(claimedRow == null ? row : claimedRow, lockOwner);
        }
    }

    private ProductSelectionSourceCollectionView toSourceCollectionView(ProductSelectionSourceCollectionRow row) {
        List<String> imageUrls = readStringListJson(row.getImageUrlsJson());
        if (StringUtils.hasText(row.getSourceImageUrl()) && !imageUrls.contains(row.getSourceImageUrl())) {
            imageUrls.add(0, row.getSourceImageUrl());
        }
        List<String> specHints = readStringListJson(row.getSpecHintsJson());
        List<String> sourceSellingPointsEn = readStringListJson(row.getSourceSellingPointsEnJson());
        if (sourceSellingPointsEn.isEmpty()) {
            sourceSellingPointsEn = extractLegacySellingPointsFromSpecHints(specHints);
        }
        List<String> sourceSellingPointsAr = readStringListJson(row.getSourceSellingPointsArJson());
        if (sourceSellingPointsAr.isEmpty()) {
            sourceSellingPointsAr = extractLegacySellingPointsFromText(row.getSelectedTextAr());
        }
        int specAttributeCount = completenessCalculator.countSpecAttributes(specHints);
        ProductSelectionSourceCollectionView view = new ProductSelectionSourceCollectionView();
        view.setId(row.getId() == null ? null : String.valueOf(row.getId()));
        view.setCollectionNo(row.getCollectionNo());
        view.setStoreId(row.getLogicalStoreId() == null ? null : String.valueOf(row.getLogicalStoreId()));
        view.setStoreName(defaultText(row.getStoreName(), "店铺"));
        view.setStoreCode(row.getStoreCode());
        view.setSourceType(defaultText(row.getSourceType(), "image-search-source"));
        view.setSourcePlatform(defaultText(row.getSourcePlatform(), "未知平台"));
        view.setSourceUrl(row.getSourceUrl());
        view.setPageUrl(row.getPageUrl());
        view.setSourceTitle(defaultText(row.getSourceTitle(), "未命名源头商品"));
        view.setSourceTitleCn(defaultText(row.getSourceTitleCn(), row.getSelectedText()));
        view.setSourceTitleAr(defaultText(row.getSourceTitleAr(), ""));
        view.setSourceImageUrl(defaultText(row.getSourceImageUrl(), imageUrls.isEmpty() ? "" : imageUrls.get(0)));
        view.setImageUrls(imageUrls);
        view.setPriceSummary(defaultText(row.getPriceSummary(), ""));
        view.setMoqHint(defaultText(row.getMoqHint(), ""));
        view.setShippingFrom(defaultText(row.getShippingFrom(), ""));
        view.setBrandName(defaultText(row.getBrandName(), completenessCalculator.firstSpecValue(specHints, Set.of("brand", "brand name"))));
        view.setUnitCount(defaultText(row.getUnitCount(), completenessCalculator.firstSpecValue(specHints, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS)));
        view.setColorName(defaultText(row.getColorName(), completenessCalculator.firstSpecValue(specHints, Set.of("color", "colour", "color name", "colour name"))));
        view.setSpecHints(specHints);
        view.setSourceDescriptionEn(defaultText(row.getSourceDescriptionEn(), englishDescriptionFallback(row)));
        view.setSourceDescriptionAr(defaultText(row.getSourceDescriptionAr(), row.getSelectedTextAr()));
        view.setSourceSellingPointsEn(sourceSellingPointsEn);
        view.setSourceSellingPointsAr(sourceSellingPointsAr);
        view.setSelectedText(defaultText(row.getSelectedText(), ""));
        view.setSelectedTextAr(defaultText(row.getSelectedTextAr(), ""));
        view.setNotes(defaultText(row.getNotes(), ""));
        view.setStatus(defaultText(row.getStatus(), "success"));
        view.setStatusText(sourceCollectionStatusText(view.getStatus()));
        view.setFailureCode(row.getFailureCode());
        view.setFailureMessage(row.getFailureMessage());
        view.setCollectedAt(defaultText(row.getCollectedAt(), nowText()));
        view.setCollectionStartedAt(defaultText(row.getCollectionStartedAt(), ""));
        view.setCollectionFinishedAt(defaultText(row.getCollectionFinishedAt(), ""));
        view.setCollectionDurationSeconds(row.getCollectionDurationSeconds());
        view.setCollectedBy(defaultText(row.getCreatedByName(), "系统"));
        view.setImageCount(imageUrls.size());
        view.setSpecAttributeCount(specAttributeCount);
        view.setCollectedFieldTotal(completenessCalculator.fieldTotal(view));
        view.setCollectedFieldCount(completenessCalculator.countCollectedFields(view));
        view.setAli1688Collection(safeCurrentAli1688View(row));
        return view;
    }

    private void requireSourceCollectionVisible(Long operatorUserId, ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (isSuperAdmin(user)) {
            return;
        }
        int visibleSites = productSelectionMapper.countVisibleLogicalStoreSites(
                user.getUserId(),
                row.getLogicalStoreId()
        );
        if (visibleSites <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该采集记录。");
        }
    }

    private void collectSourceCollection(ProductSelectionSourceCollectionRow row, String lockOwner) {
        try {
            ProductSelectionSourceCollectionResult result = sourceCollectionLocalizer.localize(
                    row,
                    sourceCollectionCollector.collect(row)
            );
            ProductSelectionSourceCollectionRow updateRow = new ProductSelectionSourceCollectionRow();
            updateRow.setId(row.getId());
            updateRow.setUpdatedBy(row.getUpdatedBy());
            updateRow.setSourcePlatform(defaultText(result.getSourcePlatform(), row.getSourcePlatform()));
            updateRow.setSourceUrl(defaultText(result.getSourceUrl(), row.getSourceUrl()));
            updateRow.setPageUrl(defaultText(result.getPageUrl(), row.getPageUrl()));
            updateRow.setSourceTitle(defaultText(result.getSourceTitle(), row.getSourceTitle()));
            updateRow.setSourceTitleCn(defaultText(result.getSourceTitleCn(), row.getSourceTitleCn()));
            updateRow.setSourceTitleAr(defaultText(result.getSourceTitleAr(), row.getSourceTitleAr()));
            updateRow.setSourceImageUrl(defaultText(result.getSourceImageUrl(), row.getSourceImageUrl()));
            updateRow.setImageUrlsJson(writeStringListJson(result.getImageUrls()));
            updateRow.setPriceSummary(defaultText(result.getPriceSummary(), row.getPriceSummary()));
            updateRow.setMoqHint(defaultText(result.getMoqHint(), row.getMoqHint()));
            updateRow.setShippingFrom(defaultText(result.getShippingFrom(), row.getShippingFrom()));
            updateRow.setBrandName(defaultText(completenessCalculator.firstSpecValue(result.getSpecHints(), Set.of("brand", "brand name")), row.getBrandName()));
            updateRow.setUnitCount(defaultText(completenessCalculator.firstSpecValue(result.getSpecHints(), SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS), row.getUnitCount()));
            updateRow.setColorName(defaultText(completenessCalculator.firstSpecValue(result.getSpecHints(), Set.of("color", "colour", "color name", "colour name")), row.getColorName()));
            updateRow.setSpecHintsJson(writeStringListJson(result.getSpecHints()));
            updateRow.setSpecAttributeCount(completenessCalculator.countSpecAttributes(result.getSpecHints()));
            updateRow.setSourceDescriptionEn(defaultText(result.getSourceDescriptionEn(), row.getSourceDescriptionEn()));
            updateRow.setSourceDescriptionAr(defaultText(result.getSourceDescriptionAr(), row.getSourceDescriptionAr()));
            updateRow.setSourceSellingPointsEnJson(writeStringListJson(normalizeList(result.getSourceSellingPointsEn(), 12)));
            updateRow.setSourceSellingPointsArJson(writeStringListJson(normalizeList(result.getSourceSellingPointsAr(), 12)));
            updateRow.setSelectedText(shrink(defaultText(row.getSelectedText(), result.getSelectedText()), 1900));
            updateRow.setSelectedTextAr(shrink(defaultText(result.getSelectedTextAr(), defaultText(result.getSourceDescriptionAr(), row.getSelectedTextAr())), 1900));
            productSelectionMapper.markSourceCollectionSuccess(updateRow, lockOwner);
            ProductSelectionSourceCollectionRow updated = productSelectionMapper.selectSourceCollectionById(row.getId());
            safeMarkAli1688Succeeded(updated == null ? updateRow : updated);
        } catch (Throwable exception) {
            if (exception instanceof ThreadDeath) {
                throw (ThreadDeath) exception;
            }
            if (exception instanceof VirtualMachineError) {
                throw (VirtualMachineError) exception;
            }
            productSelectionMapper.markSourceCollectionFailed(
                    row.getId(),
                    "source_collect_failed",
                    shrink(defaultText(exception.getMessage(), "源头页面采集失败。"), 480),
                    row.getUpdatedBy(),
                    lockOwner
            );
            safeMarkAli1688Failed(row.getId(), exception.getMessage(), row.getUpdatedBy());
        }
    }

    private void safeEnsureAli1688Task(ProductSelectionSourceCollectionRow row, Long operatorUserId) {
        runAli1688SideEffect(() -> ali1688CollectionService.ensureTaskForSourceCollection(row, operatorUserId));
    }

    private void safeResetAli1688Task(ProductSelectionSourceCollectionRow row, Long operatorUserId) {
        runAli1688SideEffect(() -> ali1688CollectionService.resetTaskForSourceRecollect(row, operatorUserId));
    }

    private void safeMarkAli1688Succeeded(ProductSelectionSourceCollectionRow row) {
        runAli1688SideEffect(() -> ali1688CollectionService.markSourceCollectionSucceeded(row));
    }

    private void safeMarkAli1688Failed(Long sourceCollectionId, String failureMessage, Long updatedBy) {
        runAli1688SideEffect(() -> ali1688CollectionService.markSourceCollectionFailed(sourceCollectionId, failureMessage, updatedBy));
    }

    private void runAli1688SideEffect(Ali1688SideEffect sideEffect) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAli1688SideEffectImmediately(sideEffect);
                }
            });
            return;
        }
        runAli1688SideEffectImmediately(sideEffect);
    }

    private void runAli1688SideEffectImmediately(Ali1688SideEffect sideEffect) {
        try {
            sideEffect.run();
        } catch (DataAccessException ignored) {
        }
    }

    private Ali1688CollectionView safeCurrentAli1688View(ProductSelectionSourceCollectionRow row) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            return unavailableAli1688View(row);
        }
        try {
            return ali1688CollectionService.getCurrentView(row.getId());
        } catch (DataAccessException ignored) {
            return unavailableAli1688View(row);
        }
    }

    private Ali1688CollectionView unavailableAli1688View(ProductSelectionSourceCollectionRow row) {
        Ali1688CollectionView view = new Ali1688CollectionView();
        view.sourceCollectionId = row.getId() == null ? null : String.valueOf(row.getId());
        view.sourceCollectionNo = row.getCollectionNo();
        view.storeId = row.getLogicalStoreId() == null ? null : String.valueOf(row.getLogicalStoreId());
        view.storeName = row.getStoreName();
        view.storeCode = row.getStoreCode();
        view.sourcePlatform = row.getSourcePlatform();
        view.sourceTitle = row.getSourceTitle();
        view.sourceTitleCn = row.getSourceTitleCn();
        view.sourceUrl = row.getSourceUrl();
        view.pageUrl = row.getPageUrl();
        view.sourceImageUrl = row.getSourceImageUrl();
        view.status = "not_started";
        view.progressPercent = 0;
        view.searchMode = "主图图搜";
        view.selectedImageCount = 0;
        view.scannedCount = 0;
        view.candidateCount = 0;
        view.recommendedCount = 0;
        view.message = "1688采集暂不可用，不影响源头采集记录。";
        view.canGenerateProcurementOrder = false;
        return view;
    }

    @FunctionalInterface
    private interface Ali1688SideEffect {
        void run();
    }

    private boolean isSuperAdmin(ProductSelectionUserContext user) {
        return user != null
                && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
    }

    private boolean isShein(String sourcePlatform) {
        return "SHEIN".equalsIgnoreCase(defaultText(sourcePlatform, ""));
    }

    private boolean isActiveMarketplaceUrlPlatform(String sourcePlatform) {
        return ACTIVE_MARKETPLACE_URL_PLATFORMS.stream()
                .anyMatch(platform -> platform.equalsIgnoreCase(defaultText(sourcePlatform, "")));
    }

    private String sourceCollectionStatusText(String status) {
        if ("failed".equals(status)) {
            return "采集失败";
        }
        if ("running".equals(status)) {
            return "采集中";
        }
        return "采集成功";
    }

    private String inferSourcePlatform(String url) {
        String normalized = defaultText(url, "").toLowerCase();
        if (normalized.contains("noon.")) {
            return "Noon";
        }
        if (normalized.contains("amazon.")) {
            return "Amazon";
        }
        if (normalized.contains("shein.")) {
            return "SHEIN";
        }
        if (normalized.contains("temu.")) {
            return "TEMU";
        }
        if (normalized.contains("1688.")) {
            return "1688";
        }
        return "其他";
    }

    private String writeStringListJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
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

    private List<String> normalizeList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(Math.max(0, limit))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String englishDescriptionFallback(ProductSelectionSourceCollectionRow row) {
        String selectedText = defaultText(row.getSelectedText(), "");
        if (!selectedText.matches(".*[A-Za-z]{3,}.*")) {
            return "";
        }
        String sourceTitleCn = defaultText(row.getSourceTitleCn(), "");
        return selectedText.equals(sourceTitleCn) ? "" : selectedText;
    }

    private List<String> splitSourceCollectionDetailItems(String value) {
        String text = defaultText(value, "").trim();
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text
                .replace("\r", "\n")
                .replaceAll("\\s*(\\[[^\\]]{2,80}\\]\\s*-?)", "\n$1");
        List<String> lines = List.of(normalized.split("\\n+")).stream()
                .map(item -> item.replaceAll("\\s+", " ").trim())
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return lines.size() > 1 ? lines : List.of(text);
    }

    private List<String> extractLegacySellingPointsFromSpecHints(List<String> specHints) {
        if (specHints == null || specHints.isEmpty()) {
            return List.of();
        }
        return specHints.stream()
                .filter(StringUtils::hasText)
                .filter(item -> !item.contains(":") && !item.contains("："))
                .map(String::trim)
                .limit(8)
                .collect(Collectors.toList());
    }

    private List<String> extractLegacySellingPointsFromText(String value) {
        String text = defaultText(value, "");
        if (!text.contains("[")) {
            return List.of();
        }
        return splitSourceCollectionDetailItems(text).stream()
                .filter(item -> item.trim().startsWith("["))
                .limit(8)
                .collect(Collectors.toList());
    }

    private Long parseLongId(String id, String message) {
        try {
            return Long.parseLong(defaultText(id, ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private int normalizedPage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizedPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_LIST_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LIST_PAGE_SIZE);
    }

    private String normalizedText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String sanitizeSourceCollectionNotes(String value) {
        String text = defaultText(value, "");
        if (text.startsWith(BATCH_RUNNER_SAMPLE_NOTE_PREFIX)) {
            return "";
        }
        return text;
    }

    private String shrink(String value, int maxLength) {
        String text = defaultText(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
