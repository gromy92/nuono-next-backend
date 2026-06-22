package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.publish.ProductPublishCommandService;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateResult;
import com.nuono.next.product.publish.ProductPublishTaskFenceLostException;
import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProductMasterService {

    private static final Logger log = LoggerFactory.getLogger(LocalDbProductMasterService.class);

    private static final ThreadLocal<Boolean> PUBLISH_TASK_WORKER_MODE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductManagementMapper productManagementMapper;
    private final ProductLiteMapper productLiteMapper;
    private final ProductPublicDetailMapper productPublicDetailMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final LocalDbStoreInitializationService localDbStoreInitializationService;
    private final ProductAttributeTemplateService productAttributeTemplateService;
    private final ProductGroupPublishService productGroupPublishService;
    private final ProductDetailBaselineBackfillService productDetailBaselineBackfillService;
    private final ProductPublishCommandService productPublishCommandService;
    private final ProductPublishChangedDomainComparator productPublishChangedDomainComparator;
    private final ProductPublishUnsupportedChangesDetector productPublishUnsupportedChangesDetector;
    private final ProductPublishSupportedSnapshotBuilder productPublishSupportedSnapshotBuilder;
    private final ProductPublishOfferWriter productPublishOfferWriter;
    private final ProductPublishSharedZskuWriter productPublishSharedZskuWriter;
    private final ProductPublishPreparationService productPublishPreparationService;
    private final ProductPublishWriteService productPublishWriteService;
    private final ProductSnapshotHydrator productSnapshotHydrator;
    private final ProductOperationalKeyHydrator productOperationalKeyHydrator;
    private final ProductPublishTaskChangedDomainsResolver productPublishTaskChangedDomainsResolver;
    private final ProductPublishTaskViewBuilder productPublishTaskViewBuilder;
    private final ProductReadModelService productReadModelService;
    private final ProductDraftMergePolicy productDraftMergePolicy = new ProductDraftMergePolicy();
    private final ProductWorkbenchViewAssembler productWorkbenchViewAssembler = new ProductWorkbenchViewAssembler();
    private final ProductWorkbenchRecordStore productWorkbenchRecordStore;
    private final ProductWorkbenchOpenService productWorkbenchOpenService;
    private final ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver;
    private final ProductWorkbenchStatusHydrator productWorkbenchStatusHydrator;
    private final ProductWorkbenchPublishTaskAttacher productWorkbenchPublishTaskAttacher;
    private final ProductWorkbenchViewFinalizer productWorkbenchViewFinalizer;
    private final ProductWorkbenchRecordRestorer productWorkbenchRecordRestorer;
    private final ProductAttributeDictionaryHydrator productAttributeDictionaryHydrator;
    private final ProductProjectSiteResolver productProjectSiteResolver;
    private final ProductSiteOfferFetcher productSiteOfferFetcher;
    private final ProductSnapshotSectionBuilder productSnapshotSectionBuilder;
    private final ProductKeyAttributeBuilder productKeyAttributeBuilder;
    private final ProductCatalogContentFallbackApplier productCatalogContentFallbackApplier;
    private final ProductSnapshotGroupFetcher productSnapshotGroupFetcher;
    private final ProductSnapshotCoreFetcher productSnapshotCoreFetcher;
    private final ProductSnapshotTemplateFetcher productSnapshotTemplateFetcher;
    private final ProductStoreContextBuilder productStoreContextBuilder;
    private final ProductNoonCredentialResolver productNoonCredentialResolver = new ProductNoonCredentialResolver();
    private final ProductPublicDetailReadonlyWorkbenchFactory productPublicDetailReadonlyWorkbenchFactory =
            new ProductPublicDetailReadonlyWorkbenchFactory();
    private final ProductSnapshotMessageBuilder productSnapshotMessageBuilder = new ProductSnapshotMessageBuilder();

    @Value("${nuono.product-management.publish-task.async-enabled:true}")
    private boolean publishTaskAsyncEnabled;

    @Value("${nuono.product-management.publish-task.scheduler.enabled:true}")
    private boolean publishTaskSchedulerEnabled;

    @Value("${nuono.product-management.publish-task.scheduler.max-items-per-tick:2}")
    private int publishTaskSchedulerMaxItemsPerTick;

    @Value("${nuono.product-management.detail-baseline-backfill.enabled:true}")
    private boolean detailBaselineBackfillEnabled;

    public LocalDbProductMasterService(
            ProductManagementMapper productManagementMapper,
            ProductLiteMapper productLiteMapper,
            ProductPublicDetailMapper productPublicDetailMapper,
            StoreSyncMapper storeSyncMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService,
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter,
            ProductProjectionPersistenceService productProjectionPersistenceService,
            LocalDbStoreInitializationService localDbStoreInitializationService,
            ProductAttributeTemplateService productAttributeTemplateService,
            ProductGroupPublishService productGroupPublishService,
            ProductNoonCatalogContentService productNoonCatalogContentService,
            ProductDetailBaselineBackfillService productDetailBaselineBackfillService,
            ProductPublishCommandService productPublishCommandService,
            ProductReadModelService productReadModelService,
            ProductWorkbenchRecordStore productWorkbenchRecordStore,
            ProductWorkbenchOpenService productWorkbenchOpenService,
            ProductWorkbenchDirtySiteResolver productWorkbenchDirtySiteResolver,
            ProductWorkbenchStatusHydrator productWorkbenchStatusHydrator
    ) {
        this.productManagementMapper = productManagementMapper;
        this.productLiteMapper = productLiteMapper;
        this.productPublicDetailMapper = productPublicDetailMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.objectMapper = objectMapper;
        this.productSnapshotHydrator = new ProductSnapshotHydrator(objectMapper);
        this.productSnapshotCoreFetcher = new ProductSnapshotCoreFetcher(objectMapper, productNoonAdapter);
        this.productSnapshotSectionBuilder = new ProductSnapshotSectionBuilder(objectMapper);
        this.productKeyAttributeBuilder = new ProductKeyAttributeBuilder(objectMapper);
        this.productStoreContextBuilder = new ProductStoreContextBuilder();
        this.productSnapshotGroupFetcher = new ProductSnapshotGroupFetcher(
                objectMapper,
                productNoonAdapter,
                this.productSnapshotSectionBuilder
        );
        this.productOperationalKeyHydrator = new ProductOperationalKeyHydrator(productProjectionPersistenceService);
        this.productNoonAdapter = productNoonAdapter;
        this.productProjectSiteResolver = new ProductProjectSiteResolver(objectMapper, storeSyncMapper, productNoonAdapter);
        this.productSiteOfferFetcher = new ProductSiteOfferFetcher(
                objectMapper,
                productNoonAdapter,
                this.productProjectSiteResolver
        );
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.localDbStoreInitializationService = localDbStoreInitializationService;
        this.productAttributeTemplateService = productAttributeTemplateService;
        this.productSnapshotTemplateFetcher = new ProductSnapshotTemplateFetcher(productAttributeTemplateService);
        this.productAttributeDictionaryHydrator = new ProductAttributeDictionaryHydrator(productAttributeTemplateService);
        this.productGroupPublishService = productGroupPublishService;
        this.productCatalogContentFallbackApplier = new ProductCatalogContentFallbackApplier(productNoonCatalogContentService);
        this.productDetailBaselineBackfillService = productDetailBaselineBackfillService;
        this.productPublishCommandService = productPublishCommandService;
        this.productReadModelService = productReadModelService;
        this.productWorkbenchRecordStore = productWorkbenchRecordStore == null
                ? new ProductWorkbenchRecordStore()
                : productWorkbenchRecordStore;
        this.productWorkbenchOpenService = productWorkbenchOpenService == null
                ? new ProductWorkbenchOpenService(productProjectionPersistenceService, this.productWorkbenchRecordStore)
                : productWorkbenchOpenService;
        this.productWorkbenchDirtySiteResolver = productWorkbenchDirtySiteResolver == null
                ? new ProductWorkbenchDirtySiteResolver(objectMapper)
                : productWorkbenchDirtySiteResolver;
        this.productWorkbenchStatusHydrator = productWorkbenchStatusHydrator == null
                ? new ProductWorkbenchStatusHydrator(productProjectionPersistenceService)
                : productWorkbenchStatusHydrator;
        this.productPublishChangedDomainComparator = new ProductPublishChangedDomainComparator(objectMapper);
        this.productPublishUnsupportedChangesDetector = new ProductPublishUnsupportedChangesDetector(objectMapper);
        this.productPublishSupportedSnapshotBuilder = new ProductPublishSupportedSnapshotBuilder(
                objectMapper,
                new ProductPublishPlanner(productDraftMergePolicy)
        );
        this.productPublishOfferWriter = new ProductPublishOfferWriter(objectMapper, productNoonAdapter);
        this.productPublishPreparationService = new ProductPublishPreparationService(
                objectMapper,
                productDraftMergePolicy,
                productPublishOfferWriter
        );
        this.productWorkbenchRecordRestorer = new ProductWorkbenchRecordRestorer(
                productSnapshotHydrator,
                productPublishPreparationService
        );
        this.productPublishSharedZskuWriter = new ProductPublishSharedZskuWriter(
                objectMapper,
                productNoonAdapter,
                this.productPublishChangedDomainComparator
        );
        this.productPublishWriteService = new ProductPublishWriteService(
                storeSyncMapper,
                productNoonAdapter,
                productGroupPublishService,
                new ProductPublishWriteService.WriteOperations() {
                    @Override
                    public String resolveProjectCode(
                            NoonSession session,
                            String localProjectCode,
                            StoreSyncStoreRecord store,
                            List<String> warnings
                    ) {
                        return LocalDbProductMasterService.this.resolveProjectCode(session, localProjectCode, store, warnings);
                    }

                    @Override
                    public NoonSession withProjectAndStore(NoonSession session, String projectCode, String storeCode) {
                        return session.withProjectCode(projectCode).withStoreCode(storeCode);
                    }

                    @Override
                    public NoonSession withStore(NoonSession session, String storeCode) {
                        return session.withStoreCode(storeCode);
                    }

                    @Override
                    public boolean sharedZskuChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
                        return productPublishPreparationService.sharedZskuChanged(draft, baseline);
                    }

                    @Override
                    public boolean groupChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
                        return productPublishPreparationService.groupChanged(draft, baseline);
                    }

                    @Override
                    public void publishSharedAttributes(
                            NoonSession session,
                            ProductMasterSnapshotView draft,
                            ProductMasterSnapshotView baseline,
                            ProductMasterSnapshotView liveBeforePublish,
                            ProductPublishUnsupportedChanges unsupportedChanges,
                            List<String> actionWarnings
                    ) {
                        productPublishSharedZskuWriter.publishSharedAttributes(
                                session,
                                draft,
                                baseline,
                                liveBeforePublish,
                                unsupportedChanges,
                                actionWarnings
                        );
                    }

                    @Override
                    public List<Map<String, Object>> targetOffers(ProductMasterSnapshotView draft, String currentSiteCode) {
                        return productPublishPreparationService.targetOffers(draft, currentSiteCode);
                    }

                    @Override
                    public Map<String, Map<String, Object>> baselineOffers(ProductMasterSnapshotView baseline) {
                        return productPublishPreparationService.baselineOffers(baseline);
                    }

                    @Override
                    public boolean siteOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
                        return productPublishPreparationService.siteOfferChanged(siteOffer, baselineOffer);
                    }

                    @Override
                    public void publishOffer(
                            NoonSession session,
                            String pskuCode,
                            Map<String, Object> siteOffer,
                            List<String> actionWarnings
                    ) {
                        productPublishOfferWriter.publishOffer(session, pskuCode, siteOffer, actionWarnings);
                    }
                }
        );
        this.productPublishTaskChangedDomainsResolver = new ProductPublishTaskChangedDomainsResolver(
                objectMapper,
                this::recomputePublishTaskChangedDomains
        );
        this.productPublishTaskViewBuilder = new ProductPublishTaskViewBuilder(
                productPublishCommandService,
                this::buildTerminalPublishTaskWorkbenchView,
                this.productPublishTaskChangedDomainsResolver
        );
        this.productWorkbenchPublishTaskAttacher = new ProductWorkbenchPublishTaskAttacher(
                productManagementMapper,
                productPublishCommandService,
                this.productPublishTaskViewBuilder::build
        );
        this.productWorkbenchViewFinalizer = new ProductWorkbenchViewFinalizer(
                productProjectionPersistenceService,
                this.productWorkbenchDirtySiteResolver,
                this.productWorkbenchStatusHydrator,
                this.productWorkbenchPublishTaskAttacher
        );
    }

    private ProductPublishCommandService requirePublishCommandService() {
        if (productPublishCommandService == null) {
            throw new IllegalStateException("商品发布命令服务尚未初始化。");
        }
        return productPublishCommandService;
    }

    private ProductReadModelService requireReadModelService() {
        if (productReadModelService == null) {
            throw new IllegalStateException("商品读模型服务尚未初始化。");
        }
        return productReadModelService;
    }

    public ProductMasterSnapshotView fetchSnapshot(ProductMasterFetchCommand command) {
        return fetchSnapshot(command, "default");
    }

    private ProductMasterSnapshotView fetchSnapshot(ProductMasterFetchCommand command, String reason) {
        return fetchSnapshot(command, reason, null);
    }

    private ProductMasterSnapshotView fetchSnapshot(
            ProductMasterFetchCommand command,
            String reason,
            ProductMasterSnapshotView siteOfferReuseSeed
    ) {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();
        view.setMode("local-db");
        String traceLabel = buildFetchTraceLabel(command, reason);
        List<String> timingEntries = new ArrayList<>();
        Map<String, Long> timingBreakdownMs = new LinkedHashMap<>();
        long fetchStartedAt = System.nanoTime();

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        view.setReady(inspection.isReady());
        view.setMissingCoreTables(inspection.getMissingTables());
        if (!inspection.isReady()) {
            view.setMessage("本地库已启用，但第一批核心表还没有补齐，商品主档快照还不能读取。");
            return view;
        }

        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法读取商品主档快照。");
        }

        String storeCode = normalize(command.getStoreCode());
        String skuParent = normalize(command.getSkuParent());
        String partnerSku = normalize(command.getPartnerSku());
        String pskuCode = normalize(command.getPskuCode());
        requireText(storeCode, "缺少店铺编码，无法读取商品主档快照。");
        requireText(skuParent, "缺少 skuParent，无法定位 Noon 商品。");
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());
        ProductOperationalKeyHydrator.OperationalKeys operationalKeys = resolveOperationalKeysFromProjection(
                command.getOwnerUserId(),
                storeCode,
                skuParent,
                partnerSku,
                pskuCode
        );
        partnerSku = operationalKeys.getPartnerSku();
        pskuCode = operationalKeys.getPskuCode();

        List<String> missingOperationalKeys = productOperationalKeyHydrator.applyMissingOperationalKeyStatus(
                view,
                partnerSku,
                pskuCode
        );

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法读取商品主档快照。");
        }

        ProductNoonCredential credential = productNoonCredentialResolver.resolve(command, store, owner);
        requireText(credential.getNoonUser(), "当前店铺还没有 Noon 账号上下文，请先绑定店铺账号或手动填写 Noon 登录账号。");
        requireText(credential.getNoonPassword(), "当前店铺还没有 Noon 登录密码，请先把密码写入数据库。");
        requireText(credential.getProjectCode(), "当前店铺缺少 Noon projectCode，无法读取真实商品主档。");

        long stageStartedAt = System.nanoTime();
        NoonSession session = productNoonAdapter.login(
                owner.getId(),
                credential.getNoonUser(),
                credential.getNoonPassword(),
                credential.getNoonCookie(),
                credential.getProjectCode(),
                storeCode
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "login", stageStartedAt);

        stageStartedAt = System.nanoTime();
        String resolvedProjectCode = resolveProjectCode(session, credential.getProjectCode(), store, view.getWarnings());
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "resolveProjectCode", stageStartedAt);
        session = session.withProjectCode(resolvedProjectCode).withStoreCode(storeCode);

        ProductSnapshotCoreFetchResult coreFetchResult = productSnapshotCoreFetcher.fetch(
                session,
                skuParent,
                view.getWarnings(),
                (stageName, startedAt) -> recordFetchStage(
                        timingEntries,
                        timingBreakdownMs,
                        traceLabel,
                        reason,
                        stageName,
                        startedAt
                )
        );
        JsonNode whoamiNode = coreFetchResult.getWhoamiNode();
        JsonNode productNode = coreFetchResult.getProductNode();
        JsonNode variantInfoNode = coreFetchResult.getVariantInfoNode();

        JsonNode commonNode = productNode.path("attributes").path("common");
        JsonNode enNode = productNode.path("attributes").path("en");
        JsonNode arNode = productNode.path("attributes").path("ar");

        String productFulltype = text(commonNode, "product_fulltype");
        String brand = text(commonNode, "brand");
        String idPartner = text(commonNode, "id_partner");

        JsonNode fulltypeTemplateNode = productSnapshotTemplateFetcher.fetch(
                session,
                resolvedProjectCode,
                storeCode,
                productFulltype,
                command.getOwnerUserId(),
                view.getWarnings(),
                (stageName, startedAt) -> recordFetchStage(
                        timingEntries,
                        timingBreakdownMs,
                        traceLabel,
                        reason,
                        stageName,
                        startedAt
                )
        );

        ProductSnapshotGroupFetchResult groupFetchResult = productSnapshotGroupFetcher.fetch(
                session,
                skuParent,
                productFulltype,
                brand,
                view.getWarnings(),
                (stageName, startedAt) -> recordFetchStage(
                        timingEntries,
                        timingBreakdownMs,
                        traceLabel,
                        reason,
                        stageName,
                        startedAt
                )
        );
        String resolvedSkuGroup = groupFetchResult.getSkuGroup();
        JsonNode groupCurrentNode = groupFetchResult.getGroupCurrentNode();
        JsonNode groupDetailNode = groupFetchResult.getGroupDetailNode();
        JsonNode groupParentAttributesNode = groupFetchResult.getGroupParentAttributesNode();
        JsonNode groupListNode = groupFetchResult.getGroupListNode();

        stageStartedAt = System.nanoTime();
        List<ProductProjectSiteContext> projectSites = loadProjectSiteContexts(
                session,
                command.getOwnerUserId(),
                store,
                view.getWarnings()
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "projectSites", stageStartedAt);

        stageStartedAt = System.nanoTime();
        ProductSiteOfferFetchResult siteFetchResult;
        if (siteOfferReuseSeed != null) {
            siteFetchResult = reuseSiteOffersFromSnapshot(siteOfferReuseSeed, projectSites, storeCode);
            log.info(
                    "product-management fetchSnapshot detail stage=siteOffers.reuse-baseline store={} reason={} reusedSiteOfferCount={}",
                    storeCode,
                    reason,
                    siteFetchResult.getSiteOffers().size()
            );
        } else {
            siteFetchResult = loadSiteOffers(
                    session,
                    projectSites,
                    storeCode,
                    idPartner,
                    partnerSku,
                    pskuCode,
                    view.getWarnings()
            );
        }
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "siteOffers", stageStartedAt);
        JsonNode pricingNode = siteFetchResult.getReferencePricingNode();
        JsonNode stockNode = siteFetchResult.getReferenceStockNode();
        Map<String, Object> identity = buildIdentity(productNode, commonNode, pricingNode, skuParent, partnerSku, pskuCode);
        Map<String, Object> content = buildContent(commonNode, enNode, arNode);
        applyFollowSellCatalogContentIfNeeded(session, identity, content, resolveReferenceSite(projectSites, storeCode), reason);

        view.setStoreContext(
                productStoreContextBuilder.build(
                        owner,
                        store,
                        credential.getNoonUser(),
                        whoamiNode,
                        resolvedProjectCode,
                        storeCode,
                        resolveReferenceSite(projectSites, storeCode),
                        projectSites.size()
                )
        );
        view.setIdentity(identity);
        view.setTaxonomy(buildTaxonomy(commonNode));
        view.setContent(content);
        view.setPlatformSignals(buildPlatformSignals(commonNode));
        view.setKeyAttributes(buildKeyAttributes(fulltypeTemplateNode, commonNode, enNode, arNode));
        view.setGroup(buildGroup(groupCurrentNode, groupDetailNode, groupListNode, resolvedSkuGroup, groupParentAttributesNode));
        view.setVariants(buildVariants(variantInfoNode, productNode));
        view.setPricing(buildPricing(pricingNode));
        view.setStock(buildStock(stockNode));
        view.setSiteOffers(siteFetchResult.getSiteOffers());
        view.setReady(true);
        view.setMessage(productSnapshotMessageBuilder.buildLoadedMessage(
                store,
                storeCode,
                missingOperationalKeys,
                view.isDegraded(),
                projectSites.size()
        ));
        stageStartedAt = System.nanoTime();
        productProjectionPersistenceService.persistSnapshotProjection(
                command.getOwnerUserId(),
                view,
                "synced",
                extractFetchedAt(view),
                view.getWarnings()
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "persistSnapshotProjection", stageStartedAt);
        logFetchSnapshotTimings(traceLabel, timingEntries, fetchStartedAt);
        logFocusedFetchBreakdown(traceLabel, reason, timingBreakdownMs, siteOfferReuseSeed != null, projectSites.size());
        return view;
    }

    public ProductMasterWorkbenchView openWorkbench(ProductMasterFetchCommand command) {
        ProductMasterWorkbenchView cachedWorkbench =
                productWorkbenchOpenService.openFromLocalBaseline(command, openWorkbenchSupport());
        if (cachedWorkbench != null) {
            return cachedWorkbench;
        }

        ProductMasterWorkbenchView publicDetailWorkbench = openPublicDetailReadonlyWorkbench(command);
        if (publicDetailWorkbench != null) {
            return publicDetailWorkbench;
        }

        ProductMasterWorkbenchView missingWorkbench = productWorkbenchViewAssembler.buildLocalBaselineMissingWorkbench(command);
        productWorkbenchViewAssembler.applyMissingBaselineBackfillPrompt(
                missingWorkbench,
                enqueueDetailBaselineBackfill(command, "open-missing-baseline")
        );
        return missingWorkbench;
    }

    private ProductMasterWorkbenchView openPublicDetailReadonlyWorkbench(ProductMasterFetchCommand command) {
        if (command == null
                || command.getOwnerUserId() == null
                || productPublicDetailMapper == null
                || productManagementMapper == null) {
            return null;
        }
        String requestedStoreCode = normalize(command.getStoreCode());
        String skuParent = normalize(command.getSkuParent());
        if (!StringUtils.hasText(requestedStoreCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                requestedStoreCode,
                "当前店铺不在选中的老板名下。"
        );
        String storeCode = normalize(store.getStoreCode());
        ProductListProjectionRecord projection = productManagementMapper.selectProductListProjectionBySkuParent(
                command.getOwnerUserId(),
                storeCode,
                skuParent
        );
        ProductPublicDetailSnapshot publicDetail = productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                command.getOwnerUserId(),
                storeCode,
                skuParent
        );
        ProductMasterSnapshotView baseline = productPublicDetailReadonlyWorkbenchFactory.buildBaseline(
                command,
                store,
                projection,
                publicDetail
        );
        if (baseline == null) {
            return null;
        }

        ProductWorkbenchRecord record = createSyncedRecord(baseline);
        record.setSyncStatus("failed");
        record.setNote("已使用 Noon 前台公开详情打开只读视图；当前尚未拿到可写的 catalog 详情基线。");
        String backfillStatus = enqueueDetailBaselineBackfill(command, "open-public-detail-readonly");
        if ("preparing".equals(backfillStatus)) {
            baseline.getWarnings().add("系统正在后台继续尝试补齐可写的 Noon catalog 详情基线，补齐后重新打开详情即可编辑。");
            record.getBaselineSnapshot().setWarnings(baseline.getWarnings());
            record.getDraftSnapshot().setWarnings(baseline.getWarnings());
        }
        productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), requestedStoreCode, skuParent), record);
        if (!Objects.equals(requestedStoreCode, storeCode)) {
            productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), storeCode, skuParent), record);
        }

        ProductMasterWorkbenchView view = productWorkbenchViewAssembler.buildWorkbenchView(
                record,
                record.getNote(),
                baseline.getWarnings()
        );
        productWorkbenchStatusHydrator.hydrateListSummaryState(command.getOwnerUserId(), view, view.getWarnings());
        return view;
    }

    private String enqueueDetailBaselineBackfill(ProductMasterFetchCommand command, String reason) {
        if (!detailBaselineBackfillEnabled || productDetailBaselineBackfillService == null) {
            return "disabled";
        }
        return productDetailBaselineBackfillService.enqueue(command, reason, this::fetchSnapshot);
    }

    private ProductWorkbenchOpenService.OpenSupport openWorkbenchSupport() {
        return new ProductWorkbenchOpenService.OpenSupport() {
            @Override
            public void hydrateBaselineSnapshot(
                    Long ownerUserId,
                    String storeCode,
                    String skuParent,
                    ProductMasterSnapshotView baselineSnapshot,
                    List<String> warnings
            ) {
                hydrateSnapshotOperationalKeys(ownerUserId, storeCode, skuParent, baselineSnapshot);
                hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, baselineSnapshot, warnings);
                clearResolvedOperationalWarnings(baselineSnapshot);
                productProjectionPersistenceService.hydrateSnapshotGroupFromCurrentProjection(
                        ownerUserId,
                        storeCode,
                        skuParent,
                        baselineSnapshot,
                        warnings
                );
            }

            @Override
            public boolean hasActivePersistedDraft(
                    ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
            ) {
                return LocalDbProductMasterService.this.hasActivePersistedDraft(persistedState);
            }

            @Override
            public void hydrateWorkbenchRecord(
                    Long ownerUserId,
                    String storeCode,
                    ProductWorkbenchRecord record,
                    List<String> warnings
            ) {
                hydrateWorkbenchAttributeDictionary(ownerUserId, storeCode, record, warnings);
            }

            @Override
            public ProductWorkbenchRecord restorePersistedWorkbenchRecord(
                    ProductMasterSnapshotView baselineSnapshot,
                    ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
            ) {
                return LocalDbProductMasterService.this.restorePersistedWorkbenchRecord(baselineSnapshot, persistedState);
            }

            @Override
            public String extractFetchedAt(ProductMasterSnapshotView snapshot) {
                return LocalDbProductMasterService.this.extractFetchedAt(snapshot);
            }

            @Override
            public ProductMasterWorkbenchView finalizeWorkbenchView(
                    Long ownerUserId,
                    ProductWorkbenchRecord record,
                    String message,
                    List<String> warnings
            ) {
                return LocalDbProductMasterService.this.finalizeWorkbenchView(
                        ownerUserId,
                        null,
                        null,
                        record,
                        message,
                        warnings
                );
            }

            @Override
            public List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings) {
                return LocalDbProductMasterService.this.mergeWarnings(baseWarnings, extraWarnings);
            }
        };
    }

    private String normalizeReason(String reason) {
        String normalized = normalize(reason);
        if (!StringUtils.hasText(normalized)) {
            return "default";
        }
        return normalized.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    private boolean hasActivePersistedDraft(ProductProjectionPersistenceService.PersistedWorkbenchState persistedState) {
        if (persistedState == null || persistedState.getBaselineSnapshot() == null || persistedState.getDraftSnapshot() == null) {
            return false;
        }
        return !sameBusinessSnapshot(persistedState.getDraftSnapshot(), persistedState.getBaselineSnapshot());
    }

    public ProductListSummaryView loadListSummary(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取商品列表摘要。");
        }
        requireText(normalize(command.getStoreCode()), "缺少店铺编码，暂时不能读取商品列表摘要。");
        requireText(normalize(command.getSkuParent()), "缺少 skuParent，暂时不能读取商品列表摘要。");
        List<String> warnings = new ArrayList<>();
        return productProjectionPersistenceService.loadProductListSummary(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent(),
                warnings
        );
    }

    public ProductGroupCandidatesView loadGroupCandidates(ProductMasterFetchCommand command) {
        return requireReadModelService().loadGroupCandidates(command);
    }

    public ProductClassificationOptionsView loadClassificationOptions(ProductClassificationOptionsCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取品牌和类目候选。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能读取品牌和类目候选。");
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());

        int limit = command.getLimit() == null ? 80 : command.getLimit();
        limit = Math.max(10, Math.min(limit, 120));
        String brandQuery = normalize(command.getBrandQuery());
        String fulltypeQuery = normalize(command.getFulltypeQuery());

        ProductClassificationOptionsView view = new ProductClassificationOptionsView();
        view.setReady(true);
        view.setSource("dictionary");
        List<ProductClassificationOptionRecord> brands = productManagementMapper.selectBrandDictionaryOptions(
                command.getOwnerUserId(),
                storeCode,
                brandQuery,
                limit
        );
        List<ProductClassificationOptionRecord> fulltypes = productManagementMapper.selectFulltypeDictionaryOptions(
                command.getOwnerUserId(),
                storeCode,
                fulltypeQuery,
                limit
        );
        boolean usedProjectionFallback = false;
        if (brands.isEmpty() && !StringUtils.hasText(brandQuery)) {
            brands = productLiteMapper.selectBrandProjectionClassificationOptions(
                    command.getOwnerUserId(),
                    storeCode,
                    brandQuery,
                    limit
            );
            usedProjectionFallback = !brands.isEmpty();
        }
        if (fulltypes.isEmpty() && !StringUtils.hasText(fulltypeQuery)) {
            fulltypes = productLiteMapper.selectFulltypeProjectionClassificationOptions(
                    command.getOwnerUserId(),
                    storeCode,
                    fulltypeQuery,
                    limit
            );
            usedProjectionFallback = usedProjectionFallback || !fulltypes.isEmpty();
        }
        view.setBrands(brands);
        view.setFulltypes(fulltypes);
        if (usedProjectionFallback) {
            view.setSource("dictionary+product-projection-fallback");
            view.getWarnings().add("品牌/类目字典暂无完整数据，本次临时使用已同步商品投影补齐候选。");
        }
        if (view.getBrands().isEmpty()) {
            view.getWarnings().add("当前店铺还没有可用品牌字典，请先从 Noon 同步商品或维护品牌字典。");
        }
        if (view.getFulltypes().isEmpty()) {
            view.getWarnings().add("当前店铺还没有可用类目字典，请先从 Noon 同步商品或维护类目字典。");
        }
        view.setMessage("已读取系统品牌和官方类目字典候选。");
        return view;
    }

    public Long persistUploadedImageAsset(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String url,
            String storageKey,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            String sha256,
            List<String> warnings
    ) {
        return productProjectionPersistenceService.persistUploadedImageAsset(
                ownerUserId,
                storeCode,
                skuParent,
                url,
                storageKey,
                originalFilename,
                contentType,
                sizeBytes,
                sha256,
                warnings
        );
    }

    public ProductListDatasetView loadListDataset(ProductMasterFetchCommand command) {
        return requireReadModelService().loadListDataset(command);
    }

    @Transactional
    public ProductListDatasetView deleteLocalProduct(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能删除商品。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能删除商品。");
        String skuParent = normalize(command.getSkuParent());
        requireText(skuParent, "缺少商品 SKU，暂时不能删除商品。");

        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());
        Long productMasterId = productManagementMapper.selectProductMasterIdByStoreCode(
                command.getOwnerUserId(),
                storeCode,
                skuParent
        );
        if (productMasterId == null) {
            throw new IllegalArgumentException("当前商品不存在或已删除。");
        }

        Long updatedBy = command.getOwnerUserId();
        productManagementMapper.markProductGroupMembersDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.refreshProductGroupMemberCountsForProductMaster(productMasterId, updatedBy);
        productManagementMapper.deleteProductMasterDraftByProductMasterId(productMasterId);
        productManagementMapper.markProductMasterSnapshotsDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.markProductImageAssetsDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.markProductIssuesDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.markProductSiteOffersDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.markProductBarcodesDeletedByProductMasterId(productMasterId, updatedBy);
        productManagementMapper.markProductVariantsDeletedByProductMasterId(productMasterId, updatedBy);
        int deleted = productManagementMapper.markProductMasterDeletedById(productMasterId, updatedBy);
        if (deleted == 0) {
            throw new IllegalArgumentException("当前商品不存在或已删除。");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("message", "商品已从本地商品目录删除。");
        summary.put("skuParent", skuParent);
        summary.put("storeCode", storeCode);
        productManagementMapper.insertProductActionLog(
                productManagementMapper.nextProductActionLogId(),
                productMasterId,
                null,
                null,
                null,
                "delete-product",
                "local_only",
                null,
                "local",
                "synced",
                null,
                normalize(command.getPskuCode()),
                null,
                0,
                writeJson(summary),
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                updatedBy
        );

        if (productDetailBaselineBackfillService != null) {
            productDetailBaselineBackfillService.cancel(command, "商品已从本地商品目录删除。");
        }

        ProductMasterFetchCommand reloadCommand = new ProductMasterFetchCommand();
        reloadCommand.setOwnerUserId(command.getOwnerUserId());
        reloadCommand.setStoreCode(storeCode);
        reloadCommand.setNoonUser(command.getNoonUser());
        reloadCommand.setNoonPassword(command.getNoonPassword());
        return loadListDataset(reloadCommand);
    }

    public ProductHistoryView loadHistory(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取关键内容历史。");
        }
        requireText(normalize(command.getStoreCode()), "缺少店铺编码，暂时不能读取关键内容历史。");
        requireText(normalize(command.getSkuParent()), "缺少 skuParent，暂时不能读取关键内容历史。");
        List<String> warnings = new ArrayList<>();
        return productProjectionPersistenceService.loadProductHistoryView(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent(),
                warnings
        );
    }

    public ProductMasterWorkbenchView applyAction(ProductMasterActionCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能执行商品详情动作。");
        }

        String resolvedAction = resolveAction(command.getAction());
        String key = workbenchKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent());

        if ("SAVE_DRAFT".equals(resolvedAction)) {
            ensureNoActivePublishTaskForCommand(command, "当前商品已有发布任务正在执行，请等待完成后再保存草稿。");
            ProductWorkbenchRecord record = ensureWorkbenchRecord(command, key);
            if (isPublicDetailReadonlyRecord(record)) {
                return rejectPublicDetailReadonlyAction(command, key, record, "保存草稿");
            }
            ProductMasterSnapshotView draftSnapshot = sanitizeSnapshot(command.getSnapshot(), record.getBaselineSnapshot());
            record.setDraftSnapshot(draftSnapshot);
            if (sameBusinessSnapshot(record.getDraftSnapshot(), record.getBaselineSnapshot())) {
                record.setSyncStatus("synced");
                record.setNote("当前草稿与最近同步基线一致。");
            } else {
                record.setSyncStatus("draft");
                record.setNote("草稿已保存。后续可以发布共享主档与当前站点经营面。");
            }
            appendRecentAction(record, "save", record.getSyncStatus(), record.getNote(), command.getCurrentSiteCode(), record.getDraftSnapshot());
            productWorkbenchRecordStore.put(key, record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "save",
                    command.getCurrentSiteCode(),
                    record,
                    "已保存为诺诺草稿。",
                    record.getDraftSnapshot().getWarnings()
            );
        }

        ProductWorkbenchRecord record = ensureWorkbenchRecord(command, key);

        if ("ROLLBACK_DRAFT".equals(resolvedAction)) {
            ensureNoActivePublishTaskForCommand(command, "当前商品已有发布任务正在执行，请等待完成后再回滚草稿。");
            if (isPublicDetailReadonlyRecord(record)) {
                return rejectPublicDetailReadonlyAction(command, key, record, "回滚草稿");
            }
            ProductMasterSnapshotView baselineSnapshot = copySnapshot(record.getBaselineSnapshot());
            record.setDraftSnapshot(baselineSnapshot);
            record.setSyncStatus("synced");
            record.setNote("已回滚本地草稿，当前工作台恢复到最近本地商品基线。");
            record.setPublishTask(null);
            appendRecentAction(record, "rollback-draft", record.getSyncStatus(), record.getNote(), command.getCurrentSiteCode(), baselineSnapshot);
            productWorkbenchRecordStore.put(key, record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "rollback-draft",
                    command.getCurrentSiteCode(),
                    record,
                    record.getNote(),
                    baselineSnapshot.getWarnings()
            );
        }

        if ("PULL_FROM_NOON".equals(resolvedAction)) {
            ensureNoActivePublishTaskForCommand(command, "当前商品已有发布任务正在执行，请等待完成后再从 Noon 同步。");
            ProductMasterSnapshotView liveSnapshot = fetchSnapshot(command, "pull-from-noon");
            ProductWorkbenchRecord refreshed = createSyncedRecord(liveSnapshot);
            if (hasDraftChanges(record) && isKeepDraftSyncPolicy(command.getSyncMergePolicy())) {
                ProductMasterSnapshotView hydratedDraft = copySnapshot(record.getDraftSnapshot());
                hydrateProjectionOnlyFields(hydratedDraft, liveSnapshot);
                refreshed.setDraftSnapshot(hydratedDraft);
                if (sameBusinessSnapshot(refreshed.getDraftSnapshot(), refreshed.getBaselineSnapshot())) {
                    refreshed.setSyncStatus("synced");
                    refreshed.setNote("已按 Noon 当前版本刷新基线，当前草稿与最新基线一致。");
                } else {
                    refreshed.setSyncStatus("draft");
                    refreshed.setNote("已按 Noon 当前版本刷新基线，并保留本地草稿。");
                }
            } else {
                refreshed.setNote("已按 Noon 当前版本刷新基线，并使用 Noon 内容覆盖本地草稿。");
            }
            productWorkbenchRecordStore.put(key, refreshed);
            appendRecentAction(refreshed, "pull", refreshed.getSyncStatus(), refreshed.getNote(), command.getCurrentSiteCode(), liveSnapshot);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "pull",
                    command.getCurrentSiteCode(),
                    refreshed,
                    refreshed.getNote(),
                    liveSnapshot.getWarnings()
            );
        }

        ProductMasterSnapshotView requestedSnapshot = sanitizeSnapshot(command.getSnapshot(), record.getDraftSnapshot());
        hydrateProjectionOnlyFields(requestedSnapshot, record.getBaselineSnapshot());
        String currentSiteCode = normalize(command.getCurrentSiteCode());
        if (isPublicDetailReadonlyRecord(record)) {
            return rejectPublicDetailReadonlyAction(command, key, record, "发布当前修改");
        }
        if ("PUBLISH_CURRENT_SITE".equals(resolvedAction)) {
            requireText(currentSiteCode, "缺少当前站点编码，暂时不能发布当前站点。");
        }
        requestedSnapshot = prepareSnapshotForPublish(requestedSnapshot, record.getBaselineSnapshot(), currentSiteCode);
        ProductPublishUnsupportedChanges unsupportedChanges = productPublishUnsupportedChangesDetector.detect(
                requestedSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode
        );
        ProductMasterSnapshotView publishableSnapshot = productPublishSupportedSnapshotBuilder.build(
                requestedSnapshot,
                record.getBaselineSnapshot(),
                unsupportedChanges
        );

        if (sameScopedSnapshot(
                publishableSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode
        )) {
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus(sameBusinessSnapshot(requestedSnapshot, record.getBaselineSnapshot()) ? "synced" : "draft");
            record.setNote("当前没有待发布改动。");
            appendRecentAction(record, "publish-current", record.getSyncStatus(), record.getNote(), currentSiteCode, requestedSnapshot);
            productWorkbenchRecordStore.put(key, record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "publish-current",
                    currentSiteCode,
                    record,
                    record.getNote(),
                    requestedSnapshot.getWarnings()
            );
        }

        List<String> validationErrors = validatePublishSnapshot(
                publishableSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode
        );
        validationErrors.addAll(validatePublishOperationalKeys(publishableSnapshot, record.getBaselineSnapshot(), currentSiteCode));
        validationErrors.addAll(productPublishUnsupportedChangesDetector.validateWriteCoverage(unsupportedChanges));
        if (!validationErrors.isEmpty()) {
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus("failed");
            record.setNote(validationErrors.get(0));
            appendRecentAction(record, "publish-current", "failed", record.getNote(), currentSiteCode, requestedSnapshot);
            productWorkbenchRecordStore.put(key, record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "publish-current",
                    currentSiteCode,
                    record,
                    validationErrors.get(0),
                    mergeWarnings(record.getDraftSnapshot().getWarnings(), validationErrors)
            );
        }

        if (publishTaskAsyncEnabled && !isPublishTaskWorkerMode()) {
            return queuePublishCurrentTask(command, record, requestedSnapshot, currentSiteCode, unsupportedChanges);
        }

        long liveBeforePublishStartedAt = System.nanoTime();
        boolean skipSiteOfferLiveReadForSharedOnlyPublish = shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
                publishableSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode
        );
        ProductMasterSnapshotView liveBeforePublish = fetchSnapshot(
                command,
                "publish-current.before",
                skipSiteOfferLiveReadForSharedOnlyPublish ? record.getBaselineSnapshot() : null
        );
        log.info(
                "product-management publish-current pre-read total trace={} durationMs={}",
                buildFetchTraceLabel(command, "publish-current.before-total"),
                nanosToMillis(liveBeforePublishStartedAt)
        );
        List<String> prePublishWarnings = new ArrayList<>();
        boolean liveChangedSinceBaseline = !sameScopedSnapshot(
                liveBeforePublish,
                record.getBaselineSnapshot(),
                currentSiteCode
        );
        boolean localChangedSinceBaseline = !sameScopedSnapshot(
                publishableSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode
        );
        if (liveChangedSinceBaseline && localChangedSinceBaseline) {
            List<Map<String, Object>> publishConflictFields = detectPublishConflictFields(
                    record.getBaselineSnapshot(),
                    publishableSnapshot,
                    liveBeforePublish,
                    currentSiteCode
            );
            if (!publishConflictFields.isEmpty()) {
                prePublishWarnings.add("发布前校验：Noon 当前内容与本地草稿存在同字段差异，本次按本地草稿覆盖 Noon 对应字段。");
            } else {
                prePublishWarnings.add("发布前校验：Noon 存在非本次编辑字段变化，未发现同字段冲突，已继续发布。");
            }
        }

        StoreSyncStoreRecord store = requirePublishStore(command.getOwnerUserId(), command.getStoreCode());
        ensurePublishStoreAllowed(store);
        ProductMasterSnapshotView baselineBeforePublish = copySnapshot(record.getBaselineSnapshot());

        List<String> actionWarnings = new ArrayList<>(prePublishWarnings);
        try {
            productPublishWriteService.publishSupportedChanges(
                    command,
                    store,
                    publishableSnapshot,
                    record.getBaselineSnapshot(),
                    liveBeforePublish,
                    currentSiteCode,
                    unsupportedChanges,
                    actionWarnings
            );
        } catch (IllegalStateException exception) {
            log.warn(
                    "product-management publish-current noon write failed owner={} store={} skuParent={} site={} error={}",
                    command.getOwnerUserId(),
                    command.getStoreCode(),
                    textValue(requestedSnapshot.getIdentity().get("skuParent")),
                    currentSiteCode,
                    shrink(exception.getMessage())
            );
            if (exception instanceof ProductGroupPartialPublishException) {
                record.setDraftSnapshot(requestedSnapshot);
                record.setSyncStatus("pending_manual_check");
                record.setNote("Group 写回可能已部分提交到 Noon，请先从 Noon 同步后确认结果。");
                appendRecentAction(record, "publish-current", "pending_manual_check", record.getNote(), currentSiteCode, requestedSnapshot);
                productWorkbenchRecordStore.put(key, record);
                return finalizeWorkbenchView(
                        command.getOwnerUserId(),
                        "publish-current",
                        currentSiteCode,
                        record,
                        record.getNote(),
                        mergeWarnings(
                                record.getDraftSnapshot().getWarnings(),
                                List.of(record.getNote(), shrink(exception.getMessage()))
                        )
                );
            }
            if (exception instanceof ProductGroupValidationException) {
                record.setDraftSnapshot(requestedSnapshot);
                record.setSyncStatus("failed");
                record.setNote(shrink(exception.getMessage()));
                appendRecentAction(record, "publish-current", "failed", record.getNote(), currentSiteCode, requestedSnapshot);
                productWorkbenchRecordStore.put(key, record);
                return finalizeWorkbenchView(
                        command.getOwnerUserId(),
                        "publish-current",
                        currentSiteCode,
                        record,
                        record.getNote(),
                        mergeWarnings(record.getDraftSnapshot().getWarnings(), List.of(record.getNote()))
                );
            }
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus("failed");
            record.setNote("Noon 发布接口暂时不可用，诺诺草稿已保留。请稍后重试或先从 Noon 同步后再发布。");
            appendRecentAction(record, "publish-current", "failed", record.getNote(), currentSiteCode, requestedSnapshot);
            productWorkbenchRecordStore.put(key, record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    "publish-current",
                    currentSiteCode,
                    record,
                    record.getNote(),
                    mergeWarnings(
                            record.getDraftSnapshot().getWarnings(),
                            List.of(record.getNote(), "Noon 发布返回错误，系统已保留本地草稿并记录后端日志。")
                    )
            );
        }

        long liveAfterPublishStartedAt = System.nanoTime();
        ProductMasterSnapshotView liveAfterPublish = fetchSnapshot(command, "publish-current.after");
        log.info(
                "product-management publish-current post-read total trace={} durationMs={}",
                buildFetchTraceLabel(command, "publish-current.after-total"),
                nanosToMillis(liveAfterPublishStartedAt)
        );
        ProductWorkbenchRecord refreshed = createSyncedRecord(liveAfterPublish);
        refreshed.setDraftSnapshot(copySnapshot(liveAfterPublish));
        productPublishSupportedSnapshotBuilder.overlayUnsupportedDraft(refreshed.getDraftSnapshot(), requestedSnapshot, unsupportedChanges);

        if (sameBusinessSnapshot(refreshed.getDraftSnapshot(), refreshed.getBaselineSnapshot())) {
            refreshed.setSyncStatus("synced");
            refreshed.setNote("共享主档和当前站点经营面已同步完成。");
        } else {
            refreshed.setSyncStatus("draft");
            refreshed.setNote(
                    "已发布共享主档与当前站点经营字段，其他站点变更仍留在诺诺草稿中。"
            );
        }
        appendRecentAction(refreshed, "publish-current", refreshed.getSyncStatus(), refreshed.getNote(), currentSiteCode, refreshed.getDraftSnapshot());
        productWorkbenchRecordStore.put(key, refreshed);

        List<String> mergedWarnings = mergeWarnings(liveAfterPublish.getWarnings(), actionWarnings);
        ProductMasterWorkbenchView view = finalizeWorkbenchView(
                command.getOwnerUserId(),
                "publish-current",
                currentSiteCode,
                refreshed,
                "已执行当前站点发布。",
                mergedWarnings,
                baselineBeforePublish,
                publishableSnapshot
        );
        productProjectionPersistenceService.persistPublishedKeyContentHistory(
                command.getOwnerUserId(),
                baselineBeforePublish,
                publishableSnapshot,
                currentSiteCode,
                view.getWarnings()
        );
        hydratePendingKeyContentHistoryState(
                command.getOwnerUserId(),
                refreshed.getBaselineSnapshot(),
                refreshed,
                view
        );
        return view;
    }

    public ProductPublishTaskView loadPublishTask(Long taskId, Long ownerUserId) {
        return productPublishTaskViewBuilder.load(taskId, ownerUserId);
    }

    public ProductPublishTaskView retryPublishTask(Long taskId, Long ownerUserId) {
        return productPublishTaskViewBuilder.retry(taskId, ownerUserId);
    }

    public ProductPublishTaskView cancelPublishTask(Long taskId, Long ownerUserId) {
        return productPublishTaskViewBuilder.cancel(taskId, ownerUserId);
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-management.publish-task.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${nuono.product-management.publish-task.scheduler.initial-delay-ms:3000}"
    )
    public void runProductPublishTaskScheduler() {
        try {
            runProductPublishTaskSchedulerTick();
        } catch (Throwable exception) {
            log.warn(
                    "product-management publish task scheduler tick aborted error={}",
                    shrink(exception.getMessage()),
                    exception
            );
        }
    }

    private void runProductPublishTaskSchedulerTick() {
        if (!publishTaskSchedulerEnabled) {
            return;
        }
        int limit = Math.max(1, publishTaskSchedulerMaxItemsPerTick);
        List<ProductPublishTaskRecord> tasks;
        try {
            tasks = requirePublishCommandService().selectRunnableTasks(limit);
        } catch (RuntimeException exception) {
            if (requirePublishCommandService().isMissingTaskTable(exception)) {
                log.debug("product-management publish task scheduler skipped: product_publish_task not initialized yet");
                return;
            }
            throw exception;
        }
        for (ProductPublishTaskRecord task : tasks) {
            if (task == null || task.getId() == null || !StringUtils.hasText(task.getStatus())) {
                continue;
            }
            String previousStatus = task.getStatus();
            String lockedBy = requirePublishCommandService().buildLockToken(task);
            if (!requirePublishCommandService().claimTask(task, previousStatus, lockedBy)) {
                continue;
            }
            try {
                if ("queued".equalsIgnoreCase(previousStatus)) {
                    executeQueuedPublishTask(task);
                } else {
                    executePublishTaskVerificationOnly(task, previousStatus);
                }
            } catch (ProductPublishTaskFenceLostException exception) {
                log.info(
                        "product-management publish task skipped after losing claim id={} targetStatus={} error={}",
                        task.getId(),
                        exception.getTargetStatus(),
                        shrink(exception.getMessage())
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "product-management publish task failed id={} previousStatus={} error={}",
                        task.getId(),
                        previousStatus,
                        shrink(exception.getMessage()),
                        exception
                );
                updatePublishTaskStatus(
                        task,
                        "failed",
                        "task_unhandled_error",
                        "发布任务执行异常，诺诺草稿已保留：" + shrink(exception.getMessage()),
                        null,
                        null,
                        LocalDateTime.now(),
                        null
                );
            }
        }
    }

    private ProductMasterWorkbenchView queuePublishCurrentTask(
            ProductMasterActionCommand command,
            ProductWorkbenchRecord record,
            ProductMasterSnapshotView requestedSnapshot,
            String currentSiteCode,
            ProductPublishUnsupportedChanges unsupportedChanges
    ) {
        Long productMasterId = resolveProductMasterId(command);
        ProductPublishTaskRecord activeTask = productMasterId == null
                ? null
                : requirePublishCommandService().selectActiveTask(productMasterId);
        if (activeTask != null) {
            record.setPublishTask(productPublishTaskViewBuilder.build(activeTask, false));
            record.setNote("当前商品已有发布任务正在执行，请等待任务完成后再继续编辑或发布。");
            productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent()), record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    null,
                    currentSiteCode,
                    record,
                    record.getNote(),
                    mergeWarnings(record.getDraftSnapshot().getWarnings(), List.of(record.getNote()))
            );
        }
        if (productMasterId == null) {
            throw new IllegalStateException("本地商品主档还没有落库，暂时不能创建发布任务。");
        }

        String draftJson = writeJson(requestedSnapshot);
        String draftHash = sha256Hex(draftJson);
        ProductPublishTaskCreateCommand createCommand = new ProductPublishTaskCreateCommand();
        createCommand.setOwnerUserId(command.getOwnerUserId());
        createCommand.setProductMasterId(productMasterId);
        createCommand.setStoreCode(command.getStoreCode());
        createCommand.setProjectCode(textValue(requestedSnapshot.getStoreContext().get("projectCode")));
        createCommand.setSkuParent(textValue(requestedSnapshot.getIdentity().get("skuParent")));
        createCommand.setPartnerSku(firstNonBlank(normalize(command.getPartnerSku()), textValue(requestedSnapshot.getIdentity().get("partnerSku"))));
        createCommand.setPskuCode(firstNonBlank(normalize(command.getPskuCode()), textValue(requestedSnapshot.getIdentity().get("pskuCode"))));
        createCommand.setCurrentSiteCode(currentSiteCode);
        createCommand.setDraftJson(draftJson);
        createCommand.setBaselineJson(writeJson(record.getBaselineSnapshot()));
        createCommand.setDraftHash(draftHash);
        createCommand.setChangedDomainsJson(writeJson(productPublishChangedDomainComparator.resolve(
                requestedSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode,
                unsupportedChanges != null && unsupportedChanges.isGroupChanged(),
                unsupportedChanges != null && unsupportedChanges.isVariantStructureChanged()
        )));
        createCommand.setRequestJson(writeJson(buildPublishTaskRequestPayload(command, currentSiteCode)));
        createCommand.setIdempotencyKey(productMasterId + ":" + draftHash + ":" + normalize(currentSiteCode));
        ProductPublishTaskCreateResult createResult = requirePublishCommandService().createPublishCurrentTask(createCommand);
        ProductPublishTaskRecord task = createResult.getTask();
        if (createResult.isDuplicate()) {
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus("draft");
            record.setNote(isActivePublishTaskStatus(task.getStatus())
                    ? "当前商品已有发布任务正在执行，请等待任务完成后再继续编辑或发布。"
                    : publishTaskMessage(task));
            record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
            productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent()), record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    null,
                    currentSiteCode,
                    record,
                    record.getNote(),
                    mergeWarnings(requestedSnapshot.getWarnings(), List.of(record.getNote()))
            );
        }

        record.setDraftSnapshot(requestedSnapshot);
        record.setSyncStatus("draft");
        record.setNote("发布已提交，正在后台校验 Noon 结果。");
        record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
        appendRecentAction(record, "publish-current", "draft", record.getNote(), currentSiteCode, requestedSnapshot);
        productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent()), record);
        return finalizeWorkbenchView(
                command.getOwnerUserId(),
                "publish-current",
                currentSiteCode,
                record,
                record.getNote(),
                requestedSnapshot.getWarnings()
        );
    }

    private void executeQueuedPublishTask(ProductPublishTaskRecord task) {
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductMasterActionCommand command = buildTaskActionCommand(task, draft);
        String currentSiteCode = normalize(task.getCurrentSiteCode());
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(baseline, draft, "draft", "发布任务正在提交 Noon。");
        productWorkbenchRecordStore.put(workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent()), record);

        Map<String, Integer> requestCounts = Map.of();
        NoonSessionGateway.RequestCountScope requestCountScope = productNoonAdapter.openRequestCountScope();
        boolean writeSubmitted = false;
        try {
            ProductMasterSnapshotView preparedDraft = prepareSnapshotForPublish(draft, baseline, currentSiteCode);
            ProductPublishUnsupportedChanges unsupportedChanges = productPublishUnsupportedChangesDetector.detect(preparedDraft, baseline, currentSiteCode);
            ProductMasterSnapshotView publishableDraft = productPublishSupportedSnapshotBuilder.build(preparedDraft, baseline, unsupportedChanges);
            if (sameScopedSnapshot(publishableDraft, baseline, currentSiteCode)) {
                ProductWorkbenchRecord refreshed = buildTaskWorkbenchRecord(baseline, preparedDraft, "synced", "当前没有待发布改动。");
                appendRecentAction(refreshed, "publish-current", "synced", refreshed.getNote(), currentSiteCode, preparedDraft);
                productWorkbenchRecordStore.put(workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent()), refreshed);
                updatePublishTaskStatus(
                        task,
                        "synced",
                        null,
                        null,
                        buildTaskResultJson("synced", requestCountScope.snapshot(), List.of(refreshed.getNote())),
                        null,
                        LocalDateTime.now(),
                        1
                );
                return;
            }
            List<String> validationErrors = validatePublishSnapshot(publishableDraft, baseline, currentSiteCode);
            validationErrors.addAll(validatePublishOperationalKeys(publishableDraft, baseline, currentSiteCode));
            validationErrors.addAll(productPublishUnsupportedChangesDetector.validateWriteCoverage(unsupportedChanges));
            if (!validationErrors.isEmpty()) {
                failPublishTask(task, "validation_failed", validationErrors.get(0), requestCountScope.snapshot());
                return;
            }

            List<String> actionWarnings = new ArrayList<>();
            ProductMasterSnapshotView liveBeforePublish = fetchSnapshot(
                    command,
                    "publish-task.before",
                    shouldSkipSiteOfferLiveReadForSharedOnlyPublish(publishableDraft, baseline, currentSiteCode) ? baseline : null
            );
            List<Map<String, Object>> publishConflictFields = detectPublishConflictFields(
                    baseline,
                    publishableDraft,
                    liveBeforePublish,
                    currentSiteCode
            );
            if (!publishConflictFields.isEmpty()) {
                actionWarnings.add("发布前校验：Noon 当前内容与本地草稿存在同字段差异，本次按本地草稿覆盖 Noon 对应字段。");
            }

            StoreSyncStoreRecord store = requirePublishStore(task.getOwnerUserId(), task.getStoreCode());
            ensurePublishStoreAllowed(store);
            try {
                productPublishWriteService.publishSupportedChanges(
                        command,
                        store,
                        publishableDraft,
                        baseline,
                        liveBeforePublish,
                        currentSiteCode,
                        unsupportedChanges,
                        actionWarnings
                );
                writeSubmitted = true;
                updatePublishTaskStatus(
                        task,
                        "submitted",
                        null,
                        null,
                        buildTaskResultJson("submitted", requestCountScope.snapshot(), actionWarnings),
                        null,
                        null,
                        null
                );
            } catch (IllegalStateException exception) {
                if (exception instanceof ProductGroupPartialPublishException) {
                    String partialMessage = "Group 写回可能已部分提交到 Noon，请先同步官方结果后确认。";
                    List<String> partialWarnings = mergeWarnings(actionWarnings, List.of(shrink(exception.getMessage())));
                    updatePublishTaskStatus(
                            task,
                            "pending_manual_check",
                            "group_partial_write_unknown",
                            partialMessage,
                            buildTaskResultJson("pending_manual_check", requestCountScope.snapshot(), partialWarnings),
                            null,
                            LocalDateTime.now(),
                            null
                    );
                    record.setSyncStatus("failed");
                    record.setNote(partialMessage);
                    record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
                    appendRecentAction(record, "publish-current", "pending_manual_check", partialMessage, currentSiteCode, draft);
                    productWorkbenchRecordStore.put(workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent()), record);
                    return;
                }
                if (exception instanceof ProductGroupValidationException) {
                    failPublishTask(
                            task,
                            "group_validation_failed",
                            shrink(exception.getMessage()),
                            requestCountScope.snapshot()
                    );
                    return;
                }
                if (isTimeoutException(exception)) {
                    updatePublishTaskStatus(
                            task,
                            "write_unknown",
                            "noon_write_timeout",
                            "Noon 写入请求超时，系统将只回读校验，不会自动重复写入。",
                            buildTaskResultJson("write_unknown", requestCountScope.snapshot(), actionWarnings),
                            nextPublishVerifyRunAt(task),
                            null,
                            null
                    );
                    return;
                }
                failPublishTask(
                        task,
                        "noon_write_failed",
                        "Noon 发布返回错误，诺诺草稿已保留：" + shrink(exception.getMessage()),
                        requestCountScope.snapshot()
                );
                return;
            }

            ProductMasterSnapshotView liveAfterPublish = fetchSnapshot(command, "publish-task.after");
                    requestCounts = requestCountScope.snapshot();
                    completePublishTaskAfterVerification(
                            task,
                            baseline,
                            preparedDraft,
                            publishableDraft,
                            liveAfterPublish,
                            unsupportedChanges,
                    requestCounts,
                    actionWarnings,
                    1
            );
        } catch (IllegalStateException exception) {
            requestCounts = requestCountScope.snapshot();
            if (writeSubmitted && isTimeoutException(exception)) {
                updatePublishTaskStatus(
                        task,
                        "verify_timeout",
                        "noon_verify_timeout",
                        "Noon 回读校验超时，系统将稍后继续核对官方结果。",
                        buildTaskResultJson("verify_timeout", requestCounts, List.of(shrink(exception.getMessage()))),
                        nextPublishVerifyRunAt(task),
                        null,
                        task.getVerifyAttemptCount() == null ? 1 : task.getVerifyAttemptCount() + 1
                );
                return;
            }
            failPublishTask(
                    task,
                    isTimeoutException(exception) ? "pre_read_timeout" : "publish_task_failed",
                    "发布任务执行失败，诺诺草稿已保留：" + shrink(exception.getMessage()),
                    requestCounts
            );
        } finally {
            requestCountScope.close();
        }
    }

    private void executePublishTaskVerificationOnly(ProductPublishTaskRecord task, String previousStatus) {
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductMasterActionCommand command = buildTaskActionCommand(task, draft);
        NoonSessionGateway.RequestCountScope requestCountScope = productNoonAdapter.openRequestCountScope();
        int verifyAttempt = task.getVerifyAttemptCount() == null ? 1 : task.getVerifyAttemptCount() + 1;
        try {
            ProductMasterSnapshotView liveAfterPublish = fetchSnapshot(command, "publish-task.verify");
            ProductMasterSnapshotView preparedDraft = prepareSnapshotForPublish(draft, baseline, task.getCurrentSiteCode());
            ProductPublishUnsupportedChanges unsupportedChanges = productPublishUnsupportedChangesDetector.detect(preparedDraft, baseline, task.getCurrentSiteCode());
            ProductMasterSnapshotView publishableDraft = productPublishSupportedSnapshotBuilder.build(preparedDraft, baseline, unsupportedChanges);
            completePublishTaskAfterVerification(
                    task,
                    baseline,
                    preparedDraft,
                    publishableDraft,
                    liveAfterPublish,
                    unsupportedChanges,
                    requestCountScope.snapshot(),
                    List.of("已从状态 " + previousStatus + " 继续回读校验。"),
                    verifyAttempt
            );
        } catch (IllegalStateException exception) {
            if (isTimeoutException(exception)) {
                updatePublishTaskStatus(
                        task,
                        "verify_timeout",
                        "noon_verify_timeout",
                        "Noon 回读校验超时，系统将稍后继续核对官方结果。",
                        buildTaskResultJson("verify_timeout", requestCountScope.snapshot(), List.of(shrink(exception.getMessage()))),
                        nextPublishVerifyRunAt(task),
                        null,
                        verifyAttempt
                );
                return;
            }
            failPublishTask(
                    task,
                    "verify_failed",
                    "回读 Noon 当前状态失败，诺诺草稿已保留：" + shrink(exception.getMessage()),
                    requestCountScope.snapshot()
            );
        } finally {
            requestCountScope.close();
        }
    }

    private void completePublishTaskAfterVerification(
            ProductPublishTaskRecord task,
            ProductMasterSnapshotView baselineBeforePublish,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView publishableDraft,
            ProductMasterSnapshotView liveAfterPublish,
            ProductPublishUnsupportedChanges unsupportedChanges,
            Map<String, Integer> requestCounts,
            List<String> actionWarnings,
            int verifyAttempt
    ) {
        String currentSiteCode = normalize(task.getCurrentSiteCode());
        if (!publishChangedFieldsMatch(baselineBeforePublish, publishableDraft, liveAfterPublish, currentSiteCode)) {
            if (verifyAttempt >= 3) {
                updatePublishTaskStatus(
                        task,
                        "pending_manual_check",
                        "noon_effect_not_confirmed",
                        "多轮回读仍未确认 Noon 已生效，请人工核对官方后台后再处理。",
                        buildTaskResultJson("pending_manual_check", requestCounts, actionWarnings),
                        null,
                        null,
                        verifyAttempt
                );
                return;
            }
            updatePublishTaskStatus(
                    task,
                    "pending_effective",
                    "noon_effect_pending",
                    "Noon 可能延迟生效，本轮未读到目标值，系统将稍后继续回读校验。",
                    buildTaskResultJson("pending_effective", requestCounts, actionWarnings),
                    nextPublishVerifyRunAt(task),
                    null,
                    verifyAttempt
            );
            return;
        }

        ProductWorkbenchRecord refreshed = createSyncedRecord(liveAfterPublish);
        refreshed.setDraftSnapshot(copySnapshot(liveAfterPublish));
        productPublishSupportedSnapshotBuilder.overlayUnsupportedDraft(refreshed.getDraftSnapshot(), draft, unsupportedChanges);
        if (sameBusinessSnapshot(refreshed.getDraftSnapshot(), refreshed.getBaselineSnapshot())) {
            refreshed.setSyncStatus("synced");
            refreshed.setNote("共享主档和当前站点经营面已同步完成。");
        } else {
            refreshed.setSyncStatus("draft");
            refreshed.setNote("已发布当前修改，其他未发布内容仍留在诺诺草稿中。");
        }
        appendRecentAction(refreshed, "publish-current", refreshed.getSyncStatus(), refreshed.getNote(), currentSiteCode, refreshed.getDraftSnapshot());
        ProductMasterWorkbenchView view = finalizeWorkbenchView(
                task.getOwnerUserId(),
                "publish-current",
                currentSiteCode,
                refreshed,
                "发布任务已完成。",
                mergeWarnings(liveAfterPublish.getWarnings(), actionWarnings)
        );
        productProjectionPersistenceService.persistPublishedKeyContentHistory(
                task.getOwnerUserId(),
                baselineBeforePublish,
                draft,
                currentSiteCode,
                view.getWarnings()
        );
        hydratePendingKeyContentHistoryState(
                task.getOwnerUserId(),
                refreshed.getBaselineSnapshot(),
                refreshed,
                view
        );
        updatePublishTaskStatus(
                task,
                "synced",
                null,
                null,
                buildTaskResultJson("synced", requestCounts, actionWarnings),
                null,
                LocalDateTime.now(),
                verifyAttempt
        );
        refreshed.setPublishTask(productPublishTaskViewBuilder.build(task, false));
        productWorkbenchRecordStore.put(workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent()), refreshed);
    }

    private void failPublishTask(
            ProductPublishTaskRecord task,
            String errorCode,
            String errorMessage,
            Map<String, Integer> requestCounts
    ) {
        updatePublishTaskStatus(
                task,
                "failed",
                errorCode,
                errorMessage,
                buildTaskResultJson("failed", requestCounts, List.of(errorMessage)),
                null,
                LocalDateTime.now(),
                null
        );
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(baseline, draft, "failed", errorMessage);
        appendRecentAction(record, "publish-current", "failed", errorMessage, task.getCurrentSiteCode(), draft);
        record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
        productWorkbenchRecordStore.put(workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent()), record);
        finalizeWorkbenchView(
                task.getOwnerUserId(),
                "publish-current",
                task.getCurrentSiteCode(),
                record,
                errorMessage,
                mergeWarnings(draft.getWarnings(), List.of(errorMessage))
        );
    }

    private ProductWorkbenchRecord reconcileWorkbenchRecord(
            ProductWorkbenchRecord existing,
            ProductMasterSnapshotView liveSnapshot
    ) {
        if (existing == null) {
            return createSyncedRecord(liveSnapshot);
        }

        if (sameBusinessSnapshot(existing.getBaselineSnapshot(), liveSnapshot)) {
            return existing;
        }

        if (sameBusinessSnapshot(existing.getDraftSnapshot(), existing.getBaselineSnapshot())) {
            ProductWorkbenchRecord refreshed = createSyncedRecord(liveSnapshot);
            refreshed.setNote("Noon 最新快照已自动刷新。");
            appendRecentAction(refreshed, "pull", refreshed.getSyncStatus(), refreshed.getNote(), null, liveSnapshot);
            return refreshed;
        }

        existing.setSyncStatus("draft");
        existing.setNote("当前保留本地未发布草稿；如需使用 Noon 当前版本，请点击“从 Noon 同步”覆盖本地草稿。");
        return existing;
    }

    private ProductWorkbenchRecord createSyncedRecord(ProductMasterSnapshotView snapshot) {
        return productWorkbenchRecordRestorer.createSyncedRecord(snapshot);
    }

    private ProductWorkbenchRecord ensureWorkbenchRecord(ProductMasterFetchCommand command, String key) {
        ProductWorkbenchRecord existing = productWorkbenchRecordStore.get(key);
        if (existing != null) {
            return existing;
        }
        ProductMasterSnapshotView liveSnapshot = fetchSnapshot(command, "ensure-workbench-record");
        ProductWorkbenchRecord record = createSyncedRecord(liveSnapshot);
        productWorkbenchRecordStore.put(key, record);
        return record;
    }

    private boolean isPublicDetailReadonlyRecord(ProductWorkbenchRecord record) {
        return record != null && (
                productPublicDetailReadonlyWorkbenchFactory.isReadonlySnapshot(record.getBaselineSnapshot())
                        || productPublicDetailReadonlyWorkbenchFactory.isReadonlySnapshot(record.getDraftSnapshot())
        );
    }

    private ProductMasterWorkbenchView rejectPublicDetailReadonlyAction(
            ProductMasterActionCommand command,
            String key,
            ProductWorkbenchRecord record,
            String actionLabel
    ) {
        String message = "当前只是 Noon 前台公开详情只读视图，" + actionLabel + "已禁用；请先从 Noon 同步拿到 catalog 详情基线。";
        record.setSyncStatus("failed");
        record.setNote(message);
        productWorkbenchRecordStore.put(key, record);
        List<String> warnings = record.getDraftSnapshot() == null
                ? List.of(message)
                : mergeWarnings(record.getDraftSnapshot().getWarnings(), List.of(message));
        return productWorkbenchViewAssembler.buildWorkbenchView(record, message, warnings);
    }

    private void logFetchSnapshotTimings(String traceLabel, List<String> timingEntries, long fetchStartedAt) {
        log.info(
                "product-management fetchSnapshot trace={} totalMs={} stages={}",
                traceLabel,
                nanosToMillis(fetchStartedAt),
                String.join(", ", timingEntries)
        );
    }

    private void recordFetchStage(
            List<String> timingEntries,
            Map<String, Long> timingBreakdownMs,
            String traceLabel,
            String reason,
            String stageName,
            long startedAt
    ) {
        long durationMs = nanosToMillis(startedAt);
        timingEntries.add(stageName + "=" + durationMs + "ms");
        timingBreakdownMs.put(stageName, durationMs);
        if (isPublishCurrentBeforeReason(reason)) {
            log.info(
                    "product-management publish-current.before stage trace={} stage={} durationMs={}",
                    traceLabel,
                    stageName,
                    durationMs
            );
        }
    }

    private void logFocusedFetchBreakdown(
            String traceLabel,
            String reason,
            Map<String, Long> timingBreakdownMs,
            boolean reusedBaselineSiteOffers,
            int projectSiteCount
    ) {
        if (!isPublishCurrentBeforeReason(reason) || timingBreakdownMs.isEmpty()) {
            return;
        }
        String slowestStage = null;
        long slowestDurationMs = Long.MIN_VALUE;
        for (Map.Entry<String, Long> entry : timingBreakdownMs.entrySet()) {
            if (entry.getValue() > slowestDurationMs) {
                slowestStage = entry.getKey();
                slowestDurationMs = entry.getValue();
            }
        }
        log.info(
                "product-management publish-current.before summary trace={} slowestStage={} slowestDurationMs={} reusedBaselineSiteOffers={} projectSiteCount={} stages={}",
                traceLabel,
                slowestStage,
                slowestDurationMs,
                reusedBaselineSiteOffers,
                projectSiteCount,
                timingBreakdownMs
        );
    }

    private String timingEntry(String stageName, long startedAt) {
        return stageName + "=" + nanosToMillis(startedAt) + "ms";
    }

    private boolean isPublishCurrentBeforeReason(String reason) {
        return "publish-current.before".equalsIgnoreCase(reason);
    }

    private long nanosToMillis(long startedAt) {
        return Math.round((System.nanoTime() - startedAt) / 1_000_000.0d);
    }

    private String buildFetchTraceLabel(ProductMasterFetchCommand command, String reason) {
        if (command == null) {
            return reason;
        }
        return reason
                + "|owner=" + command.getOwnerUserId()
                + "|store=" + normalize(command.getStoreCode())
                + "|skuParent=" + normalize(command.getSkuParent());
    }

    private ProductMasterWorkbenchView buildWorkbenchView(
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings
    ) {
        return productWorkbenchViewAssembler.buildWorkbenchView(record, message, warnings);
    }

    private ProductMasterWorkbenchView buildTerminalPublishTaskWorkbenchView(ProductPublishTaskRecord task) {
        ProductWorkbenchRecord existingRecord = productWorkbenchRecordStore.get(
                workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent())
        );
        ProductPublishTaskView existingTask = existingRecord != null ? existingRecord.getPublishTask() : null;
        if (
                existingRecord != null
                        && existingTask != null
                        && Objects.equals(existingTask.getTaskId(), task.getId())
        ) {
            return finalizeWorkbenchView(
                    task.getOwnerUserId(),
                    "publish-current",
                    task.getCurrentSiteCode(),
                    existingRecord,
                    publishTaskMessage(task),
                    existingRecord.getDraftSnapshot() != null
                            ? existingRecord.getDraftSnapshot().getWarnings()
                            : new ArrayList<>()
            );
        }

        if ("synced".equalsIgnoreCase(normalize(task.getStatus()))) {
            return productWorkbenchOpenService.openFromLocalBaseline(buildFetchCommand(task), openWorkbenchSupport());
        }

        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        String syncStatus = terminalPublishTaskWorkbenchStatus(task, baseline, draft);
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(baseline, draft, syncStatus, publishTaskMessage(task));
        appendRecentAction(record, "publish-current", task.getStatus(), publishTaskMessage(task), task.getCurrentSiteCode(), draft);
        return finalizeWorkbenchView(
                task.getOwnerUserId(),
                "publish-current",
                task.getCurrentSiteCode(),
                record,
                publishTaskMessage(task),
                draft != null ? draft.getWarnings() : new ArrayList<>()
        );
    }

    private String terminalPublishTaskWorkbenchStatus(
            ProductPublishTaskRecord task,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft
    ) {
        String status = normalize(task != null ? task.getStatus() : null);
        if ("failed".equalsIgnoreCase(status) || "pending_manual_check".equalsIgnoreCase(status)) {
            return "failed";
        }
        if ("cancelled".equalsIgnoreCase(status)) {
            return sameBusinessSnapshot(draft, baseline) ? "synced" : "draft";
        }
        return "draft";
    }

    private String publishTaskMessage(ProductPublishTaskRecord task) {
        return requirePublishCommandService().message(task, productPublishTaskChangedDomainsResolver);
    }

    private boolean isTerminalPublishTaskStatus(String status) {
        return requirePublishCommandService().isTerminalStatus(status);
    }

    private boolean isActivePublishTaskStatus(String status) {
        return requirePublishCommandService().isActiveStatus(status);
    }

    private boolean isPublishTaskWorkerMode() {
        return Boolean.TRUE.equals(PUBLISH_TASK_WORKER_MODE.get());
    }

    private void ensureNoActivePublishTaskForCommand(ProductMasterFetchCommand command, String message) {
        if (isPublishTaskWorkerMode()) {
            return;
        }
        recoverStaleRunningProductPublishTasks();
        Long productMasterId = resolveProductMasterId(command);
        if (productMasterId == null) {
            return;
        }
        requirePublishCommandService().ensureNoActiveTask(productMasterId, message);
    }

    private Long resolveProductMasterId(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            return null;
        }
        String storeCode = normalize(command.getStoreCode());
        String skuParent = normalize(command.getSkuParent());
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }
        return productManagementMapper.selectProductMasterIdByStoreCode(command.getOwnerUserId(), storeCode, skuParent);
    }

    private ProductMasterFetchCommand buildFetchCommand(ProductPublishTaskRecord task) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        if (task == null) {
            return command;
        }
        command.setOwnerUserId(task.getOwnerUserId());
        command.setStoreCode(task.getStoreCode());
        command.setSkuParent(task.getSkuParent());
        command.setPartnerSku(task.getPartnerSku());
        command.setPskuCode(task.getPskuCode());
        return command;
    }

    private ProductMasterActionCommand buildTaskActionCommand(
            ProductPublishTaskRecord task,
            ProductMasterSnapshotView draft
    ) {
        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(task.getOwnerUserId());
        command.setStoreCode(task.getStoreCode());
        command.setSkuParent(firstNonBlank(task.getSkuParent(), textValue(draft.getIdentity().get("skuParent"))));
        command.setPartnerSku(firstNonBlank(task.getPartnerSku(), textValue(draft.getIdentity().get("partnerSku"))));
        command.setPskuCode(firstNonBlank(task.getPskuCode(), textValue(draft.getIdentity().get("pskuCode"))));
        command.setCurrentSiteCode(task.getCurrentSiteCode());
        command.setAction("publish-current");
        command.setSnapshot(draft);
        return command;
    }

    private ProductWorkbenchRecord buildTaskWorkbenchRecord(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft,
            String syncStatus,
            String note
    ) {
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(copySnapshot(baseline));
        record.setDraftSnapshot(copySnapshot(draft));
        record.setSyncStatus(syncStatus);
        record.setLastSyncedAt(extractFetchedAt(baseline));
        record.setNote(note);
        return record;
    }

    private Map<String, Object> buildPublishTaskRequestPayload(
            ProductMasterActionCommand command,
            String currentSiteCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ownerUserId", command.getOwnerUserId());
        payload.put("storeCode", normalize(command.getStoreCode()));
        payload.put("skuParent", normalize(command.getSkuParent()));
        payload.put("partnerSku", normalize(command.getPartnerSku()));
        payload.put("pskuCode", normalize(command.getPskuCode()));
        payload.put("currentSiteCode", normalize(currentSiteCode));
        payload.put("action", "publish-current");
        return payload;
    }

    private List<String> recomputePublishTaskChangedDomains(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        ProductPublishUnsupportedChanges unsupportedChanges = productPublishUnsupportedChangesDetector.detect(draft, baseline, currentSiteCode);
        return productPublishChangedDomainComparator.resolve(
                draft,
                baseline,
                currentSiteCode,
                unsupportedChanges != null && unsupportedChanges.isGroupChanged(),
                unsupportedChanges != null && unsupportedChanges.isVariantStructureChanged()
        );
    }

    private boolean publishChangedFieldsMatch(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView noonCurrent,
            String siteCode
    ) {
        return productPublishPreparationService.publishChangedFieldsMatch(baseline, draft, noonCurrent, siteCode);
    }

    private LocalDateTime nextPublishVerifyRunAt(ProductPublishTaskRecord task) {
        return requirePublishCommandService().nextVerifyRunAt(task);
    }

    private void updatePublishTaskStatus(
            ProductPublishTaskRecord task,
            String status,
            String errorCode,
            String errorMessage,
            String resultJson,
            LocalDateTime nextRunAt,
            LocalDateTime finishedAt,
            Integer verifyAttemptCount
    ) {
        requirePublishCommandService().updateStatus(
                task,
                status,
                errorCode,
                errorMessage,
                resultJson,
                nextRunAt,
                finishedAt,
                verifyAttemptCount
        );
    }

    private String buildTaskResultJson(
            String status,
            Map<String, Integer> requestCounts,
            List<String> warnings
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("requestCounts", requestCounts == null ? Map.of() : requestCounts);
        payload.put("warnings", warnings == null ? List.of() : warnings);
        payload.put("recordedAt", FETCH_TIME_FORMATTER.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
        return writeJson(payload);
    }

    private ProductMasterSnapshotView readTaskSnapshot(String json) {
        try {
            return objectMapper.readValue(json, ProductMasterSnapshotView.class);
        } catch (Exception exception) {
            throw new IllegalStateException("发布任务快照读取失败：" + exception.getMessage(), exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("商品发布任务 JSON 序列化失败：" + exception.getMessage(), exception);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", exception);
        }
    }

    private boolean isTimeoutException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private ProductMasterSnapshotView sanitizeSnapshot(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView fallback
    ) {
        return productSnapshotHydrator.sanitizeSnapshot(snapshot, fallback);
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return productSnapshotHydrator.copySnapshot(source);
    }

    private List<Map<String, Object>> copyRecordList(List<Map<String, Object>> source) {
        return productSnapshotHydrator.copyRecordList(source);
    }

    private String workbenchKey(Long ownerUserId, String storeCode, String skuParent) {
        return ownerUserId + "::" + normalize(storeCode) + "::" + normalize(skuParent);
    }

    private String resolveAction(String action) {
        String normalized = normalize(action);
        if ("save".equalsIgnoreCase(normalized)) {
            return "SAVE_DRAFT";
        }
        if ("pull".equalsIgnoreCase(normalized)) {
            return "PULL_FROM_NOON";
        }
        if ("rollback-draft".equalsIgnoreCase(normalized) || "restore".equalsIgnoreCase(normalized)) {
            return "ROLLBACK_DRAFT";
        }
        if ("publish-current".equalsIgnoreCase(normalized)) {
            return "PUBLISH_CURRENT_SITE";
        }
        if ("publish-all".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("该操作暂未开放，请只发布当前站点。");
        }
        throw new IllegalArgumentException("当前动作暂不支持：" + action);
    }

    private boolean hasDraftChanges(ProductWorkbenchRecord record) {
        return record != null
                && record.getDraftSnapshot() != null
                && record.getBaselineSnapshot() != null
                && !sameBusinessSnapshot(record.getDraftSnapshot(), record.getBaselineSnapshot());
    }

    private ProductOperationalKeyHydrator.OperationalKeys resolveOperationalKeysFromProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        return productOperationalKeyHydrator.resolveOperationalKeysFromProjection(
                ownerUserId,
                storeCode,
                skuParent,
                partnerSku,
                pskuCode
        );
    }

    private void hydrateSnapshotOperationalKeys(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot
    ) {
        productOperationalKeyHydrator.hydrateSnapshotOperationalKeys(
                ownerUserId,
                storeCode,
                skuParent,
                snapshot
        );
    }

    private void clearResolvedOperationalWarnings(ProductMasterSnapshotView snapshot) {
        productSnapshotHydrator.clearResolvedOperationalWarnings(snapshot);
    }

    private void hydrateWorkbenchAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductWorkbenchRecord record,
            List<String> warnings
    ) {
        productAttributeDictionaryHydrator.hydrateWorkbenchAttributeDictionary(ownerUserId, storeCode, record, warnings);
    }

    private void hydrateSnapshotAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        productAttributeDictionaryHydrator.hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, snapshot, warnings);
    }

    private boolean isKeepDraftSyncPolicy(String syncMergePolicy) {
        String normalized = normalize(syncMergePolicy);
        return "keep_draft".equalsIgnoreCase(normalized)
                || "keep-local".equalsIgnoreCase(normalized)
                || "keep_local".equalsIgnoreCase(normalized);
    }

    private boolean isUseLocalPublishConflictResolution(String publishConflictResolution) {
        String normalized = normalize(publishConflictResolution);
        return "use_local".equalsIgnoreCase(normalized)
                || "local".equalsIgnoreCase(normalized)
                || "overwrite_noon".equalsIgnoreCase(normalized);
    }

    private String extractFetchedAt(ProductMasterSnapshotView snapshot) {
        Object fetchedAt = snapshot != null ? snapshot.getStoreContext().get("fetchedAt") : null;
        return fetchedAt == null ? null : String.valueOf(fetchedAt);
    }

    private ProductMasterWorkbenchView finalizeWorkbenchView(
            Long ownerUserId,
            String actionType,
            String targetSiteCode,
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings
    ) {
        return finalizeWorkbenchView(
                ownerUserId,
                actionType,
                targetSiteCode,
                record,
                message,
                warnings,
                null,
                null
        );
    }

    private ProductMasterWorkbenchView finalizeWorkbenchView(
            Long ownerUserId,
            String actionType,
            String targetSiteCode,
            ProductWorkbenchRecord record,
            String message,
            List<String> warnings,
            ProductMasterSnapshotView actionBaselineSnapshot,
            ProductMasterSnapshotView actionDraftSnapshot
    ) {
        return productWorkbenchViewFinalizer.finalizeView(
                ownerUserId,
                actionType,
                targetSiteCode,
                record,
                message,
                warnings,
                actionBaselineSnapshot,
                actionDraftSnapshot
        );
    }

    private int recoverStaleRunningProductPublishTasks() {
        return requirePublishCommandService().recoverStaleRunningTasks();
    }

    private void hydratePendingKeyContentHistoryState(
            Long ownerUserId,
            ProductMasterSnapshotView liveSnapshot,
            ProductWorkbenchRecord record,
            ProductMasterWorkbenchView view
    ) {
        if (ownerUserId == null || liveSnapshot == null || record == null || view == null) {
            return;
        }
        ProductProjectionPersistenceService.PersistedWorkbenchState persistedState =
                productProjectionPersistenceService.loadPersistedWorkbenchState(
                        ownerUserId,
                        liveSnapshot,
                        view.getWarnings()
                );
        if (persistedState == null) {
            return;
        }
        record.setPendingKeyContentHistoryCount(persistedState.getPendingKeyContentHistoryCount());
        record.setPendingKeyContentHistoryVisibleAfter(persistedState.getPendingKeyContentHistoryVisibleAfter());
        view.setPendingKeyContentHistoryCount(persistedState.getPendingKeyContentHistoryCount());
        view.setPendingKeyContentHistoryVisibleAfter(persistedState.getPendingKeyContentHistoryVisibleAfter());
    }

    private ProductWorkbenchRecord restorePersistedWorkbenchRecord(
            ProductMasterSnapshotView liveSnapshot,
            ProductProjectionPersistenceService.PersistedWorkbenchState persistedState
    ) {
        return productWorkbenchRecordRestorer.restorePersistedWorkbenchRecord(liveSnapshot, persistedState);
    }

    private void hydrateProjectionOnlyFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        productSnapshotHydrator.hydrateProjectionOnlyFields(target, source);
    }

    private ProductMasterSnapshotView prepareSnapshotForPublish(
            ProductMasterSnapshotView requestedSnapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        return productPublishPreparationService.prepareSnapshotForPublish(requestedSnapshot, baseline, currentSiteCode);
    }

    private boolean shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        return productPublishPreparationService.shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
                draft,
                baseline,
                currentSiteCode
        );
    }

    private List<String> siteCodesFromSnapshot(ProductMasterSnapshotView snapshot) {
        LinkedHashSet<String> siteCodes = new LinkedHashSet<>();
        if (snapshot == null) {
            return new ArrayList<>();
        }
        String currentStoreCode = textValue(snapshot.getStoreContext().get("storeCode"));
        if (StringUtils.hasText(currentStoreCode)) {
            siteCodes.add(currentStoreCode);
        }
        if (snapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
                String siteCode = textValue(siteOffer.get("storeCode"));
                if (StringUtils.hasText(siteCode)) {
                    siteCodes.add(siteCode);
                }
            }
        }
        return new ArrayList<>(siteCodes);
    }

    private boolean sameBusinessSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right) {
        return productPublishPreparationService.sameBusinessSnapshot(left, right);
    }

    private boolean sameScopedSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right, String siteCode) {
        return productPublishPreparationService.sameScopedSnapshot(left, right, siteCode);
    }

    private List<Map<String, Object>> detectPublishConflictFields(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView localDraft,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        return productPublishPreparationService.detectPublishConflictFields(
                baseline,
                localDraft,
                noonCurrent,
                currentSiteCode
        );
    }

    private Map<String, Object> buildPublishConflictPayload(
            List<Map<String, Object>> fields,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        return productPublishPreparationService.buildPublishConflictPayload(fields, noonCurrent, currentSiteCode);
    }

    private List<String> validatePublishSnapshot(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        return productPublishPreparationService.validatePublishSnapshot(snapshot, baseline, currentSiteCode);
    }

    private List<String> validatePublishOperationalKeys(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        return productPublishPreparationService.validatePublishOperationalKeys(draft, baseline, currentSiteCode);
    }

    private StoreSyncStoreRecord requirePublishStore(Long ownerUserId, String storeCode) {
        return resolveProductOwnerStore(ownerUserId, storeCode, "当前店铺不在选中的老板名下。");
    }

    private StoreSyncStoreRecord resolveProductOwnerStore(Long ownerUserId, String storeCode, String errorMessage) {
        String normalizedStoreCode = normalize(storeCode);
        StoreSyncStoreRecord store = storeSyncMapper.selectOwnerStore(ownerUserId, normalizedStoreCode);
        if (store == null) {
            store = storeSyncMapper.selectOwnerProjectionStore(ownerUserId, normalizedStoreCode);
        }
        if (store == null || !StringUtils.hasText(store.getStoreCode())) {
            throw new IllegalArgumentException(errorMessage);
        }
        return store;
    }

    private void ensurePublishStoreAllowed(StoreSyncStoreRecord store) {
        String projectName = normalize(store.getProjectName());
        String storeCode = normalize(store.getStoreCode());
        if ("xingyao".equalsIgnoreCase(projectName) || "STR245027-NAE".equalsIgnoreCase(storeCode)) {
            return;
        }
        throw new IllegalArgumentException("当前只开放 xingyao 测试店铺的受控发布。");
    }

    private List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings) {
        return productSnapshotHydrator.mergeWarnings(baseWarnings, extraWarnings);
    }

    private List<String> userVisibleWarnings(List<String> warnings) {
        return productSnapshotHydrator.userVisibleWarnings(warnings);
    }

    private String siteOfferCode(Map<String, Object> siteOffer) {
        return textValue(siteOffer.get("storeCode"));
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        } else if (StringUtils.hasText(textValue(value))) {
            values.add(textValue(value));
        }
        return values;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private List<ProductProjectSiteContext> loadProjectSiteContexts(
            NoonSession session,
            Long ownerUserId,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        return productProjectSiteResolver.loadProjectSiteContexts(session, ownerUserId, store, warnings);
    }

    private ProductSiteOfferFetchResult loadSiteOffers(
            NoonSession session,
            List<ProductProjectSiteContext> projectSites,
            String referenceStoreCode,
            String idPartner,
            String partnerSku,
            String pskuCode,
            List<String> warnings
    ) {
        return productSiteOfferFetcher.loadSiteOffers(
                session,
                projectSites,
                referenceStoreCode,
                idPartner,
                partnerSku,
                pskuCode,
                warnings
        );
    }

    private ProductSiteOfferFetchResult reuseSiteOffersFromSnapshot(
            ProductMasterSnapshotView snapshot,
            List<ProductProjectSiteContext> projectSites,
            String referenceStoreCode
    ) {
        return productSiteOfferFetcher.reuseSiteOffersFromSnapshot(snapshot, projectSites, referenceStoreCode);
    }

    private String resolveReferenceSite(List<ProductProjectSiteContext> projectSites, String referenceStoreCode) {
        return productProjectSiteResolver.resolveReferenceSite(projectSites, referenceStoreCode);
    }

    private String resolveProjectCode(
            NoonSession session,
            String localProjectCode,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        return productProjectSiteResolver.resolveProjectCode(session, localProjectCode, store, warnings);
    }

    private Map<String, Object> buildIdentity(
            JsonNode productNode,
            JsonNode commonNode,
            JsonNode pricingRoot,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        return productSnapshotSectionBuilder.buildIdentity(
                productNode,
                commonNode,
                pricingRoot,
                skuParent,
                partnerSku,
                pskuCode
        );
    }

    private Map<String, Object> buildTaxonomy(JsonNode commonNode) {
        return productSnapshotSectionBuilder.buildTaxonomy(commonNode);
    }

    private Map<String, Object> buildContent(JsonNode commonNode, JsonNode enNode, JsonNode arNode) {
        return productSnapshotSectionBuilder.buildContent(commonNode, enNode, arNode);
    }

    private void applyFollowSellCatalogContentIfNeeded(
            NoonSession session,
            Map<String, Object> identity,
            Map<String, Object> content,
            String referenceSite,
            String reason
    ) {
        productCatalogContentFallbackApplier.applyIfNeeded(session, identity, content, referenceSite, reason);
    }

    private Map<String, Object> buildPlatformSignals(JsonNode commonNode) {
        return productSnapshotSectionBuilder.buildPlatformSignals(commonNode);
    }

    private List<Map<String, Object>> buildKeyAttributes(
            JsonNode fulltypeTemplateRoot,
            JsonNode commonNode,
            JsonNode enNode,
            JsonNode arNode
    ) {
        return productKeyAttributeBuilder.buildKeyAttributes(fulltypeTemplateRoot, commonNode, enNode, arNode);
    }

    private Map<String, Object> buildGroup(
            JsonNode groupCurrentNode,
            JsonNode groupDetailRoot,
            JsonNode groupListNode,
            String skuGroup,
            JsonNode groupParentAttributesRoot
    ) {
        return productSnapshotSectionBuilder.buildGroup(
                groupCurrentNode,
                groupDetailRoot,
                groupListNode,
                skuGroup,
                groupParentAttributesRoot
        );
    }

    private List<Map<String, Object>> buildVariants(JsonNode variantInfoRoot, JsonNode productNode) {
        return productSnapshotSectionBuilder.buildVariants(variantInfoRoot, productNode);
    }

    private Map<String, Object> buildPricing(JsonNode pricingRoot) {
        return productSiteOfferFetcher.buildPricing(pricingRoot);
    }

    private Map<String, Object> buildStock(JsonNode stockRoot) {
        return productSiteOfferFetcher.buildStock(stockRoot);
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void putIfNotEmpty(Map<String, Object> target, String key, List<?> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }

    private void putMapIfNotEmpty(Map<String, Object> target, String key, Map<?, ?> values) {
        if (values != null && !values.isEmpty()) {
            target.put(key, values);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
        return StringUtils.hasText(value) ? value : null;
    }

    private String noonText(JsonNode node, String field) {
        String value = text(node, field);
        return "null".equalsIgnoreCase(value) ? null : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeAttributeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase().replaceAll("[\\s-]+", "_");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void appendRecentAction(
            ProductWorkbenchRecord record,
            String actionType,
            String resultStatus,
            String message,
            String targetSiteCode,
            ProductMasterSnapshotView snapshot
    ) {
        if (record == null) {
            return;
        }
        Map<String, Object> action = new LinkedHashMap<>();
        putIfNotBlank(action, "occurredAt", FETCH_TIME_FORMATTER.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
        putIfNotBlank(action, "actionType", actionType);
        putIfNotBlank(action, "resultStatus", resultStatus);
        putIfNotBlank(action, "message", message);
        putIfNotBlank(action, "targetSiteCode", normalize(targetSiteCode));
        if (snapshot != null) {
            putIfNotBlank(action, "skuParent", textValue(snapshot.getIdentity().get("skuParent")));
            putIfNotBlank(action, "partnerSku", textValue(snapshot.getIdentity().get("partnerSku")));
            putIfNotBlank(action, "pskuCode", textValue(snapshot.getIdentity().get("pskuCode")));
            putIfNotBlank(action, "offerCode", textValue(snapshot.getIdentity().get("offerCode")));
            putIfNotNull(action, "degraded", snapshot.isDegraded());
        }
        record.getRecentActions().add(0, action);
        if (record.getRecentActions().size() > 20) {
            record.getRecentActions().remove(record.getRecentActions().size() - 1);
        }
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "未返回更多错误信息";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

}
