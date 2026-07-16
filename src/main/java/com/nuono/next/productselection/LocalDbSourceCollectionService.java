package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbSourceCollectionService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SOURCE_COLLECTION_WORKER = "source-collection-scheduler";
    private static final Set<String> SUPPORTED_MARKETPLACE_PLATFORMS = Set.of("Noon", "Amazon", "SHEIN", "Temu");

    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ProductSelectionSourceCollectionCollector sourceCollectionCollector;
    private final ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;
    private final SourceCollectionCompletenessCalculator completenessCalculator;
    private final LocalDbAli1688CollectionService ali1688CollectionService;
    private final ObjectMapper objectMapper;
    private final ProductSelectionGroupService groupService;
    private final NoonRiskBackoffGuard noonRiskBackoffGuard;

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
            ObjectMapper objectMapper,
            NoonRiskBackoffGuard noonRiskBackoffGuard
    ) {
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.sourceCollectionCollector = sourceCollectionCollector;
        this.sourceCollectionLocalizer = sourceCollectionLocalizer;
        this.completenessCalculator = completenessCalculator;
        this.ali1688CollectionService = ali1688CollectionService;
        this.objectMapper = objectMapper;
        this.noonRiskBackoffGuard = noonRiskBackoffGuard == null ? NoonRiskBackoffGuard.disabled() : noonRiskBackoffGuard;
        this.groupService = new ProductSelectionGroupService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                objectMapper,
                this::toSourceCollectionView,
                this::toSourceCollectionSnapshotView
        );
    }

    public List<ProductSelectionSourceCollectionView> listSourceCollections(
            String storeName,
            String storeCode,
            Long operatorUserId
    ) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        return listSourceCollections(scope.getLogicalStoreId(), siteCodeFromScope(scope), 50);
    }

    public List<ProductSelectionSourceCollectionView> listSourceCollections(Long logicalStoreId, int limit) {
        return listSourceCollections(logicalStoreId, null, limit);
    }

    public List<ProductSelectionSourceCollectionView> listSourceCollections(Long logicalStoreId, String siteCode, int limit) {
        return productSelectionMapper.listSourceCollections(logicalStoreId, normalizeSiteCode(siteCode), limit).stream()
                .map(this::toSourceCollectionView)
                .collect(Collectors.toList());
    }

    public List<ProductSelectionAnalysisItemView> listAnalysisItems(
            String storeName,
            String storeCode,
            Long operatorUserId
    ) {
        ProductSelectionStoreScope scope = permissionGuard.requireReadableStore(operatorUserId, storeCode);
        return productSelectionMapper.listAnalysisItems(scope.getLogicalStoreId(), siteCodeFromScope(scope), 100).stream()
                .map(this::toAnalysisItemView)
                .collect(Collectors.toList());
    }

    public List<ProductSelectionGroupView> listGroups(
            String storeName,
            String storeCode,
            Long operatorUserId
    ) {
        return groupService.listGroups(storeName, storeCode, operatorUserId);
    }

    @Transactional(readOnly = true)
    public ProductSelectionGroupView getGroup(
            String groupIdValue,
            Long operatorUserId
    ) {
        return groupService.getGroup(groupIdValue, operatorUserId);
    }

    @Transactional
    public ProductSelectionGroupView createGroup(ProductSelectionGroupCommand command) {
        return groupService.createGroup(command);
    }

    @Transactional
    public ProductSelectionGroupView addGroupMaterials(String groupIdValue, ProductSelectionGroupCommand command) {
        return groupService.addGroupMaterials(groupIdValue, command);
    }

    @Transactional
    public void deleteGroupMaterial(
            String groupIdValue,
            String sourceCollectionIdValue,
            boolean deleteSourceCollection,
            String storeCode,
            Long operatorUserId
    ) {
        groupService.deleteGroupMaterial(
                groupIdValue,
                sourceCollectionIdValue,
                deleteSourceCollection,
                storeCode,
                operatorUserId
        );
    }

    @Transactional
    public ProductSelectionGroupView updateGroupName(
            String groupIdValue,
            ProductSelectionGroupCommand command
    ) {
        return groupService.updateGroupName(groupIdValue, command);
    }

    @Transactional
    public ProductSelectionGroupView updateGroupProcurement(
            String groupIdValue,
            ProductSelectionGroupCommand command
    ) {
        return groupService.updateGroupProcurement(groupIdValue, command);
    }

    @Transactional(readOnly = true)
    public ProductSelectionGroupProfitSnapshotView getGroupProfitEstimate(
            String groupIdValue,
            Long operatorUserId
    ) {
        return groupService.getGroupProfitEstimate(groupIdValue, operatorUserId);
    }

    @Transactional
    public ProductSelectionGroupProfitSnapshotView saveGroupProfitEstimate(
            String groupIdValue,
            ProductSelectionGroupProfitSnapshotCommand command
    ) {
        return groupService.saveGroupProfitEstimate(groupIdValue, command);
    }

    @Transactional
    public ProductSelectionGroupView updateGroupCompetitors(
            String groupIdValue,
            ProductSelectionGroupCompetitorCommand command
    ) {
        return groupService.updateGroupCompetitors(groupIdValue, command);
    }

    @Transactional
    public ProductSelectionGroupView recollectGroupCompetitor(
            String groupIdValue,
            String competitorIdValue,
            Long operatorUserId
    ) {
        return groupService.recollectGroupCompetitor(groupIdValue, competitorIdValue, operatorUserId);
    }

    @Transactional
    public ProductSelectionGroupView deleteGroupCompetitor(
            String groupIdValue,
            String competitorIdValue,
            Long operatorUserId
    ) {
        return groupService.deleteGroupCompetitor(groupIdValue, competitorIdValue, operatorUserId);
    }

    @Transactional
    public List<ProductSelectionAnalysisItemView> addAnalysisItems(ProductSelectionAnalysisItemCommand command) {
        ProductSelectionAnalysisItemCommand source = command == null
                ? new ProductSelectionAnalysisItemCommand()
                : command;
        List<Long> sourceCollectionIds = normalizeSourceCollectionIds(source.getSourceCollectionIds());
        if (sourceCollectionIds.isEmpty()) {
            throw new IllegalArgumentException("请选择要加入选品分析的商品。");
        }

        List<ProductSelectionSourceCollectionRow> insertableSources = new ArrayList<>();
        for (Long sourceCollectionId : sourceCollectionIds) {
            if (productSelectionMapper.lockActiveSourceCollectionById(sourceCollectionId) == null) {
                throw new IllegalArgumentException("采集记录不存在或已被删除。");
            }
            ProductSelectionSourceCollectionRow sourceCollection =
                    productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
            requireSourceCollectionVisible(source.getOperatorUserId(), sourceCollection);
            if (!"success".equals(defaultText(sourceCollection.getStatus(), ""))) {
                throw new IllegalArgumentException("采集成功后才能加入选品分析。");
            }
            if (productSelectionMapper.selectAnalysisItemBySourceCollectionId(sourceCollectionId) != null) {
                continue;
            }
            insertableSources.add(sourceCollection);
        }

        Long projectId = parseNullableLongId(source.getProjectId(), "选品项目不存在或已被删除。");
        String projectName = shrink(
                defaultText(source.getProjectName(), defaultProjectName(insertableSources)),
                200
        );
        for (ProductSelectionSourceCollectionRow sourceCollection : insertableSources) {
            Long analysisItemId = productSelectionMapper.nextAnalysisItemId();
            if (projectId == null) {
                projectId = analysisItemId;
            }
            ProductSelectionAnalysisItemRow row = new ProductSelectionAnalysisItemRow();
            row.setAnalysisItemId(analysisItemId);
            row.setProjectId(projectId);
            row.setProjectName(projectName);
            row.setOwnerUserId(sourceCollection.getOwnerUserId());
            row.setLogicalStoreId(sourceCollection.getLogicalStoreId());
            row.setSiteCode(normalizeSiteCode(sourceCollection.getSiteCode()));
            row.setSourceCollectionId(sourceCollection.getId());
            row.setAnalysisStatus("active");
            row.setCreatedBy(source.getOperatorUserId());
            row.setUpdatedBy(source.getOperatorUserId());
            productSelectionMapper.insertAnalysisItem(row);
        }

        return productSelectionMapper.listAnalysisItemsBySourceCollectionIds(sourceCollectionIds).stream()
                .map(this::toAnalysisItemView)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductSelectionAnalysisItemView updateAnalysisItemProcurement(
            String collectionId,
            ProductSelectionAnalysisItemCommand command
    ) {
        ProductSelectionAnalysisItemCommand source = command == null
                ? new ProductSelectionAnalysisItemCommand()
                : command;
        Long sourceCollectionId = parseLongId(collectionId, "采集记录不存在或已被删除。");
        ProductSelectionSourceCollectionRow sourceCollection =
                productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
        requireSourceCollectionVisible(source.getOperatorUserId(), sourceCollection);
        if (productSelectionMapper.selectAnalysisItemBySourceCollectionId(sourceCollectionId) == null) {
            ProductSelectionAnalysisItemCommand addCommand = new ProductSelectionAnalysisItemCommand();
            addCommand.setOperatorUserId(source.getOperatorUserId());
            addCommand.setSourceCollectionIds(List.of(collectionId));
            addAnalysisItems(addCommand);
        }
        BigDecimal purchasePrice = source.getPurchasePrice();
        if (purchasePrice != null && purchasePrice.signum() < 0) {
            throw new IllegalArgumentException("采购单价不能小于 0。");
        }
        productSelectionMapper.updateAnalysisItemProcurement(
                sourceCollectionId,
                shrink(defaultText(source.getAli1688PurchaseUrl(), ""), 1000),
                purchasePrice,
                source.getOperatorUserId()
        );
        return productSelectionMapper.listAnalysisItemsBySourceCollectionIds(List.of(sourceCollectionId)).stream()
                .findFirst()
                .map(this::toAnalysisItemView)
                .orElseThrow(() -> new IllegalArgumentException("选品分析记录不存在或已被删除。"));
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
        if (marketplaceUrlCollection && !Set.of("Noon", "Amazon", "SHEIN").contains(sourcePlatform)) {
            throw new IllegalArgumentException("链接自动采集当前只支持 Noon、Amazon 和 SHEIN。");
        }

        Long id = productSelectionMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(scope.getOwnerUserId());
        row.setLogicalStoreId(scope.getLogicalStoreId());
        row.setSiteCode(siteCodeFromScope(scope));
        row.setCollectionNo("PSC-" + id);
        row.setSourceType(sourceType);
        row.setCollectionSource(normalizeCollectionSource(source.getCollectionSource()));
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
        row.setCategoryLinksJson(writeCategoryLinksJson(List.of()));
        row.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specHints));
        row.setSourceDescriptionEn(defaultText(source.getSourceDescriptionEn(), ""));
        row.setSourceDescriptionAr(defaultText(source.getSourceDescriptionAr(), source.getSelectedTextAr()));
        row.setSourceSellingPointsEnJson(writeStringListJson(normalizeList(source.getSourceSellingPointsEn(), 12)));
        row.setSourceSellingPointsArJson(writeStringListJson(normalizeList(source.getSourceSellingPointsAr(), 12)));
        row.setSelectedText(defaultText(source.getSelectedText(), ""));
        row.setSelectedTextAr(defaultText(source.getSelectedTextAr(), ""));
        row.setNotes(defaultText(source.getNotes(), ""));
        row.setStatus(marketplaceUrlCollection ? "running" : "success");
        row.setCreatedBy(source.getOperatorUserId());
        row.setUpdatedBy(source.getOperatorUserId());
        productSelectionMapper.insertSourceCollection(row);
        ProductSelectionSourceCollectionRow inserted = productSelectionMapper.selectSourceCollectionById(id);
        ProductSelectionSourceCollectionRow effectiveRow = inserted == null ? row : inserted;
        ali1688CollectionService.ensureTaskForSourceCollection(effectiveRow, source.getOperatorUserId());
        return toSourceCollectionView(effectiveRow);
    }

    @Transactional
    public ProductSelectionPluginIngestResponse pluginIngestSourceCollection(ProductSelectionPluginIngestCommand command) {
        ProductSelectionPluginIngestCommand source = command == null
                ? new ProductSelectionPluginIngestCommand()
                : command;
        ProductSelectionStoreScope scope = permissionGuard.requireWritableStore(
                source.getOperatorUserId(),
                source.getStoreCode()
        );
        String pageUrl = defaultText(source.getPageUrl(), source.getSourceUrl());
        String sourceUrl = defaultText(source.getSourceUrl(), source.getPageUrl());
        if (!StringUtils.hasText(pageUrl)) {
            throw new IllegalArgumentException("插件采集需要商品页链接。");
        }
        String sourcePlatform = resolveUrlAuthoritySourcePlatform(source.getSourcePlatform(), pageUrl, sourceUrl, "插件采集");
        if (!SUPPORTED_MARKETPLACE_PLATFORMS.contains(sourcePlatform)) {
            throw new IllegalArgumentException("插件采集当前只支持 Noon、Amazon、SHEIN 和 Temu。");
        }

        List<String> imageUrls = normalizeList(source.getImageUrls(), 20);
        String mainImageUrl = defaultText(source.getSourceImageUrl(), "");
        if (StringUtils.hasText(mainImageUrl) && !imageUrls.contains(mainImageUrl)) {
            imageUrls.add(0, mainImageUrl);
        }
        List<String> specHints = normalizeList(source.getSpecHints(), 30);
        List<String> sellingPointsEn = normalizeSourceSellingPoints(source.getSourceSellingPointsEn(), 12);
        List<String> sellingPointsAr = normalizeSourceSellingPoints(source.getSourceSellingPointsAr(), 12);
        if (!hasPluginCollectedFacts(source, imageUrls, specHints, sellingPointsEn, sellingPointsAr)) {
            throw new IllegalArgumentException("插件未采集到有效商品信息，请确认当前页是商品详情页。");
        }

        Long id = productSelectionMapper.nextSourceCollectionId();
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(id);
        row.setOwnerUserId(scope.getOwnerUserId());
        row.setLogicalStoreId(scope.getLogicalStoreId());
        row.setSiteCode(siteCodeFromScope(scope));
        row.setCollectionNo("PSC-" + id);
        row.setSourceType("marketplace-url");
        row.setCollectionSource("plugin");
        row.setSourceUrl(sourceUrl);
        row.setPageUrl(pageUrl);
        row.setSourcePlatform(sourcePlatform);
        row.setSourceTitle(defaultText(
                source.getSourceTitle(),
                defaultText(source.getSourceTitleCn(), defaultText(source.getSourceTitleAr(), "插件采集源头商品"))
        ));
        row.setSourceTitleCn(defaultText(source.getSourceTitleCn(), source.getSelectedText()));
        row.setSourceTitleAr(defaultText(source.getSourceTitleAr(), ""));
        row.setSourceImageUrl(imageUrls.isEmpty() ? "" : imageUrls.get(0));
        row.setImageUrlsJson(writeStringListJson(imageUrls));
        row.setPriceSummary(defaultText(source.getPriceSummary(), ""));
        row.setMoqHint(defaultText(source.getMoqHint(), ""));
        row.setShippingFrom(defaultText(source.getShippingFrom(), ""));
        row.setBrandName(completenessCalculator.firstSpecValue(specHints, Set.of("brand", "brand name")));
        row.setUnitCount(completenessCalculator.firstSpecValue(specHints, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS));
        row.setColorName(completenessCalculator.firstSpecValue(specHints, Set.of("color", "colour", "color name", "colour name")));
        row.setSpecHintsJson(writeStringListJson(specHints));
        row.setCategoryLinksJson(writeCategoryLinksJson(List.of()));
        row.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specHints));
        row.setSourceDescriptionEn(defaultText(source.getSourceDescriptionEn(), ""));
        row.setSourceDescriptionAr(defaultText(source.getSourceDescriptionAr(), source.getSelectedTextAr()));
        row.setSourceSellingPointsEnJson(writeStringListJson(sellingPointsEn));
        row.setSourceSellingPointsArJson(writeStringListJson(sellingPointsAr));
        row.setSelectedText(defaultText(source.getSelectedText(), ""));
        row.setSelectedTextAr(defaultText(source.getSelectedTextAr(), ""));
        row.setNotes(defaultText(source.getNotes(), pluginWarningNotes(source.getWarnings())));
        row.setStatus("success");
        row.setCreatedBy(source.getOperatorUserId());
        row.setUpdatedBy(source.getOperatorUserId());
        productSelectionMapper.insertSourceCollection(row);
        ProductSelectionSourceCollectionRow inserted = productSelectionMapper.selectSourceCollectionById(id);
        ProductSelectionSourceCollectionRow effectiveRow = inserted == null ? row : inserted;
        ali1688CollectionService.ensureTaskForSourceCollection(effectiveRow, source.getOperatorUserId());

        ProductSelectionPluginIngestResponse response = new ProductSelectionPluginIngestResponse();
        response.setSourceCollection(toSourceCollectionView(effectiveRow));
        response.setWarnings(normalizeWarnings(source.getWarnings()));
        return response;
    }

    @Transactional
    public void deleteSourceCollection(
            String collectionId,
            String storeCode,
            Long operatorUserId
    ) {
        Long sourceCollectionId = parseLongId(collectionId, "采集记录不存在或已被删除。");
        if (productSelectionMapper.lockActiveSourceCollectionById(sourceCollectionId) == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        ProductSelectionSourceCollectionRow sourceCollection =
                productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
        requireSourceCollectionWritable(operatorUserId, storeCode, sourceCollection);
        if (productSelectionMapper.countActiveSelectionReferences(sourceCollectionId) > 0) {
            throw new ProductSelectionConflictException("该采集数据已被选品分析引用，请先在选品分析中解除关联。");
        }
        if (productSelectionMapper.softDeleteSourceCollection(sourceCollectionId, operatorUserId) <= 0) {
            throw new IllegalArgumentException("采集记录已变化，请刷新后重试。");
        }
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
        if (!Set.of("Noon", "Amazon", "SHEIN").contains(sourcePlatform)) {
            throw new IllegalArgumentException("链接自动采集当前只支持 Noon、Amazon 和 SHEIN。");
        }

        productSelectionMapper.markSourceCollectionRunning(id, source.getOperatorUserId());
        ProductSelectionSourceCollectionRow updated = productSelectionMapper.selectSourceCollectionById(id);
        ProductSelectionSourceCollectionRow effectiveRow = updated == null ? row : updated;
        ali1688CollectionService.resetTaskForSourceRecollect(effectiveRow, source.getOperatorUserId());
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
        List<ProductSelectionCompetitorCategoryLink> categoryLinks = readCategoryLinksJson(row.getCategoryLinksJson());
        List<String> sourceSellingPointsEn = normalizeSourceSellingPoints(
                readStringListJson(row.getSourceSellingPointsEnJson()),
                12
        );
        if (sourceSellingPointsEn.isEmpty()) {
            sourceSellingPointsEn = extractLegacySellingPointsFromSpecHints(specHints);
        }
        List<String> sourceSellingPointsAr = normalizeSourceSellingPoints(
                readStringListJson(row.getSourceSellingPointsArJson()),
                12
        );
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
        view.setSiteCode(normalizeSiteCode(row.getSiteCode()));
        view.setSourceType(defaultText(row.getSourceType(), "image-search-source"));
        view.setCollectionSource(defaultText(row.getCollectionSource(), "browser"));
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
        applyCategoryLinks(view, categoryLinks);
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
        view.setCollectedBy(defaultText(row.getCreatedByName(), "系统"));
        view.setImageCount(imageUrls.size());
        view.setSpecAttributeCount(specAttributeCount);
        view.setCollectedFieldTotal(completenessCalculator.fieldTotal(view));
        view.setCollectedFieldCount(completenessCalculator.countCollectedFields(view));
        view.setAli1688Collection(ali1688CollectionService.getCurrentView(row.getId()));
        return view;
    }

    private ProductSelectionSourceCollectionView toSourceCollectionSnapshotView(
            ProductSelectionSourceCollectionRow row,
            ProductSelectionSourceCollectionResult result
    ) {
        List<String> imageUrls = normalizeList(result.getImageUrls(), 24);
        if (StringUtils.hasText(result.getSourceImageUrl()) && !imageUrls.contains(result.getSourceImageUrl())) {
            imageUrls.add(0, result.getSourceImageUrl());
        }
        List<String> specHints = normalizeList(result.getSpecHints(), 80);
        List<ProductSelectionCompetitorCategoryLink> categoryLinks = normalizeCategoryLinks(result.getCategoryLinks());
        ProductSelectionSourceCollectionView view = new ProductSelectionSourceCollectionView();
        view.setSiteCode(normalizeSiteCode(row.getSiteCode()));
        view.setSourceType(defaultText(row.getSourceType(), "image-search-source"));
        view.setCollectionSource(defaultText(row.getCollectionSource(), "system"));
        view.setSourcePlatform(defaultText(result.getSourcePlatform(), row.getSourcePlatform()));
        view.setSourceUrl(defaultText(result.getSourceUrl(), row.getSourceUrl()));
        view.setPageUrl(defaultText(result.getPageUrl(), row.getPageUrl()));
        view.setSourceTitle(defaultText(result.getSourceTitle(), row.getSourceTitle()));
        view.setSourceTitleCn(defaultText(result.getSourceTitleCn(), row.getSelectedText()));
        view.setSourceTitleAr(defaultText(result.getSourceTitleAr(), row.getSourceTitleAr()));
        view.setSourceImageUrl(defaultText(result.getSourceImageUrl(), imageUrls.isEmpty() ? "" : imageUrls.get(0)));
        view.setImageUrls(imageUrls);
        view.setPriceSummary(defaultText(result.getPriceSummary(), row.getPriceSummary()));
        view.setMoqHint(defaultText(result.getMoqHint(), row.getMoqHint()));
        view.setShippingFrom(defaultText(result.getShippingFrom(), row.getShippingFrom()));
        view.setBrandName(completenessCalculator.firstSpecValue(specHints, Set.of("brand", "brand name")));
        view.setUnitCount(completenessCalculator.firstSpecValue(specHints, SourceCollectionCompletenessCalculator.UNIT_COUNT_SPEC_LABELS));
        view.setColorName(completenessCalculator.firstSpecValue(specHints, Set.of("color", "colour", "color name", "colour name")));
        view.setSpecHints(specHints);
        applyCategoryLinks(view, categoryLinks);
        view.setSourceDescriptionEn(defaultText(result.getSourceDescriptionEn(), row.getSourceDescriptionEn()));
        view.setSourceDescriptionAr(defaultText(result.getSourceDescriptionAr(), row.getSourceDescriptionAr()));
        view.setSourceSellingPointsEn(normalizeSourceSellingPoints(result.getSourceSellingPointsEn(), 12));
        view.setSourceSellingPointsAr(normalizeSourceSellingPoints(result.getSourceSellingPointsAr(), 12));
        view.setSelectedText(defaultText(result.getSelectedText(), row.getSelectedText()));
        view.setSelectedTextAr(defaultText(result.getSelectedTextAr(), row.getSelectedTextAr()));
        view.setStatus("success");
        view.setStatusText("采集成功");
        view.setImageCount(imageUrls.size());
        view.setSpecAttributeCount(completenessCalculator.countSpecAttributes(specHints));
        view.setCollectedFieldTotal(completenessCalculator.fieldTotal(view));
        view.setCollectedFieldCount(completenessCalculator.countCollectedFields(view));
        return view;
    }

    private ProductSelectionAnalysisItemView toAnalysisItemView(ProductSelectionAnalysisItemRow row) {
        ProductSelectionAnalysisItemView view = new ProductSelectionAnalysisItemView();
        view.setId(row.getAnalysisItemId() == null ? null : String.valueOf(row.getAnalysisItemId()));
        Long projectId = row.getProjectId() == null ? row.getAnalysisItemId() : row.getProjectId();
        view.setProjectId(projectId == null ? null : String.valueOf(projectId));
        view.setProjectName(defaultText(row.getProjectName(), defaultProjectName(row)));
        view.setProjectMaterialCount(row.getProjectMaterialCount() == null ? 1 : row.getProjectMaterialCount());
        view.setSourceCollectionId(row.getSourceCollectionId() == null ? null : String.valueOf(row.getSourceCollectionId()));
        view.setAli1688PurchaseUrl(defaultText(row.getAli1688PurchaseUrl(), ""));
        view.setPurchasePrice(row.getPurchasePriceRmb());
        view.setSourceCollection(toSourceCollectionView(row));
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

    private void requireSourceCollectionWritable(
            Long operatorUserId,
            String storeCode,
            ProductSelectionSourceCollectionRow row
    ) {
        if (row == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        ProductSelectionStoreScope scope = permissionGuard.requireWritableStore(operatorUserId, storeCode);
        String scopeSiteCode = siteCodeFromScope(scope);
        String sourceSiteCode = normalizeSiteCode(row.getSiteCode());
        if (!scope.getLogicalStoreId().equals(row.getLogicalStoreId())
                || (StringUtils.hasText(scopeSiteCode) && !scopeSiteCode.equals(sourceSiteCode))) {
            throw new ProductSelectionAccessDeniedException("当前店铺站点不能删除该采集记录。");
        }
    }

    private void collectSourceCollection(ProductSelectionSourceCollectionRow row, String lockOwner) {
        Optional<NoonRiskBackoffHold> activeNoonHold = currentNoonRiskHold(row);
        if (activeNoonHold.isPresent()) {
            markSourceCollectionRiskBackoff(row, lockOwner, activeNoonHold.get(), "Noon 风控冷却中，源头采集延后。");
            return;
        }
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
            updateRow.setCategoryLinksJson(writeCategoryLinksJson(normalizeCategoryLinks(result.getCategoryLinks())));
            updateRow.setSpecAttributeCount(completenessCalculator.countSpecAttributes(result.getSpecHints()));
            updateRow.setSourceDescriptionEn(defaultText(result.getSourceDescriptionEn(), row.getSourceDescriptionEn()));
            updateRow.setSourceDescriptionAr(defaultText(result.getSourceDescriptionAr(), row.getSourceDescriptionAr()));
            updateRow.setSourceSellingPointsEnJson(writeStringListJson(normalizeSourceSellingPoints(result.getSourceSellingPointsEn(), 12)));
            updateRow.setSourceSellingPointsArJson(writeStringListJson(normalizeSourceSellingPoints(result.getSourceSellingPointsAr(), 12)));
            updateRow.setSelectedText(shrink(defaultText(row.getSelectedText(), result.getSelectedText()), 1900));
            updateRow.setSelectedTextAr(shrink(defaultText(result.getSelectedTextAr(), defaultText(result.getSourceDescriptionAr(), row.getSelectedTextAr())), 1900));
            productSelectionMapper.markSourceCollectionSuccess(updateRow, lockOwner);
            ProductSelectionSourceCollectionRow updated = productSelectionMapper.selectSourceCollectionById(row.getId());
            ali1688CollectionService.markSourceCollectionSucceeded(updated == null ? updateRow : updated);
        } catch (Exception exception) {
            if (isNoonSourceCollection(row) && isNoonRiskException(exception)) {
                NoonRiskBackoffHold hold = noonRiskBackoffGuard.recordRiskSignal(
                        NoonRiskBackoffScope.sourceCollection(row.getOwnerUserId(), row.getStoreCode(), null),
                        noonRiskType(exception),
                        "SOURCE_COLLECTION",
                        row.getId(),
                        null,
                        shrink(defaultText(exception.getMessage(), "Noon 源头采集触发风控。"), 480)
                );
                markSourceCollectionRiskBackoff(row, lockOwner, hold, exception.getMessage());
                return;
            }
            productSelectionMapper.markSourceCollectionFailed(
                    row.getId(),
                    "source_collect_failed",
                    shrink(defaultText(exception.getMessage(), "源头页面采集失败。"), 480),
                    row.getUpdatedBy(),
                    lockOwner
            );
            ali1688CollectionService.markSourceCollectionFailed(row.getId(), exception.getMessage(), row.getUpdatedBy());
        }
    }

    private Optional<NoonRiskBackoffHold> currentNoonRiskHold(ProductSelectionSourceCollectionRow row) {
        if (!isNoonSourceCollection(row)) {
            return Optional.empty();
        }
        return noonRiskBackoffGuard.currentHold(NoonRiskBackoffScope.sourceCollection(row.getOwnerUserId(), row.getStoreCode(), null));
    }

    private void markSourceCollectionRiskBackoff(
            ProductSelectionSourceCollectionRow row,
            String lockOwner,
            NoonRiskBackoffHold hold,
            String reason
    ) {
        LocalDateTime nextRunAt = hold == null ? LocalDateTime.now().plusMinutes(2) : hold.getBlockedUntil();
        productSelectionMapper.markSourceCollectionRiskBackoff(
                row.getId(),
                "noon_risk_backoff",
                shrink(defaultText(reason, "Noon 源头采集触发风控，等待冷却后重试。"), 480),
                row.getUpdatedBy(),
                lockOwner,
                nextRunAt
        );
    }

    private boolean isNoonSourceCollection(ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            return false;
        }
        String platform = defaultText(row.getSourcePlatform(), inferSourcePlatform(defaultText(row.getPageUrl(), row.getSourceUrl())));
        return "Noon".equalsIgnoreCase(platform);
    }

    private boolean isNoonRiskException(Exception exception) {
        String message = exception == null ? "" : defaultText(exception.getMessage(), exception.toString()).toLowerCase(Locale.ROOT);
        return message.contains("captcha")
                || message.contains("status=403")
                || message.contains("http 403")
                || message.contains(" 403")
                || message.contains("status=429")
                || message.contains("http 429")
                || message.contains(" 429")
                || message.contains("rate_limited")
                || message.contains("rate limited");
    }

    private String noonRiskType(Exception exception) {
        String message = exception == null ? "" : defaultText(exception.getMessage(), exception.toString()).toLowerCase(Locale.ROOT);
        if (message.contains("captcha")) {
            return "captcha_required";
        }
        if (message.contains("429") || message.contains("rate_limited") || message.contains("rate limited")) {
            return "rate_limited";
        }
        return "blocked_by_risk_control";
    }

    private boolean isSuperAdmin(ProductSelectionUserContext user) {
        return user != null
                && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
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
        String host = urlHost(url);
        if (hostMatches(host, "noon.com")) {
            return "Noon";
        }
        if (hostMatches(host, "amazon.sa")
                || hostMatches(host, "amazon.ae")
                || hostMatches(host, "amazon.com")
                || hostMatches(host, "amazon.co.uk")) {
            return "Amazon";
        }
        if (hostMatches(host, "shein.com")) {
            return "SHEIN";
        }
        if (hostMatches(host, "temu.com")) {
            return "Temu";
        }
        if (hostMatches(host, "1688.com")) {
            return "1688";
        }
        return "其他";
    }

    private String resolveUrlAuthoritySourcePlatform(
            String explicitPlatform,
            String pageUrl,
            String sourceUrl,
            String actionLabel
    ) {
        String inferredPlatform = normalizeSourcePlatform(inferSourcePlatform(defaultText(pageUrl, sourceUrl)));
        if (!SUPPORTED_MARKETPLACE_PLATFORMS.contains(inferredPlatform)) {
            throw new IllegalArgumentException(actionLabel + "当前只支持 Noon、Amazon、SHEIN 和 Temu 商品页链接。");
        }

        String normalizedExplicitPlatform = normalizeSourcePlatform(explicitPlatform);
        if (StringUtils.hasText(normalizedExplicitPlatform) && !inferredPlatform.equals(normalizedExplicitPlatform)) {
            throw new IllegalArgumentException(actionLabel + "平台与商品链接不一致，请刷新商品页后重试。");
        }
        return inferredPlatform;
    }

    private String urlHost(String url) {
        try {
            String value = defaultText(url, "");
            if (!StringUtils.hasText(value)) {
                return "";
            }
            URI uri = URI.create(value);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private boolean hostMatches(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }

    private String normalizeSourcePlatform(String sourcePlatform) {
        String value = defaultText(sourcePlatform, "");
        if ("noon".equalsIgnoreCase(value)) {
            return "Noon";
        }
        if ("amazon".equalsIgnoreCase(value)) {
            return "Amazon";
        }
        if ("shein".equalsIgnoreCase(value)) {
            return "SHEIN";
        }
        if ("temu".equalsIgnoreCase(value) || "TEMU".equals(value)) {
            return "Temu";
        }
        return value;
    }

    private String siteCodeFromScope(ProductSelectionStoreScope scope) {
        if (scope == null) {
            return "SA";
        }
        String normalizedSite = normalizeSiteCode(scope.getSite());
        if (StringUtils.hasText(normalizedSite)) {
            return normalizedSite;
        }
        return normalizeSiteCode(scope.getStoreCode());
    }

    private String normalizeSiteCode(String value) {
        String normalized = defaultText(value, "").toUpperCase();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.equals("AE")
                || normalized.equals("ARE")
                || normalized.equals("UAE")
                || normalized.contains("-NAE")
                || normalized.endsWith("NAE")
                || normalized.contains("阿联酋")) {
            return "AE";
        }
        if (normalized.equals("SA")
                || normalized.equals("SAU")
                || normalized.equals("KSA")
                || normalized.contains("-NSA")
                || normalized.endsWith("NSA")
                || normalized.contains("沙特")) {
            return "SA";
        }
        return normalized;
    }

    private String normalizeCollectionSource(String value) {
        String normalized = defaultText(value, "").toLowerCase();
        if (normalized.equals("plugin")
                || normalized.equals("extension")
                || normalized.equals("browser-extension")
                || normalized.contains("插件")) {
            return "plugin";
        }
        return "browser";
    }

    private boolean hasPluginCollectedFacts(
            ProductSelectionPluginIngestCommand source,
            List<String> imageUrls,
            List<String> specHints,
            List<String> sellingPointsEn,
            List<String> sellingPointsAr
    ) {
        return StringUtils.hasText(source.getSourceTitle())
                || StringUtils.hasText(source.getSourceTitleCn())
                || StringUtils.hasText(source.getSourceTitleAr())
                || StringUtils.hasText(source.getPriceSummary())
                || StringUtils.hasText(source.getSourceDescriptionEn())
                || StringUtils.hasText(source.getSourceDescriptionAr())
                || StringUtils.hasText(source.getSelectedText())
                || StringUtils.hasText(source.getSelectedTextAr())
                || !imageUrls.isEmpty()
                || !specHints.isEmpty()
                || !sellingPointsEn.isEmpty()
                || !sellingPointsAr.isEmpty();
    }

    private List<ProductSelectionPluginIngestCommand.PluginWarning> normalizeWarnings(
            List<ProductSelectionPluginIngestCommand.PluginWarning> warnings
    ) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        return warnings.stream()
                .filter(warning -> warning != null && StringUtils.hasText(warning.getCode()))
                .map(warning -> new ProductSelectionPluginIngestCommand.PluginWarning(
                        shrink(defaultText(warning.getCode(), "extractor_partial"), 80),
                        shrink(defaultText(warning.getField(), ""), 120),
                        shrink(defaultText(warning.getMessage(), ""), 240)
                ))
                .limit(20)
                .collect(Collectors.toList());
    }

    private String pluginWarningNotes(List<ProductSelectionPluginIngestCommand.PluginWarning> warnings) {
        List<ProductSelectionPluginIngestCommand.PluginWarning> normalized = normalizeWarnings(warnings);
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.stream()
                .map(warning -> warning.getCode()
                        + (StringUtils.hasText(warning.getField()) ? ":" + warning.getField() : "")
                        + (StringUtils.hasText(warning.getMessage()) ? " - " + warning.getMessage() : ""))
                .collect(Collectors.joining("\n"));
    }

    private String writeStringListJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private String writeCategoryLinksJson(List<ProductSelectionCompetitorCategoryLink> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<ProductSelectionCompetitorCategoryLink> readCategoryLinksJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return normalizeCategoryLinks(objectMapper.readValue(
                    json,
                    new TypeReference<List<ProductSelectionCompetitorCategoryLink>>() {
                    }
            ));
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    private List<ProductSelectionCompetitorCategoryLink> normalizeCategoryLinks(
            List<ProductSelectionCompetitorCategoryLink> values
    ) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<ProductSelectionCompetitorCategoryLink> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ProductSelectionCompetitorCategoryLink value : values) {
            if (value == null) {
                continue;
            }
            String name = defaultText(value.getName(), "");
            String path = defaultText(value.getPath(), name);
            String url = defaultText(value.getUrl(), "");
            String key = (path + "\n" + url).toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(name) || !seen.add(key)) {
                continue;
            }
            normalized.add(new ProductSelectionCompetitorCategoryLink(name, path, url));
        }
        return normalized;
    }

    private void applyCategoryLinks(
            ProductSelectionSourceCollectionView view,
            List<ProductSelectionCompetitorCategoryLink> categoryLinks
    ) {
        List<ProductSelectionCompetitorCategoryLink> normalized = normalizeCategoryLinks(categoryLinks);
        view.setCategoryLinks(normalized);
        if (normalized.isEmpty()) {
            return;
        }
        ProductSelectionCompetitorCategoryLink leaf = normalized.get(normalized.size() - 1);
        view.setCategoryName(leaf.getName());
        view.setCategoryPath(leaf.getPath());
        view.setCategoryUrl(leaf.getUrl());
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

    private List<String> normalizeSourceSellingPoints(List<String> values, int limit) {
        return normalizeList(values, limit).stream()
                .filter(value -> !isNoonNavigationSellingPoint(value))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isNoonNavigationSellingPoint(String value) {
        String normalized = defaultText(value, "").toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.contains("electronics mobiles & accessories")
                || normalized.equals("›")
                || normalized.equals("2+")
                || normalized.contains("class='nav_a'")
                || normalized.equals("galaxy ai")
                || normalized.equals("iphone 17 series")
                || normalized.equals("premium androids")
                || normalized.equals("tablets")
                || normalized.equals("headsets & speakers")
                || normalized.equals("wearables")
                || normalized.equals("power banks")
                || normalized.equals("chargers")
                || normalized.contains("الإلكترونيات الجوالات والاكسسوارات")
                || normalized.equals("سلسة أيفون 17")
                || normalized.equals("جوالات أندرويد فخمة")
                || normalized.equals("تابلت")
                || normalized.equals("سماعات ومكبرات صوت")
                || normalized.equals("أجهزة الارتداء")
                || normalized.equals("شواحن متنقلة")
                || normalized.equals("شواحن")
                || normalized.equals("المحتوى الرئيسي")
                || normalized.equals("عن هذه السلعة")
                || normalized.equals("خيارات الشراء")
                || normalized.equals("مقارنة مع سلع مماثلة")
                || normalized.equals("مقاطع الفيديو")
                || normalized.equals("التقييمات")
                || normalized.startsWith("بحث opt")
                || normalized.startsWith("عربة التسوق");
    }

    private List<Long> normalizeSourceCollectionIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.hasText(id)) {
                normalizedIds.add(parseLongId(id, "采集记录不存在或已被删除。"));
            }
        }
        return new ArrayList<>(normalizedIds);
    }

    private String defaultProjectName(List<ProductSelectionSourceCollectionRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "未命名选品项目";
        }
        return rows.stream()
                .map(this::defaultProjectName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("未命名选品项目");
    }

    private String defaultProjectName(ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            return "";
        }
        return defaultText(row.getSourceTitleCn(), defaultText(row.getSourceTitle(), "未命名选品项目"));
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

    private Long parseNullableLongId(String id, String message) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return parseLongId(id, message);
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

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
