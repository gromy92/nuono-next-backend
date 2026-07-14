package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductLiteMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.publish.ProductPublishCommandService;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateResult;
import com.nuono.next.product.publish.ProductPublishTaskFenceLostException;
import com.nuono.next.productlisting.ProductListingDraftCommand;
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
import java.util.Locale;
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
    private static final int PRODUCT_DELETE_VERIFY_OFFER_PAGE_SIZE = 100;

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
    private final ProductDeleteRequestBuilder productDeleteRequestBuilder;

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
        ObjectMapper effectiveObjectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.productManagementMapper = productManagementMapper;
        this.productLiteMapper = productLiteMapper;
        this.productPublicDetailMapper = productPublicDetailMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.objectMapper = effectiveObjectMapper;
        this.productDeleteRequestBuilder = new ProductDeleteRequestBuilder(effectiveObjectMapper);
        this.productSnapshotHydrator = new ProductSnapshotHydrator(effectiveObjectMapper);
        this.productSnapshotCoreFetcher = new ProductSnapshotCoreFetcher(effectiveObjectMapper, productNoonAdapter);
        this.productSnapshotSectionBuilder = new ProductSnapshotSectionBuilder(effectiveObjectMapper);
        this.productKeyAttributeBuilder = new ProductKeyAttributeBuilder(effectiveObjectMapper);
        this.productStoreContextBuilder = new ProductStoreContextBuilder();
        this.productSnapshotGroupFetcher = new ProductSnapshotGroupFetcher(
                effectiveObjectMapper,
                productNoonAdapter,
                this.productSnapshotSectionBuilder
        );
        this.productOperationalKeyHydrator = new ProductOperationalKeyHydrator(productProjectionPersistenceService);
        this.productNoonAdapter = productNoonAdapter;
        this.productProjectSiteResolver = new ProductProjectSiteResolver(effectiveObjectMapper, storeSyncMapper, productNoonAdapter);
        this.productSiteOfferFetcher = new ProductSiteOfferFetcher(
                effectiveObjectMapper,
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
                ? new ProductWorkbenchDirtySiteResolver(effectiveObjectMapper)
                : productWorkbenchDirtySiteResolver;
        this.productWorkbenchStatusHydrator = productWorkbenchStatusHydrator == null
                ? new ProductWorkbenchStatusHydrator(productProjectionPersistenceService)
                : productWorkbenchStatusHydrator;
        this.productPublishChangedDomainComparator = new ProductPublishChangedDomainComparator(effectiveObjectMapper);
        this.productPublishUnsupportedChangesDetector = new ProductPublishUnsupportedChangesDetector(effectiveObjectMapper);
        this.productPublishSupportedSnapshotBuilder = new ProductPublishSupportedSnapshotBuilder(
                effectiveObjectMapper,
                new ProductPublishPlanner(productDraftMergePolicy)
        );
        this.productPublishOfferWriter = new ProductPublishOfferWriter(effectiveObjectMapper, productNoonAdapter);
        this.productPublishPreparationService = new ProductPublishPreparationService(
                effectiveObjectMapper,
                productDraftMergePolicy,
                productPublishOfferWriter
        );
        this.productWorkbenchRecordRestorer = new ProductWorkbenchRecordRestorer(
                productSnapshotHydrator,
                productPublishPreparationService
        );
        this.productPublishSharedZskuWriter = new ProductPublishSharedZskuWriter(
                effectiveObjectMapper,
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
                            Map<String, Object> baselineOffer,
                            List<String> actionWarnings
                    ) {
                        productPublishOfferWriter.publishOffer(session, pskuCode, siteOffer, baselineOffer, actionWarnings);
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
        return fetchSnapshot(command, reason, siteOfferReuseSeed, true);
    }

    private ProductMasterSnapshotView fetchSnapshotWithoutProjection(
            ProductMasterFetchCommand command,
            String reason
    ) {
        return fetchSnapshot(command, reason, null, false);
    }

    private ProductMasterSnapshotView fetchSnapshot(
            ProductMasterFetchCommand command,
            String reason,
            ProductMasterSnapshotView siteOfferReuseSeed,
            boolean persistProjection
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
        requireText(credential.getProjectCode(), "当前店铺缺少 Noon projectCode，无法读取真实商品主档。");

        long stageStartedAt = System.nanoTime();
        NoonSession session = openProductNoonSession(owner.getId(), credential, storeCode);
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
        if (persistProjection) {
            stageStartedAt = System.nanoTime();
            productProjectionPersistenceService.persistSnapshotProjection(
                    command.getOwnerUserId(),
                    view,
                    "synced",
                    extractFetchedAt(view),
                    view.getWarnings()
            );
            recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "persistSnapshotProjection", stageStartedAt);
        }
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
        String partnerSku = normalize(command.getPartnerSku());
        String skuParent = normalize(command.getSkuParent());
        if (!StringUtils.hasText(requestedStoreCode)
                || (!StringUtils.hasText(partnerSku) && !StringUtils.hasText(skuParent))) {
            return null;
        }
        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                requestedStoreCode,
                "当前店铺不在选中的老板名下。"
        );
        String storeCode = normalize(store.getStoreCode());
        ProductListProjectionRecord projection;
        String publicDetailLookupSkuParent;
        if (StringUtils.hasText(partnerSku)) {
            Long logicalStoreId = productManagementMapper.selectLogicalStoreIdByOwnerStoreCode(
                    command.getOwnerUserId(),
                    storeCode
            );
            if (logicalStoreId == null) {
                return null;
            }
            projection = productManagementMapper.selectProductListProjectionByStorePartnerSku(
                    logicalStoreId,
                    storeCode,
                    partnerSku
            );
            if (projection == null) {
                return null;
            }
            publicDetailLookupSkuParent = partnerSku;
        } else {
            projection = productManagementMapper.selectProductListProjectionBySkuParent(
                    command.getOwnerUserId(),
                    storeCode,
                    skuParent
            );
            publicDetailLookupSkuParent = skuParent;
        }
        ProductPublicDetailSnapshot publicDetail = productPublicDetailMapper.selectLatestUsableSnapshotBySkuParent(
                command.getOwnerUserId(),
                storeCode,
                publicDetailLookupSkuParent
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
        productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), requestedStoreCode, skuParent, partnerSku), record);
        if (!Objects.equals(requestedStoreCode, storeCode)) {
            productWorkbenchRecordStore.put(workbenchKey(command.getOwnerUserId(), storeCode, skuParent, partnerSku), record);
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
        if (!StringUtils.hasText(normalize(command.getPartnerSku())) && !StringUtils.hasText(normalize(command.getSkuParent()))) {
            throw new IllegalArgumentException("缺少商品身份，暂时不能读取商品列表摘要。");
        }
        List<String> warnings = new ArrayList<>();
        return productProjectionPersistenceService.loadProductListSummary(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getPartnerSku(),
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
    public ProductListDatasetView updateOperationStage(ProductOperationStageUpdateCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能修改商品运营阶段。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能修改商品运营阶段。");
        String partnerSku = normalize(command.getPartnerSku());
        requireText(partnerSku, "缺少商品 PSKU，暂时不能修改商品运营阶段。");
        ProductOperationStage stage = ProductOperationStage.fromNullableCode(command.getOperationStageCode());
        String operationStageCode = stage == null ? null : stage.getCode();
        Long operatorUserId = command.getOperatorUserId() == null
                ? command.getOwnerUserId()
                : command.getOperatorUserId();

        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());
        int updated = productManagementMapper.updateProductSiteOfferOperationStage(
                command.getOwnerUserId(),
                storeCode,
                partnerSku,
                operationStageCode,
                operatorUserId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("当前店铺站点下未找到可修改运营阶段的商品。");
        }

        ProductMasterFetchCommand reloadCommand = new ProductMasterFetchCommand();
        reloadCommand.setOwnerUserId(command.getOwnerUserId());
        reloadCommand.setStoreCode(storeCode);
        ProductListDatasetView view = loadListDataset(reloadCommand);
        view.setMessage("商品运营阶段已更新。");
        return view;
    }

    @Transactional
    public ProductListDatasetView deleteProduct(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能删除商品。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能删除商品。");
        String skuParent = normalize(command.getSkuParent());
        String partnerSku = normalize(command.getPartnerSku());
        if (!StringUtils.hasText(partnerSku) && !StringUtils.hasText(skuParent)) {
            throw new IllegalArgumentException("缺少商品 PSKU，暂时不能删除商品。");
        }

        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());
        ProductMasterIdentityRecord identity = resolveCurrentProductIdentity(command, storeCode);
        if (identity == null || identity.getProductMasterId() == null) {
            throw new IllegalArgumentException("当前商品不存在或已删除。");
        }
        requireText(
                firstNonBlank(normalize(identity.getPartnerSku()), partnerSku),
                "当前商品缺少系统 PSKU（partnerSku），暂时不能删除。"
        );

        requirePublishCommandService().ensureNoForegroundBlockingActiveTask(
                identity.getProductMasterId(),
                "当前商品已有后台任务正在执行，请等待完成后再删除商品。"
        );
        ProductPublishTaskRecord task = queueProductDeleteTask(command, store, identity);

        ProductMasterFetchCommand reloadCommand = new ProductMasterFetchCommand();
        reloadCommand.setOwnerUserId(command.getOwnerUserId());
        reloadCommand.setStoreCode(storeCode);
        reloadCommand.setNoonUser(command.getNoonUser());
        reloadCommand.setNoonPassword(command.getNoonPassword());
        ProductListDatasetView view = loadListDataset(reloadCommand);
        attachProductDeleteTaskToListDataset(view, identity.getSkuParent(), identity.getPartnerSku(), task);
        view.setMessage("商品删除已提交后台处理，请在发布状态和历史中查看进度。");
        return view;
    }

    @Transactional
    public ProductListDatasetView rebuildProduct(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能重建商品。");
        }
        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少店铺编码，暂时不能重建商品。");
        String skuParent = normalize(command.getSkuParent());
        String partnerSku = normalize(command.getPartnerSku());
        if (!StringUtils.hasText(partnerSku) && !StringUtils.hasText(skuParent)) {
            throw new IllegalArgumentException("缺少商品 PSKU，暂时不能重建商品。");
        }

        StoreSyncStoreRecord store = resolveProductOwnerStore(
                command.getOwnerUserId(),
                storeCode,
                "当前店铺不在选中的老板名下。"
        );
        storeCode = normalize(store.getStoreCode());
        ProductMasterIdentityRecord identity = resolveCurrentProductIdentity(command, storeCode);
        if (identity == null || identity.getProductMasterId() == null) {
            throw new IllegalArgumentException("当前商品不存在或已删除。");
        }
        requireText(
                firstNonBlank(normalize(identity.getPartnerSku()), partnerSku),
                "当前商品缺少系统 PSKU（partnerSku），暂时不能重建。"
        );

        requirePublishCommandService().ensureNoForegroundBlockingActiveTask(
                identity.getProductMasterId(),
                "当前商品已有后台任务正在执行，请等待完成后再重建商品。"
        );
        ProductListingDraftCommand rebuildListingDraft = buildProductRebuildListingDraft(command, store, identity);
        ProductPublishTaskRecord task = queueProductDeleteTask(command, store, identity, rebuildListingDraft);

        ProductMasterFetchCommand reloadCommand = new ProductMasterFetchCommand();
        reloadCommand.setOwnerUserId(command.getOwnerUserId());
        reloadCommand.setStoreCode(storeCode);
        reloadCommand.setNoonUser(command.getNoonUser());
        reloadCommand.setNoonPassword(command.getNoonPassword());
        ProductListDatasetView view = loadListDataset(reloadCommand);
        attachProductDeleteTaskToListDataset(view, identity.getSkuParent(), identity.getPartnerSku(), task);
        view.setMessage("商品重建已提交后台处理：系统会先删除 Noon 旧商品，确认删除后按当前本地数据重新上架。");
        return view;
    }

    public ProductHistoryView loadHistory(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取关键内容历史。");
        }
        requireText(normalize(command.getStoreCode()), "缺少店铺编码，暂时不能读取关键内容历史。");
        if (!StringUtils.hasText(normalize(command.getPartnerSku())) && !StringUtils.hasText(normalize(command.getSkuParent()))) {
            throw new IllegalArgumentException("缺少商品身份，暂时不能读取关键内容历史。");
        }
        List<String> warnings = new ArrayList<>();
        return productProjectionPersistenceService.loadProductHistoryView(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getPartnerSku(),
                command.getSkuParent(),
                warnings
        );
    }

    public ProductMasterWorkbenchView applyAction(ProductMasterActionCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能执行商品详情动作。");
        }

        String resolvedAction = resolveAction(command.getAction());
        String key = workbenchKey(command);

        if ("SAVE_DRAFT".equals(resolvedAction)) {
            ensureNoForegroundBlockingPublishTaskForCommand(command, "当前商品已有发布任务正在执行，请等待完成后再保存草稿。");
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
                if (isProductDeleteBackgroundTask(task)) {
                    executeProductDeleteTask(task, previousStatus);
                } else if ("queued".equalsIgnoreCase(previousStatus)
                        || requirePublishCommandService().isWriteRetryScheduledStatus(previousStatus)) {
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
                if (isProductDeleteBackgroundTask(task)) {
                    scheduleProductDeleteUnhandledFailure(task, previousStatus, exception);
                    continue;
                }
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
        String draftJson = writeJson(requestedSnapshot);
        String draftHash = sha256Hex(draftJson);
        String baselineJson = writeJson(record.getBaselineSnapshot());
        String changedDomainsJson = writeJson(productPublishChangedDomainComparator.resolve(
                requestedSnapshot,
                record.getBaselineSnapshot(),
                currentSiteCode,
                unsupportedChanges != null && unsupportedChanges.isGroupChanged(),
                unsupportedChanges != null && unsupportedChanges.isVariantStructureChanged()
        ));
        String requestJson = writeJson(buildPublishTaskRequestPayload(command, currentSiteCode));
        if (activeTask != null && requirePublishCommandService().isWriteRetryScheduledStatus(activeTask.getStatus())) {
            boolean refreshed = requirePublishCommandService().refreshRetryScheduledTaskDraft(
                    activeTask,
                    draftJson,
                    baselineJson,
                    requestJson,
                    changedDomainsJson,
                    draftHash,
                    "noon_write_failed",
                    "Noon 发布接口暂时不可用，系统将后台自动核对并重试。"
            );
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus("draft");
            record.setNote("发布正在后台处理，系统会自动发布最新草稿。");
            record.setPublishTask(productPublishTaskViewBuilder.build(activeTask, false));
            appendRecentAction(record, "publish-current", activeTask.getStatus(), record.getNote(), currentSiteCode, requestedSnapshot);
            productWorkbenchRecordStore.put(workbenchKey(command), record);
            return finalizeWorkbenchView(
                    command.getOwnerUserId(),
                    refreshed ? "publish-current" : null,
                    currentSiteCode,
                    record,
                    null,
                    requestedSnapshot.getWarnings()
            );
        }
        if (activeTask != null && requirePublishCommandService().isForegroundBlockingActiveTask(activeTask)) {
            record.setPublishTask(productPublishTaskViewBuilder.build(activeTask, false));
            record.setNote("当前商品已有发布任务正在执行，请等待任务完成后再继续编辑或发布。");
            productWorkbenchRecordStore.put(workbenchKey(command), record);
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
        createCommand.setBaselineJson(baselineJson);
        createCommand.setDraftHash(draftHash);
        createCommand.setChangedDomainsJson(changedDomainsJson);
        createCommand.setRequestJson(requestJson);
        createCommand.setIdempotencyKey(publishCurrentTaskIdempotencyKey(
                command,
                requestedSnapshot,
                currentSiteCode,
                draftHash
        ));
        ProductPublishTaskCreateResult createResult = requirePublishCommandService().createPublishCurrentTask(createCommand);
        ProductPublishTaskRecord task = createResult.getTask();
        if (createResult.isDuplicate()) {
            record.setDraftSnapshot(requestedSnapshot);
            record.setSyncStatus("draft");
            record.setNote(isActivePublishTaskStatus(task.getStatus())
                    ? "当前商品已有发布任务正在执行，请等待任务完成后再继续编辑或发布。"
                    : publishTaskMessage(task));
            record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
            productWorkbenchRecordStore.put(workbenchKey(command), record);
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
        productWorkbenchRecordStore.put(workbenchKey(command), record);
        return finalizeWorkbenchView(
                command.getOwnerUserId(),
                "publish-current",
                currentSiteCode,
                record,
                record.getNote(),
                requestedSnapshot.getWarnings()
        );
    }

    private ProductPublishTaskRecord queueProductDeleteTask(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductMasterIdentityRecord identity
    ) {
        return queueProductDeleteTask(command, store, identity, null);
    }

    private ProductPublishTaskRecord queueProductDeleteTask(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductMasterIdentityRecord identity,
            ProductListingDraftCommand rebuildListingDraft
    ) {
        ProductMasterSnapshotView snapshot = buildProductDeleteTaskSnapshot(command, store, identity);
        String snapshotJson = writeJson(snapshot);
        String currentSiteCode = normalize(store == null ? null : store.getSite());
        String skuParent = normalize(identity.getSkuParent());
        String partnerSku = firstNonBlank(normalize(identity.getPartnerSku()), normalize(command.getPartnerSku()));
        String pskuCode = firstNonBlank(normalize(identity.getPskuCode()), normalize(command.getPskuCode()));
        ProductPublishTaskCreateCommand createCommand = new ProductPublishTaskCreateCommand();
        createCommand.setOwnerUserId(command.getOwnerUserId());
        createCommand.setProductMasterId(identity.getProductMasterId());
        createCommand.setStoreCode(store == null ? command.getStoreCode() : store.getStoreCode());
        createCommand.setProjectCode(store == null ? null : store.getProjectCode());
        createCommand.setSkuParent(skuParent);
        createCommand.setPartnerSku(partnerSku);
        createCommand.setPskuCode(pskuCode);
        createCommand.setCurrentSiteCode(currentSiteCode);
        createCommand.setDraftJson(snapshotJson);
        createCommand.setBaselineJson(snapshotJson);
        createCommand.setDraftHash(sha256Hex(snapshotJson));
        createCommand.setRequestJson(writeJson(buildProductDeleteTaskRequestPayload(
                command,
                identity,
                currentSiteCode,
                rebuildListingDraft
        )));
        createCommand.setIdempotencyKey(
                "delete:" + productDeleteStoreIdentityKey(identity, createCommand.getStoreCode())
                        + ":" + partnerSku
                        + ":" + System.nanoTime()
        );
        ProductPublishTaskCreateResult createResult = requirePublishCommandService().createProductDeleteTask(createCommand);
        return createResult.getTask();
    }

    private ProductListingDraftCommand buildProductRebuildListingDraft(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductMasterIdentityRecord identity
    ) {
        if (productProjectionPersistenceService == null) {
            throw new IllegalStateException("商品本地投影服务尚未初始化，暂时不能重建商品。");
        }
        String storeCode = normalize(store == null ? command.getStoreCode() : store.getStoreCode());
        String partnerSku = firstNonBlank(normalize(identity.getPartnerSku()), normalize(command.getPartnerSku()));
        String skuParent = firstNonBlank(normalize(identity.getSkuParent()), normalize(command.getSkuParent()));
        List<String> warnings = new ArrayList<>();
        ProductMasterSnapshotView baselineSnapshot =
                productProjectionPersistenceService.loadLatestBaselineSnapshot(
                        command.getOwnerUserId(),
                        storeCode,
                        partnerSku,
                        skuParent,
                        warnings
                );
        if (baselineSnapshot == null || !baselineSnapshot.isReady()) {
            throw new IllegalArgumentException("当前商品缺少可用本地基线，暂时不能重建。");
        }
        ProductProjectionPersistenceService.PersistedWorkbenchState persistedState =
                productProjectionPersistenceService.loadPersistedWorkbenchState(
                        command.getOwnerUserId(),
                        storeCode,
                        partnerSku,
                        skuParent,
                        warnings
                );
        ProductMasterSnapshotView sourceSnapshot = hasActivePersistedDraft(persistedState)
                ? persistedState.getDraftSnapshot()
                : baselineSnapshot;
        hydrateProductRebuildSourceIdentity(sourceSnapshot, identity, partnerSku, skuParent);
        ProductListingDraftCommand draft = new ProductRebuildDraftBuilder().build(
                identity.getProductMasterId(),
                storeCode,
                sourceSnapshot
        );
        if (!StringUtils.hasText(draft.getInheritedListingStartedAt())) {
            throw new IllegalArgumentException("当前商品缺少旧 PSKU 上架时间，暂时不能重建。");
        }
        return draft;
    }

    private void hydrateProductRebuildSourceIdentity(
            ProductMasterSnapshotView snapshot,
            ProductMasterIdentityRecord identity,
            String partnerSku,
            String skuParent
    ) {
        if (snapshot == null) {
            return;
        }
        Map<String, Object> sourceIdentity = snapshot.getIdentity();
        String sourceSkuParent = firstNonBlank(
                normalize(identity == null ? null : identity.getSkuParent()),
                normalize(skuParent),
                normalizeValue(sourceIdentity.get("skuParent")),
                normalizeValue(sourceIdentity.get("currentZCode"))
        );
        String sourceProductType = ProductSourceTypeSupport.resolve(
                firstNonBlank(
                        normalize(identity == null ? null : identity.getProductSourceType()),
                        normalizeValue(sourceIdentity.get("productSourceType")),
                        normalizeValue(sourceIdentity.get("sourceType"))
                ),
                null,
                sourceSkuParent
        );
        sourceIdentity.put("productSourceType", sourceProductType);
        putIfPresent(sourceIdentity, "partnerSku", firstNonBlank(
                normalize(identity == null ? null : identity.getPartnerSku()),
                normalize(partnerSku),
                normalizeValue(sourceIdentity.get("partnerSku"))
        ));
        putIfPresent(sourceIdentity, "skuParent", sourceSkuParent);
        putIfPresent(sourceIdentity, "currentZCode", firstNonBlank(
                normalize(identity == null ? null : identity.getSkuParent()),
                normalizeValue(sourceIdentity.get("currentZCode")),
                sourceSkuParent
        ));
        putIfPresent(sourceIdentity, "pskuCode", firstNonBlank(
                normalize(identity == null ? null : identity.getPskuCode()),
                normalizeValue(sourceIdentity.get("pskuCode"))
        ));
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (target != null && StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String normalizeValue(Object value) {
        return value == null ? null : normalize(String.valueOf(value));
    }

    private String productDeleteStoreIdentityKey(ProductMasterIdentityRecord identity, String storeCode) {
        if (identity != null && identity.getLogicalStoreId() != null) {
            return String.valueOf(identity.getLogicalStoreId());
        }
        return normalize(storeCode);
    }

    private ProductMasterSnapshotView buildProductDeleteTaskSnapshot(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductMasterIdentityRecord identity
    ) {
        String partnerSku = firstNonBlank(normalize(identity.getPartnerSku()), normalize(command.getPartnerSku()));
        String pskuCode = firstNonBlank(normalize(identity.getPskuCode()), normalize(command.getPskuCode()));
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("product-delete-task");
        snapshot.setReady(true);
        snapshot.setMessage("商品删除任务已提交，系统会先删除 Noon 后台商品，成功后再清理本地目录。");
        snapshot.getStoreContext().put("storeCode", normalize(store == null ? command.getStoreCode() : store.getStoreCode()));
        snapshot.getStoreContext().put("projectCode", normalize(store == null ? null : store.getProjectCode()));
        snapshot.getStoreContext().put("siteCode", normalize(store == null ? null : store.getSite()));
        snapshot.getStoreContext().put("projectName", normalize(store == null ? null : store.getProjectName()));
        snapshot.getStoreContext().put("fetchedAt", FETCH_TIME_FORMATTER.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
        snapshot.getIdentity().put("skuParent", normalize(identity.getSkuParent()));
        snapshot.getIdentity().put("partnerSku", partnerSku);
        snapshot.getIdentity().put("pskuCode", pskuCode);
        return snapshot;
    }

    private void attachProductDeleteTaskToListDataset(
            ProductListDatasetView view,
            String skuParent,
            String partnerSku,
            ProductPublishTaskRecord task
    ) {
        if (view == null || view.getItems() == null || task == null) {
            return;
        }
        String normalizedSkuParent = normalize(skuParent);
        String normalizedPartnerSku = normalize(partnerSku);
        Map<String, Object> taskSummary = buildProductDeleteListTaskSummary(task);
        for (LocalDbStoreInitializationService.StoreInitializationProductListItemView item : view.getItems()) {
            if (item == null) {
                continue;
            }
            boolean samePartnerSku = StringUtils.hasText(normalizedPartnerSku)
                    && Objects.equals(normalize(item.getPartnerSku()), normalizedPartnerSku);
            boolean sameSkuParentFallback = !StringUtils.hasText(normalizedPartnerSku)
                    && Objects.equals(normalize(item.getSkuParent()), normalizedSkuParent);
            if (!samePartnerSku && !sameSkuParentFallback) {
                continue;
            }
            item.setLastPublishTask(taskSummary);
            return;
        }
    }

    private Map<String, Object> buildProductDeleteListTaskSummary(ProductPublishTaskRecord task) {
        boolean rebuildTask = isProductRebuildDeleteTask(task);
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfNotNull(summary, "taskId", task.getId());
        putIfNotBlank(summary, "taskType", rebuildTask ? "product-rebuild" : normalize(task.getTaskType()));
        putIfNotBlank(summary, "status", normalize(task.getStatus()));
        putIfNotBlank(summary, "statusLabel", rebuildTask
                ? productRebuildDeleteListStatusLabel(task.getStatus())
                : productDeleteListStatusLabel(task.getStatus()));
        putIfNotBlank(summary, "resultText", rebuildTask
                ? productRebuildDeleteListMessage(task)
                : publishTaskMessage(task));
        putIfNotBlank(summary, "submittedAt", FETCH_TIME_FORMATTER.format(ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))));
        putIfNotBlank(summary, "targetSiteCode", task.getCurrentSiteCode());
        putIfNotBlank(summary, "pskuCode", task.getPskuCode());
        putIfNotBlank(summary, "partnerSku", task.getPartnerSku());
        return summary;
    }

    private boolean isProductRebuildDeleteTask(ProductPublishTaskRecord task) {
        JsonNode request = readTaskRequestNode(task);
        return "product-rebuild".equalsIgnoreCase(text(request, "rebuildAction"))
                || "product-rebuild".equalsIgnoreCase(text(request, "action"));
    }

    private String productRebuildDeleteListStatusLabel(String status) {
        String normalized = normalizeProductDeleteStatus(status);
        if ("failed".equalsIgnoreCase(normalized)) {
            return "重建失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "重建待核对";
        }
        if ("cancelled".equalsIgnoreCase(normalized)) {
            return "已取消";
        }
        return "重建中";
    }

    private String productRebuildDeleteListMessage(ProductPublishTaskRecord task) {
        String status = task == null ? null : normalizeProductDeleteStatus(task.getStatus());
        if ("failed".equalsIgnoreCase(status)) {
            return firstNonBlank(task.getErrorMessage(), "商品重建失败，本地商品未删除。");
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            return "商品重建结果需要人工核对。";
        }
        if ("queued".equalsIgnoreCase(status)) {
            return "商品重建已排队，系统会先删除旧 PSKU。";
        }
        if ("synced".equalsIgnoreCase(status)) {
            return "旧 PSKU 删除已确认，系统正在提交重建上架。";
        }
        return "商品重建正在后台处理。";
    }

    private String productDeleteListStatusLabel(String status) {
        String normalized = normalizeProductDeleteStatus(status);
        if ("synced".equalsIgnoreCase(normalized)) {
            return "删除成功";
        }
        if ("failed".equalsIgnoreCase(normalized)) {
            return "删除失败";
        }
        if ("pending_manual_check".equalsIgnoreCase(normalized)) {
            return "删除待核对";
        }
        if ("cancelled".equalsIgnoreCase(normalized)) {
            return "已取消";
        }
        return "删除中";
    }

    private String normalizeProductDeleteStatus(String status) {
        String normalized = normalize(status);
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_QUEUED.equalsIgnoreCase(normalized)) {
            return "queued";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_RUNNING.equalsIgnoreCase(normalized)) {
            return "running";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_SUBMITTED.equalsIgnoreCase(normalized)) {
            return "submitted";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFYING.equalsIgnoreCase(normalized)) {
            return "verifying";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE.equalsIgnoreCase(normalized)) {
            return "pending_effective";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_WRITE_RETRY_SCHEDULED.equalsIgnoreCase(normalized)) {
            return "write_retry_scheduled";
        }
        if (ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFY_TIMEOUT.equalsIgnoreCase(normalized)) {
            return "verify_timeout";
        }
        return normalized;
    }

    private void executeProductDeleteTask(ProductPublishTaskRecord task, String previousStatus) {
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductMasterActionCommand command = buildTaskActionCommand(task, draft);
        command.setAction("delete-product");
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(
                baseline,
                draft,
                "draft",
                "商品删除正在后台自动处理。"
        );
        record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
        productWorkbenchRecordStore.put(workbenchKey(task), record);

        NoonSessionGateway.RequestCountScope requestCountScope = productNoonAdapter.openRequestCountScope();
        List<String> actionWarnings = new ArrayList<>();
        try {
            StoreSyncStoreRecord store = requirePublishStore(task.getOwnerUserId(), task.getStoreCode());
            NoonSession session = openNoonProductDeleteSession(command, store, actionWarnings);
            ProductMasterSnapshotView preDeleteSnapshot = readProductDeletePreDeleteSnapshot(task);
            String stage = productDeleteStage(task);

            if (!isProductDeleteAfterDeleteStage(stage)) {
                if (!isProductDeleteAfterUnmapStage(stage)) {
                    try {
                        preDeleteSnapshot = fetchSnapshot(command, "product-delete.before");
                    } catch (IllegalStateException exception) {
                        if (!isNoonProductMissingAfterDelete(exception)) {
                            throw exception;
                        }
                        actionWarnings.add("删除前旧 skuParent 已不可回读，系统将继续按 pskuCode 调用 Noon 删除接口。");
                    }
                    updatePublishTaskStatus(
                            task,
                            ProductPublishCommandService.PRODUCT_DELETE_STATUS_SUBMITTED,
                            null,
                            null,
                            buildTaskResultJson(
                                    "submitted",
                                    requestCountScope.snapshot(),
                                    actionWarnings,
                                    productDeleteResultExtras(
                                            preDeleteSnapshot == null ? "pre_delete_unavailable" : "pre_delete_captured",
                                            preDeleteSnapshot,
                                            null
                                    )
                            ),
                            null,
                            null,
                            null
                    );
                    if (shouldSkipNoonProductUnmap(preDeleteSnapshot)) {
                        addLocalWarning(actionWarnings, "Noon 回读未提供旧 catalog/Child SKU 映射，系统跳过解除映射请求，直接执行删除。");
                    } else {
                        executeNoonProductUnmap(task, session, preDeleteSnapshot);
                        updatePublishTaskStatus(
                                task,
                                ProductPublishCommandService.PRODUCT_DELETE_STATUS_SUBMITTED,
                                null,
                                null,
                                buildTaskResultJson(
                                        "submitted",
                                        requestCountScope.snapshot(),
                                        actionWarnings,
                                        productDeleteResultExtras("unmap_submitted", preDeleteSnapshot, NoonProductGateway.PSKU_DELETE_URL)
                                ),
                                null,
                                null,
                                null
                        );
                        stage = "unmap_submitted";
                    }
                }

                try {
                    executeNoonProductDelete(task, session, preDeleteSnapshot);
                } catch (IllegalStateException exception) {
                    if (!isNoonDeleteTargetInvalidOrDeleted(exception)) {
                        handleProductDeleteFailure(
                                task,
                                record,
                                draft,
                                requestCountScope.snapshot(),
                                actionWarnings,
                                exception,
                                isNoonPskuUnmapRequired(exception) ? "pre_delete_captured" : stage,
                                preDeleteSnapshot
                        );
                        return;
                    }
                    addLocalWarning(actionWarnings, "Noon 删除接口返回目标已无效或已删除，系统继续回查确认。");
                }
                updatePublishTaskStatus(
                        task,
                        ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFYING,
                        null,
                        null,
                        buildTaskResultJson(
                                "verifying",
                                requestCountScope.snapshot(),
                                actionWarnings,
                                productDeleteResultExtras("delete_submitted", preDeleteSnapshot, NoonProductGateway.PSKU_DELETE_URL)
                        ),
                        null,
                        null,
                        task.getVerifyAttemptCount() == null ? 1 : task.getVerifyAttemptCount() + 1
                );
            }

            int verifyAttempt = Math.max(1, task.getVerifyAttemptCount() == null ? 1 : task.getVerifyAttemptCount() + 1);
            try {
                fetchSnapshotWithoutProjection(command, "product-delete.after");
                updatePublishTaskStatus(
                        task,
                        ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE,
                        "product_delete_effect_pending",
                        "商品删除已提交，Noon 仍能回读到商品，系统将稍后继续核对。",
                        buildTaskResultJson(
                                "pending_effective",
                                requestCountScope.snapshot(),
                                actionWarnings,
                                productDeleteResultExtras("delete_submitted", preDeleteSnapshot, NoonProductGateway.PSKU_DELETE_URL)
                        ),
                        nextPublishVerifyRunAt(task),
                        null,
                        verifyAttempt
                );
                return;
            } catch (IllegalStateException exception) {
                if (!isNoonProductMissingAfterDelete(exception)) {
                    throw exception;
                }
            }

            ProductDeleteNoonPresence noonPresence = findProductDeleteNoonPresenceAfterDelete(
                    session,
                    task,
                    store,
                    actionWarnings
            );
            boolean currentNoonPskuDeleteSubmitted =
                    "current_psku_delete_submitted".equalsIgnoreCase(normalize(stage));
            if (noonPresence.isPresent()) {
                if (shouldDeleteCurrentNoonPresencePsku(task, noonPresence)) {
                    String currentPskuCode = noonPresence.hitPskuCode();
                    addLocalWarning(
                            actionWarnings,
                            "Noon 回查发现 partnerSku 当前关联新的 pskuCode="
                                    + currentPskuCode
                                    + "，系统将按当前 Noon code 继续删除。"
                    );
                    try {
                        executeNoonProductDelete(session, currentPskuCode);
                    } catch (IllegalStateException exception) {
                        if (!isNoonDeleteTargetInvalidOrDeleted(exception)) {
                            handleProductDeleteFailure(
                                    task,
                                    record,
                                    draft,
                                    requestCountScope.snapshot(),
                                    actionWarnings,
                                    exception,
                                    "current_psku_delete_submitted",
                                    preDeleteSnapshot
                            );
                            return;
                        }
                        addLocalWarning(actionWarnings, "Noon 当前 pskuCode 删除接口返回目标已无效或已删除，系统继续回查确认。");
                    }
                    updatePublishTaskStatus(
                            task,
                            ProductPublishCommandService.PRODUCT_DELETE_STATUS_VERIFYING,
                            null,
                            null,
                            buildTaskResultJson(
                                    "verifying",
                                    requestCountScope.snapshot(),
                                    actionWarnings,
                                    productDeleteResultExtras(
                                            "current_psku_delete_submitted",
                                            preDeleteSnapshot,
                                            NoonProductGateway.PSKU_DELETE_URL,
                                            noonPresence
                                    )
                            ),
                            null,
                            null,
                            verifyAttempt
                    );
                    currentNoonPskuDeleteSubmitted = true;
                    noonPresence = findProductDeleteNoonPresenceAfterDelete(
                            session,
                            task,
                            store,
                            actionWarnings
                    );
                    if (!noonPresence.isPresent()) {
                        completeProductDeleteTaskAfterNoonMissing(
                                task,
                                preDeleteSnapshot,
                                requestCountScope.snapshot(),
                                mergeWarnings(actionWarnings, List.of("已从状态 " + previousStatus + " 执行商品删除。")),
                                verifyAttempt
                        );
                        return;
                    }
                }
                addLocalWarning(actionWarnings, noonPresence.toWarning());
                updatePublishTaskStatus(
                        task,
                        ProductPublishCommandService.PRODUCT_DELETE_STATUS_PENDING_EFFECTIVE,
                        "product_delete_effect_pending",
                        "商品删除已提交，但 Noon 后台仍能查到该商品，系统暂不清理本地商品目录，将稍后继续核对。",
                        buildTaskResultJson(
                                "pending_effective",
                                requestCountScope.snapshot(),
                                actionWarnings,
                                productDeleteResultExtras(
                                        currentNoonPskuDeleteSubmitted
                                                ? "current_psku_delete_submitted"
                                                : "delete_submitted",
                                        preDeleteSnapshot,
                                        NoonProductGateway.PSKU_DELETE_URL,
                                        noonPresence
                                )
                        ),
                        nextPublishVerifyRunAt(task),
                        null,
                        verifyAttempt
                );
                return;
            }

            completeProductDeleteTaskAfterNoonMissing(
                    task,
                    preDeleteSnapshot,
                    requestCountScope.snapshot(),
                    mergeWarnings(actionWarnings, List.of("已从状态 " + previousStatus + " 执行商品删除。")),
                    verifyAttempt
            );
        } catch (IllegalStateException exception) {
            handleProductDeleteFailure(task, record, draft, requestCountScope.snapshot(), actionWarnings, exception);
        } finally {
            requestCountScope.close();
        }
    }

    private void executeQueuedPublishTask(ProductPublishTaskRecord task) {
        if (isProductDeleteBackgroundTask(task)) {
            throw new IllegalStateException("product-delete task must not run through publish-current execution.");
        }
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductMasterActionCommand command = buildTaskActionCommand(task, draft);
        String currentSiteCode = normalize(task.getCurrentSiteCode());
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(baseline, draft, "draft", "发布任务正在提交 Noon。");
        productWorkbenchRecordStore.put(workbenchKey(task), record);

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
                productWorkbenchRecordStore.put(workbenchKey(task), refreshed);
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
            if (publishChangedFieldsMatch(baseline, publishableDraft, liveBeforePublish, currentSiteCode)) {
                requestCounts = requestCountScope.snapshot();
                completePublishTaskAfterVerification(
                        task,
                        baseline,
                        preparedDraft,
                        publishableDraft,
                        liveBeforePublish,
                        unsupportedChanges,
                        requestCounts,
                        mergeWarnings(actionWarnings, List.of("回读已确认 Noon 当前值符合本地草稿，未重复提交写入。")),
                        Math.max(1, task.getVerifyAttemptCount() == null ? 0 : task.getVerifyAttemptCount() + 1)
                );
                return;
            }

            StoreSyncStoreRecord store = requirePublishStore(task.getOwnerUserId(), task.getStoreCode());
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
                    productWorkbenchRecordStore.put(workbenchKey(task), record);
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
                if (requirePublishCommandService().isRetryableNoonWriteFailure(exception)) {
                    requestCounts = requestCountScope.snapshot();
                    boolean hasRetryBudget = requirePublishCommandService().hasRemainingAutomaticWriteRetries(task);
                    String retryMessage = hasRetryBudget
                            ? "Noon 发布接口暂时不可用，系统将后台自动核对并重试。"
                            : "Noon 多次返回系统错误，系统已停止自动重试，诺诺草稿已保留。";
                    boolean scheduled = requirePublishCommandService().scheduleNoonWriteRetryOrManualCheck(
                            task,
                            "noon_write_failed",
                            retryMessage,
                            buildTaskResultJson(
                                    hasRetryBudget ? "write_retry_scheduled" : "pending_manual_check",
                                    requestCounts,
                                    mergeWarnings(actionWarnings, List.of(shrink(exception.getMessage())))
                            )
                    );
                    log.warn(
                            "product-management publish task noon write retryable failure id={} owner={} store={} skuParent={} site={} scheduled={} retryCount={} maxRetryCount={} error={}",
                            task.getId(),
                            task.getOwnerUserId(),
                            task.getStoreCode(),
                            task.getSkuParent(),
                            currentSiteCode,
                            scheduled,
                            task.getRetryCount(),
                            task.getMaxRetryCount(),
                            shrink(exception.getMessage())
                    );
                    record.setDraftSnapshot(draft);
                    record.setSyncStatus(scheduled ? "draft" : "failed");
                    record.setNote(scheduled ? "发布正在后台处理，系统会自动核对 Noon 结果。" : task.getErrorMessage());
                    record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
                    appendRecentAction(
                            record,
                            "publish-current",
                            task.getStatus(),
                            record.getNote(),
                            currentSiteCode,
                            draft
                    );
                    productWorkbenchRecordStore.put(workbenchKey(task), record);
                    return;
                }
                if (requirePublishCommandService().isRetryableNoonRequestFailure(exception)) {
                    requestCounts = requestCountScope.snapshot();
                    boolean hasRetryBudget = requirePublishCommandService().hasRemainingAutomaticWriteRetries(task);
                    String retryMessage = hasRetryBudget
                            ? "Noon 请求暂时不可用，系统将后台自动核对并重试。"
                            : "Noon 多次请求失败，系统已停止自动重试，诺诺草稿已保留。";
                    boolean scheduled = requirePublishCommandService().scheduleNoonRetryOrManualCheck(
                            task,
                            "noon_request_failed",
                            retryMessage,
                            "noon_request_retry_exhausted",
                            "Noon 多次请求失败，系统已停止自动重试，诺诺草稿已保留。",
                            buildTaskResultJson(
                                    hasRetryBudget ? "write_retry_scheduled" : "pending_manual_check",
                                    requestCounts,
                                    mergeWarnings(actionWarnings, List.of(shrink(exception.getMessage())))
                            )
                    );
                    log.warn(
                            "product-management publish task noon request retryable failure id={} owner={} store={} skuParent={} site={} scheduled={} retryCount={} maxRetryCount={} error={}",
                            task.getId(),
                            task.getOwnerUserId(),
                            task.getStoreCode(),
                            task.getSkuParent(),
                            currentSiteCode,
                            scheduled,
                            task.getRetryCount(),
                            task.getMaxRetryCount(),
                            shrink(exception.getMessage())
                    );
                    record.setDraftSnapshot(draft);
                    record.setSyncStatus(scheduled ? "draft" : "failed");
                    record.setNote(scheduled ? "发布正在后台处理，系统会自动核对 Noon 结果。" : task.getErrorMessage());
                    record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
                    appendRecentAction(
                            record,
                            "publish-current",
                            task.getStatus(),
                            record.getNote(),
                            currentSiteCode,
                            draft
                    );
                    productWorkbenchRecordStore.put(workbenchKey(task), record);
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
            if (requirePublishCommandService().isRetryableNoonRequestFailure(exception)) {
                if (writeSubmitted) {
                    updatePublishTaskStatus(
                            task,
                            "verify_timeout",
                            "noon_verify_transient",
                            "Noon 回读暂时不可用，系统稍后继续核对官方结果。",
                            buildTaskResultJson("verify_timeout", requestCounts, List.of(shrink(exception.getMessage()))),
                            nextPublishVerifyRunAt(task),
                            null,
                            task.getVerifyAttemptCount() == null ? 1 : task.getVerifyAttemptCount() + 1
                    );
                    return;
                }
                boolean hasRetryBudget = requirePublishCommandService().hasRemainingAutomaticWriteRetries(task);
                String retryMessage = hasRetryBudget
                        ? "Noon 请求暂时不可用，系统将后台自动核对并重试。"
                        : "Noon 多次请求失败，系统已停止自动重试，诺诺草稿已保留。";
                boolean scheduled = requirePublishCommandService().scheduleNoonRetryOrManualCheck(
                        task,
                        "noon_request_failed",
                        retryMessage,
                        "noon_request_retry_exhausted",
                        "Noon 多次请求失败，系统已停止自动重试，诺诺草稿已保留。",
                        buildTaskResultJson(
                                hasRetryBudget ? "write_retry_scheduled" : "pending_manual_check",
                                requestCounts,
                                List.of(shrink(exception.getMessage()))
                        )
                );
                log.warn(
                        "product-management publish task noon pre-write retryable failure id={} owner={} store={} skuParent={} site={} scheduled={} retryCount={} maxRetryCount={} error={}",
                        task.getId(),
                        task.getOwnerUserId(),
                        task.getStoreCode(),
                        task.getSkuParent(),
                        currentSiteCode,
                        scheduled,
                        task.getRetryCount(),
                        task.getMaxRetryCount(),
                        shrink(exception.getMessage())
                );
                record.setDraftSnapshot(draft);
                record.setSyncStatus(scheduled ? "draft" : "failed");
                record.setNote(scheduled ? "发布正在后台处理，系统会自动核对 Noon 结果。" : task.getErrorMessage());
                record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
                appendRecentAction(
                        record,
                        "publish-current",
                        task.getStatus(),
                        record.getNote(),
                        currentSiteCode,
                        draft
                );
                productWorkbenchRecordStore.put(workbenchKey(task), record);
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
        productWorkbenchRecordStore.put(workbenchKey(task), refreshed);
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
        productWorkbenchRecordStore.put(workbenchKey(task), record);
        finalizeWorkbenchView(
                task.getOwnerUserId(),
                "publish-current",
                task.getCurrentSiteCode(),
                record,
                errorMessage,
                mergeWarnings(draft.getWarnings(), List.of(errorMessage))
        );
    }

    private void completeProductDeleteTaskAfterNoonMissing(
            ProductPublishTaskRecord task,
            ProductMasterSnapshotView preDeleteSnapshot,
            Map<String, Integer> requestCounts,
            List<String> actionWarnings,
            int verifyAttempt
    ) {
        deleteLocalProductAfterNoonDelete(task, preDeleteSnapshot, actionWarnings);
        updatePublishTaskStatus(
                task,
                "synced",
                null,
                null,
                buildTaskResultJson(
                        "synced",
                        requestCounts,
                        actionWarnings,
                        productDeleteResultExtras("synced", preDeleteSnapshot, NoonProductGateway.PSKU_DELETE_URL)
                ),
                null,
                LocalDateTime.now(),
                verifyAttempt
        );
        productWorkbenchRecordStore.remove(workbenchKey(task));
    }

    private void deleteLocalProductAfterNoonDelete(
            ProductPublishTaskRecord task,
            ProductMasterSnapshotView preDeleteSnapshot,
            List<String> actionWarnings
    ) {
        Long productMasterId = task == null ? null : task.getProductMasterId();
        if (productMasterId == null) {
            throw new IllegalStateException("商品删除任务缺少本地商品 ID，无法清理本地目录。");
        }
        Long updatedBy = task.getOwnerUserId();
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
            addLocalWarning(actionWarnings, "本地商品目录已经清理过，本次只确认 Noon 删除任务完成。");
        } else {
            addLocalWarning(actionWarnings, "已执行真实 Noon 删除，并同步清理本地商品目录。");
        }

        insertProductDeleteActionLogIfAbsent(task, productMasterId, preDeleteSnapshot, actionWarnings, updatedBy, deleted == 0);

        if (deleted > 0 && productDetailBaselineBackfillService != null) {
            productDetailBaselineBackfillService.cancel(buildFetchCommand(task), "商品已从 Noon 后台删除，并已清理本地商品目录。");
        }
    }

    private void insertProductDeleteActionLogIfAbsent(
            ProductPublishTaskRecord task,
            Long productMasterId,
            ProductMasterSnapshotView preDeleteSnapshot,
            List<String> actionWarnings,
            Long updatedBy,
            boolean localAlreadyDeleted
    ) {
        String idempotencyKey = "product-delete-task:" + (task == null ? productMasterId : task.getId());
        if (productManagementMapper.selectProductActionLogIdByIdempotency(idempotencyKey) != null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(
                "message",
                localAlreadyDeleted
                        ? "Noon 删除已确认，本地商品目录此前已清理。"
                        : "商品已从 Noon 后台删除，并已清理本地商品目录。"
        );
        summary.put("skuParent", task.getSkuParent());
        summary.put("partnerSku", task.getPartnerSku());
        summary.put("storeCode", task.getStoreCode());
        summary.put("unmapEndpoint", "psku/map");
        summary.put("deleteEndpoint", "psku/delete");
        summary.put("warnings", actionWarnings);
        summary.put("preDeleteSnapshot", preDeleteSnapshot);
        productManagementMapper.insertProductActionLog(
                productManagementMapper.nextProductActionLogId(),
                productMasterId,
                null,
                null,
                null,
                "delete-product",
                "noon_delete",
                idempotencyKey,
                "noon+local",
                "synced",
                null,
                firstNonBlank(
                        normalize(task.getPskuCode()),
                        preDeleteSnapshot == null || preDeleteSnapshot.getIdentity() == null
                                ? null
                                : textValue(preDeleteSnapshot.getIdentity().get("pskuCode"))
                ),
                null,
                0,
                writeJson(summary),
                null,
                writeJson(productDeleteRequestBuilder.buildDeleteBody(task, preDeleteSnapshot)),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                updatedBy
        );
    }

    private void handleProductDeleteFailure(
            ProductPublishTaskRecord task,
            ProductWorkbenchRecord record,
            ProductMasterSnapshotView draft,
            Map<String, Integer> requestCounts,
            List<String> actionWarnings,
            IllegalStateException exception
    ) {
        handleProductDeleteFailure(
                task,
                record,
                draft,
                requestCounts,
                actionWarnings,
                exception,
                firstNonBlank(productDeleteStage(task), "retry_scheduled"),
                readProductDeletePreDeleteSnapshot(task)
        );
    }

    private void handleProductDeleteFailure(
            ProductPublishTaskRecord task,
            ProductWorkbenchRecord record,
            ProductMasterSnapshotView draft,
            Map<String, Integer> requestCounts,
            List<String> actionWarnings,
            IllegalStateException exception,
            String stage,
            ProductMasterSnapshotView preDeleteSnapshot
    ) {
        String retryMessage = "商品删除正在后台自动处理，系统会继续核对 Noon 删除结果。";
        String manualCheckMessage = "商品删除结果需要人工核对：请在 Noon 后台确认 ZSKU/catalog 是否已删除后重试或联系技术处理。";
        boolean scheduled = requirePublishCommandService().scheduleProductDeleteRetryOrManualCheck(
                task,
                isTimeoutException(exception) ? "product_delete_timeout" : "product_delete_failed",
                retryMessage,
                "product_delete_retry_exhausted",
                manualCheckMessage,
                buildTaskResultJson(
                        "write_retry_scheduled",
                        requestCounts,
                        mergeWarnings(actionWarnings, List.of(shrink(exception.getMessage()))),
                        productDeleteResultExtras(stage, preDeleteSnapshot, NoonProductGateway.PSKU_DELETE_URL)
                )
        );
        log.warn(
                "product-management product delete task failure id={} owner={} store={} skuParent={} scheduled={} retryCount={} error={}",
                task.getId(),
                task.getOwnerUserId(),
                task.getStoreCode(),
                task.getSkuParent(),
                scheduled,
                task.getRetryCount(),
                shrink(exception.getMessage()),
                exception
        );
        record.setDraftSnapshot(draft);
        record.setSyncStatus("draft");
        record.setNote(scheduled ? retryMessage : manualCheckMessage);
        record.setPublishTask(productPublishTaskViewBuilder.build(task, false));
        appendRecentAction(record, "product-delete", task.getStatus(), record.getNote(), task.getCurrentSiteCode(), draft);
        productWorkbenchRecordStore.put(workbenchKey(task), record);
    }

    private void scheduleProductDeleteUnhandledFailure(
            ProductPublishTaskRecord task,
            String previousStatus,
            RuntimeException exception
    ) {
        ProductMasterSnapshotView baseline = readTaskSnapshot(task.getBaselineJson());
        ProductMasterSnapshotView draft = readTaskSnapshot(task.getDraftJson());
        ProductWorkbenchRecord record = buildTaskWorkbenchRecord(
                baseline,
                draft,
                "draft",
                "商品删除正在后台自动处理。"
        );
        handleProductDeleteFailure(
                task,
                record,
                draft,
                Map.of(),
                List.of("已从状态 " + previousStatus + " 执行商品删除。"),
                exception instanceof IllegalStateException
                        ? (IllegalStateException) exception
                        : new IllegalStateException(exception.getMessage(), exception)
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
        if (isProductDeleteBackgroundTask(task)) {
            return null;
        }
        String actionType = "publish-current";
        ProductWorkbenchRecord existingRecord = productWorkbenchRecordStore.get(
                workbenchKey(task)
        );
        ProductPublishTaskView existingTask = existingRecord != null ? existingRecord.getPublishTask() : null;
        if (
                existingRecord != null
                        && existingTask != null
                        && Objects.equals(existingTask.getTaskId(), task.getId())
        ) {
            return finalizeWorkbenchView(
                    task.getOwnerUserId(),
                    actionType,
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
        appendRecentAction(record, actionType, task.getStatus(), publishTaskMessage(task), task.getCurrentSiteCode(), draft);
        return finalizeWorkbenchView(
                task.getOwnerUserId(),
                actionType,
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

    private void ensureNoForegroundBlockingPublishTaskForCommand(ProductMasterFetchCommand command, String message) {
        if (isPublishTaskWorkerMode()) {
            return;
        }
        recoverStaleRunningProductPublishTasks();
        Long productMasterId = resolveProductMasterId(command);
        if (productMasterId == null) {
            return;
        }
        requirePublishCommandService().ensureNoForegroundBlockingActiveTask(productMasterId, message);
    }

    private Long resolveProductMasterId(ProductMasterFetchCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            return null;
        }
        String storeCode = normalize(command.getStoreCode());
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        ProductMasterIdentityRecord identity = resolveCurrentProductIdentity(command, storeCode);
        return identity == null ? null : identity.getProductMasterId();
    }

    private ProductMasterIdentityRecord resolveCurrentProductIdentity(
            ProductMasterFetchCommand command,
            String storeCode
    ) {
        if (command == null || command.getOwnerUserId() == null || !StringUtils.hasText(storeCode)) {
            return null;
        }
        String partnerSku = normalize(command.getPartnerSku());
        if (StringUtils.hasText(partnerSku)) {
            ProductMasterIdentityRecord byPartnerSku = productManagementMapper.selectProductMasterIdentityByStorePartnerSku(
                    command.getOwnerUserId(),
                    storeCode,
                    partnerSku
            );
            if (byPartnerSku != null) {
                return byPartnerSku;
            }
        }
        String skuParent = normalize(command.getSkuParent());
        if (!StringUtils.hasText(skuParent)) {
            return null;
        }
        return productManagementMapper.selectProductMasterIdentityByStoreSkuParent(
                command.getOwnerUserId(),
                storeCode,
                skuParent
        );
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

    private String publishCurrentTaskIdempotencyKey(
            ProductMasterActionCommand command,
            ProductMasterSnapshotView requestedSnapshot,
            String currentSiteCode,
            String draftHash
    ) {
        String partnerSku = firstNonBlank(
                normalize(command == null ? null : command.getPartnerSku()),
                requestedSnapshot == null || requestedSnapshot.getIdentity() == null
                        ? null
                        : textValue(requestedSnapshot.getIdentity().get("partnerSku"))
        );
        String skuParent = firstNonBlank(
                normalize(command == null ? null : command.getSkuParent()),
                requestedSnapshot == null || requestedSnapshot.getIdentity() == null
                        ? null
                        : textValue(requestedSnapshot.getIdentity().get("skuParent"))
        );
        return "publish:"
                + (command == null ? null : command.getOwnerUserId())
                + ":"
                + normalize(command == null ? null : command.getStoreCode())
                + ":"
                + normalize(currentSiteCode)
                + ":"
                + firstNonBlank(partnerSku, skuParent)
                + ":"
                + normalize(draftHash);
    }

    private Map<String, Object> buildProductDeleteTaskRequestPayload(
            ProductMasterFetchCommand command,
            ProductMasterIdentityRecord identity,
            String currentSiteCode
    ) {
        return buildProductDeleteTaskRequestPayload(command, identity, currentSiteCode, null);
    }

    private Map<String, Object> buildProductDeleteTaskRequestPayload(
            ProductMasterFetchCommand command,
            ProductMasterIdentityRecord identity,
            String currentSiteCode,
            ProductListingDraftCommand rebuildListingDraft
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String partnerSku = identity == null
                ? normalize(command.getPartnerSku())
                : firstNonBlank(normalize(identity.getPartnerSku()), normalize(command.getPartnerSku()));
        String pskuCode = identity == null
                ? normalize(command.getPskuCode())
                : firstNonBlank(normalize(identity.getPskuCode()), normalize(command.getPskuCode()));
        payload.put("ownerUserId", command.getOwnerUserId());
        payload.put("storeCode", normalize(identity == null ? command.getStoreCode() : identity.getStoreCode()));
        payload.put("skuParent", normalize(identity == null ? command.getSkuParent() : identity.getSkuParent()));
        payload.put("partnerSku", partnerSku);
        payload.put("pskuCode", pskuCode);
        payload.put("currentSiteCode", normalize(currentSiteCode));
        payload.put("action", "product-delete");
        if (rebuildListingDraft != null) {
            payload.put("rebuildAction", "product-rebuild");
            payload.put("rebuildSourceProductMasterId", rebuildListingDraft.getRebuildSourceProductMasterId());
            payload.put("rebuildListingDraft", rebuildListingDraft);
        }
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
        return buildTaskResultJson(status, requestCounts, warnings, Map.of());
    }

    private String buildTaskResultJson(
            String status,
            Map<String, Integer> requestCounts,
            List<String> warnings,
            Map<String, Object> extras
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("requestCounts", requestCounts == null ? Map.of() : requestCounts);
        payload.put("warnings", warnings == null ? List.of() : warnings);
        if (extras != null) {
            payload.putAll(extras);
        }
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

    private boolean isProductDeleteBackgroundTask(ProductPublishTaskRecord task) {
        if (requirePublishCommandService().isProductDeleteTask(task)) {
            return true;
        }
        return isTaskRequestAction(task, "product-delete")
                || isTaskIdempotencyPrefix(task, "delete:")
                || isTaskChangedDomain(task, "delete")
                || isTaskSnapshotMode(task, "product-delete-task");
    }

    private boolean isTaskRequestAction(ProductPublishTaskRecord task, String expectedAction) {
        if (!StringUtils.hasText(expectedAction)) {
            return false;
        }
        JsonNode request = readTaskRequestNode(task);
        return expectedAction.equalsIgnoreCase(normalize(text(request, "action")));
    }

    private boolean isTaskIdempotencyPrefix(ProductPublishTaskRecord task, String prefix) {
        if (task == null || !StringUtils.hasText(prefix)) {
            return false;
        }
        String idempotencyKey = normalize(task.getIdempotencyKey());
        return idempotencyKey != null && idempotencyKey.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    private boolean isTaskChangedDomain(ProductPublishTaskRecord task, String expectedDomain) {
        if (task == null || !StringUtils.hasText(expectedDomain) || !StringUtils.hasText(task.getChangedDomainsJson())) {
            return false;
        }
        try {
            JsonNode domains = objectMapper.readTree(task.getChangedDomainsJson());
            if (domains.isArray()) {
                for (JsonNode domain : domains) {
                    if (expectedDomain.equalsIgnoreCase(normalize(domain.asText()))) {
                        return true;
                    }
                }
            }
            return expectedDomain.equalsIgnoreCase(normalize(text(domains, "domain")))
                    || expectedDomain.equalsIgnoreCase(normalize(text(domains, "action")));
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isTaskSnapshotMode(ProductPublishTaskRecord task, String expectedMode) {
        if (!StringUtils.hasText(expectedMode)) {
            return false;
        }
        return expectedMode.equalsIgnoreCase(normalize(readTaskSnapshotMode(task == null ? null : task.getDraftJson())))
                || expectedMode.equalsIgnoreCase(normalize(readTaskSnapshotMode(task == null ? null : task.getBaselineJson())));
    }

    private String readTaskSnapshotMode(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson)) {
            return null;
        }
        try {
            JsonNode snapshot = objectMapper.readTree(snapshotJson);
            return text(snapshot, "mode");
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode readTaskRequestNode(ProductPublishTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getRequestJson())) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(task.getRequestJson());
        } catch (Exception exception) {
            return MissingNode.getInstance();
        }
    }

    private JsonNode readTaskResultNode(ProductPublishTaskRecord task) {
        if (task == null || !StringUtils.hasText(task.getResultJson())) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(task.getResultJson());
        } catch (Exception exception) {
            return MissingNode.getInstance();
        }
    }

    private String productDeleteStage(ProductPublishTaskRecord task) {
        JsonNode result = readTaskResultNode(task);
        return text(result, "stage");
    }

    private ProductMasterSnapshotView readProductDeletePreDeleteSnapshot(ProductPublishTaskRecord task) {
        JsonNode result = readTaskResultNode(task);
        JsonNode snapshotNode = result == null ? null : result.path("preDeleteSnapshot");
        if (snapshotNode == null || snapshotNode.isMissingNode() || snapshotNode.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(snapshotNode, ProductMasterSnapshotView.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isProductDeleteAfterUnmapStage(String stage) {
        String normalized = normalize(stage);
        return "unmap_submitted".equalsIgnoreCase(normalized)
                || isProductDeleteAfterDeleteStage(normalized);
    }

    private boolean isProductDeleteAfterDeleteStage(String stage) {
        String normalized = normalize(stage);
        return "delete_submitted".equalsIgnoreCase(normalized)
                || "current_psku_delete_submitted".equalsIgnoreCase(normalized)
                || "synced".equalsIgnoreCase(normalized);
    }

    private boolean isNoonProductMissingAfterDelete(Throwable exception) {
        String details = shrink(exception == null ? null : exception.getMessage()).toLowerCase(Locale.ROOT);
        return details.contains("404")
                || details.contains("not found")
                || details.contains("not_found")
                || details.contains("not exist")
                || details.contains("does not exist")
                || details.contains("invalid sku_parents")
                || details.contains("invalid sku parents")
                || details.contains("invalid (deleted or belong to another partner) psku codes")
                || details.contains("不存在");
    }

    private boolean isNoonDeleteTargetInvalidOrDeleted(Throwable exception) {
        String details = shrink(exception == null ? null : exception.getMessage()).toLowerCase(Locale.ROOT);
        return details.contains("invalid (deleted or belong to another partner) psku codes")
                || (details.contains("invalid") && details.contains("psku") && details.contains("deleted"))
                || details.contains("invalid sku_parents")
                || details.contains("invalid sku parents");
    }

    private boolean isNoonPskuUnmapRequired(Throwable exception) {
        String details = shrink(exception == null ? null : exception.getMessage()).toLowerCase(Locale.ROOT);
        return details.contains("must be unmapped")
                || details.contains("unmapped before deleting")
                || details.contains("unmap before delete")
                || details.contains("先解除映射");
    }

    private boolean shouldSkipNoonProductUnmap(ProductMasterSnapshotView preDeleteSnapshot) {
        if (preDeleteSnapshot == null) {
            return true;
        }
        if (preDeleteSnapshot.getIdentity() != null
                && (StringUtils.hasText(textValue(preDeleteSnapshot.getIdentity().get("catalogSku")))
                || StringUtils.hasText(textValue(preDeleteSnapshot.getIdentity().get("childSku")))
                || StringUtils.hasText(textValue(preDeleteSnapshot.getIdentity().get("noonProductCode"))))) {
            return false;
        }
        if (preDeleteSnapshot.getSiteOffers() != null) {
            for (Map<String, Object> siteOffer : preDeleteSnapshot.getSiteOffers()) {
                if (siteOffer == null) {
                    continue;
                }
                if (StringUtils.hasText(textValue(siteOffer.get("catalogSku")))
                        || StringUtils.hasText(textValue(siteOffer.get("childSku")))
                        || StringUtils.hasText(textValue(siteOffer.get("noonProductCode")))
                        || StringUtils.hasText(textValue(siteOffer.get("sku")))) {
                    return false;
                }
            }
        }
        return true;
    }

    private ProductDeleteNoonPresence findProductDeleteNoonPresenceAfterDelete(
            NoonSession session,
            ProductPublishTaskRecord task,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        List<ProductProjectSiteContext> projectSites = loadProjectSiteContexts(
                session,
                task.getOwnerUserId(),
                store,
                warnings
        );
        for (ProductProjectSiteContext projectSite : projectSites) {
            ProductDeleteNoonPresence presence = findProductDeleteNoonPresenceInOfferList(
                    session,
                    task,
                    projectSite
            );
            if (presence.isPresent()) {
                return presence;
            }
        }
        return ProductDeleteNoonPresence.absent();
    }

    private ProductDeleteNoonPresence findProductDeleteNoonPresenceInOfferList(
            NoonSession session,
            ProductPublishTaskRecord task,
            ProductProjectSiteContext projectSite
    ) {
        String storeCode = firstNonBlank(projectSite.getStoreCode(), task.getStoreCode());
        requireText(storeCode, "商品删除回查缺少 Noon 店铺编码。");
        NoonSession siteSession = session.withStoreCode(storeCode);
        int page = 1;
        int pageCount = 1;
        while (page <= pageCount) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("page", page);
            body.put("per_page", PRODUCT_DELETE_VERIFY_OFFER_PAGE_SIZE);
            body.put("noon_store_code", storeCode);
            body.put("noonChannelType", "noon");
            JsonNode root = productNoonAdapter.postJson(siteSession, NoonProductGateway.OFFER_LIST_NOON_URL, body, true);
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("Noon 商品删除回查失败：" + root.path("error").asText());
            }
            JsonNode dataNode = root.path("data");
            JsonNode hitsNode = dataNode.path("hits");
            if (hitsNode.isArray()) {
                for (JsonNode hitNode : hitsNode) {
                    ProductDeleteNoonPresence presence = matchProductDeleteNoonPresence(task, projectSite, hitNode);
                    if (presence.isPresent()) {
                        return presence;
                    }
                }
            }
            int currentHitCount = hitsNode.isArray() ? hitsNode.size() : 0;
            int total = dataNode.path("total").asInt(currentHitCount);
            if (total <= 0) {
                total = currentHitCount;
            }
            pageCount = Math.max((int) Math.ceil(total / (double) PRODUCT_DELETE_VERIFY_OFFER_PAGE_SIZE), 1);
            page++;
        }
        return ProductDeleteNoonPresence.absent();
    }

    private ProductDeleteNoonPresence matchProductDeleteNoonPresence(
            ProductPublishTaskRecord task,
            ProductProjectSiteContext projectSite,
            JsonNode hitNode
    ) {
        String targetPskuCode = normalize(task.getPskuCode());
        if (StringUtils.hasText(targetPskuCode)
                && equalsAnyNoonField(targetPskuCode, hitNode, "psku_code", "pskuCode")) {
            return ProductDeleteNoonPresence.present(projectSite, "pskuCode", targetPskuCode, hitNode);
        }

        String targetPartnerSku = normalize(task.getPartnerSku());
        if (StringUtils.hasText(targetPartnerSku)
                && equalsAnyNoonField(targetPartnerSku, hitNode, "partner_sku", "partnerSku", "psku")) {
            return ProductDeleteNoonPresence.present(projectSite, "partnerSku", targetPartnerSku, hitNode);
        }

        String targetSkuParent = normalize(task.getSkuParent());
        if (StringUtils.hasText(targetSkuParent)
                && equalsAnyNoonField(targetSkuParent, hitNode, "csku_parent", "zsku_parent", "sku_parent", "skuParent")) {
            return ProductDeleteNoonPresence.present(projectSite, "skuParent", targetSkuParent, hitNode);
        }

        return ProductDeleteNoonPresence.absent();
    }

    private boolean shouldDeleteCurrentNoonPresencePsku(ProductPublishTaskRecord task, ProductDeleteNoonPresence presence) {
        if (presence == null || !presence.isPresent() || !StringUtils.hasText(presence.hitPskuCode())) {
            return false;
        }
        String currentPskuCode = normalize(presence.hitPskuCode());
        String taskPskuCode = normalize(task == null ? null : task.getPskuCode());
        if (StringUtils.hasText(taskPskuCode) && taskPskuCode.equalsIgnoreCase(currentPskuCode)) {
            return false;
        }
        JsonNode previousPresence = readTaskResultNode(task).path("noonPresence");
        String previousHitPskuCode = firstNonBlank(
                text(previousPresence, "hitPskuCode"),
                text(previousPresence, "pskuCode")
        );
        if ("current_psku_delete_submitted".equalsIgnoreCase(productDeleteStage(task))
                && StringUtils.hasText(previousHitPskuCode)
                && previousHitPskuCode.equalsIgnoreCase(currentPskuCode)) {
            return false;
        }
        return true;
    }

    private boolean equalsAnyNoonField(String expected, JsonNode node, String... fields) {
        if (!StringUtils.hasText(expected) || fields == null) {
            return false;
        }
        for (String field : fields) {
            String actual = normalize(text(node, field));
            if (StringUtils.hasText(actual) && expected.equalsIgnoreCase(actual)) {
                return true;
            }
        }
        return false;
    }

    private void addLocalWarning(List<String> warnings, String warning) {
        if (warnings == null || !StringUtils.hasText(warning) || warnings.contains(warning)) {
            return;
        }
        warnings.add(warning);
    }

    private Map<String, Object> productDeleteResultExtras(
            String stage,
            ProductMasterSnapshotView preDeleteSnapshot,
            String deleteUrl
    ) {
        return productDeleteResultExtras(stage, preDeleteSnapshot, deleteUrl, null);
    }

    private Map<String, Object> productDeleteResultExtras(
            String stage,
            ProductMasterSnapshotView preDeleteSnapshot,
            String deleteUrl,
            ProductDeleteNoonPresence noonPresence
    ) {
        Map<String, Object> extras = new LinkedHashMap<>();
        putIfNotNull(extras, "stage", stage);
        putIfNotNull(extras, "unmapUrl", NoonProductGateway.PSKU_UNMAP_URL);
        putIfNotNull(extras, "deleteUrl", deleteUrl);
        putIfNotNull(extras, "preDeleteSnapshot", preDeleteSnapshot);
        if (noonPresence != null && noonPresence.isPresent()) {
            extras.put("noonPresence", noonPresence.toResultMap());
        }
        return extras;
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

    private String workbenchKey(ProductMasterFetchCommand command) {
        if (command == null) {
            return workbenchKey(null, null, null, null);
        }
        return workbenchKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent(), command.getPartnerSku());
    }

    private String workbenchKey(ProductPublishTaskRecord task) {
        if (task == null) {
            return workbenchKey(null, null, null, null);
        }
        return workbenchKey(task.getOwnerUserId(), task.getStoreCode(), task.getSkuParent(), task.getPartnerSku());
    }

    private String workbenchKey(Long ownerUserId, String storeCode, String skuParent) {
        return workbenchKey(ownerUserId, storeCode, skuParent, null);
    }

    private String workbenchKey(Long ownerUserId, String storeCode, String skuParent, String partnerSku) {
        return ownerUserId
                + "::"
                + normalize(storeCode)
                + "::"
                + firstNonBlank(normalize(partnerSku), normalize(skuParent));
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

    private NoonSession openNoonProductDeleteSession(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        return openNoonProductDeleteSession(command, store, warnings, "删除商品");
    }

    private NoonSession openNoonProductDeleteSession(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            List<String> warnings,
            String actionLabel
    ) {
        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法" + actionLabel + "。");
        }
        ProductNoonCredential credential = productNoonCredentialResolver.resolve(command, store, owner);
        requireText(credential.getNoonUser(), "当前店铺还没有 Noon 账号上下文，暂时不能" + actionLabel + "。");
        requireText(credential.getProjectCode(), "当前店铺缺少 Noon projectCode，暂时不能" + actionLabel + "。");
        NoonSession session = openProductNoonSession(owner.getId(), credential, normalize(store.getStoreCode()));
        String resolvedProjectCode = resolveProjectCode(session, credential.getProjectCode(), store, warnings);
        return session.withProjectCode(resolvedProjectCode).withStoreCode(normalize(store.getStoreCode()));
    }

    private NoonSession openProductNoonSession(
            Long ownerUserId,
            ProductNoonCredential credential,
            String storeCode
    ) {
        return productNoonAdapter.loginWithPersistedCookie(
                ownerUserId,
                credential.getNoonUser(),
                credential.getNoonCookie(),
                credential.getProjectCode(),
                storeCode
        );
    }

    private void executeNoonProductUnmap(
            ProductPublishTaskRecord task,
            NoonSession session,
            ProductMasterSnapshotView preDeleteSnapshot
    ) {
        ObjectNode body = productDeleteRequestBuilder.buildUnmapBody(task, preDeleteSnapshot);
        postNoonProductOperationWrite(session, NoonProductGateway.PSKU_UNMAP_URL, body, "Noon PSKU 解除映射");
    }

    private void executeNoonProductDelete(
            ProductPublishTaskRecord task,
            NoonSession session,
            ProductMasterSnapshotView preDeleteSnapshot
    ) {
        ObjectNode body = productDeleteRequestBuilder.buildDeleteBody(task, preDeleteSnapshot);
        postNoonProductOperationWrite(session, NoonProductGateway.PSKU_DELETE_URL, body, "Noon 商品删除");
    }

    private void executeNoonProductDelete(NoonSession session, String pskuCode) {
        ObjectNode body = productDeleteRequestBuilder.buildDeleteBody(pskuCode);
        postNoonProductOperationWrite(session, NoonProductGateway.PSKU_DELETE_URL, body, "Noon 商品删除");
    }

    private JsonNode postNoonProductOperationWrite(
            NoonSession session,
            String url,
            JsonNode body,
            String actionLabel
    ) {
        JsonNode response = productNoonAdapter.postWriteJson(session, url, body, true);
        NoonWriteResponseValidator.throwIfFailure(response, actionLabel);
        return response == null ? MissingNode.getInstance() : response;
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

    private static final class ProductDeleteNoonPresence {
        private final boolean present;
        private final String storeCode;
        private final String siteCode;
        private final String matchedField;
        private final String matchedValue;
        private final String hitPskuCode;
        private final String hitPartnerSku;
        private final String hitSkuParent;

        private ProductDeleteNoonPresence(
                boolean present,
                String storeCode,
                String siteCode,
                String matchedField,
                String matchedValue,
                String hitPskuCode,
                String hitPartnerSku,
                String hitSkuParent
        ) {
            this.present = present;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
            this.matchedField = matchedField;
            this.matchedValue = matchedValue;
            this.hitPskuCode = hitPskuCode;
            this.hitPartnerSku = hitPartnerSku;
            this.hitSkuParent = hitSkuParent;
        }

        static ProductDeleteNoonPresence absent() {
            return new ProductDeleteNoonPresence(false, null, null, null, null, null, null, null);
        }

        static ProductDeleteNoonPresence present(
                ProductProjectSiteContext projectSite,
                String matchedField,
                String matchedValue,
                JsonNode hitNode
        ) {
            return new ProductDeleteNoonPresence(
                    true,
                    projectSite == null ? null : projectSite.getStoreCode(),
                    projectSite == null ? null : projectSite.getSite(),
                    matchedField,
                    matchedValue,
                    firstNonBlank(text(hitNode, "psku_code"), text(hitNode, "pskuCode")),
                    firstNonBlank(text(hitNode, "partner_sku"), text(hitNode, "partnerSku"), text(hitNode, "psku")),
                    firstNonBlank(
                            text(hitNode, "csku_parent"),
                            text(hitNode, "zsku_parent"),
                            text(hitNode, "sku_parent"),
                            text(hitNode, "skuParent")
                    )
            );
        }

        boolean isPresent() {
            return present;
        }

        String toWarning() {
            return "Noon 后台仍能在 "
                    + firstNonBlank(siteCode, storeCode, "当前站点")
                    + " 的商品列表查到 "
                    + matchedField
                    + "="
                    + matchedValue
                    + "，系统暂不清理本地商品目录。";
        }

        Map<String, Object> toResultMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("present", present);
            putIfHasText(result, "storeCode", storeCode);
            putIfHasText(result, "siteCode", siteCode);
            putIfHasText(result, "matchedField", matchedField);
            putIfHasText(result, "matchedValue", matchedValue);
            putIfHasText(result, "hitPskuCode", hitPskuCode);
            putIfHasText(result, "hitPartnerSku", hitPartnerSku);
            putIfHasText(result, "hitSkuParent", hitSkuParent);
            return result;
        }

        String hitPskuCode() {
            return hitPskuCode;
        }

        private static String text(JsonNode node, String field) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            JsonNode valueNode = node.path(field);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                return null;
            }
            String value = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        private static String firstNonBlank(String... values) {
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

        private static void putIfHasText(Map<String, Object> target, String key, String value) {
            if (StringUtils.hasText(value)) {
                target.put(key, value);
            }
        }
    }

}
