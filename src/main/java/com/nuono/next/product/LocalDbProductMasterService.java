package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.product.publish.ProductPublishCommandService;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateCommand;
import com.nuono.next.product.publish.ProductPublishCommandService.ProductPublishTaskCreateResult;
import com.nuono.next.product.publish.ProductPublishTaskFenceLostException;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private static final String WHOAMI_URL =
            NoonProductGateway.WHOAMI_URL;
    private static final String PROJECT_LIST_URL =
            NoonProductGateway.PROJECT_LIST_URL;
    private static final String STORE_LIST_URL =
            NoonProductGateway.STORE_LIST_URL;
    private static final String ZSKU_RETRIEVE_URL =
            NoonProductGateway.ZSKU_RETRIEVE_URL;
    private static final String GROUP_CURRENT_URL_PREFIX =
            NoonProductGateway.GROUP_CURRENT_URL_PREFIX;
    private static final String GROUP_DETAIL_URL =
            NoonProductGateway.GROUP_DETAIL_URL;
    private static final String GROUP_LIST_URL =
            NoonProductGateway.GROUP_LIST_URL;
    private static final String VARIANT_INFO_URL =
            NoonProductGateway.VARIANT_INFO_URL;
    private static final String PRICING_INFO_URL =
            NoonProductGateway.PRICING_INFO_URL;
    private static final String STOCK_INFO_URL =
            NoonProductGateway.STOCK_INFO_URL;
    private static final String PRICING_METHOD_MANUAL = "manual";
    private static final Set<String> EXPLICIT_CLEARABLE_OFFER_FIELDS = Set.of(
            "salePrice",
            "saleStart",
            "saleEnd"
    );
    private static final ThreadLocal<Boolean> PUBLISH_TASK_WORKER_MODE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOON_OFFER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProductManagementMapper productManagementMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final LocalDbStoreInitializationService localDbStoreInitializationService;
    private final ProductAttributeTemplateService productAttributeTemplateService;
    private final ProductGroupPublishService productGroupPublishService;
    private final ProductNoonCatalogContentService productNoonCatalogContentService;
    private final ProductDetailBaselineBackfillService productDetailBaselineBackfillService;
    private final ProductPublishCommandService productPublishCommandService;
    private final ProductPublishChangedDomainComparator productPublishChangedDomainComparator;
    private final ProductPublishUnsupportedChangesDetector productPublishUnsupportedChangesDetector;
    private final ProductPublishSupportedSnapshotBuilder productPublishSupportedSnapshotBuilder;
    private final ProductPublishOfferWriter productPublishOfferWriter;
    private final ProductPublishSharedZskuWriter productPublishSharedZskuWriter;
    private final ProductPublishWriteService productPublishWriteService;
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
    private final ConcurrentMap<String, ResolvedProjectCodeCacheEntry> resolvedProjectCodeCache = new ConcurrentHashMap<>();

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
        this.storeSyncMapper = storeSyncMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.localDbStoreInitializationService = localDbStoreInitializationService;
        this.productAttributeTemplateService = productAttributeTemplateService;
        this.productGroupPublishService = productGroupPublishService;
        this.productNoonCatalogContentService = productNoonCatalogContentService;
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
                        return !LocalDbProductMasterService.this.objectMapper.valueToTree(sharedZskuComparableView(draft))
                                .equals(LocalDbProductMasterService.this.objectMapper.valueToTree(sharedZskuComparableView(baseline)));
                    }

                    @Override
                    public boolean groupChanged(ProductMasterSnapshotView draft, ProductMasterSnapshotView baseline) {
                        return !LocalDbProductMasterService.this.objectMapper.valueToTree(publishComparableGroup(draft))
                                .equals(LocalDbProductMasterService.this.objectMapper.valueToTree(publishComparableGroup(baseline)));
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
                        return siteOfferComparableList(draft, currentSiteCode, false);
                    }

                    @Override
                    public Map<String, Map<String, Object>> baselineOffers(ProductMasterSnapshotView baseline) {
                        return siteOfferMap(baseline.getSiteOffers());
                    }

                    @Override
                    public boolean siteOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
                        return !LocalDbProductMasterService.this.objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                                .equals(LocalDbProductMasterService.this.objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)));
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
        OperationalKeys operationalKeys = resolveOperationalKeysFromProjection(
                command.getOwnerUserId(),
                storeCode,
                skuParent,
                partnerSku,
                pskuCode
        );
        partnerSku = operationalKeys.getPartnerSku();
        pskuCode = operationalKeys.getPskuCode();

        List<String> missingOperationalKeys = collectMissingOperationalKeys(partnerSku, pskuCode);
        view.setMissingOperationalKeys(missingOperationalKeys);
        view.setDegraded(!missingOperationalKeys.isEmpty());
        if (view.isDegraded()) {
            view.getWarnings().add(
                    "当前索引缺少 "
                            + String.join(" / ", missingOperationalKeys)
                            + "，本次会先按降级模式打开详情，站点价格或库存信息可能不完整。"
            );
        }

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法读取商品主档快照。");
        }

        String noonUser = firstNonBlank(
                normalize(command.getNoonUser()),
                owner.getNoonPartnerProjectUser(),
                owner.getNoonPartnerUser()
        );
        requireText(noonUser, "当前店铺还没有 Noon 账号上下文，请先绑定店铺账号或手动填写 Noon 登录账号。");
        String noonPassword = firstNonBlank(
                normalize(command.getNoonPassword()),
                normalize(owner.getNoonPartnerPwd())
        );
        requireText(noonPassword, "当前店铺还没有 Noon 登录密码，请先把密码写入数据库。");

        String projectCode = firstNonBlank(store.getProjectCode(), owner.getNoonPartnerId());
        requireText(projectCode, "当前店铺缺少 Noon projectCode，无法读取真实商品主档。");

        long stageStartedAt = System.nanoTime();
        NoonSession session = productNoonAdapter.login(
                owner.getId(),
                noonUser,
                noonPassword,
                owner.getNoonPartnerCookie(),
                projectCode,
                storeCode
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "login", stageStartedAt);

        stageStartedAt = System.nanoTime();
        String resolvedProjectCode = resolveProjectCode(session, projectCode, store, view.getWarnings());
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "resolveProjectCode", stageStartedAt);
        session = session.withProjectCode(resolvedProjectCode).withStoreCode(storeCode);

        stageStartedAt = System.nanoTime();
        JsonNode whoamiNode = safeGet(
                session,
                WHOAMI_URL,
                false,
                view.getWarnings(),
                "读取 whoami 失败"
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "whoami", stageStartedAt);

        ObjectNode retrieveBody = objectMapper.createObjectNode();
        ArrayNode skuParentsNode = retrieveBody.putArray("skuParents");
        skuParentsNode.add(skuParent);
        retrieveBody.putArray("attributeCodes");
        stageStartedAt = System.nanoTime();
        JsonNode retrieveRoot = productNoonAdapter.postJson(session, ZSKU_RETRIEVE_URL, retrieveBody, true);
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "zsku.retrieve", stageStartedAt);
        JsonNode productNode = retrieveRoot.path(skuParent);
        if (productNode.isMissingNode() || productNode.isNull()) {
            throw new IllegalStateException("Noon 没有返回 skuParent=" + skuParent + " 的商品快照。");
        }

        JsonNode commonNode = productNode.path("attributes").path("common");
        JsonNode enNode = productNode.path("attributes").path("en");
        JsonNode arNode = productNode.path("attributes").path("ar");

        String productFulltype = text(commonNode, "product_fulltype");
        String brand = text(commonNode, "brand");
        String idPartner = text(commonNode, "id_partner");
        String resolvedSkuGroup = null;

        JsonNode fulltypeTemplateNode = MissingNode.getInstance();
        if (StringUtils.hasText(productFulltype)) {
            stageStartedAt = System.nanoTime();
            fulltypeTemplateNode = productAttributeTemplateService.loadTemplate(
                    session,
                    resolvedProjectCode,
                    storeCode,
                    productFulltype,
                    command.getOwnerUserId(),
                    view.getWarnings()
            );
            recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "fulltypeTemplate", stageStartedAt);
        }

        stageStartedAt = System.nanoTime();
        JsonNode groupCurrentNode = safeGet(
                session,
                GROUP_CURRENT_URL_PREFIX + skuParent,
                true,
                view.getWarnings(),
                "读取当前 group 失败"
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "group.current", stageStartedAt);
        if (groupCurrentNode.isObject()) {
            resolvedSkuGroup = text(groupCurrentNode, "sku_group");
        }

        JsonNode groupDetailNode = MissingNode.getInstance();
        if (StringUtils.hasText(resolvedSkuGroup)) {
            ObjectNode groupBody = objectMapper.createObjectNode();
            ArrayNode groupCodes = groupBody.putArray("zskuGroup");
            groupCodes.add(resolvedSkuGroup);
            stageStartedAt = System.nanoTime();
            groupDetailNode = safePost(
                    session,
                    GROUP_DETAIL_URL,
                    groupBody,
                    true,
                    view.getWarnings(),
                    "读取 group 详情失败"
            );
            recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "group.detail", stageStartedAt);
        }

        JsonNode groupParentAttributesNode = MissingNode.getInstance();
        ObjectNode groupParentAttributesBody = buildGroupParentAttributeFetchBody(groupDetailNode, resolvedSkuGroup);
        if (groupParentAttributesBody != null) {
            stageStartedAt = System.nanoTime();
            groupParentAttributesNode = safePost(
                    session,
                    ZSKU_RETRIEVE_URL,
                    groupParentAttributesBody,
                    true,
                    view.getWarnings(),
                    "读取 group 轴属性失败"
            );
            recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "group.parentAttributes", stageStartedAt);
        }

        JsonNode groupListNode = MissingNode.getInstance();
        if (StringUtils.hasText(productFulltype) && StringUtils.hasText(brand)) {
            ObjectNode groupListBody = objectMapper.createObjectNode();
            groupListBody.put("fulltype", productFulltype);
            groupListBody.put("brand", brand);
            stageStartedAt = System.nanoTime();
            groupListNode = safePost(
                    session,
                    GROUP_LIST_URL,
                    groupListBody,
                    true,
                    view.getWarnings(),
                    "读取候选 group 列表失败"
            );
            recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "group.list", stageStartedAt);
        }

        ObjectNode variantBody = objectMapper.createObjectNode();
        variantBody.put("zskuParent", skuParent);
        stageStartedAt = System.nanoTime();
        JsonNode variantInfoNode = safePost(
                session,
                VARIANT_INFO_URL,
                variantBody,
                true,
                view.getWarnings(),
                "读取尺码与变体信息失败"
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "variant.info", stageStartedAt);

        stageStartedAt = System.nanoTime();
        List<ProjectSiteContext> projectSites = loadProjectSiteContexts(
                session,
                command.getOwnerUserId(),
                store,
                view.getWarnings()
        );
        recordFetchStage(timingEntries, timingBreakdownMs, traceLabel, reason, "projectSites", stageStartedAt);

        stageStartedAt = System.nanoTime();
        ProjectSiteFetchResult siteFetchResult;
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
                buildStoreContext(
                        owner,
                        store,
                        noonUser,
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
        if (view.isDegraded()) {
            view.setMessage(
                    "已读取 "
                            + (store.getProjectName() != null ? store.getProjectName() : storeCode)
                            + " 的 Noon 商品详情，但当前索引还缺少 "
                            + String.join(" / ", missingOperationalKeys)
                            + "，站点经营数据先按降级模式展示。"
            );
        } else {
            view.setMessage(
                    "已读取 "
                            + (store.getProjectName() != null ? store.getProjectName() : storeCode)
                            + " 的真实 Noon 商品主档快照，并汇总 "
                            + projectSites.size()
                            + " 个站点经营面。"
            );
        }
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

        ProductMasterWorkbenchView missingWorkbench = productWorkbenchViewAssembler.buildLocalBaselineMissingWorkbench(command);
        productWorkbenchViewAssembler.applyMissingBaselineBackfillPrompt(
                missingWorkbench,
                enqueueDetailBaselineBackfill(command, "open-missing-baseline")
        );
        return missingWorkbench;
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
            brands = productManagementMapper.selectBrandProjectionClassificationOptions(
                    command.getOwnerUserId(),
                    storeCode,
                    brandQuery,
                    limit
            );
            usedProjectionFallback = !brands.isEmpty();
        }
        if (fulltypes.isEmpty() && !StringUtils.hasText(fulltypeQuery)) {
            fulltypes = productManagementMapper.selectFulltypeProjectionClassificationOptions(
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
        ProductWorkbenchRecord record = new ProductWorkbenchRecord();
        record.setBaselineSnapshot(copySnapshot(snapshot));
        record.setDraftSnapshot(copySnapshot(snapshot));
        record.setSyncStatus("synced");
        record.setLastSyncedAt(extractFetchedAt(snapshot));
        record.setNote(
                snapshot != null && snapshot.isDegraded()
                        ? "已按降级模式打开详情；共享主档可继续查看和编辑，站点经营数据待索引补齐后再完整同步。"
                        : "已读取商品详情基线，可以开始调整共享主档和站点经营面。"
        );
        return record;
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
        return publishChangedFieldsMatch(
                toPublishComparableScopedJson(baseline, siteCode),
                toPublishComparableScopedJson(draft, siteCode),
                toPublishComparableScopedJson(noonCurrent, siteCode)
        );
    }

    private boolean publishChangedFieldsMatch(JsonNode baseline, JsonNode draft, JsonNode noonCurrent) {
        JsonNode baselineNode = comparableNode(baseline);
        JsonNode draftNode = comparableNode(draft);
        JsonNode noonNode = comparableNode(noonCurrent);
        if (baselineNode.equals(draftNode)) {
            return true;
        }
        if (baselineNode.isObject() || draftNode.isObject() || noonNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(baselineNode, fieldNames);
            collectFieldNames(draftNode, fieldNames);
            collectFieldNames(noonNode, fieldNames);
            for (String fieldName : fieldNames) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(fieldName),
                        draftNode.path(fieldName),
                        noonNode.path(fieldName)
                )) {
                    return false;
                }
            }
            return true;
        }
        if (baselineNode.isArray() || draftNode.isArray() || noonNode.isArray()) {
            int maxSize = Math.max(baselineNode.size(), Math.max(draftNode.size(), noonNode.size()));
            for (int index = 0; index < maxSize; index++) {
                if (!publishChangedFieldsMatch(
                        baselineNode.path(index),
                        draftNode.path(index),
                        noonNode.path(index)
                )) {
                    return false;
                }
            }
            return true;
        }
        return draftNode.equals(noonNode);
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
        if (snapshot == null) {
            return copySnapshot(fallback);
        }
        ProductMasterSnapshotView sanitized = copySnapshot(snapshot);
        sanitized.setMode("local-db");
        sanitized.setReady(true);
        if (sanitized.getWarnings() == null) {
            sanitized.setWarnings(new ArrayList<>());
        }
        sanitized.setWarnings(userVisibleWarnings(sanitized.getWarnings()));
        if (sanitized.getMissingCoreTables() == null) {
            sanitized.setMissingCoreTables(new ArrayList<>());
        }
        if (sanitized.getMissingOperationalKeys() == null) {
            sanitized.setMissingOperationalKeys(new ArrayList<>());
        }
        hydrateProjectionOnlyFields(sanitized, fallback);
        return sanitized;
    }

    private void copySnapshotFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        if (target == null || source == null) {
            return;
        }
        target.setMode(source.getMode());
        target.setReady(source.isReady());
        target.setDegraded(source.isDegraded());
        target.setMessage(source.getMessage());
        target.setWarnings(userVisibleWarnings(source.getWarnings()));
        target.setMissingCoreTables(new ArrayList<>(source.getMissingCoreTables()));
        target.setMissingOperationalKeys(new ArrayList<>(source.getMissingOperationalKeys()));
        target.setStoreContext(new LinkedHashMap<>(source.getStoreContext()));
        target.setIdentity(new LinkedHashMap<>(source.getIdentity()));
        target.setTaxonomy(new LinkedHashMap<>(source.getTaxonomy()));
        target.setContent(new LinkedHashMap<>(source.getContent()));
        target.setPlatformSignals(new LinkedHashMap<>(source.getPlatformSignals()));
        target.setKeyAttributes(copyRecordList(source.getKeyAttributes()));
        target.setGroup(new LinkedHashMap<>(source.getGroup()));
        target.setVariants(copyRecordList(source.getVariants()));
        target.setPricing(new LinkedHashMap<>(source.getPricing()));
        target.setStock(new LinkedHashMap<>(source.getStock()));
        target.setSiteOffers(copyRecordList(source.getSiteOffers()));
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        if (source == null) {
            return null;
        }
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private List<Map<String, Object>> copyRecordList(List<Map<String, Object>> source) {
        return source == null
                ? new ArrayList<>()
                : objectMapper.convertValue(source, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Map.class));
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

    private OperationalKeys resolveOperationalKeysFromProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        if (StringUtils.hasText(partnerSku) && StringUtils.hasText(pskuCode)) {
            return new OperationalKeys(partnerSku, pskuCode);
        }
        List<String> lookupWarnings = new ArrayList<>();
        ProductListSummaryView summary = productProjectionPersistenceService.loadProductListSummary(
                ownerUserId,
                storeCode,
                skuParent,
                lookupWarnings
        );
        if (summary == null || !summary.isReady()) {
            return new OperationalKeys(partnerSku, pskuCode);
        }
        return new OperationalKeys(
                firstNonBlank(partnerSku, summary.getPartnerSku()),
                firstNonBlank(pskuCode, summary.getPskuCode())
        );
    }

    private void hydrateSnapshotOperationalKeys(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot
    ) {
        if (snapshot == null) {
            return;
        }
        OperationalKeys operationalKeys = resolveOperationalKeysFromProjection(
                ownerUserId,
                storeCode,
                skuParent,
                textValue(snapshot.getIdentity().get("partnerSku")),
                textValue(snapshot.getIdentity().get("pskuCode"))
        );
        putIfNotBlank(snapshot.getIdentity(), "partnerSku", operationalKeys.getPartnerSku());
        putIfNotBlank(snapshot.getIdentity(), "pskuCode", operationalKeys.getPskuCode());
    }

    private void clearResolvedOperationalWarnings(ProductMasterSnapshotView snapshot) {
        if (snapshot == null) {
            return;
        }
        boolean hasPartnerSku = StringUtils.hasText(textValue(snapshot.getIdentity().get("partnerSku")));
        boolean hasPskuCode = StringUtils.hasText(textValue(snapshot.getIdentity().get("pskuCode")));
        if (snapshot.getMissingOperationalKeys() != null) {
            snapshot.getMissingOperationalKeys().removeIf((key) ->
                    (hasPartnerSku && "partnerSku".equalsIgnoreCase(String.valueOf(key)))
                            || (hasPskuCode && "pskuCode".equalsIgnoreCase(String.valueOf(key)))
            );
        }
        if (snapshot.getWarnings() != null) {
            snapshot.getWarnings().removeIf((warning) ->
                    (hasPartnerSku && hasPskuCode && warning.contains("当前索引缺少 partnerSku / pskuCode"))
                            || (hasPartnerSku && warning.contains("当前索引缺少 partnerSku"))
                            || (hasPskuCode && warning.contains("当前索引缺少 pskuCode"))
                            || (hasPartnerSku && warning.contains("当前详情还缺少 partnerSku"))
                            || isNonBlockingNoonRealtimeWarning(warning)
            );
        }
        if (snapshot.getMissingOperationalKeys() == null || snapshot.getMissingOperationalKeys().isEmpty()) {
            snapshot.setDegraded(false);
        }
    }

    private void hydrateWorkbenchAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductWorkbenchRecord record,
            List<String> warnings
    ) {
        if (record == null) {
            return;
        }
        hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, record.getBaselineSnapshot(), warnings);
        hydrateSnapshotAttributeDictionary(ownerUserId, storeCode, record.getDraftSnapshot(), warnings);
    }

    private void hydrateSnapshotAttributeDictionary(
            Long ownerUserId,
            String storeCode,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        if (snapshot == null || snapshot.getKeyAttributes() == null || snapshot.getKeyAttributes().isEmpty()) {
            return;
        }
        String projectCode = firstNonBlank(
                textValue(snapshot.getStoreContext().get("projectCode")),
                storeCode
        );
        String resolvedStoreCode = firstNonBlank(
                textValue(snapshot.getStoreContext().get("storeCode")),
                storeCode
        );
        String productFulltype = textValue(snapshot.getTaxonomy().get("productFulltype"));
        if (!StringUtils.hasText(projectCode)
                || !StringUtils.hasText(resolvedStoreCode)
                || !StringUtils.hasText(productFulltype)) {
            return;
        }
        JsonNode templateRoot = productAttributeTemplateService.loadTemplate(
                null,
                projectCode,
                resolvedStoreCode,
                productFulltype,
                ownerUserId,
                warnings
        );
        Map<String, JsonNode> dictionaryByCode = attributeDictionaryMap(templateRoot);
        if (dictionaryByCode.isEmpty()) {
            return;
        }
        for (Map<String, Object> attribute : snapshot.getKeyAttributes()) {
            String code = textValue(attribute.get("code"));
            JsonNode dictionaryNode = dictionaryByCode.get(normalizeAttributeCode(code));
            if (dictionaryNode == null || dictionaryNode.isMissingNode()) {
                continue;
            }
            List<Map<String, Object>> options = extractDictionaryOptions(dictionaryNode.path("options"));
            List<Map<String, Object>> unitOptions = extractDictionaryOptions(dictionaryNode.path("unitOptions"));
            if (!options.isEmpty() && !hasListValue(attribute.get("options"))) {
                attribute.put("options", options);
            }
            if (!unitOptions.isEmpty() && !hasListValue(attribute.get("unitOptions"))) {
                attribute.put("unitOptions", unitOptions);
            }
            if (StringUtils.hasText(text(dictionaryNode, "labelEn")) && !StringUtils.hasText(textValue(attribute.get("labelEn")))) {
                attribute.put("labelEn", text(dictionaryNode, "labelEn"));
            }
            if (StringUtils.hasText(text(dictionaryNode, "labelAr")) && !StringUtils.hasText(textValue(attribute.get("labelAr")))) {
                attribute.put("labelAr", text(dictionaryNode, "labelAr"));
            }
            if (StringUtils.hasText(text(dictionaryNode, "groupName")) && !StringUtils.hasText(textValue(attribute.get("groupName")))) {
                attribute.put("groupName", text(dictionaryNode, "groupName"));
            }
            if (!options.isEmpty() || !unitOptions.isEmpty()) {
                attribute.put("dictionarySource", firstNonBlank(text(dictionaryNode, "dictionarySource"), "official-template"));
            }
            String currentKind = normalize(textValue(attribute.get("kind")));
            if (!unitOptions.isEmpty()) {
                attribute.put("kind", "dimension");
            } else if (!options.isEmpty()) {
                attribute.put("kind", "select");
            } else if (!StringUtils.hasText(currentKind) && StringUtils.hasText(text(dictionaryNode, "kind"))) {
                attribute.put("kind", text(dictionaryNode, "kind"));
            }
        }
    }

    private boolean hasListValue(Object value) {
        return value instanceof List<?> && !((List<?>) value).isEmpty();
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
        ProductWorkbenchRecord record = createSyncedRecord(liveSnapshot);
        if (persistedState == null) {
            return record;
        }
        if (persistedState.getRecentActions() != null && !persistedState.getRecentActions().isEmpty()) {
            record.setRecentActions(copyRecordList(persistedState.getRecentActions()));
        }
        if (persistedState.getKeyContentHistory() != null && !persistedState.getKeyContentHistory().isEmpty()) {
            record.setKeyContentHistory(copyRecordList(persistedState.getKeyContentHistory()));
        }
        record.setPendingKeyContentHistoryCount(persistedState.getPendingKeyContentHistoryCount());
        record.setPendingKeyContentHistoryVisibleAfter(persistedState.getPendingKeyContentHistoryVisibleAfter());
        ProductMasterSnapshotView persistedBaseline = persistedState.getBaselineSnapshot();
        ProductMasterSnapshotView persistedDraft = persistedState.getDraftSnapshot();
        if (persistedBaseline == null || persistedDraft == null) {
            return record;
        }

        if (sameBusinessSnapshot(persistedDraft, persistedBaseline)) {
            if (!sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
                record.setNote("检测到 Noon 新基线，已按最新快照恢复工作台。");
            }
            return record;
        }

        ProductMasterSnapshotView hydratedDraft = copySnapshot(persistedDraft);
        hydrateProjectionOnlyFields(hydratedDraft, liveSnapshot);
        if (sameBusinessSnapshot(hydratedDraft, liveSnapshot)) {
            if (!sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
                record.setNote("已按最新发布结果恢复工作台。");
            }
            return record;
        }
        record.setBaselineSnapshot(copySnapshot(liveSnapshot));
        record.setDraftSnapshot(hydratedDraft);
        record.setLastSyncedAt(extractFetchedAt(liveSnapshot));
        if (sameBusinessSnapshot(persistedBaseline, liveSnapshot)) {
            record.setSyncStatus("draft");
            record.setNote("已从本地库恢复未发布草稿。");
            return record;
        }

        record.setSyncStatus("draft");
        record.setNote("已从本地库恢复未发布草稿。");
        return record;
    }

    private void hydrateProjectionOnlyFields(ProductMasterSnapshotView target, ProductMasterSnapshotView source) {
        if (target == null || source == null) {
            return;
        }
        Map<String, Map<String, Object>> sourceOffers = siteOfferMap(source.getSiteOffers());
        for (Map<String, Object> targetOffer : target.getSiteOffers()) {
            String storeCode = siteOfferCode(targetOffer);
            Map<String, Object> sourceOffer = sourceOffers.get(storeCode);
            if (sourceOffer == null) {
                continue;
            }
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "finalPrice");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "finalPriceSource");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionCode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionName");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "activePromotionUrl");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "barcode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "fbnStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "supermallStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "fbpStock");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "statusCode");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "deliveryMethod");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "isWinningBuybox");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "viewsCount");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "unitsSold");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "salesAmount");
            copyProjectionOnlyOfferField(targetOffer, sourceOffer, "salesCurrency");
        }
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "finalPrice");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "finalPriceSource");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionCode");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionName");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "activePromotionUrl");
        copyProjectionOnlyOfferField(target.getPricing(), source.getPricing(), "barcode");
        target.setPlatformSignals(new LinkedHashMap<>(source.getPlatformSignals()));
    }

    private ProductMasterSnapshotView prepareSnapshotForPublish(
            ProductMasterSnapshotView requestedSnapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (requestedSnapshot == null || baseline == null) {
            return requestedSnapshot;
        }
        ProductMasterSnapshotView preparedSnapshot = copySnapshot(requestedSnapshot);
        hydrateWritableOfferFieldsForPublish(preparedSnapshot, baseline, currentSiteCode);
        return preparedSnapshot;
    }

    private void hydrateWritableOfferFieldsForPublish(
            ProductMasterSnapshotView target,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (target == null || baseline == null || target.getSiteOffers() == null) {
            return;
        }
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline.getSiteOffers());
        String normalizedCurrentSiteCode = normalize(currentSiteCode);
        for (Map<String, Object> targetOffer : target.getSiteOffers()) {
            if (targetOffer == null) {
                continue;
            }
            String siteCode = siteOfferCode(targetOffer);
            if (StringUtils.hasText(normalizedCurrentSiteCode)
                    && !normalizedCurrentSiteCode.equalsIgnoreCase(siteCode)) {
                continue;
            }
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer == null) {
                continue;
            }
            hydrateWritableOfferFieldsForPublish(targetOffer, baselineOffer);
            if (isPublishOfferChanged(targetOffer, baselineOffer)) {
                applyDefaultSaleWindowForPublish(targetOffer);
            }
            mirrorCurrentOfferToPricing(target, targetOffer, normalizedCurrentSiteCode);
        }
    }

    private void hydrateWritableOfferFieldsForPublish(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer
    ) {
        Map<String, Object> hydrated = productDraftMergePolicy.hydrateMissingOfferFieldsForPublish(
                targetOffer,
                baselineOffer,
                List.of(
                        "site",
                        "pskuCode",
                        "offerCode",
                        "currency",
                        "price",
                        "salePrice",
                        "saleStart",
                        "saleEnd",
                        "priceMin",
                        "priceMax",
                        "isActive",
                        "idWarranty"
                )
        );
        targetOffer.clear();
        targetOffer.putAll(hydrated);
        copyBaselineOfferFieldIfMissing(targetOffer, baselineOffer, "offerNote");
    }

    private void applyDefaultSaleWindowForPublish(Map<String, Object> siteOffer) {
        if (asBigDecimal(siteOffer.get("salePrice")) == null) {
            return;
        }
        boolean missingSaleStart = !StringUtils.hasText(textValue(siteOffer.get("saleStart")));
        boolean missingSaleEnd = !StringUtils.hasText(textValue(siteOffer.get("saleEnd")));
        if (!missingSaleStart && !missingSaleEnd) {
            return;
        }
        Map<String, String> saleWindow = saleWindowForPublish(siteOffer);
        if (missingSaleStart && StringUtils.hasText(saleWindow.get("saleStart"))) {
            siteOffer.put("saleStart", saleWindow.get("saleStart"));
        }
        if (missingSaleEnd && StringUtils.hasText(saleWindow.get("saleEnd"))) {
            siteOffer.put("saleEnd", saleWindow.get("saleEnd"));
        }
    }

    private boolean isPublishOfferChanged(Map<String, Object> siteOffer, Map<String, Object> baselineOffer) {
        return !objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)));
    }

    private void mirrorCurrentOfferToPricing(
            ProductMasterSnapshotView target,
            Map<String, Object> siteOffer,
            String currentSiteCode
    ) {
        String siteCode = siteOfferCode(siteOffer);
        Map<String, Object> storeContext = target.getStoreContext() == null ? Map.of() : target.getStoreContext();
        String pricingSiteCode = firstNonBlank(currentSiteCode, textValue(storeContext.get("storeCode")));
        if (!StringUtils.hasText(siteCode)
                || !StringUtils.hasText(pricingSiteCode)
                || !siteCode.equalsIgnoreCase(pricingSiteCode)) {
            return;
        }
        if (target.getPricing() == null) {
            target.setPricing(new LinkedHashMap<>());
        }
        for (String field : new String[]{
                "price",
                "salePrice",
                "saleStart",
                "saleEnd",
                "priceMin",
                "priceMax",
                "isActive",
                "idWarranty",
                "offerNote"
        }) {
            if (siteOffer.containsKey(field)) {
                target.getPricing().put(field, siteOffer.get(field));
            }
        }
    }

    private void copyBaselineOfferFieldIfMissingOrBlank(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer,
            String field
    ) {
        if (EXPLICIT_CLEARABLE_OFFER_FIELDS.contains(field)) {
            copyBaselineOfferFieldIfMissing(targetOffer, baselineOffer, field);
            return;
        }
        if (!hasOfferFieldValue(targetOffer, field) && baselineOffer.containsKey(field)) {
            targetOffer.put(field, baselineOffer.get(field));
        }
    }

    private void copyBaselineOfferFieldIfMissing(
            Map<String, Object> targetOffer,
            Map<String, Object> baselineOffer,
            String field
    ) {
        if (!targetOffer.containsKey(field) && baselineOffer.containsKey(field)) {
            targetOffer.put(field, baselineOffer.get(field));
        }
    }

    private boolean hasOfferFieldValue(Map<String, Object> siteOffer, String field) {
        if (siteOffer == null || !siteOffer.containsKey(field)) {
            return false;
        }
        Object value = siteOffer.get(field);
        if (value == null) {
            return false;
        }
        if (value instanceof String) {
            return StringUtils.hasText((String) value);
        }
        return true;
    }

    private void copyProjectionOnlyOfferField(Map<String, Object> target, Map<String, Object> source, String fieldName) {
        if (target == null || source == null || !source.containsKey(fieldName)) {
            return;
        }
        Object value = source.get(fieldName);
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private boolean shouldSkipSiteOfferLiveReadForSharedOnlyPublish(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (draft == null || baseline == null || !StringUtils.hasText(currentSiteCode)) {
            return false;
        }
        boolean sharedChanged = !objectMapper.valueToTree(sharedComparableView(draft))
                .equals(objectMapper.valueToTree(sharedComparableView(baseline)));
        if (!sharedChanged) {
            return false;
        }
        Map<String, Object> draftOffer = siteOfferMap(draft.getSiteOffers()).get(currentSiteCode);
        Map<String, Object> baselineOffer = siteOfferMap(baseline.getSiteOffers()).get(currentSiteCode);
        return objectMapper.valueToTree(siteOfferComparable(
                draftOffer != null ? draftOffer : new LinkedHashMap<>(),
                false
        )).equals(objectMapper.valueToTree(siteOfferComparable(
                baselineOffer != null ? baselineOffer : new LinkedHashMap<>(),
                false
        )));
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
        return toComparableBusinessJson(left).equals(toComparableBusinessJson(right));
    }

    private boolean sameScopedSnapshot(ProductMasterSnapshotView left, ProductMasterSnapshotView right, String siteCode) {
        return toComparableScopedJson(left, siteCode).equals(toComparableScopedJson(right, siteCode));
    }

    private JsonNode toPublishComparableScopedJson(ProductMasterSnapshotView snapshot, String siteCode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, siteCode, false)));
        return node;
    }

    private List<Map<String, Object>> detectPublishConflictFields(
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView localDraft,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        List<Map<String, Object>> conflicts = new ArrayList<>();
        collectPublishConflictFields(
                "",
                toPublishComparableScopedJson(baseline, currentSiteCode),
                toPublishComparableScopedJson(localDraft, currentSiteCode),
                toPublishComparableScopedJson(noonCurrent, currentSiteCode),
                conflicts
        );
        return conflicts;
    }

    private void collectPublishConflictFields(
            String path,
            JsonNode baseline,
            JsonNode localDraft,
            JsonNode noonCurrent,
            List<Map<String, Object>> conflicts
    ) {
        JsonNode baselineNode = comparableNode(baseline);
        JsonNode localNode = comparableNode(localDraft);
        JsonNode noonNode = comparableNode(noonCurrent);
        if (baselineNode.isObject() || localNode.isObject() || noonNode.isObject()) {
            Set<String> fieldNames = new LinkedHashSet<>();
            collectFieldNames(baselineNode, fieldNames);
            collectFieldNames(localNode, fieldNames);
            collectFieldNames(noonNode, fieldNames);
            for (String fieldName : fieldNames) {
                String childPath = StringUtils.hasText(path) ? path + "." + fieldName : fieldName;
                collectPublishConflictFields(
                        childPath,
                        baselineNode.path(fieldName),
                        localNode.path(fieldName),
                        noonNode.path(fieldName),
                        conflicts
                );
            }
            return;
        }
        if (baselineNode.isArray() || localNode.isArray() || noonNode.isArray()) {
            int maxSize = Math.max(baselineNode.size(), Math.max(localNode.size(), noonNode.size()));
            for (int index = 0; index < maxSize; index++) {
                collectPublishConflictFields(
                        path + "[" + index + "]",
                        baselineNode.path(index),
                        localNode.path(index),
                        noonNode.path(index),
                        conflicts
                );
            }
            return;
        }

        boolean localChanged = !baselineNode.equals(localNode);
        boolean noonChanged = !baselineNode.equals(noonNode);
        if (!localChanged || !noonChanged || localNode.equals(noonNode)) {
            return;
        }
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("path", path);
        conflict.put("label", publishConflictFieldLabel(path));
        conflict.put("baselineValue", jsonNodeValue(baselineNode));
        conflict.put("localValue", jsonNodeValue(localNode));
        conflict.put("noonValue", jsonNodeValue(noonNode));
        conflict.put("scope", path.startsWith("siteOffers") ? "site" : "shared");
        conflicts.add(conflict);
    }

    private void collectFieldNames(JsonNode node, Set<String> fieldNames) {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
    }

    private JsonNode comparableNode(JsonNode node) {
        return node == null || node.isMissingNode() ? MissingNode.getInstance() : node;
    }

    private Object jsonNodeValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        return node.toString();
    }

    private String publishConflictFieldLabel(String path) {
        if (!StringUtils.hasText(path)) {
            return "商品字段";
        }
        if (path.contains("titleEn")) {
            return "英文标题";
        }
        if (path.contains("descriptionEn")) {
            return "英文长描述";
        }
        if (path.contains("highlightsEn")) {
            return "英文卖点";
        }
        if (path.contains("images")) {
            return "商品图片";
        }
        if (path.contains("priceMin")) {
            return "最低价格";
        }
        if (path.contains("priceMax")) {
            return "最高价格";
        }
        if (path.contains("salePrice")) {
            return "活动价";
        }
        if (path.contains("saleStart")) {
            return "活动开始时间";
        }
        if (path.contains("saleEnd")) {
            return "活动结束时间";
        }
        if (path.contains("price")) {
            return "价格";
        }
        if (path.contains("isActive")) {
            return "在线状态";
        }
        if (path.contains("idWarranty")) {
            return "质保";
        }
        if (path.contains("offerNote")) {
            return "Offer 备注";
        }
        if (path.contains("keyAttributes")) {
            return "商品属性";
        }
        return path;
    }

    private Map<String, Object> buildPublishConflictPayload(
            List<Map<String, Object>> fields,
            ProductMasterSnapshotView noonCurrent,
            String currentSiteCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "blocked");
        payload.put("message", "Noon 当前内容与本地草稿存在同字段冲突，请选择使用哪边内容。");
        payload.put("currentSiteCode", currentSiteCode);
        payload.put("checkedAt", extractFetchedAt(noonCurrent));
        payload.put("fields", fields);
        return payload;
    }

    private JsonNode toComparableBusinessJson(ProductMasterSnapshotView snapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, null, true)));
        return node;
    }

    private JsonNode toComparableScopedJson(ProductMasterSnapshotView snapshot, String siteCode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("shared", objectMapper.valueToTree(sharedComparableView(snapshot)));
        node.set("siteOffers", objectMapper.valueToTree(siteOfferComparableList(snapshot, siteCode, true)));
        return node;
    }

    private Map<String, Object> sharedComparableView(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("identity", publishComparableIdentity(snapshot));
        comparable.put("taxonomy", publishComparableTaxonomy(snapshot));
        comparable.put("content", publishComparableContent(snapshot));
        comparable.put("keyAttributes", publishComparableKeyAttributes(snapshot));
        comparable.put("group", publishComparableGroup(snapshot));
        comparable.put("variants", publishComparableVariants(snapshot));
        return comparable;
    }

    private Map<String, Object> sharedZskuComparableView(ProductMasterSnapshotView snapshot) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("identity", publishComparableIdentity(snapshot));
        comparable.put("taxonomy", publishComparableTaxonomy(snapshot));
        comparable.put("content", publishComparableContent(snapshot));
        comparable.put("keyAttributes", publishComparableKeyAttributes(snapshot));
        comparable.put("variants", publishComparableVariants(snapshot));
        return comparable;
    }

    private Map<String, Object> publishComparableGroup(ProductMasterSnapshotView snapshot) {
        return publishComparableGroup(snapshot != null ? snapshot.getGroup() : null);
    }

    private Map<String, Object> publishComparableGroup(Map<String, Object> group) {
        Map<String, Object> comparable = groupStructureComparable(group);
        comparable.put("memberAxisValues", groupMemberAxisValuesComparable(group, "en"));
        comparable.put("memberAxisValuesAr", groupMemberAxisValuesComparable(group, "ar"));
        return comparable;
    }

    private Map<String, Object> groupStructureComparable(Map<String, Object> group) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (group == null) {
            return comparable;
        }
        comparable.put("skuGroup", textValue(group.get("skuGroup")));
        comparable.put("groupRef", textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = textValue(firstNonNull(axis.get("axisCode"), axis.get("axis_code")));
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", textValue(firstNonNull(axis.get("axisName"), axis.get("axis_name"))));
            axes.add(axisComparable);
        }
        axes.sort((left, right) -> textValue(left.get("axisCode")).compareTo(textValue(right.get("axisCode"))));
        comparable.put("axes", axes);

        List<String> memberSkuParents = new ArrayList<>();
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (StringUtils.hasText(skuParent)) {
                memberSkuParents.add(skuParent);
            }
        }
        memberSkuParents.sort(String::compareTo);
        comparable.put("memberSkuParents", memberSkuParents);
        return comparable;
    }

    private Map<String, Object> groupDefinitionComparable(Map<String, Object> group) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        if (group == null) {
            return comparable;
        }
        comparable.put("skuGroup", textValue(group.get("skuGroup")));
        comparable.put("groupRef", textValue(group.get("groupRef")));
        comparable.put("groupRefCanonical", textValue(group.get("groupRefCanonical")));
        comparable.put("conditionsBrand", textValue(group.get("conditionsBrand")));
        comparable.put("conditionsFulltype", textValue(group.get("conditionsFulltype")));

        List<Map<String, Object>> axes = new ArrayList<>();
        for (Map<String, Object> axis : recordListValue(group.get("axes"))) {
            String axisCode = textValue(firstNonNull(axis.get("axisCode"), axis.get("axis_code")));
            if (!StringUtils.hasText(axisCode)) {
                continue;
            }
            Map<String, Object> axisComparable = new LinkedHashMap<>();
            axisComparable.put("axisCode", axisCode);
            axisComparable.put("axisName", textValue(firstNonNull(axis.get("axisName"), axis.get("axis_name"))));
            axes.add(axisComparable);
        }
        axes.sort((left, right) -> textValue(left.get("axisCode")).compareTo(textValue(right.get("axisCode"))));
        comparable.put("axes", axes);
        return comparable;
    }

    private List<Map<String, Object>> groupMemberAxisValuesComparable(Map<String, Object> group, String lang) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (group == null) {
            return comparable;
        }
        List<String> axisCodes = groupAxisCodes(recordListValue(group.get("axes")));
        if (axisCodes.isEmpty()) {
            return comparable;
        }
        for (Map<String, Object> member : recordListValue(group.get("members"))) {
            String skuParent = groupMemberSkuParent(member);
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (String axisCode : axisCodes) {
                values.put(axisCode, groupMemberAxisValue(member, axisCode, lang));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuParent", skuParent);
            row.put("values", values);
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("skuParent")).compareTo(textValue(right.get("skuParent"))));
        return comparable;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recordListValue(Object value) {
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                records.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return records;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String groupMemberSkuParent(Map<String, Object> member) {
        if (member == null) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("skuParent")),
                textValue(member.get("parentSku")),
                textValue(member.get("sku")),
                textValue(member.get("childSku")),
                textValue(member.get("partnerSku"))
        );
    }

    @SuppressWarnings("unchecked")
    private String groupMemberAxisValue(Map<String, Object> member, String axisCode, String lang) {
        if (member == null || !StringUtils.hasText(axisCode)) {
            return null;
        }
        String normalizedLang = normalize(lang);
        if ("ar".equalsIgnoreCase(normalizedLang)) {
            Map<String, Object> axisValuesAr = member.get("axisValuesAr") instanceof Map<?, ?>
                    ? (Map<String, Object>) member.get("axisValuesAr")
                    : Map.of();
            String axisSpecificArValue = firstNonBlank(
                    textValue(axisValuesAr.get(axisCode)),
                    textValue(member.get(axisCode + "Ar"))
            );
            if (StringUtils.hasText(axisSpecificArValue)) {
                return axisSpecificArValue;
            }
            if (!axisValuesAr.isEmpty()) {
                return null;
            }
            return firstNonBlank(
                    textValue(member.get("axisValueAr"))
            );
        }
        Map<String, Object> axisValues = member.get("axisValues") instanceof Map<?, ?>
                ? (Map<String, Object>) member.get("axisValues")
                : Map.of();
        String axisSpecificValue = firstNonBlank(
                textValue(member.get(axisCode)),
                textValue(axisValues.get(axisCode))
        );
        if (StringUtils.hasText(axisSpecificValue)) {
            return axisSpecificValue;
        }
        if (!axisValues.isEmpty()) {
            return null;
        }
        return firstNonBlank(
                textValue(member.get("axisValue"))
        );
    }

    private Map<String, Object> publishComparableIdentity(ProductMasterSnapshotView snapshot) {
        Map<String, Object> identity = snapshot != null && snapshot.getIdentity() != null
                ? snapshot.getIdentity()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("brand", textValue(identity.get("brand")));
        return comparable;
    }

    private Map<String, Object> publishComparableTaxonomy(ProductMasterSnapshotView snapshot) {
        Map<String, Object> taxonomy = snapshot != null && snapshot.getTaxonomy() != null
                ? snapshot.getTaxonomy()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("family", textValue(taxonomy.get("family")));
        comparable.put("productType", textValue(taxonomy.get("productType")));
        comparable.put("productSubtype", textValue(taxonomy.get("productSubtype")));
        comparable.put("productFulltype", textValue(taxonomy.get("productFulltype")));
        comparable.put("grade", textValue(taxonomy.get("grade")));
        comparable.put("itemCondition", textValue(taxonomy.get("itemCondition")));
        return comparable;
    }

    private Map<String, Object> publishComparableContent(ProductMasterSnapshotView snapshot) {
        Map<String, Object> content = snapshot != null && snapshot.getContent() != null
                ? snapshot.getContent()
                : Map.of();
        Map<String, Object> comparable = new LinkedHashMap<>();
        comparable.put("titleEn", textValue(content.get("titleEn")));
        comparable.put("titleAr", textValue(content.get("titleAr")));
        comparable.put("descriptionEn", textValue(content.get("descriptionEn")));
        comparable.put("descriptionAr", textValue(content.get("descriptionAr")));
        comparable.put("highlightsEn", stringList(content.get("highlightsEn")));
        comparable.put("highlightsAr", stringList(content.get("highlightsAr")));
        comparable.put("images", stringList(content.get("images")));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableKeyAttributes(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getKeyAttributes() != null
                ? snapshot.getKeyAttributes()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> attribute : source) {
            if (attribute == null) {
                continue;
            }
            String code = normalizeAttributeCode(textValue(attribute.get("code")));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("commonValue", attribute.get("commonValue"));
            row.put("enValue", attribute.get("enValue"));
            row.put("arValue", attribute.get("arValue"));
            row.put("unit", textValue(attribute.get("unit")));
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("code")).compareTo(textValue(right.get("code"))));
        return comparable;
    }

    private List<Map<String, Object>> publishComparableVariants(ProductMasterSnapshotView snapshot) {
        List<Map<String, Object>> source = snapshot != null && snapshot.getVariants() != null
                ? snapshot.getVariants()
                : List.of();
        List<Map<String, Object>> comparable = new ArrayList<>();
        for (Map<String, Object> variant : source) {
            if (variant == null) {
                continue;
            }
            String childSku = textValue(variant.get("childSku"));
            if (!StringUtils.hasText(childSku)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("childSku", childSku);
            row.put("sizeEn", textValue(variant.get("sizeEn")));
            row.put("sizeAr", textValue(variant.get("sizeAr")));
            comparable.add(row);
        }
        comparable.sort((left, right) -> textValue(left.get("childSku")).compareTo(textValue(right.get("childSku"))));
        return comparable;
    }

    private List<Map<String, Object>> siteOfferComparableList(
            ProductMasterSnapshotView snapshot,
            String siteCode,
            boolean includeUnsupportedFields
    ) {
        List<Map<String, Object>> comparable = new ArrayList<>();
        if (snapshot == null || snapshot.getSiteOffers() == null) {
            return comparable;
        }

        for (Map<String, Object> siteOffer : snapshot.getSiteOffers()) {
            String storeCode = siteOfferCode(siteOffer);
            if (StringUtils.hasText(siteCode) && !storeCode.equalsIgnoreCase(siteCode)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            putIfNotNull(row, "storeCode", storeCode);
            putIfNotNull(row, "site", siteOffer.get("site"));
            putComparableDecimalIfPresent(row, "price", siteOffer.get("price"));
            putComparableDecimalIfPresent(row, "salePrice", siteOffer.get("salePrice"));
            putIfNotNull(row, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
            putIfNotNull(row, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
            putComparableDecimalIfPresent(row, "priceMin", siteOffer.get("priceMin"));
            putComparableDecimalIfPresent(row, "priceMax", siteOffer.get("priceMax"));
            putComparableBooleanIfPresent(row, "isActive", siteOffer.get("isActive"));
            putComparableDecimalIfPresent(row, "idWarranty", siteOffer.get("idWarranty"));
            putIfNotNull(row, "offerNote", siteOffer.get("offerNote"));
            if (includeUnsupportedFields) {
                putIfNotNull(row, "barcode", siteOffer.get("barcode"));
            }
            comparable.add(row);
        }
        return comparable;
    }

    private List<String> validatePublishSnapshot(
            ProductMasterSnapshotView snapshot,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        String titleEn = textValue(snapshot.getContent().get("titleEn"));
        String brand = textValue(snapshot.getIdentity().get("brand"));
        String productFulltype = textValue(snapshot.getTaxonomy().get("productFulltype"));
        List<String> images = stringList(snapshot.getContent().get("images"));

        if (!StringUtils.hasText(titleEn)) {
            errors.add("共享主档缺少标题 EN。");
        }
        if (!StringUtils.hasText(brand)) {
            errors.add("共享主档缺少品牌。");
        }
        if (!StringUtils.hasText(productFulltype)) {
            errors.add("共享主档缺少 Fulltype。");
        }
        if (images.isEmpty()) {
            errors.add("共享主档至少需要保留 1 张图片。");
        }
        List<String> baselineImages = baseline == null ? List.of() : stringList(baseline.getContent().get("images"));
        if (!objectMapper.valueToTree(images).equals(objectMapper.valueToTree(baselineImages))
                && images.stream().anyMatch(this::isLocalProductImageAssetUrl)) {
            errors.add("本地上传图片还没有 Noon 可访问 URL，暂时不能发布；请先删除本地上传图片或等待图片外链适配。");
        }

        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline != null ? baseline.getSiteOffers() : null);
        for (Map<String, Object> siteOffer : siteOfferComparableList(snapshot, currentSiteCode, false)) {
            String siteCode = textValue(siteOffer.get("storeCode"));
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (baselineOffer != null
                    && objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                    .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)))) {
                continue;
            }
            String label = textValue(siteOffer.get("site")) + " / " + textValue(siteOffer.get("storeCode"));
            BigDecimal price = asBigDecimal(siteOffer.get("price"));
            BigDecimal salePrice = asBigDecimal(siteOffer.get("salePrice"));
            BigDecimal priceMin = asBigDecimal(siteOffer.get("priceMin"));
            BigDecimal priceMax = asBigDecimal(siteOffer.get("priceMax"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(label + " 缺少有效售价。");
            }
            if (salePrice != null && price != null && salePrice.compareTo(price) > 0) {
                errors.add(label + " 的促销价不能高于原价。");
            }
            if (price != null && priceMin != null && price.compareTo(priceMin) < 0) {
                errors.add(label + " 的售价低于允许范围。");
            }
            if (price != null && priceMax != null && price.compareTo(priceMax) > 0) {
                errors.add(label + " 的售价高于允许范围。");
            }
        }
        return errors;
    }

    private List<String> validatePublishOperationalKeys(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Object>> draftOffers = siteOfferMap(draft.getSiteOffers());
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(baseline.getSiteOffers());
        Set<String> relevantSiteCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(currentSiteCode)) {
            relevantSiteCodes.add(currentSiteCode);
        } else {
            relevantSiteCodes.addAll(draftOffers.keySet());
        }
        for (String siteCode : relevantSiteCodes) {
            Map<String, Object> siteOffer = draftOffers.get(siteCode);
            Map<String, Object> baselineOffer = baselineOffers.get(siteCode);
            if (siteOffer == null || baselineOffer == null) {
                continue;
            }
            if (objectMapper.valueToTree(siteOfferComparable(siteOffer, false))
                    .equals(objectMapper.valueToTree(siteOfferComparable(baselineOffer, false)))) {
                continue;
            }
            if (!StringUtils.hasText(firstNonBlank(
                    textValue(siteOffer.get("pskuCode")),
                    textValue(baselineOffer.get("pskuCode"))
            ))) {
                errors.add(
                        textValue(siteOffer.get("site"))
                                + " / "
                                + siteCode
                                + " 缺少 pskuCode，暂时不能发布当前站点经营字段。"
                );
            }
        }
        return errors;
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

    private Map<String, Map<String, Object>> siteOfferMap(List<Map<String, Object>> siteOffers) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (siteOffers == null) {
            return map;
        }
        for (Map<String, Object> siteOffer : siteOffers) {
            map.put(siteOfferCode(siteOffer), new LinkedHashMap<>(siteOffer));
        }
        return map;
    }

    private Map<String, Object> siteOfferComparable(Map<String, Object> siteOffer, boolean includeUnsupportedFields) {
        Map<String, Object> comparable = new LinkedHashMap<>();
        putIfNotNull(comparable, "storeCode", siteOffer.get("storeCode"));
        putIfNotNull(comparable, "site", siteOffer.get("site"));
        putComparableDecimalIfPresent(comparable, "price", siteOffer.get("price"));
        putComparableDecimalIfPresent(comparable, "salePrice", siteOffer.get("salePrice"));
        putIfNotNull(comparable, "saleStart", normalizeOfferDateForNoon(siteOffer.get("saleStart")));
        putIfNotNull(comparable, "saleEnd", normalizeOfferDateForNoon(siteOffer.get("saleEnd")));
        putComparableDecimalIfPresent(comparable, "priceMin", siteOffer.get("priceMin"));
        putComparableDecimalIfPresent(comparable, "priceMax", siteOffer.get("priceMax"));
        putComparableBooleanIfPresent(comparable, "isActive", siteOffer.get("isActive"));
        putComparableDecimalIfPresent(comparable, "idWarranty", siteOffer.get("idWarranty"));
        putIfNotNull(comparable, "offerNote", siteOffer.get("offerNote"));
        if (includeUnsupportedFields) {
            putIfNotNull(comparable, "barcode", siteOffer.get("barcode"));
        }
        return comparable;
    }

    private boolean isLocalProductImageAssetUrl(String url) {
        return StringUtils.hasText(url) && url.trim().startsWith("/api/product-master/image-assets/");
    }

    private List<String> mergeWarnings(List<String> baseWarnings, List<String> extraWarnings) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (baseWarnings != null) {
            merged.addAll(userVisibleWarnings(baseWarnings));
        }
        if (extraWarnings != null) {
            merged.addAll(userVisibleWarnings(extraWarnings));
        }
        return new ArrayList<>(merged);
    }

    private List<String> userVisibleWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> visible = new ArrayList<>();
        for (String warning : warnings) {
            if (!StringUtils.hasText(warning) || isNonBlockingNoonRealtimeWarning(warning)) {
                continue;
            }
            visible.add(warning);
        }
        return visible;
    }

    private boolean isNonBlockingNoonRealtimeWarning(String warning) {
        if (!StringUtils.hasText(warning)) {
            return false;
        }
        String normalized = warning.toLowerCase(Locale.ROOT);
        return normalized.contains("价格信息失败")
                || normalized.contains("库存摘要失败")
                || normalized.contains("读取 fulltype 模板失败")
                || normalized.contains("fulltype 模板实时读取失败")
                || normalized.contains("没有返回 product_fulltype")
                || normalized.contains("类目模板读取已跳过");
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

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = textValue(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "active".equalsIgnoreCase(text);
    }

    private int parseInteger(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void putIfHasText(ObjectNode target, String key, Object value) {
        String text = textValue(value);
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private String normalizeOfferDateForNoon(Object value) {
        String text = textValue(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(text, FETCH_TIME_FORMATTER).toLocalDate().format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(text).format(NOON_OFFER_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private Map<String, String> saleWindowForPublish(Map<String, Object> siteOffer) {
        return productPublishOfferWriter.saleWindowForPublish(siteOffer);
    }

    private Map<String, Object> buildStoreContext(
            StoreSyncOwnerContext owner,
            StoreSyncStoreRecord store,
            String noonUser,
            JsonNode whoamiNode,
            String projectCode,
            String referenceStoreCode,
            String referenceSite,
            int projectSiteCount
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        putIfNotNull(context, "ownerUserId", owner.getId());
        putIfNotBlank(context, "ownerName", resolveOwnerName(owner));
        putIfNotBlank(context, "accountNo", owner.getAccountNo());
        putIfNotBlank(context, "projectName", store.getProjectName());
        putIfNotBlank(context, "projectCode", projectCode);
        putIfNotBlank(context, "storeCode", referenceStoreCode);
        putIfNotBlank(context, "site", referenceSite);
        putIfNotNull(context, "projectSiteCount", projectSiteCount);
        putIfNotBlank(context, "noonUser", noonUser);
        putIfNotBlank(context, "whoamiEmail", text(whoamiNode, "email"));
        putIfNotBlank(context, "whoamiRole", text(whoamiNode, "idp_role"));
        putIfNotBlank(
                context,
                "fetchedAt",
                ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(FETCH_TIME_FORMATTER)
        );
        return context;
    }

    private List<ProjectSiteContext> loadProjectSiteContexts(
            NoonSession session,
            Long ownerUserId,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        Map<String, ProjectSiteContext> siteMap = new LinkedHashMap<>();
        List<StoreSyncStoreRecord> localProjectStores = findRelatedStores(ownerUserId, store);
        for (StoreSyncStoreRecord localStore : localProjectStores) {
            if (!StringUtils.hasText(localStore.getStoreCode())) {
                continue;
            }
            siteMap.put(
                    localStore.getStoreCode(),
                    new ProjectSiteContext(localStore.getStoreCode(), localStore.getSite(), null)
            );
        }

        ObjectNode storeListBody = objectMapper.createObjectNode();
        storeListBody.put("noonStoreCode", "");
        long storeListStartedAt = System.nanoTime();
        JsonNode storeListRoot = safePost(
                session,
                STORE_LIST_URL,
                storeListBody,
                true,
                warnings,
                "读取项目站点列表失败"
        );
        log.info(
                "product-management fetchSnapshot detail stage=store.list store={} durationMs={}",
                normalize(store.getStoreCode()),
                nanosToMillis(storeListStartedAt)
        );
        JsonNode noonStoresNode = storeListRoot.path("noonStores");
        if (noonStoresNode.isArray()) {
            for (JsonNode siteNode : noonStoresNode) {
                String liveStoreCode = text(siteNode, "noonStoreCode");
                if (!StringUtils.hasText(liveStoreCode)) {
                    continue;
                }

                String liveSite = firstNonBlank(text(siteNode, "countryCode"), deriveSiteFromStoreCode(liveStoreCode));
                String statusCode = text(siteNode, "statusCode");
                ProjectSiteContext existing = siteMap.get(liveStoreCode);
                if (existing != null) {
                    existing.setSite(firstNonBlank(liveSite, existing.getSite()));
                    existing.setStatusCode(firstNonBlank(statusCode, existing.getStatusCode()));
                }
            }
        }

        if (!StringUtils.hasText(store.getStoreCode())) {
            return new ArrayList<>(siteMap.values());
        }

        ProjectSiteContext referenceStore = siteMap.get(store.getStoreCode());
        if (referenceStore == null) {
            siteMap.put(
                    store.getStoreCode(),
                    new ProjectSiteContext(
                            store.getStoreCode(),
                            firstNonBlank(store.getSite(), deriveSiteFromStoreCode(store.getStoreCode())),
                            null
                    )
            );
        } else {
            referenceStore.setSite(firstNonBlank(referenceStore.getSite(), store.getSite(), deriveSiteFromStoreCode(store.getStoreCode())));
        }

        return new ArrayList<>(siteMap.values());
    }

    private ProjectSiteFetchResult loadSiteOffers(
            NoonSession session,
            List<ProjectSiteContext> projectSites,
            String referenceStoreCode,
            String idPartner,
            String partnerSku,
            String pskuCode,
            List<String> warnings
    ) {
        List<Map<String, Object>> siteOffers = new ArrayList<>();
        JsonNode referencePricingNode = MissingNode.getInstance();
        JsonNode referenceStockNode = MissingNode.getInstance();

        if (!StringUtils.hasText(idPartner)) {
            warnings.add("当前商品没有返回 id_partner，价格读取已跳过。");
        }
        if (!StringUtils.hasText(partnerSku)) {
            warnings.add("当前索引缺少 partnerSku，站点价格读取已跳过。");
        }
        if (!StringUtils.hasText(pskuCode)) {
            warnings.add("当前索引缺少 pskuCode，站点库存摘要读取已跳过。");
        }

        for (ProjectSiteContext projectSite : projectSites) {
            NoonSession siteSession = session.withStoreCode(projectSite.getStoreCode());
            JsonNode pricingNode = MissingNode.getInstance();
            String resolvedSite = firstNonBlank(projectSite.getSite(), deriveSiteFromStoreCode(projectSite.getStoreCode()), "SA");
            if (StringUtils.hasText(idPartner) && StringUtils.hasText(partnerSku)) {
                ObjectNode pricingBody = objectMapper.createObjectNode();
                ArrayNode pskuList = pricingBody.putArray("psku_list");
                ObjectNode pricingItem = pskuList.addObject();
                pricingItem.put("psku", partnerSku);
                pricingItem.put("country_code", resolvedSite.toUpperCase());
                pricingItem.put("id_partner", idPartner);
                long pricingStartedAt = System.nanoTime();
                pricingNode = safePostOptional(
                        siteSession,
                        PRICING_INFO_URL,
                        pricingBody,
                        true,
                        "读取站点 " + describeSite(projectSite) + " 价格信息失败"
                );
                log.info(
                        "product-management fetchSnapshot detail stage=pricing.info store={} site={} durationMs={}",
                        projectSite.getStoreCode(),
                        resolvedSite,
                        nanosToMillis(pricingStartedAt)
                );
            }

            JsonNode stockNode = MissingNode.getInstance();
            if (StringUtils.hasText(pskuCode)) {
                ObjectNode stockBody = objectMapper.createObjectNode();
                ArrayNode pskuCodes = stockBody.putArray("psku_codes");
                pskuCodes.add(pskuCode);
                stockBody.put("noon_store_code", projectSite.getStoreCode());
                long stockStartedAt = System.nanoTime();
                stockNode = safePostOptional(
                        siteSession,
                        STOCK_INFO_URL,
                        stockBody,
                        true,
                        "读取站点 " + describeSite(projectSite) + " 库存摘要失败"
                );
                log.info(
                        "product-management fetchSnapshot detail stage=stock.info store={} site={} durationMs={}",
                        projectSite.getStoreCode(),
                        resolvedSite,
                        nanosToMillis(stockStartedAt)
                );
            }

            siteOffers.add(buildSiteOffer(projectSite, pricingNode, stockNode, projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)));
            if (projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)) {
                referencePricingNode = pricingNode;
                referenceStockNode = stockNode;
            }
        }

        return new ProjectSiteFetchResult(siteOffers, referencePricingNode, referenceStockNode);
    }

    private ProjectSiteFetchResult reuseSiteOffersFromSnapshot(
            ProductMasterSnapshotView snapshot,
            List<ProjectSiteContext> projectSites,
            String referenceStoreCode
    ) {
        List<Map<String, Object>> siteOffers = new ArrayList<>();
        Map<String, Map<String, Object>> baselineOffers = siteOfferMap(snapshot != null ? snapshot.getSiteOffers() : null);
        for (ProjectSiteContext projectSite : projectSites) {
            Map<String, Object> baselineOffer = baselineOffers.get(projectSite.getStoreCode());
            Map<String, Object> siteOffer = baselineOffer != null
                    ? new LinkedHashMap<>(baselineOffer)
                    : buildSiteOffer(
                    projectSite,
                    MissingNode.getInstance(),
                    MissingNode.getInstance(),
                    projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)
            );
            siteOffers.add(siteOffer);
        }
        return new ProjectSiteFetchResult(siteOffers, MissingNode.getInstance(), MissingNode.getInstance());
    }

    private String resolveReferenceSite(List<ProjectSiteContext> projectSites, String referenceStoreCode) {
        for (ProjectSiteContext projectSite : projectSites) {
            if (projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)) {
                return firstNonBlank(projectSite.getSite(), deriveSiteFromStoreCode(referenceStoreCode));
            }
        }
        return deriveSiteFromStoreCode(referenceStoreCode);
    }

    private String resolveProjectCode(
            NoonSession session,
            String localProjectCode,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        String cacheKey = buildResolvedProjectCodeCacheKey(store, localProjectCode);
        ResolvedProjectCodeCacheEntry cachedEntry = resolvedProjectCodeCache.get(cacheKey);
        if (cachedEntry != null) {
            if (StringUtils.hasText(cachedEntry.warning()) && !warnings.contains(cachedEntry.warning())) {
                warnings.add(cachedEntry.warning());
            }
            return cachedEntry.resolvedProjectCode();
        }

        ObjectNode emptyBody = objectMapper.createObjectNode();
        JsonNode projectListRoot = safePost(
                session,
                PROJECT_LIST_URL,
                emptyBody,
                false,
                warnings,
                "读取 Noon 项目列表失败"
        );
        JsonNode projectsNode = projectListRoot.path("projects");
        if (!projectsNode.isArray() || projectsNode.size() == 0) {
            return localProjectCode;
        }

        String localDigits = extractDigits(localProjectCode);
        String storeProjectName = normalize(store.getProjectName());

        for (JsonNode projectNode : projectsNode) {
            String candidateCode = text(projectNode, "projectCode");
            if (localProjectCode != null && localProjectCode.equalsIgnoreCase(candidateCode)) {
                return cacheResolvedProjectCode(cacheKey, candidateCode, null);
            }
        }

        if (StringUtils.hasText(localDigits)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateCode = text(projectNode, "projectCode");
                if (extractDigits(candidateCode).equals(localDigits)) {
                    return cacheResolvedProjectCode(cacheKey, candidateCode, null);
                }
            }
        }

        if (StringUtils.hasText(storeProjectName)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateName = normalize(text(projectNode, "projectName"));
                if (storeProjectName.equalsIgnoreCase(candidateName)) {
                    return cacheResolvedProjectCode(cacheKey, text(projectNode, "projectCode"), null);
                }
            }
        }

        String fallbackProjectCode = text(projectsNode.get(0), "projectCode");
        if (StringUtils.hasText(fallbackProjectCode)
                && !fallbackProjectCode.equalsIgnoreCase(localProjectCode)) {
            String warning = "本地店铺 projectCode="
                    + localProjectCode
                    + " 与 Noon 实时 projectCode="
                    + fallbackProjectCode
                    + " 不一致，当前已按 Noon 实时项目码读取。";
            warnings.add(warning);
            return cacheResolvedProjectCode(cacheKey, fallbackProjectCode, warning);
        }

        return cacheResolvedProjectCode(
                cacheKey,
                StringUtils.hasText(fallbackProjectCode) ? fallbackProjectCode : localProjectCode,
                null
        );
    }

    private String buildResolvedProjectCodeCacheKey(StoreSyncStoreRecord store, String localProjectCode) {
        String storeCode = normalize(store.getStoreCode());
        if (StringUtils.hasText(storeCode)) {
            return "store:" + storeCode.toLowerCase();
        }
        String projectCode = normalize(localProjectCode);
        if (StringUtils.hasText(projectCode)) {
            return "project:" + projectCode.toLowerCase();
        }
        String projectName = normalize(store.getProjectName());
        if (StringUtils.hasText(projectName)) {
            return "project-name:" + projectName.toLowerCase();
        }
        return "store:unknown";
    }

    private String cacheResolvedProjectCode(String cacheKey, String resolvedProjectCode, String warning) {
        if (StringUtils.hasText(cacheKey) && StringUtils.hasText(resolvedProjectCode)) {
            resolvedProjectCodeCache.put(
                    cacheKey,
                    new ResolvedProjectCodeCacheEntry(resolvedProjectCode, warning)
            );
        }
        return resolvedProjectCode;
    }

    private Map<String, Object> buildIdentity(
            JsonNode productNode,
            JsonNode commonNode,
            JsonNode pricingRoot,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        Map<String, Object> identity = new LinkedHashMap<>();
        JsonNode pricingItem = firstDataItem(pricingRoot);
        putIfNotBlank(identity, "skuParent", skuParent);
        putIfNotBlank(identity, "parentSku", text(productNode, "parent_sku"));
        putIfNotBlank(identity, "partnerSku", firstNonBlank(partnerSku, text(pricingItem, "psku")));
        putIfNotBlank(identity, "pskuCode", pskuCode);
        putIfNotBlank(identity, "brand", text(commonNode, "brand"));
        putIfNotBlank(identity, "barcode", firstNonBlank(
                text(commonNode, "barcode"),
                text(commonNode, "gtin"),
                text(commonNode, "ean"),
                text(commonNode, "upc"),
                text(pricingItem, "barcode"),
                text(pricingItem, "gtin"),
                text(pricingItem, "ean"),
                text(pricingItem, "upc")
        ));

        putIfNotBlank(identity, "childSku", text(pricingItem, "sku"));
        putIfNotBlank(identity, "offerCode", text(pricingItem, "offer_code"));
        putIfNotBlank(identity, "productSourceType", ProductSourceTypeSupport.resolve(
                textValue(identity.get("productSourceType")),
                textValue(identity.get("childSku")),
                skuParent
        ));
        putIfNotNull(identity, "variantCount", productNode.path("variants").size());
        return identity;
    }

    private Map<String, Object> buildTaxonomy(JsonNode commonNode) {
        Map<String, Object> taxonomy = new LinkedHashMap<>();
        putIfNotBlank(taxonomy, "family", text(commonNode, "family"));
        putIfNotBlank(taxonomy, "familyNameEn", noonText(commonNode, "family_option_name_en"));
        putIfNotBlank(taxonomy, "familyNameAr", noonText(commonNode, "family_option_name_ar"));
        putIfNotBlank(taxonomy, "productType", text(commonNode, "product_type"));
        putIfNotBlank(taxonomy, "productTypeNameEn", noonText(commonNode, "product_type_option_name_en"));
        putIfNotBlank(taxonomy, "productTypeNameAr", noonText(commonNode, "product_type_option_name_ar"));
        putIfNotBlank(taxonomy, "productSubtype", text(commonNode, "product_subtype"));
        putIfNotBlank(taxonomy, "productSubtypeNameEn", noonText(commonNode, "product_subtype_option_name_en"));
        putIfNotBlank(taxonomy, "productSubtypeNameAr", noonText(commonNode, "product_subtype_option_name_ar"));
        putIfNotBlank(taxonomy, "productFulltype", text(commonNode, "product_fulltype"));
        putIfNotBlank(taxonomy, "grade", text(commonNode, "grade"));
        putIfNotBlank(taxonomy, "itemCondition", text(commonNode, "item_condition"));
        return taxonomy;
    }

    private Map<String, Object> buildContent(JsonNode commonNode, JsonNode enNode, JsonNode arNode) {
        Map<String, Object> content = new LinkedHashMap<>();
        putIfNotBlank(content, "titleEn", text(enNode, "product_title"));
        putIfNotBlank(content, "titleAr", text(arNode, "product_title"));
        putIfNotBlank(content, "fullTitleEn", text(enNode, "full_product_title"));
        putIfNotBlank(content, "fullTitleAr", text(arNode, "full_product_title"));
        putIfNotBlank(content, "descriptionEn", text(enNode, "long_description"));
        putIfNotBlank(content, "descriptionAr", text(arNode, "long_description"));
        List<String> highlightsEn = collectOrderedText(enNode, "feature_bullet_", 5);
        List<String> highlightsAr = collectOrderedText(arNode, "feature_bullet_", 5);
        List<String> images = collectImages(commonNode);
        putIfNotNull(content, "imageCount", images.size());
        putIfNotEmpty(content, "images", images);
        putIfNotEmpty(content, "highlightsEn", highlightsEn);
        putIfNotEmpty(content, "highlightsAr", highlightsAr);
        return content;
    }

    private void applyFollowSellCatalogContentIfNeeded(
            NoonSession session,
            Map<String, Object> identity,
            Map<String, Object> content,
            String referenceSite,
            String reason
    ) {
        if (!needsCatalogContentFallback(content)) {
            return;
        }
        String catalogSku = textValue(identity.get("childSku"));
        productNoonCatalogContentService.fetchFollowSellCatalogContent(
                session,
                catalogSku,
                referenceSite,
                "detail." + normalizeReason(reason)
        ).ifPresent(catalogContent -> mergeCatalogContent(identity, content, catalogContent));
    }

    private boolean needsCatalogContentFallback(Map<String, Object> content) {
        if (content == null) {
            return true;
        }
        return !StringUtils.hasText(textValue(content.get("titleEn")))
                || stringList(content.get("images")).isEmpty();
    }

    private void mergeCatalogContent(
            Map<String, Object> identity,
            Map<String, Object> content,
            ProductNoonCatalogContentService.CatalogContent catalogContent
    ) {
        if (catalogContent == null) {
            return;
        }
        if (!StringUtils.hasText(textValue(identity.get("brand")))) {
            putIfNotBlank(identity, "brand", catalogContent.getBrand());
        }
        if (!StringUtils.hasText(textValue(identity.get("childSku")))) {
            putIfNotBlank(identity, "childSku", catalogContent.getCatalogSku());
        }
        putIfNotBlank(identity, "productSourceType", ProductSourceTypeSupport.resolve(
                textValue(identity.get("productSourceType")),
                textValue(identity.get("childSku")),
                textValue(identity.get("skuParent"))
        ));
        if (!StringUtils.hasText(textValue(content.get("titleEn")))) {
            putIfNotBlank(content, "titleEn", catalogContent.getTitleEn());
        }
        if (!StringUtils.hasText(textValue(content.get("titleAr")))) {
            putIfNotBlank(content, "titleAr", catalogContent.getTitleAr());
        }
        if (!StringUtils.hasText(textValue(content.get("descriptionEn")))) {
            putIfNotBlank(content, "descriptionEn", catalogContent.getDescriptionEn());
        }
        if (!StringUtils.hasText(textValue(content.get("descriptionAr")))) {
            putIfNotBlank(content, "descriptionAr", catalogContent.getDescriptionAr());
        }
        if (stringList(content.get("images")).isEmpty()) {
            putIfNotEmpty(content, "images", catalogContent.getImages());
            putIfNotNull(content, "imageCount", catalogContent.getImages().size());
        }
        if (stringList(content.get("highlightsEn")).isEmpty()) {
            putIfNotEmpty(content, "highlightsEn", catalogContent.getHighlightsEn());
        }
        if (stringList(content.get("highlightsAr")).isEmpty()) {
            putIfNotEmpty(content, "highlightsAr", catalogContent.getHighlightsAr());
        }
    }

    private Map<String, Object> buildPlatformSignals(JsonNode commonNode) {
        Map<String, Object> platformSignals = new LinkedHashMap<>();
        putIfNotBlank(platformSignals, "qcState", text(commonNode, "qc_state"));
        putIfNotBlank(platformSignals, "statusQc", text(commonNode, "status_qc_localized"));
        putIfNotBlank(platformSignals, "isActiveLocalized", text(commonNode, "is_active_localized"));
        putIfNotBlank(platformSignals, "qcApproved", text(commonNode, "qc_approved_localized"));
        putIfNotBlank(platformSignals, "completenessMandatory", text(commonNode, "status_completeness_mandatory"));
        putIfNotBlank(platformSignals, "completenessLocalized", text(commonNode, "status_completeness_details_localized"));
        putIfNotBlank(platformSignals, "qcSource", text(commonNode, "noon_qc_source_localized"));
        putIfNotNull(platformSignals, "statusImages", numberOrText(commonNode.path("status_images")));
        putIfNotNull(platformSignals, "imageCount", collectImages(commonNode).size());
        putIfNotNull(platformSignals, "hiddenImageCount", countHiddenImages(commonNode));
        putIfNotEmpty(
                platformSignals,
                "rejectionReasons",
                collectNodeTextList(commonNode.path("noon_qc_rejection_reasons_localized"))
        );
        putIfNotEmpty(
                platformSignals,
                "affectingAttributes",
                collectNodeTextList(commonNode.path("is_active_localized_affecting_attributes"))
        );
        return platformSignals;
    }

    private List<Map<String, Object>> buildKeyAttributes(
            JsonNode fulltypeTemplateRoot,
            JsonNode commonNode,
            JsonNode enNode,
            JsonNode arNode
    ) {
        JsonNode templateRoot = fulltypeTemplateDataNode(fulltypeTemplateRoot);
        Map<String, JsonNode> dictionaryByCode = attributeDictionaryMap(fulltypeTemplateRoot);
        List<Map<String, Object>> attributes = new ArrayList<>();
        Set<String> mandatoryCodes = new LinkedHashSet<>();
        JsonNode mandatoryNode = templateRoot.path("fundamental").path("attribute_class").path("mandatory");
        if (mandatoryNode.isArray()) {
            for (JsonNode node : mandatoryNode) {
                if (node.isTextual()) {
                    mandatoryCodes.add(node.asText());
                }
            }
        }

        JsonNode attributePropertiesNode = templateRoot.path("fundamental").path("attribute_properties");
        Set<String> candidateCodes = new LinkedHashSet<>(mandatoryCodes);
        if (attributePropertiesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = attributePropertiesNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode propertyNode = entry.getValue();
                if (propertyNode.path("is_grouping").asInt(0) == 1
                        || propertyNode.path("is_visible_seller").asInt(0) == 1) {
                    candidateCodes.add(entry.getKey());
                }
            }
        }
        for (JsonNode dictionaryNode : dictionaryByCode.values()) {
            String dictionaryCode = text(dictionaryNode, "code");
            if (StringUtils.hasText(dictionaryCode)) {
                candidateCodes.add(dictionaryCode);
            }
        }

        if (candidateCodes.isEmpty()) {
            candidateCodes.add("brand");
            candidateCodes.add("model_name");
            candidateCodes.add("model_number");
            candidateCodes.add("colour_family");
            candidateCodes.add("colour_name");
            candidateCodes.add("item_condition");
        }

        for (String code : candidateCodes) {
            JsonNode propertyNode = attributePropertiesNode.path(code);
            JsonNode dictionaryNode = dictionaryByCode.getOrDefault(normalizeAttributeCode(code), MissingNode.getInstance());
            List<Map<String, Object>> options = extractAttributeOptions(templateRoot, propertyNode, code);
            List<Map<String, Object>> unitOptions = extractAttributeUnitOptions(templateRoot, propertyNode, code);
            if (options.isEmpty()) {
                options = extractDictionaryOptions(dictionaryNode.path("options"));
            }
            if (unitOptions.isEmpty()) {
                unitOptions = extractDictionaryOptions(dictionaryNode.path("unitOptions"));
            }
            Object commonValue = toDisplayValue(commonNode.path(code));
            Object enValue = toDisplayValue(enNode.path(code));
            Object arValue = toDisplayValue(arNode.path(code));
            String unitValue = firstNonBlank(
                    text(commonNode, code + "_unit"),
                    text(enNode, code + "_unit"),
                    text(arNode, code + "_unit")
            );
            boolean required = mandatoryCodes.contains(code);
            if (!required && dictionaryNode.path("required").asBoolean(false)) {
                required = true;
            }
            boolean grouping = propertyNode.path("is_grouping").asInt(0) == 1
                    || dictionaryNode.path("grouping").asBoolean(false);
            boolean visibleSeller = propertyNode.path("is_visible_seller").asInt(0) == 1
                    || dictionaryNode.path("visibleSeller").asBoolean(false);

            if (!required && !grouping && !visibleSeller
                    && commonValue == null && enValue == null && arValue == null) {
                continue;
            }

            Map<String, Object> attribute = new LinkedHashMap<>();
            attribute.put("code", code);
            attribute.put("required", required);
            attribute.put("grouping", grouping);
            attribute.put("visibleSeller", visibleSeller);
            attribute.put("kind", resolveDictionaryInputKind(dictionaryNode, code, propertyNode, options, unitOptions));
            putIfNotBlank(attribute, "labelEn", firstNonBlank(
                    resolveAttributeLabel(propertyNode, code, "en"),
                    text(dictionaryNode, "labelEn")
            ));
            putIfNotBlank(attribute, "labelAr", firstNonBlank(
                    resolveAttributeLabel(propertyNode, code, "ar"),
                    text(dictionaryNode, "labelAr")
            ));
            putIfNotBlank(attribute, "groupName", firstNonBlank(
                    text(propertyNode.path("attribute_group_name"), "en"),
                    text(dictionaryNode, "groupName")
            ));
            putIfNotEmpty(attribute, "options", options);
            putIfNotEmpty(attribute, "unitOptions", unitOptions);
            if (!options.isEmpty() || !unitOptions.isEmpty()) {
                attribute.put("dictionarySource", firstNonBlank(text(dictionaryNode, "dictionarySource"), "official-template"));
            }
            putIfNotNull(attribute, "commonValue", commonValue);
            putIfNotNull(attribute, "enValue", enValue);
            putIfNotNull(attribute, "arValue", arValue);
            putIfNotBlank(attribute, "unit", unitValue);
            attributes.add(attribute);
        }

        return attributes;
    }

    private JsonNode fulltypeTemplateDataNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return MissingNode.getInstance();
        }
        if (root.path("fundamental").isObject()) {
            return root;
        }
        JsonNode dataCandidate = fulltypeTemplateDataNodeFromContainer(root.path("data"));
        if (!dataCandidate.isMissingNode()) {
            return dataCandidate;
        }
        JsonNode rootCandidate = fulltypeTemplateDataNodeFromContainer(root);
        if (!rootCandidate.isMissingNode()) {
            return rootCandidate;
        }
        return root;
    }

    private JsonNode fulltypeTemplateDataNodeFromContainer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        if (node.path("fundamental").isObject()) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(item);
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
            return MissingNode.getInstance();
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                JsonNode candidate = fulltypeTemplateDataNodeFromContainer(iterator.next().getValue());
                if (!candidate.isMissingNode()) {
                    return candidate;
                }
            }
        }
        return MissingNode.getInstance();
    }

    private Map<String, JsonNode> attributeDictionaryMap(JsonNode fulltypeTemplateRoot) {
        Map<String, JsonNode> byCode = new LinkedHashMap<>();
        JsonNode dictionaryNode = fulltypeTemplateRoot == null
                ? MissingNode.getInstance()
                : fulltypeTemplateRoot.path("_nuonoAttributeDictionary");
        if (!dictionaryNode.isArray()) {
            return byCode;
        }
        for (JsonNode fieldNode : dictionaryNode) {
            String code = text(fieldNode, "code");
            String key = normalizeAttributeCode(code);
            if (StringUtils.hasText(key)) {
                byCode.putIfAbsent(key, fieldNode);
            }
        }
        return byCode;
    }

    private String resolveDictionaryInputKind(
            JsonNode dictionaryNode,
            String code,
            JsonNode propertyNode,
            List<Map<String, Object>> options,
            List<Map<String, Object>> unitOptions
    ) {
        String resolvedKind = resolveAttributeInputKind(code, propertyNode, options, unitOptions);
        if (!"text".equals(resolvedKind)) {
            return resolvedKind;
        }
        String dictionaryKind = text(dictionaryNode, "kind");
        if (!StringUtils.hasText(dictionaryKind)) {
            return resolvedKind;
        }
        String normalizedKind = dictionaryKind.trim().toLowerCase();
        if ("select".equals(normalizedKind) || "dimension".equals(normalizedKind) || "textarea".equals(normalizedKind)) {
            return normalizedKind;
        }
        return resolvedKind;
    }

    private List<Map<String, Object>> extractDictionaryOptions(JsonNode node) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, node);
        return options;
    }

    private String resolveAttributeInputKind(
            String code,
            JsonNode propertyNode,
            List<Map<String, Object>> options,
            List<Map<String, Object>> unitOptions
    ) {
        if (!unitOptions.isEmpty() || isDimensionAttribute(code)) {
            return "dimension";
        }
        if (!options.isEmpty()) {
            return "select";
        }
        String valueType = firstNonBlank(
                text(propertyNode, "value_type"),
                text(propertyNode, "data_type"),
                text(propertyNode, "input_type"),
                text(propertyNode, "type")
        );
        if (!StringUtils.hasText(valueType)) {
            return "text";
        }
        String normalizedType = valueType.toLowerCase();
        if (normalizedType.contains("select") || normalizedType.contains("enum") || normalizedType.contains("option")) {
            return "select";
        }
        if (normalizedType.contains("textarea") || normalizedType.contains("rich") || normalizedType.contains("long")) {
            return "textarea";
        }
        return "text";
    }

    private List<Map<String, Object>> extractAttributeOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> options = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(options, seen, firstExisting(
                propertyNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode specsNode = firstExisting(propertyNode, "specs", "attribute_specs", "attributeSpecs");
        collectOptionsFromNode(options, seen, firstExisting(
                specsNode,
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        ));
        JsonNode templateSpecs = firstExisting(
                templateRoot.path("fundamental").path("attribute_specs").path(code),
                "options",
                "attribute_options",
                "option_values",
                "values",
                "allowed_values",
                "allowedValues"
        );
        collectOptionsFromNode(options, seen, templateSpecs);
        return options;
    }

    private List<Map<String, Object>> extractAttributeUnitOptions(JsonNode templateRoot, JsonNode propertyNode, String code) {
        List<Map<String, Object>> unitOptions = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                propertyNode,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        JsonNode templateSpecs = templateRoot.path("fundamental").path("attribute_specs").path(code);
        collectOptionsFromNode(unitOptions, seen, firstExisting(
                templateSpecs,
                "unit_options",
                "unitOptions",
                "units",
                "allowed_units",
                "allowedUnits",
                "measurement_units"
        ));
        if (unitOptions.isEmpty() && isDimensionAttribute(code)) {
            String[] fallbackUnits = code.toLowerCase().contains("weight")
                    ? new String[]{"g", "KG", "lb", "lbs"}
                    : new String[]{"mm", "cm", "m", "in", "ft"};
            for (String unit : fallbackUnits) {
                addOption(unitOptions, seen, unit, unit, null);
            }
        }
        return unitOptions;
    }

    private void collectOptionsFromNode(List<Map<String, Object>> options, Set<String> seen, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectSingleOption(options, seen, item);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode item = entry.getValue();
                if (item.isValueNode()) {
                    addOption(options, seen, entry.getKey(), item.asText(), null);
                } else {
                    collectSingleOption(options, seen, item);
                }
            }
            return;
        }
        collectSingleOption(options, seen, node);
    }

    private void collectSingleOption(List<Map<String, Object>> options, Set<String> seen, JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return;
        }
        if (item.isValueNode()) {
            String value = item.asText();
            addOption(options, seen, value, value, null);
            return;
        }
        String en = firstNonBlank(
                localizedText(item, "option_name", "en"),
                localizedText(item, "name", "en"),
                localizedText(item, "label", "en"),
                text(item, "option_name_en"),
                text(item, "name_en"),
                text(item, "label_en"),
                text(item, "en")
        );
        String ar = firstNonBlank(
                localizedText(item, "option_name", "ar"),
                localizedText(item, "name", "ar"),
                localizedText(item, "label", "ar"),
                text(item, "option_name_ar"),
                text(item, "name_ar"),
                text(item, "label_ar"),
                text(item, "ar")
        );
        String value = firstNonBlank(
                text(item, "value"),
                text(item, "option_value"),
                text(item, "code"),
                text(item, "option_code"),
                en
        );
        addOption(options, seen, value, firstNonBlank(en, value), ar);
    }

    private void addOption(List<Map<String, Object>> options, Set<String> seen, String value, String en, String ar) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(en)) {
            return;
        }
        String key = value.trim().toLowerCase();
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value.trim());
        option.put("en", en.trim());
        putIfNotBlank(option, "ar", ar);
        options.add(option);
    }

    private String resolveAttributeLabel(JsonNode propertyNode, String code, String lang) {
        return firstNonBlank(
                localizedText(propertyNode, "attribute_name", lang),
                localizedText(propertyNode, "display_name", lang),
                localizedText(propertyNode, "name", lang),
                localizedText(propertyNode, "label", lang),
                text(propertyNode, "attribute_name_" + lang),
                text(propertyNode, "display_name_" + lang),
                text(propertyNode, "name_" + lang),
                text(propertyNode, "label_" + lang),
                "en".equals(lang) ? humanizeAttributeCode(code) : null
        );
    }

    private String localizedText(JsonNode node, String field, String lang) {
        JsonNode valueNode = node != null ? node.path(field) : MissingNode.getInstance();
        if (valueNode.isObject()) {
            return text(valueNode, lang);
        }
        if ("en".equals(lang) && valueNode.isValueNode()) {
            return valueNode.asText();
        }
        return null;
    }

    private boolean isDimensionAttribute(String code) {
        String normalizedCode = normalize(code);
        if (!StringUtils.hasText(normalizedCode)) {
            return false;
        }
        String lowerCaseCode = normalizedCode.toLowerCase();
        return lowerCaseCode.contains("height")
                || lowerCaseCode.contains("length")
                || lowerCaseCode.contains("weight")
                || lowerCaseCode.contains("width")
                || lowerCaseCode.contains("depth");
    }

    private String humanizeAttributeCode(String code) {
        String normalizedCode = normalize(code);
        if (!StringUtils.hasText(normalizedCode)) {
            return null;
        }
        String[] parts = normalizedCode.replace('-', '_').split("_+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return builder.toString();
    }

    private ObjectNode buildGroupParentAttributeFetchBody(JsonNode groupDetailRoot, String skuGroup) {
        if (!StringUtils.hasText(skuGroup)) {
            return null;
        }
        JsonNode groupDetailNode = groupDetailRoot.path(skuGroup);
        LinkedHashSet<String> skuParents = new LinkedHashSet<>();
        JsonNode membersNode = groupDetailNode.path("zsku_parents");
        if (membersNode.isArray()) {
            for (JsonNode memberNode : membersNode) {
                String skuParent = memberNode.isTextual()
                        ? memberNode.asText()
                        : firstNonBlank(
                                text(memberNode, "sku_parent"),
                                text(memberNode, "zsku_parent"),
                                text(memberNode, "skuParent")
                        );
                if (StringUtils.hasText(skuParent)) {
                    skuParents.add(skuParent);
                }
            }
        }
        LinkedHashSet<String> axisCodes = new LinkedHashSet<>();
        JsonNode axesNode = groupDetailNode.path("axes");
        if (axesNode.isArray()) {
            for (JsonNode axisNode : axesNode) {
                String axisCode = text(axisNode, "axis_code");
                if (StringUtils.hasText(axisCode)) {
                    axisCodes.add(axisCode);
                }
            }
        }
        if (skuParents.isEmpty() || axisCodes.isEmpty()) {
            return null;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode skuParentsNode = body.putArray("skuParents");
        skuParents.forEach(skuParentsNode::add);
        ArrayNode attributeCodesNode = body.putArray("attributeCodes");
        axisCodes.forEach(attributeCodesNode::add);
        return body;
    }

    private Map<String, Object> buildGroup(
            JsonNode groupCurrentNode,
            JsonNode groupDetailRoot,
            JsonNode groupListNode,
            String skuGroup,
            JsonNode groupParentAttributesRoot
    ) {
        Map<String, Object> group = new LinkedHashMap<>();
        putIfNotBlank(group, "skuGroup", skuGroup);
        putIfNotNull(group, "candidateGroupCount", groupListNode.isArray() ? groupListNode.size() : 0);

        JsonNode groupDetailNode = StringUtils.hasText(skuGroup)
                ? groupDetailRoot.path(skuGroup)
                : MissingNode.getInstance();
        JsonNode groupMeta = groupDetailNode.path("group");
        putIfNotBlank(group, "groupRef", text(groupMeta, "group_ref"));
        putIfNotBlank(group, "groupRefCanonical", text(groupMeta, "group_ref_canonical"));
        JsonNode conditionsNode = groupDetailNode.path("conditions");
        putIfNotBlank(group, "conditionsBrand", text(conditionsNode, "brand"));
        putIfNotBlank(group, "conditionsFulltype", text(conditionsNode, "fulltype"));

        JsonNode axesNode = groupDetailNode.path("axes");
        List<Map<String, Object>> axes = new ArrayList<>();
        if (axesNode.isArray()) {
            for (JsonNode axisNode : axesNode) {
                Map<String, Object> axis = new LinkedHashMap<>();
                putIfNotBlank(axis, "axisCode", text(axisNode, "axis_code"));
                putIfNotBlank(axis, "axisName", text(axisNode, "axis_name"));
                if (!axis.isEmpty()) {
                    axes.add(axis);
                }
            }
        }
        putIfNotNull(group, "memberCount", groupDetailNode.path("zsku_parents").size());
        putIfNotEmpty(group, "axes", axes);
        putIfNotEmpty(group, "members", buildGroupMembers(groupDetailNode.path("zsku_parents"), axes, groupParentAttributesRoot));
        putIfNotEmpty(group, "candidateGroups", buildCandidateGroups(groupListNode));

        if (!StringUtils.hasText(skuGroup) && groupCurrentNode.isObject()) {
            putIfNotBlank(group, "state", "当前商品未挂 group");
        }

        return group;
    }

    private List<Map<String, Object>> buildGroupMembers(
            JsonNode membersNode,
            List<Map<String, Object>> axes,
            JsonNode groupParentAttributesRoot
    ) {
        List<Map<String, Object>> members = new ArrayList<>();
        if (!membersNode.isArray()) {
            return members;
        }

        List<String> axisCodes = groupAxisCodes(axes);
        for (JsonNode memberNode : membersNode) {
            Map<String, Object> member = new LinkedHashMap<>();
            if (memberNode.isTextual()) {
                putIfNotBlank(member, "skuParent", memberNode.asText());
            } else {
                putIfNotBlank(
                        member,
                        "skuParent",
                        firstNonBlank(
                                text(memberNode, "sku_parent"),
                                text(memberNode, "zsku_parent"),
                                text(memberNode, "skuParent")
                        )
                );
                putIfNotBlank(member, "title", text(memberNode, "title"));
                putIfNotBlank(member, "imageKey", text(memberNode, "image_key"));
                putIfNotBlank(member, "imageUrl", text(memberNode, "image_url"));
                putIfNotBlank(member, "groupRef", text(memberNode, "group_ref"));
                putIfNotBlank(member, "groupRefCanonical", text(memberNode, "group_ref_canonical"));
            }
            enrichGroupMemberAxisValues(member, axisCodes, groupParentAttributesRoot);
            if (!member.isEmpty()) {
                members.add(member);
            }
        }

        return members;
    }

    private List<String> groupAxisCodes(List<Map<String, Object>> axes) {
        List<String> axisCodes = new ArrayList<>();
        if (axes == null) {
            return axisCodes;
        }
        for (Map<String, Object> axis : axes) {
            String axisCode = firstNonBlank(textValue(axis.get("axisCode")), textValue(axis.get("axis_code")));
            if (StringUtils.hasText(axisCode)) {
                axisCodes.add(axisCode);
            }
        }
        return axisCodes;
    }

    private void enrichGroupMemberAxisValues(
            Map<String, Object> member,
            List<String> axisCodes,
            JsonNode groupParentAttributesRoot
    ) {
        if (member == null || axisCodes == null || axisCodes.isEmpty()) {
            return;
        }
        String skuParent = textValue(member.get("skuParent"));
        if (!StringUtils.hasText(skuParent)) {
            return;
        }
        JsonNode attributesNode = groupParentAttributesRoot != null
                ? groupParentAttributesRoot.path(skuParent).path("attributes")
                : MissingNode.getInstance();
        JsonNode commonNode = attributesNode.path("common");
        JsonNode enNode = attributesNode.path("en");
        JsonNode arNode = attributesNode.path("ar");
        Map<String, Object> axisValues = new LinkedHashMap<>();
        Map<String, Object> axisValuesAr = new LinkedHashMap<>();
        for (String axisCode : axisCodes) {
            String enValue = firstNonBlank(text(enNode, axisCode), text(commonNode, axisCode));
            String arValue = text(arNode, axisCode);
            if (StringUtils.hasText(enValue)) {
                axisValues.put(axisCode, enValue);
                member.put(axisCode, enValue);
                if (!member.containsKey("axisValue")) {
                    member.put("axisValue", enValue);
                }
            }
            if (StringUtils.hasText(arValue)) {
                axisValuesAr.put(axisCode, arValue);
            }
        }
        putMapIfNotEmpty(member, "axisValues", axisValues);
        putMapIfNotEmpty(member, "axisValuesAr", axisValuesAr);
    }

    private List<Map<String, Object>> buildCandidateGroups(JsonNode groupListNode) {
        List<Map<String, Object>> candidateGroups = new ArrayList<>();
        if (!groupListNode.isArray()) {
            return candidateGroups;
        }

        for (JsonNode candidateNode : groupListNode) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            JsonNode groupNode = candidateNode.path("group");
            JsonNode conditionsNode = candidateNode.path("conditions");
            putIfNotBlank(
                    candidate,
                    "skuGroup",
                    firstNonBlank(
                            text(candidateNode, "zsku_group"),
                            text(candidateNode, "sku_group"),
                            text(groupNode, "zsku_group")
                    )
            );
            putIfNotBlank(
                    candidate,
                    "groupRef",
                    firstNonBlank(
                            text(candidateNode, "group_ref"),
                            text(groupNode, "group_ref")
                    )
            );
            putIfNotBlank(
                    candidate,
                    "groupRefCanonical",
                    firstNonBlank(
                            text(candidateNode, "group_ref_canonical"),
                            text(groupNode, "group_ref_canonical")
                    )
            );
            putIfNotBlank(candidate, "brand", firstNonBlank(text(candidateNode, "brand"), text(conditionsNode, "brand")));
            putIfNotBlank(
                    candidate,
                    "fulltype",
                    firstNonBlank(text(candidateNode, "fulltype"), text(conditionsNode, "fulltype"))
            );
            if (candidateNode.path("zsku_parents").isArray()) {
                putIfNotNull(candidate, "memberCount", candidateNode.path("zsku_parents").size());
            }
            if (!candidate.isEmpty()) {
                candidateGroups.add(candidate);
            }
        }

        return candidateGroups;
    }

    private List<Map<String, Object>> buildVariants(JsonNode variantInfoRoot, JsonNode productNode) {
        Map<String, Map<String, Object>> variants = new LinkedHashMap<>();
        mergeVariantInfoRecords(variants, variantInfoRoot);
        mergeVariantSnapshotRecords(variants, productNode != null ? productNode.path("variants") : MissingNode.getInstance());
        return new ArrayList<>(variants.values());
    }

    private void mergeVariantInfoRecords(Map<String, Map<String, Object>> variants, JsonNode variantInfoRoot) {
        if (variantInfoRoot == null || !variantInfoRoot.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> iterator = variantInfoRoot.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            mergeVariantRecord(variants, entry.getKey(), entry.getValue());
        }
    }

    private void mergeVariantSnapshotRecords(Map<String, Map<String, Object>> variants, JsonNode variantsNode) {
        if (variantsNode == null || variantsNode.isMissingNode() || variantsNode.isNull()) {
            return;
        }
        if (variantsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = variantsNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                mergeVariantRecord(variants, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (variantsNode.isArray()) {
            for (JsonNode item : variantsNode) {
                mergeVariantRecord(variants, null, item);
            }
        }
    }

    private void mergeVariantRecord(Map<String, Map<String, Object>> variants, String fallbackChildSku, JsonNode variantNode) {
        String childSku = firstNonBlank(
                fallbackChildSku,
                text(variantNode, "childSku"),
                text(variantNode, "child_sku"),
                text(variantNode, "sku_child"),
                text(variantNode, "sku"),
                text(variantNode, "zsku"),
                text(variantNode, "zsku_child")
        );
        if (!StringUtils.hasText(childSku)) {
            return;
        }

        Map<String, Object> variant = variants.computeIfAbsent(childSku, (key) -> {
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("childSku", key);
            return next;
        });
        putIfAbsentNotBlank(variant, "partnerSku", firstNonBlank(
                text(variantNode, "partnerSku"),
                text(variantNode, "partner_sku"),
                text(variantNode, "catalogSku"),
                text(variantNode, "catalog_sku"),
                text(variantNode, "sellerSku"),
                text(variantNode, "seller_sku")
        ));
        putIfAbsentNotBlank(variant, "pskuCode", firstNonBlank(
                text(variantNode, "pskuCode"),
                text(variantNode, "psku_code")
        ));
        putIfAbsentNotBlank(variant, "sizeEn", extractVariantSize(variantNode, "en"));
        putIfAbsentNotBlank(variant, "sizeAr", extractVariantSize(variantNode, "ar"));
        putIfAbsentNumber(variant, "variantIndex", firstNonMissingNode(
                variantNode.path("ix"),
                variantNode.path("variantIndex"),
                variantNode.path("variant_ix")
        ));
    }

    private String extractVariantSize(JsonNode variantNode, String lang) {
        String langSuffix = "en".equals(lang) ? "en" : "ar";
        String camelSuffix = "en".equals(lang) ? "En" : "Ar";
        return firstNonBlank(
                localizedNodeText(variantNode.path("size"), lang),
                localizedNodeText(variantNode.path("seller_size"), lang),
                localizedNodeText(variantNode.path("sellerSize"), lang),
                localizedNodeText(variantNode.path("attributes").path("size"), lang),
                localizedNodeText(variantNode.path("attributes").path(lang).path("size"), lang),
                localizedNodeText(variantNode.path("attributes").path("common").path("size"), lang),
                text(variantNode, "size_" + langSuffix),
                text(variantNode, "size" + camelSuffix),
                text(variantNode, langSuffix + "_size"),
                text(variantNode, "seller_size_" + langSuffix),
                text(variantNode, "sellerSize" + camelSuffix)
        );
    }

    private String localizedNodeText(JsonNode node, String lang) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return textValue(node.asText());
        }
        String value = firstNonBlank(
                text(node, lang),
                text(node, lang.toUpperCase()),
                text(node, "value"),
                text(node, "name"),
                text(node, "label")
        );
        return StringUtils.hasText(value) ? value : null;
    }

    private JsonNode firstNonMissingNode(JsonNode... nodes) {
        if (nodes == null) {
            return MissingNode.getInstance();
        }
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return MissingNode.getInstance();
    }

    private void putIfAbsentNotBlank(Map<String, Object> target, String key, String value) {
        if (!target.containsKey(key) && StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private void putIfAbsentNumber(Map<String, Object> target, String key, JsonNode valueNode) {
        if (target.containsKey(key) || valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return;
        }
        if (valueNode.isInt() || valueNode.isLong()) {
            target.put(key, valueNode.asInt());
        }
    }

    private Map<String, Object> buildPricing(JsonNode pricingRoot) {
        Map<String, Object> pricing = new LinkedHashMap<>();
        JsonNode pricingItem = firstDataItem(pricingRoot);
        if (pricingItem.isMissingNode()) {
            return pricing;
        }

        putIfNotBlank(pricing, "partnerSku", text(pricingItem, "psku"));
        putIfNotBlank(pricing, "offerCode", text(pricingItem, "offer_code"));
        putIfNotBlank(pricing, "currency", text(pricingItem, "currency"));
        putIfNotBlank(pricing, "barcode", firstNonBlank(
                text(pricingItem, "barcode"),
                text(pricingItem, "gtin"),
                text(pricingItem, "ean"),
                text(pricingItem, "upc")
        ));
        putIfNotNull(pricing, "price", numberOrText(pricingItem.path("price")));
        putIfNotNull(pricing, "salePrice", numberOrText(pricingItem.path("sale_price")));
        putIfNotBlank(pricing, "saleStart", text(pricingItem, "sale_start"));
        putIfNotBlank(pricing, "saleEnd", text(pricingItem, "sale_end"));
        putIfNotNull(pricing, "priceMin", numberOrText(pricingItem.path("price_min")));
        putIfNotNull(pricing, "priceMax", numberOrText(pricingItem.path("price_max")));
        putIfNotNull(
                pricing,
                "finalPrice",
                numberOrText(firstExisting(
                        pricingItem,
                        "final_price",
                        "finalPrice",
                        "final_selling_price",
                        "finalSellingPrice",
                        "selling_price",
                        "sellingPrice",
                        "current_price",
                        "currentPrice",
                        "promo_price",
                        "promoPrice",
                        "promotion_price",
                        "promotionPrice",
                        "deal_price",
                        "dealPrice"
                ))
        );
        putIfNotBlank(
                pricing,
                "finalPriceSource",
                firstNonBlank(text(pricingItem, "final_price_source"), text(pricingItem, "price_source"))
        );
        putIfNotBlank(
                pricing,
                "activePromotionCode",
                firstNonBlank(
                        text(pricingItem, "active_promotion_code"),
                        text(pricingItem, "promotion_code"),
                        text(pricingItem, "promo_code"),
                        text(pricingItem, "campaign_code"),
                        text(pricingItem, "deal_code")
                )
        );
        putIfNotBlank(
                pricing,
                "activePromotionName",
                firstNonBlank(
                        text(pricingItem, "active_promotion_name"),
                        text(pricingItem, "promotion_name"),
                        text(pricingItem, "promo_name"),
                        text(pricingItem, "campaign_name"),
                        text(pricingItem, "deal_name")
                )
        );
        putIfNotBlank(
                pricing,
                "activePromotionUrl",
                firstNonBlank(
                        text(pricingItem, "active_promotion_url"),
                        text(pricingItem, "promotion_url"),
                        text(pricingItem, "promo_url"),
                        text(pricingItem, "campaign_url"),
                        text(pricingItem, "deal_url")
                )
        );
        pricing.put("pricingMethod", PRICING_METHOD_MANUAL);
        putIfNotBlank(pricing, "offerNote", text(pricingItem, "offer_note"));
        if (!pricingItem.path("is_active").isMissingNode() && !pricingItem.path("is_active").isNull()) {
            pricing.put("isActive", pricingItem.path("is_active").asBoolean());
        }
        putIfNotNull(pricing, "idWarranty", numberOrText(pricingItem.path("id_warranty")));
        putIfNotBlank(pricing, "priceSource", text(pricingItem, "price_source"));
        putIfNotNull(pricing, "liveStatus", pricingItem.path("live_status").isBoolean()
                ? pricingItem.path("live_status").asBoolean()
                : null);
        return pricing;
    }

    private Map<String, Object> buildStock(JsonNode stockRoot) {
        Map<String, Object> stock = new LinkedHashMap<>();
        JsonNode stockItem = firstDataItem(stockRoot);
        if (stockItem.isMissingNode()) {
            return stock;
        }

        putIfNotBlank(stock, "pskuCode", text(stockItem, "psku_code"));
        putIfNotNull(stock, "fbnStock", numberOrText(stockItem.path("fbn_stock")));
        putIfNotNull(stock, "supermallStock", numberOrText(firstExisting(stockItem, "supermall_stock", "supermall_fbn_stock", "fbn_supermall_stock")));
        putIfNotNull(stock, "fbpStock", numberOrText(stockItem.path("fbp_stock")));
        return stock;
    }

    private Map<String, Object> buildSiteOffer(
            ProjectSiteContext projectSite,
            JsonNode pricingRoot,
            JsonNode stockRoot,
            boolean reference
    ) {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        putIfNotBlank(siteOffer, "storeCode", projectSite.getStoreCode());
        putIfNotBlank(siteOffer, "site", projectSite.getSite());
        putIfNotBlank(siteOffer, "statusCode", projectSite.getStatusCode());
        putIfNotNull(siteOffer, "reference", reference);

        Map<String, Object> pricing = buildPricing(pricingRoot);
        Map<String, Object> stock = buildStock(stockRoot);
        siteOffer.putAll(pricing);
        siteOffer.putAll(stock);
        return siteOffer;
    }

    private List<StoreSyncStoreRecord> findRelatedStores(Long ownerUserId, StoreSyncStoreRecord referenceStore) {
        List<StoreSyncStoreRecord> ownerStores = storeSyncMapper.listOwnerStores(ownerUserId);
        String projectKey = projectKey(referenceStore);
        List<StoreSyncStoreRecord> relatedStores = new ArrayList<>();
        for (StoreSyncStoreRecord ownerStore : ownerStores) {
            if (projectKey.equals(projectKey(ownerStore))) {
                relatedStores.add(ownerStore);
            }
        }
        if (relatedStores.isEmpty()) {
            relatedStores.add(referenceStore);
        }
        return relatedStores;
    }

    private String projectKey(StoreSyncStoreRecord store) {
        String projectCode = normalize(store.getProjectCode());
        if (StringUtils.hasText(projectCode)) {
            return "project:" + projectCode.toLowerCase();
        }
        String projectName = normalize(store.getProjectName());
        if (StringUtils.hasText(projectName)) {
            return "project-name:" + projectName.toLowerCase();
        }
        return "store:" + normalize(store.getStoreCode());
    }

    private String deriveSiteFromStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode) || storeCode.length() < 2) {
            return null;
        }
        String suffix = storeCode.substring(storeCode.length() - 2).toUpperCase();
        if ("SA".equals(suffix) || "AE".equals(suffix)) {
            return suffix;
        }
        return null;
    }

    private String describeSite(ProjectSiteContext projectSite) {
        String storeCode = firstNonBlank(projectSite.getStoreCode(), "未知站点");
        if (!StringUtils.hasText(projectSite.getSite())) {
            return storeCode;
        }
        return projectSite.getSite() + " / " + storeCode;
    }

    private JsonNode safeGet(
            NoonSession session,
            String url,
            boolean withProject,
            List<String> warnings,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.getJson(session, url, withProject);
        } catch (IllegalStateException exception) {
            warnings.add(warningPrefix + "：" + noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private JsonNode safePost(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            List<String> warnings,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.postJson(session, url, body, withProject);
        } catch (IllegalStateException exception) {
            warnings.add(warningPrefix + "：" + noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private JsonNode safePostOptional(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.postJson(session, url, body, withProject);
        } catch (IllegalStateException exception) {
            log.warn("{}：{}", warningPrefix, noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private String noonFailureMessage(RuntimeException exception) {
        if (productNoonAdapter == null) {
            return shrink(exception.getMessage());
        }
        return shrink(productNoonAdapter.userMessage(exception));
    }

    private JsonNode firstDataItem(JsonNode root) {
        if (root.isObject() && root.path("data").isArray() && root.path("data").size() > 0) {
            return root.path("data").get(0);
        }
        if (root.isArray() && root.size() > 0) {
            return root.get(0);
        }
        return MissingNode.getInstance();
    }

    private JsonNode firstExisting(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            JsonNode candidate = node.path(fieldName);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return MissingNode.getInstance();
    }

    private List<String> collectOrderedText(JsonNode node, String fieldPrefix, int maxCount) {
        List<String> values = new ArrayList<>();
        for (int index = 1; index <= maxCount; index++) {
            String value = text(node, fieldPrefix + index);
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> collectImages(JsonNode commonNode) {
        Set<String> images = new LinkedHashSet<>();
        for (int index = 1; index <= 10; index++) {
            String imageUrl = text(commonNode, "image_url_" + index);
            if (StringUtils.hasText(imageUrl)) {
                images.add(imageUrl);
            }
        }
        for (int index = 1; index <= 10; index++) {
            String originalUrl = text(commonNode, "original_" + index);
            if (StringUtils.hasText(originalUrl)) {
                images.add(originalUrl);
            }
        }
        return new ArrayList<>(images);
    }

    private int countHiddenImages(JsonNode commonNode) {
        int hiddenCount = 0;
        for (int index = 1; index <= 10; index++) {
            JsonNode hiddenNode = commonNode.path("is_hidden_" + index);
            if (hiddenNode.isBoolean() && hiddenNode.asBoolean()) {
                hiddenCount++;
                continue;
            }
            if (hiddenNode.isTextual()) {
                String value = hiddenNode.asText();
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    hiddenCount++;
                }
            }
        }
        return hiddenCount;
    }

    private List<String> collectNodeTextList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.isValueNode() ? item.asText() : item.toString();
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            }
            return values;
        }

        String value = node.isValueNode() ? node.asText() : node.toString();
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
        return values;
    }

    private Object toDisplayValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private Object numberOrText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
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

    private void putComparableDecimalIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        BigDecimal decimal = asBigDecimal(value);
        if (decimal == null) {
            target.put(key, value);
            return;
        }
        target.put(key, decimal.stripTrailingZeros().toPlainString());
    }

    private void putComparableBooleanIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        target.put(key, truthy(value));
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

    private String resolveOwnerName(StoreSyncOwnerContext owner) {
        if (StringUtils.hasText(owner.getRealName())) {
            return owner.getRealName();
        }
        if (StringUtils.hasText(owner.getAccountNo())) {
            return owner.getAccountNo();
        }
        return "当前老板";
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

    private static final class ResolvedProjectCodeCacheEntry {

        private final String resolvedProjectCode;
        private final String warning;

        private ResolvedProjectCodeCacheEntry(String resolvedProjectCode, String warning) {
            this.resolvedProjectCode = resolvedProjectCode;
            this.warning = warning;
        }

        private String resolvedProjectCode() {
            return resolvedProjectCode;
        }

        private String warning() {
            return warning;
        }
    }

    private List<String> collectMissingOperationalKeys(String partnerSku, String pskuCode) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(partnerSku)) {
            missing.add("partnerSku");
        }
        if (!StringUtils.hasText(pskuCode)) {
            missing.add("pskuCode");
        }
        return missing;
    }

    private String extractDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\D+", "");
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

    private static class OperationalKeys {

        private final String partnerSku;

        private final String pskuCode;

        private OperationalKeys(String partnerSku, String pskuCode) {
            this.partnerSku = partnerSku;
            this.pskuCode = pskuCode;
        }

        private String getPartnerSku() {
            return partnerSku;
        }

        private String getPskuCode() {
            return pskuCode;
        }
    }

    private static class ProjectSiteContext {

        private final String storeCode;

        private String site;

        private String statusCode;

        private ProjectSiteContext(String storeCode, String site, String statusCode) {
            this.storeCode = storeCode;
            this.site = site;
            this.statusCode = statusCode;
        }

        private String getStoreCode() {
            return storeCode;
        }

        private String getSite() {
            return site;
        }

        private void setSite(String site) {
            this.site = site;
        }

        private String getStatusCode() {
            return statusCode;
        }

        private void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }
    }

    private static class ProjectSiteFetchResult {

        private final List<Map<String, Object>> siteOffers;

        private final JsonNode referencePricingNode;

        private final JsonNode referenceStockNode;

        private ProjectSiteFetchResult(
                List<Map<String, Object>> siteOffers,
                JsonNode referencePricingNode,
                JsonNode referenceStockNode
        ) {
            this.siteOffers = siteOffers;
            this.referencePricingNode = referencePricingNode;
            this.referenceStockNode = referenceStockNode;
        }

        private List<Map<String, Object>> getSiteOffers() {
            return siteOffers;
        }

        private JsonNode getReferencePricingNode() {
            return referencePricingNode;
        }

        private JsonNode getReferenceStockNode() {
            return referenceStockNode;
        }
    }

}
