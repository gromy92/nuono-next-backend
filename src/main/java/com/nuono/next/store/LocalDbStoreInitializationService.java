package com.nuono.next.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreInitializationSnapshotMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.product.NoonProductListFieldSupport;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.product.ProductListSummaryView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import com.nuono.next.product.ProductSourceTypeSupport;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import com.nuono.next.product.ProductNoonCatalogContentService;
import com.nuono.next.product.ProductNoonCatalogContentService.CatalogContent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbStoreInitializationService {

    private static final String PROJECT_LIST_URL =
            "https://toolbar.noon.partners/_svc/mp-partner-platform/project/list";
    private static final String STORE_LIST_URL =
            "https://noon-store.noon.partners/_svc/mp-noon-store/noon/store/list";
    private static final String OFFER_LIST_NOON_URL =
            NoonCatalogApiRoutes.OFFER_LIST_NOON;
    private static final String OFFER_LIST_SUPERMALL_URL =
            NoonCatalogApiRoutes.OFFER_LIST_SUPERMALL;
    private static final String ZSKU_RETRIEVE_URL =
            NoonCatalogApiRoutes.ZSKU_RETRIEVE;
    private static final int OFFER_PAGE_SIZE = 100;
    private static final int RETRIEVE_BATCH_SIZE = 20;
    private static final String NOON_REQUEST_TOTAL_KEY = "__total__";
    private static final List<String> DEFAULT_NOON_REQUEST_KEYS = List.of(
            "auth.signin",
            "auth.whoami",
            "project.list",
            "store.list",
            "offer.list",
            "zsku.retrieve"
    );
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StoreSyncMapper storeSyncMapper;
    private final StoreInitializationSnapshotMapper storeInitializationSnapshotMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final ObjectMapper objectMapper;
    private final NoonAccountTaskQueue noonAccountTaskQueue;
    private final NoonSessionGateway noonSessionGateway;
    private final ProductProjectionPersistenceService productProjectionPersistenceService;
    private final ProductNoonCatalogContentService productNoonCatalogContentService;
    private final Map<String, InitializationState> stateMap = new ConcurrentHashMap<>();

    public LocalDbStoreInitializationService(
            StoreSyncMapper storeSyncMapper,
            StoreInitializationSnapshotMapper storeInitializationSnapshotMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService,
            ObjectMapper objectMapper,
            NoonAccountTaskQueue noonAccountTaskQueue,
            NoonSessionGateway noonSessionGateway,
            ProductProjectionPersistenceService productProjectionPersistenceService,
            ProductNoonCatalogContentService productNoonCatalogContentService
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.storeInitializationSnapshotMapper = storeInitializationSnapshotMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.objectMapper = objectMapper;
        this.noonAccountTaskQueue = noonAccountTaskQueue;
        this.noonSessionGateway = noonSessionGateway;
        this.productProjectionPersistenceService = productProjectionPersistenceService;
        this.productNoonCatalogContentService = productNoonCatalogContentService;
    }

    public StoreInitializationStatusView getStatus(Long ownerUserId, String storeCode) {
        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        StoreInitializationStatusView blockedView = buildBlockedView(inspection);
        if (!inspection.isReady()) {
            return blockedView;
        }

        StoreContext context = resolveContext(ownerUserId, storeCode);
        InitializationState state = stateMap.get(stateKey(ownerUserId, storeCode));
        if (state != null) {
            return state.snapshot();
        }

        StoreInitializationStatusView persistedSnapshot = loadPersistedSnapshot(ownerUserId, storeCode, context);
        if (persistedSnapshot != null) {
            overlayProjectedProductItems(ownerUserId, storeCode, persistedSnapshot);
            return persistedSnapshot;
        }
        return buildIdleView(context);
    }

    public StoreInitializationStatusView start(StoreInitializationCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法启动店铺初始化。");
        }

        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少逻辑店铺参考站点，无法启动初始化。");

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        if (!inspection.isReady()) {
            throw new IllegalArgumentException("本地核心表还没齐备，当前不能启动店铺初始化。");
        }

        StoreContext context = resolveContext(command.getOwnerUserId(), storeCode);
        String key = stateKey(command.getOwnerUserId(), storeCode);
        InitializationState existingState = stateMap.get(key);
        if (existingState != null && existingState.isRunning()) {
            return existingState.snapshot();
        }

        String noonUser = resolveNoonUser(context);
        requireText(noonUser, "当前店铺还没有 Noon 账号上下文，请先完成店铺绑定。");
        requireText(resolveNoonCookie(context), "当前店铺会话待恢复，请稍后重试。");

        InitializationState state = new InitializationState(buildIdleView(context));
        state.markRunning("开始初始化", 5, 1, 8, "正在识别项目和站点结构...");
        stateMap.put(key, state);
        persistSnapshot(command.getOwnerUserId(), storeCode, state.snapshot());

        noonAccountTaskQueue.submit(noonUser, () -> runInitialization(
                state,
                context,
                noonUser
        ));
        return state.snapshot();
    }

    public StoreInitializationPreflightView preflight(StoreInitializationCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法执行店铺初始化预检。");
        }

        String storeCode = normalize(command.getStoreCode());
        requireText(storeCode, "缺少逻辑店铺参考站点，无法执行初始化预检。");

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        if (!inspection.isReady()) {
            throw new IllegalArgumentException("本地核心表还没齐备，当前不能执行店铺初始化预检。");
        }

        StoreContext context = resolveContext(command.getOwnerUserId(), storeCode);
        String noonUser = resolveNoonUser(context);
        requireText(noonUser, "当前店铺还没有 Noon 账号上下文，请先完成店铺绑定。");
        requireText(resolveNoonCookie(context), "当前店铺会话待恢复，请稍后重试。");

        String localProjectCode = firstNonBlank(context.referenceStore.getProjectCode(), context.owner.getNoonPartnerId());
        requireText(localProjectCode, "当前店铺缺少 Noon projectCode，暂时无法执行初始化预检。");

        StoreInitializationPreflightView view = buildPreflightView(context, localProjectCode);
        NoonSessionGateway.RequestCountScope requestCountScope = noonSessionGateway.openRequestCountScope();
        try {
            NoonSession session = openInitializationNoonSession(
                    requestOwnerId(context),
                    noonUser,
                    resolveNoonCookie(context),
                    localProjectCode,
                    context.referenceStore.getStoreCode()
            );
            ProjectAuthorizationCheck authorizationCheck =
                    inspectProjectAuthorization(session, localProjectCode, context.referenceStore);
            view.setCandidateProjectCount(authorizationCheck.candidateProjectCount);
            view.setCandidateProjectCodes(authorizationCheck.candidateProjectCodes);
            view.setMatchedProjectCode(authorizationCheck.matchedProjectCode);
            view.setMatchedProjectName(authorizationCheck.matchedProjectName);
            view.setNoonRequestCounts(normalizeNoonRequestCounts(requestCountScope.snapshot()));
            view.setNoonRequestTotalCount(view.getNoonRequestCounts().getOrDefault(NOON_REQUEST_TOTAL_KEY, 0));
            if (authorizationCheck.authorized) {
                view.setStatus("AUTHORIZED");
                view.setMessage("Noon 账号可访问目标项目 " + localProjectCode + "，可以启动初始化同步。");
            } else {
                view.setStatus("BLOCKED");
                view.setMessage("Noon project.list 未返回目标项目 " + localProjectCode + "，当前不能作为该店同步材料。");
            }
            return view;
        } catch (Exception exception) {
            view.setStatus("FAILED");
            view.setMessage(StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "店铺初始化预检失败。");
            view.setNoonRequestCounts(normalizeNoonRequestCounts(requestCountScope.snapshot()));
            view.setNoonRequestTotalCount(view.getNoonRequestCounts().getOrDefault(NOON_REQUEST_TOTAL_KEY, 0));
            return view;
        } finally {
            requestCountScope.close();
        }
    }

    private void runInitialization(
            InitializationState state,
            StoreContext context,
            String noonUser
    ) {
        NoonSessionGateway.RequestCountScope requestCountScope = noonSessionGateway.openRequestCountScope();
        try {
            String localProjectCode = firstNonBlank(context.referenceStore.getProjectCode(), context.owner.getNoonPartnerId());
            requireText(localProjectCode, "当前店铺缺少 Noon projectCode，暂时无法初始化。");

            NoonSession session = openInitializationNoonSession(
                    context.owner.getId(),
                    noonUser,
                    resolveNoonCookie(context),
                    localProjectCode,
                    context.referenceStore.getStoreCode()
            );
            String resolvedProjectCode = resolveProjectCode(
                    session,
                    localProjectCode,
                    context.referenceStore,
                    state.mutableWarnings()
            );
            ensureProjectAuthorization(localProjectCode, resolvedProjectCode, context.referenceStore);
            session = session.withProjectCode(resolvedProjectCode).withStoreCode(context.referenceStore.getStoreCode());

            List<ProjectSiteContext> projectSites = buildScopedProjectSites(context);
            state.updateStoreMeta(
                    context.referenceStore.getProjectName(),
                    resolvedProjectCode,
                    context.referenceStore.getStoreCode(),
                    projectSites.size()
            );
            state.completeStep("店铺结构已识别", 18);

            state.markRunning("拉商品清单", 5, 2, 28, "正在读取各站点商品索引...");
            List<StoreInitializationSiteSummaryView> siteSummaries = new ArrayList<>();
            LinkedHashMap<String, ProductIndexEntry> productIndexMap = new LinkedHashMap<>();
            int totalSiteOffers = 0;
            int finishedSites = 0;

            for (ProjectSiteContext projectSite : projectSites) {
                NoonSession siteSession = session.withStoreCode(projectSite.getStoreCode());
                OfferListFetchResult offerListFetchResult = fetchNoonOfferList(siteSession, projectSite, state.mutableWarnings());
                OfferListFetchResult supermallOfferListFetchResult = fetchSupermallOfferList(siteSession, projectSite, state.mutableWarnings());

                StoreInitializationSiteSummaryView siteSummary = new StoreInitializationSiteSummaryView();
                siteSummary.setStoreCode(projectSite.getStoreCode());
                siteSummary.setSite(projectSite.getSite());
                siteSummary.setLiveStatus(projectSite.getStatusCode());
                siteSummary.setProductCount(offerListFetchResult.total);
                siteSummaries.add(siteSummary);
                totalSiteOffers += offerListFetchResult.total;

                for (JsonNode hitNode : offerListFetchResult.hits) {
                    String skuParent = firstNonBlank(
                            text(hitNode, "csku_parent"),
                            text(hitNode, "zsku_parent"),
                            text(hitNode, "sku_parent"),
                            text(hitNode, "catalog_sku")
                    );
                    if (!StringUtils.hasText(skuParent)) {
                        continue;
                    }

                    ProductIndexEntry entry = productIndexMap.computeIfAbsent(skuParent, ProductIndexEntry::new);
                    if (!StringUtils.hasText(entry.partnerSku)) {
                        entry.partnerSku = text(hitNode, "partner_sku");
                    }
                    if (!StringUtils.hasText(entry.pskuCode)) {
                        entry.pskuCode = NoonProductListFieldSupport.pskuCode(hitNode);
                    }
                    if (!StringUtils.hasText(entry.offerCode)) {
                        entry.offerCode = text(hitNode, "offer_code");
                    }
                    if (!StringUtils.hasText(entry.catalogSku)) {
                        entry.catalogSku = firstNonBlank(
                                text(hitNode, "sku"),
                                text(hitNode, "catalog_sku"),
                                text(hitNode, "zsku_child"),
                                text(hitNode, "child_sku")
                        );
                    }
                    List<String> hitBarcodes = NoonProductListFieldSupport.barcodes(hitNode);
                    if (!hitBarcodes.isEmpty()) {
                        entry.barcodes.addAll(hitBarcodes);
                        entry.barcode = firstNonBlank(entry.barcode, hitBarcodes.get(0));
                    }
                    if (!StringUtils.hasText(entry.title)) {
                        entry.title = text(hitNode.path("content"), "title");
                    }
                    if (!StringUtils.hasText(entry.brand)) {
                        entry.brand = firstNonBlank(text(hitNode.path("content"), "brand"), text(hitNode, "brand_code"));
                    }
                    if (!StringUtils.hasText(entry.imageUrl)) {
                        entry.imageUrl = resolveImageUrl(text(hitNode.path("content"), "image"));
                    }
                    if (!StringUtils.hasText(entry.storeCode)) {
                        entry.storeCode = projectSite.getStoreCode();
                    }
                    if (!StringUtils.hasText(entry.site)) {
                        entry.site = projectSite.getSite();
                    }
                    if (StringUtils.hasText(projectSite.getSite())) {
                        entry.sites.add(projectSite.getSite());
                    }
                    entry.siteOfferCount++;
                    ProductSiteOfferEntry siteOfferEntry = entry.siteOffers.computeIfAbsent(
                            normalize(projectSite.getStoreCode()),
                            ignored -> new ProductSiteOfferEntry(projectSite.getStoreCode())
                    );
                    siteOfferEntry.pskuCode = firstNonBlank(
                            siteOfferEntry.pskuCode,
                            NoonProductListFieldSupport.pskuCode(hitNode)
                    );
                    siteOfferEntry.offerCode = firstNonBlank(siteOfferEntry.offerCode, text(hitNode, "offer_code"));
                    siteOfferEntry.currency = firstNonBlank(siteOfferEntry.currency, text(hitNode, "currency"));
                    siteOfferEntry.barcode = firstNonBlank(
                            siteOfferEntry.barcode,
                            firstItem(hitBarcodes)
                    );
                    siteOfferEntry.originalPrice = firstNonBlank(
                            siteOfferEntry.originalPrice,
                            text(hitNode, "base_price"),
                            text(hitNode, "original_price"),
                            text(hitNode, "regular_price"),
                            text(hitNode, "list_price")
                    );
                    if (!StringUtils.hasText(siteOfferEntry.salePrice) && hitNode.hasNonNull("sale_price")) {
                        siteOfferEntry.salePrice = hitNode.get("sale_price").asText();
                    }
                    siteOfferEntry.finalPrice = firstNonBlank(siteOfferEntry.finalPrice, text(hitNode, "price"));
                    siteOfferEntry.finalPriceSource = firstNonBlank(
                            siteOfferEntry.finalPriceSource,
                            resolveOfferListFinalPriceSource(siteOfferEntry.finalPrice, siteOfferEntry.salePrice, siteOfferEntry.originalPrice),
                            text(hitNode, "final_price_source"),
                            text(hitNode, "price_source")
                    );
                    siteOfferEntry.activePromotionCode = firstNonBlank(
                            siteOfferEntry.activePromotionCode,
                            text(hitNode, "active_promotion_code"),
                            text(hitNode, "promotion_code"),
                            text(hitNode, "promo_code"),
                            text(hitNode, "campaign_code"),
                            text(hitNode, "deal_code")
                    );
                    siteOfferEntry.activePromotionName = firstNonBlank(
                            siteOfferEntry.activePromotionName,
                            text(hitNode, "active_promotion_name"),
                            text(hitNode, "promotion_name"),
                            text(hitNode, "promo_name"),
                            text(hitNode, "campaign_name"),
                            text(hitNode, "deal_name")
                    );
                    siteOfferEntry.activePromotionUrl = firstNonBlank(
                            siteOfferEntry.activePromotionUrl,
                            text(hitNode, "active_promotion_url"),
                            text(hitNode, "promotion_url"),
                            text(hitNode, "promo_url"),
                            text(hitNode, "campaign_url"),
                            text(hitNode, "deal_url")
                    );
                    siteOfferEntry.deliveryMethod = firstNonBlank(siteOfferEntry.deliveryMethod, text(hitNode, "delivery_method"));
                    siteOfferEntry.isWinningBuybox = firstNonNull(siteOfferEntry.isWinningBuybox, booleanValue(hitNode, "is_winning_buybox"));
                    siteOfferEntry.viewsCount = firstNonNull(siteOfferEntry.viewsCount, longValue(hitNode, "gvs", "views", "view_count"));
                    siteOfferEntry.unitsSold = firstNonNull(siteOfferEntry.unitsSold, longValue(hitNode, "units_sold", "sales_units"));
                    siteOfferEntry.salesAmount = firstNonBlank(siteOfferEntry.salesAmount, text(hitNode, "gmv"), text(hitNode, "sales"), text(hitNode, "revenue"));
                    siteOfferEntry.salesCurrency = firstNonBlank(siteOfferEntry.salesCurrency, text(hitNode, "currency"));
                    siteOfferEntry.liveStatus = firstNonBlank(
                            siteOfferEntry.liveStatus,
                            firstNonBlank(text(hitNode, "live_status"), text(hitNode, "seller_status"))
                    );
                    siteOfferEntry.statusCode = firstNonBlank(siteOfferEntry.statusCode, text(hitNode, "status_code"));
                    siteOfferEntry.isActive = firstNonNull(
                            siteOfferEntry.isActive,
                            resolveLiveStatusActive(siteOfferEntry.liveStatus)
                    );
                    int supermallStock = explicitSupermallStock(hitNode);
                    siteOfferEntry.fbnStock += hitNode.path("fbn_stock").asInt(0);
                    siteOfferEntry.supermallStock += supermallStock;
                    siteOfferEntry.fbpStock += hitNode.path("fbp_stock").asInt(0);
                    if (!StringUtils.hasText(entry.currency)) {
                        entry.currency = text(hitNode, "currency");
                    }
                    entry.originalPrice = firstNonBlank(
                            entry.originalPrice,
                            text(hitNode, "base_price"),
                            text(hitNode, "original_price"),
                            text(hitNode, "regular_price"),
                            text(hitNode, "list_price")
                    );
                    if (!StringUtils.hasText(entry.salePrice) && hitNode.hasNonNull("sale_price")) {
                        entry.salePrice = hitNode.get("sale_price").asText();
                    }
                    if (!StringUtils.hasText(entry.price)) {
                        entry.price = firstNonBlank(text(hitNode, "price"), entry.salePrice, entry.originalPrice);
                    }
                    entry.finalPrice = firstNonBlank(entry.finalPrice, siteOfferEntry.finalPrice, entry.price);
                    entry.finalPriceSource = firstNonBlank(entry.finalPriceSource, siteOfferEntry.finalPriceSource);
                    if (!StringUtils.hasText(entry.liveStatus)) {
                        entry.liveStatus = firstNonBlank(text(hitNode, "live_status"), text(hitNode, "seller_status"));
                    }
                    entry.statusCode = firstNonBlank(entry.statusCode, text(hitNode, "status_code"));
                    entry.deliveryMethod = firstNonBlank(entry.deliveryMethod, siteOfferEntry.deliveryMethod);
                    entry.isWinningBuybox = firstNonNull(entry.isWinningBuybox, siteOfferEntry.isWinningBuybox);
                    entry.viewsCount = firstNonNull(entry.viewsCount, siteOfferEntry.viewsCount);
                    entry.unitsSold = firstNonNull(entry.unitsSold, siteOfferEntry.unitsSold);
                    entry.salesAmount = firstNonBlank(entry.salesAmount, siteOfferEntry.salesAmount);
                    entry.salesCurrency = firstNonBlank(entry.salesCurrency, siteOfferEntry.salesCurrency);
                    if (StringUtils.hasText(text(hitNode, "live_status"))) {
                        entry.liveStatuses.add(text(hitNode, "live_status"));
                    }
                    if (StringUtils.hasText(text(hitNode, "seller_status"))) {
                        entry.liveStatuses.add(text(hitNode, "seller_status"));
                    }
                    entry.totalFbnStock += hitNode.path("fbn_stock").asInt(0);
                    entry.totalSupermallStock += supermallStock;
                    entry.totalFbpStock += hitNode.path("fbp_stock").asInt(0);
                    JsonNode issueNode = hitNode.path("offer_issues");
                    if (issueNode.isArray()) {
                        for (JsonNode issueItem : issueNode) {
                            if (issueItem.isTextual() && StringUtils.hasText(issueItem.asText())) {
                                entry.issueTags.add(issueItem.asText().trim());
                            }
                        }
                    }
                }
                mergeSupermallOffers(projectSite, supermallOfferListFetchResult.hits, productIndexMap);

                finishedSites++;
                int stageProgress = 28 + (int) Math.round((finishedSites * 28.0d) / Math.max(projectSites.size(), 1));
                state.markRunning(
                        "拉商品清单",
                        5,
                        2,
                        stageProgress,
                        "已完成 " + finishedSites + " / " + projectSites.size() + " 个站点的商品索引拉取。"
                );
                state.replaceSiteSummaries(siteSummaries);
            }

            state.setCounts(productIndexMap.size(), totalSiteOffers);
            state.replaceProductItems(buildProductItems(productIndexMap));
            state.replaceSampleProducts(buildSampleProducts(productIndexMap));
            state.completeStep("商品清单已拉取", 58);

            state.markRunning("建立商品主档基线", 5, 3, 68, "正在批量补全商品标题、图片、品牌和类目...");
            List<String> skuParents = new ArrayList<>(productIndexMap.keySet());
            int finishedBatches = 0;
            int totalBatches = Math.max((int) Math.ceil(skuParents.size() / (double) RETRIEVE_BATCH_SIZE), 1);

            for (int startIndex = 0; startIndex < skuParents.size(); startIndex += RETRIEVE_BATCH_SIZE) {
                int endIndex = Math.min(startIndex + RETRIEVE_BATCH_SIZE, skuParents.size());
                List<String> batchSkuParents = skuParents.subList(startIndex, endIndex);
                JsonNode retrieveRoot = fetchRetrieveBatch(session, batchSkuParents);
                for (String skuParent : batchSkuParents) {
                    ProductIndexEntry entry = productIndexMap.get(skuParent);
                    if (entry == null) {
                        continue;
                    }
                    JsonNode productNode = retrieveRoot.path(skuParent);
                    if (productNode.isMissingNode() || productNode.isNull()) {
                        applyFollowSellCatalogContent(session, entry);
                        state.mutableWarnings().add("商品 " + skuParent + " 没有返回主档基线，已跳过。");
                        continue;
                    }

                    JsonNode commonNode = productNode.path("attributes").path("common");
                    JsonNode enNode = productNode.path("attributes").path("en");
                    entry.title = firstNonBlank(entry.title, text(enNode, "product_title"));
                    entry.brand = firstNonBlank(entry.brand, text(commonNode, "brand"));
                    entry.imageUrl = firstNonBlank(entry.imageUrl, resolveFirstImage(commonNode));
                    applyFollowSellCatalogContent(session, entry);
                    entry.productFulltype = text(commonNode, "product_fulltype");
                    entry.skuGroup = firstNonBlank(
                            entry.skuGroup,
                            text(commonNode, "sku_group"),
                            text(productNode, "sku_group")
                    );
                    entry.groupRefCanonical = firstNonBlank(
                            entry.groupRefCanonical,
                            text(commonNode, "group_ref_canonical"),
                            text(productNode, "group_ref_canonical")
                    );
                    entry.groupRef = firstNonBlank(
                            entry.groupRef,
                            text(commonNode, "group_ref"),
                            text(productNode, "group_ref"),
                            entry.groupRefCanonical,
                            entry.skuGroup
                    );
                    entry.variantCount = productNode.path("variants").size();
                }

                finishedBatches++;
                int stageProgress = 68 + (int) Math.round((finishedBatches * 14.0d) / totalBatches);
                state.markRunning(
                        "建立商品主档基线",
                        5,
                        3,
                        stageProgress,
                        "已完成 " + finishedBatches + " / " + totalBatches + " 组商品主档基线。"
                );
            }

            state.completeStep("商品主档基线已建立", 82);

            state.markRunning("汇总站点经营面", 5, 4, 90, "正在生成初始化结果和样本商品入口...");
            state.replaceProductItems(buildProductItems(productIndexMap));
            state.replaceSampleProducts(buildSampleProducts(productIndexMap));
            state.completeStep("站点经营面已汇总", 94);

            state.markRunning("生成初始化结果", 5, 5, 98, "正在整理最近同步时间和结果摘要...");
            state.setNoonRequestCounts(requestCountScope.snapshot());
            state.markReady(
                    context.referenceStore.getProjectName(),
                    "已完成 "
                            + context.referenceStore.getProjectName()
                            + " 的新店初始化，识别 "
                            + projectSites.size()
                            + " 个站点、"
                            + productIndexMap.size()
                            + " 个共享商品、"
                            + totalSiteOffers
                            + " 条站点经营面。"
            );
            StoreInitializationStatusView readySnapshot = state.snapshot();
            productProjectionPersistenceService.persistInitializationProjection(
                    context.owner.getId(),
                    resolvedProjectCode,
                    context.referenceStore.getProjectName(),
                    context.referenceStore.getStoreCode(),
                    buildProjectionSiteSeeds(readySnapshot.getSiteSummaries(), context.referenceStore.getStoreCode()),
                    buildProjectionProductSeeds(productIndexMap, readySnapshot.getLastInitializedAt()),
                    state.mutableWarnings()
            );
            overlayProjectedProductItems(context.owner.getId(), context.referenceStore.getStoreCode(), readySnapshot);
            state.replaceProductItems(readySnapshot.getProductItems());
            persistSnapshot(context.owner.getId(), context.referenceStore.getStoreCode(), state.snapshot());
        } catch (Exception exception) {
            state.setNoonRequestCounts(requestCountScope.snapshot());
            if (StringUtils.hasText(exception.getMessage())) {
                state.mutableWarnings().add(exception.getMessage());
            }
            state.markFailed(buildFailureMessage(state, exception));
            persistSnapshot(context.owner.getId(), context.referenceStore.getStoreCode(), state.snapshot());
        } finally {
            requestCountScope.close();
        }
    }

    private List<ProjectSiteContext> buildScopedProjectSites(StoreContext context) {
        LinkedHashMap<String, ProjectSiteContext> siteMap = new LinkedHashMap<>();
        for (StoreSyncStoreRecord localStore : context.localProjectStores) {
            if (!StringUtils.hasText(localStore.getStoreCode())) {
                continue;
            }
            siteMap.put(
                    localStore.getStoreCode(),
                    new ProjectSiteContext(
                            localStore.getStoreCode(),
                            firstNonBlank(localStore.getSite(), deriveSiteFromStoreCode(localStore.getStoreCode())),
                            Boolean.TRUE.equals(localStore.getOwnerAuthorized()) ? "LOCAL_READY" : "UNBOUND"
                    )
            );
        }

        if (!siteMap.containsKey(context.referenceStore.getStoreCode())) {
            siteMap.put(
                    context.referenceStore.getStoreCode(),
                    new ProjectSiteContext(
                            context.referenceStore.getStoreCode(),
                            firstNonBlank(context.referenceStore.getSite(), deriveSiteFromStoreCode(context.referenceStore.getStoreCode())),
                            Boolean.TRUE.equals(context.referenceStore.getOwnerAuthorized()) ? "LOCAL_READY" : "UNBOUND"
                    )
            );
        }

        return new ArrayList<>(siteMap.values());
    }

    private String buildFailureMessage(InitializationState state, Exception exception) {
        String message = normalize(exception != null ? exception.getMessage() : null);
        if (isRateLimitedMessage(message) && state.hasProductItems()) {
            return "Noon 当前限流，已保留已拉到的商品列表。稍后再补商品主档基线。";
        }
        return StringUtils.hasText(message) ? message : "店铺初始化失败，请稍后再试。";
    }

    private StoreInitializationStatusView buildBlockedView(CoreTableInspection inspection) {
        StoreInitializationStatusView view = new StoreInitializationStatusView();
        view.setMode("local-db");
        view.setReady(inspection.isReady());
        view.setStatus("BLOCKED");
        view.setMessage("本地库已启用，但第一批核心表还没有补齐，当前不能做新店初始化。");
        view.setMissingCoreTables(new ArrayList<>(inspection.getMissingTables()));
        view.setSteps(defaultSteps());
        return view;
    }

    private StoreInitializationStatusView buildIdleView(StoreContext context) {
        StoreInitializationStatusView view = new StoreInitializationStatusView();
        view.setMode("local-db");
        view.setReady(true);
        view.setStatus("IDLE");
        view.setOwnerUserId(context.owner.getId());
        view.setProjectName(context.referenceStore.getProjectName());
        view.setProjectCode(context.referenceStore.getProjectCode());
        view.setStoreCode(context.referenceStore.getStoreCode());
        view.setProgressPercent(0);
        view.setMessage("当前还没开始拉取这家店的数据。确认后会先拉店铺结构，再拉商品清单和主档基线。");
        view.setSteps(defaultSteps());
        view.setSiteCount(context.localProjectStores.size());
        view.setSiteSummaries(buildLocalSiteSummaries(context.localProjectStores));
        return view;
    }

    private StoreInitializationPreflightView buildPreflightView(StoreContext context, String targetProjectCode) {
        StoreInitializationPreflightView view = new StoreInitializationPreflightView();
        view.setMode("local-db");
        view.setReady(true);
        view.setStatus("CHECKING");
        view.setMessage("正在验证 Noon 账号是否可访问目标项目。");
        view.setOwnerUserId(context.owner.getId());
        view.setProjectName(context.referenceStore.getProjectName());
        view.setProjectCode(context.referenceStore.getProjectCode());
        view.setTargetProjectCode(targetProjectCode);
        view.setStoreCode(context.referenceStore.getStoreCode());
        view.setSiteCount(context.localProjectStores.size());
        view.setSiteSummaries(buildLocalSiteSummaries(context.localProjectStores));
        return view;
    }

    private StoreInitializationStatusView loadPersistedSnapshot(Long ownerUserId, String storeCode, StoreContext context) {
        StoreInitializationSnapshotRecord record =
                storeInitializationSnapshotMapper.selectByOwnerAndStore(ownerUserId, normalize(storeCode));
        if (record == null || !StringUtils.hasText(record.getSnapshotJson())) {
            return null;
        }

        try {
            StoreInitializationStatusView view = objectMapper.readValue(
                    record.getSnapshotJson(),
                    StoreInitializationStatusView.class
            );
            fillPersistedSnapshotDefaults(view, context);
            if ("RUNNING".equalsIgnoreCase(view.getStatus())) {
                markPersistedRunInterrupted(view);
                persistSnapshot(ownerUserId, storeCode, view);
            }
            return view;
        } catch (IOException exception) {
            throw new IllegalStateException("读取已落库的店铺初始化快照失败。", exception);
        }
    }

    private void fillPersistedSnapshotDefaults(StoreInitializationStatusView view, StoreContext context) {
        if (view == null) {
            return;
        }
        view.setMode("local-db");
        view.setReady(true);
        view.setOwnerUserId(context.owner.getId());
        if (!StringUtils.hasText(view.getProjectName())) {
            view.setProjectName(context.referenceStore.getProjectName());
        }
        if (!StringUtils.hasText(view.getProjectCode())) {
            view.setProjectCode(context.referenceStore.getProjectCode());
        }
        if (!StringUtils.hasText(view.getStoreCode())) {
            view.setStoreCode(context.referenceStore.getStoreCode());
        }
        if (view.getMissingCoreTables() == null) {
            view.setMissingCoreTables(new ArrayList<>());
        }
        if (view.getWarnings() == null) {
            view.setWarnings(new ArrayList<>());
        }
        if (view.getNoonRequestCounts() == null) {
            view.setNoonRequestCounts(new LinkedHashMap<>());
        }
        if (view.getNoonRequestTotalCount() == null) {
            view.setNoonRequestTotalCount(view.getNoonRequestCounts().getOrDefault(NOON_REQUEST_TOTAL_KEY, 0));
        }
        if (view.getSteps() == null || view.getSteps().isEmpty()) {
            view.setSteps(defaultSteps());
        }
        if (view.getSiteSummaries() == null || view.getSiteSummaries().isEmpty()) {
            view.setSiteSummaries(buildLocalSiteSummaries(context.localProjectStores));
        }
        if (view.getSampleProducts() == null) {
            view.setSampleProducts(new ArrayList<>());
        }
        if (view.getProductItems() == null) {
            view.setProductItems(new ArrayList<>());
        }
    }

    private void markPersistedRunInterrupted(StoreInitializationStatusView view) {
        String message = "后端重启后，上次初始化已中断，请重新初始化当前店铺。";
        view.setStatus("FAILED");
        view.setPhaseLabel("初始化中断");
        view.setMessage(message);

        boolean stepMarked = false;
        for (StoreInitializationStepView step : view.getSteps()) {
            if ("running".equalsIgnoreCase(step.getStatus())) {
                step.setStatus("failed");
                step.setMessage(message);
                stepMarked = true;
            }
        }

        if (!stepMarked && !view.getSteps().isEmpty()) {
            StoreInitializationStepView lastStep = view.getSteps().get(view.getSteps().size() - 1);
            if (!"completed".equalsIgnoreCase(lastStep.getStatus())) {
                lastStep.setStatus("failed");
                lastStep.setMessage(message);
            }
        }

        if (view.getWarnings().stream().noneMatch(message::equals)) {
            view.getWarnings().add(message);
        }
    }

    private void persistSnapshot(Long ownerUserId, String storeCode, StoreInitializationStatusView view) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || view == null) {
            return;
        }

        try {
            StoreInitializationSnapshotRecord existing =
                    storeInitializationSnapshotMapper.selectByOwnerAndStore(ownerUserId, normalize(storeCode));
            Long id = existing != null && existing.getId() != null
                    ? existing.getId()
                    : storeInitializationSnapshotMapper.nextId();
            String snapshotJson = objectMapper.writeValueAsString(view);
            storeInitializationSnapshotMapper.upsert(
                    id,
                    ownerUserId,
                    normalize(storeCode),
                    normalize(view.getProjectCode()),
                    normalize(view.getProjectName()),
                    normalize(view.getStatus()),
                    parseDateTime(view.getLastInitializedAt()),
                    snapshotJson
            );
        } catch (IOException exception) {
            throw new IllegalStateException("写入店铺初始化快照失败。", exception);
        }
    }

    private List<ProductProjectionPersistenceService.SiteSeed> buildProjectionSiteSeeds(
            List<StoreInitializationSiteSummaryView> siteSummaries,
            String referenceStoreCode
    ) {
        List<ProductProjectionPersistenceService.SiteSeed> seeds = new ArrayList<>();
        if (siteSummaries == null) {
            return seeds;
        }
        for (StoreInitializationSiteSummaryView siteSummary : siteSummaries) {
            if (!StringUtils.hasText(siteSummary.getStoreCode())) {
                continue;
            }
            seeds.add(new ProductProjectionPersistenceService.SiteSeed(
                    siteSummary.getStoreCode(),
                    siteSummary.getSite(),
                    siteSummary.getLiveStatus(),
                    true
            ));
        }
        if (StringUtils.hasText(referenceStoreCode) && seeds.stream().noneMatch(seed -> referenceStoreCode.equalsIgnoreCase(seed.getStoreCode()))) {
            seeds.add(new ProductProjectionPersistenceService.SiteSeed(referenceStoreCode, null, null, true));
        }
        return seeds;
    }

    private List<ProductProjectionPersistenceService.ProductMasterSeed> buildProjectionProductSeeds(
            LinkedHashMap<String, ProductIndexEntry> productIndexMap,
            String lastInitializedAt
    ) {
        List<ProductProjectionPersistenceService.ProductMasterSeed> seeds = new ArrayList<>();
        if (productIndexMap == null) {
            return seeds;
        }
        for (ProductIndexEntry productItem : productIndexMap.values()) {
            if (!StringUtils.hasText(productItem.skuParent)) {
                continue;
            }
            ProductProjectionPersistenceService.ProductMasterSeed seed =
                    new ProductProjectionPersistenceService.ProductMasterSeed();
            seed.setSkuParent(productItem.skuParent);
            seed.setBrandCache(productItem.brand);
            seed.setTitleCache(productItem.title);
            seed.setProductFulltypeCache(productItem.productFulltype);
            seed.setCoverImageUrl(productItem.imageUrl);
            seed.setGroupRef(productItem.groupRef);
            seed.setVariantCountCache(productItem.variantCount);
            seed.setSyncStatus("synced");
            seed.setLastSyncedAt(lastInitializedAt);
            seed.setPartnerSku(productItem.partnerSku);
            seed.setChildSku(productItem.catalogSku);
            seed.setProductSourceType(ProductSourceTypeSupport.resolve(null, productItem.catalogSku, productItem.skuParent));
            seed.setBarcodes(new ArrayList<>(productItem.barcodes));
            seed.setPskuCode(productItem.pskuCode);
            seed.setOfferCode(productItem.offerCode);
            seed.setReferenceStoreCode(productItem.storeCode);
            seed.setOriginalPrice(productItem.originalPrice);
            seed.setSalePrice(productItem.salePrice);
            seed.setFinalPrice(firstNonBlank(productItem.finalPrice, productItem.price));
            seed.setFinalPriceSource(productItem.finalPriceSource);
            seed.setDeliveryMethod(productItem.deliveryMethod);
            seed.setIsWinningBuybox(productItem.isWinningBuybox);
            seed.setViewsCount(productItem.viewsCount);
            seed.setUnitsSold(productItem.unitsSold);
            seed.setSalesAmount(productItem.salesAmount);
            seed.setSalesCurrency(productItem.salesCurrency);
            seed.setLiveStatus(firstNonBlank(
                    firstItem(new ArrayList<>(productItem.liveStatuses)),
                    productItem.liveStatus
            ));
            seed.setStatusCode(productItem.statusCode);
            seed.setIsActive(resolveLiveStatusActive(productItem.liveStatus));
            seed.setFbnStock(productItem.totalFbnStock);
            seed.setSupermallStock(productItem.totalSupermallStock);
            seed.setFbpStock(productItem.totalFbpStock);
            seed.setIssueTags(sanitizedIssueTags(productItem));
            for (ProductSiteOfferEntry offerEntry : productItem.siteOffers.values()) {
                ProductProjectionPersistenceService.SiteOfferSeed siteOfferSeed =
                        new ProductProjectionPersistenceService.SiteOfferSeed();
                siteOfferSeed.setStoreCode(offerEntry.storeCode);
                siteOfferSeed.setPskuCode(offerEntry.pskuCode);
                siteOfferSeed.setOfferCode(offerEntry.offerCode);
                siteOfferSeed.setCurrency(offerEntry.currency);
                siteOfferSeed.setBarcode(offerEntry.barcode);
                siteOfferSeed.setOriginalPrice(offerEntry.originalPrice);
                siteOfferSeed.setSalePrice(offerEntry.salePrice);
                siteOfferSeed.setFinalPrice(offerEntry.finalPrice);
                siteOfferSeed.setFinalPriceSource(offerEntry.finalPriceSource);
                siteOfferSeed.setActivePromotionCode(offerEntry.activePromotionCode);
                siteOfferSeed.setActivePromotionName(offerEntry.activePromotionName);
                siteOfferSeed.setActivePromotionUrl(offerEntry.activePromotionUrl);
                siteOfferSeed.setDeliveryMethod(offerEntry.deliveryMethod);
                siteOfferSeed.setIsWinningBuybox(offerEntry.isWinningBuybox);
                siteOfferSeed.setViewsCount(offerEntry.viewsCount);
                siteOfferSeed.setUnitsSold(offerEntry.unitsSold);
                siteOfferSeed.setSalesAmount(offerEntry.salesAmount);
                siteOfferSeed.setSalesCurrency(offerEntry.salesCurrency);
                siteOfferSeed.setLiveStatus(offerEntry.liveStatus);
                siteOfferSeed.setStatusCode(offerEntry.statusCode);
                siteOfferSeed.setIsActive(offerEntry.isActive);
                siteOfferSeed.setFbnStock(offerEntry.fbnStock);
                siteOfferSeed.setSupermallStock(offerEntry.supermallStock);
                siteOfferSeed.setFbpStock(offerEntry.fbpStock);
                seed.addSiteOffer(siteOfferSeed);
            }
            seeds.add(seed);
        }
        return seeds;
    }

    private void overlayProjectedProductItems(Long ownerUserId, String storeCode, StoreInitializationStatusView view) {
        if (view == null) {
            return;
        }
        if (!"READY".equalsIgnoreCase(view.getStatus())) {
            view.setProductItems(new ArrayList<>());
            view.setSampleProducts(new ArrayList<>());
            view.setCanEnterProductWorkbench(false);
            return;
        }
        if (view.getProductItems() == null) {
            view.setProductItems(new ArrayList<>());
        }
        List<ProductListSummaryView> summaries = productProjectionPersistenceService.loadProductListSummaries(
                ownerUserId,
                storeCode,
                view.getWarnings()
        );
        if (summaries.isEmpty()) {
            return;
        }
        LinkedHashMap<String, StoreInitializationProductListItemView> bySkuParent = new LinkedHashMap<>();
        for (StoreInitializationProductListItemView item : view.getProductItems()) {
            if (item != null && StringUtils.hasText(item.getSkuParent())) {
                bySkuParent.put(normalize(item.getSkuParent()), item);
            }
        }
        for (ProductListSummaryView summary : summaries) {
            String skuParent = normalize(summary.getSkuParent());
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            StoreInitializationProductListItemView item = bySkuParent.computeIfAbsent(
                    skuParent,
                    ignored -> {
                        StoreInitializationProductListItemView created = new StoreInitializationProductListItemView();
                        created.setSkuParent(summary.getSkuParent());
                        created.setCurrentZCode(firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent()));
                        created.setProductSourceType(summary.getProductSourceType());
                        created.setReferenceStoreCode(storeCode);
                        created.setIssueTags(new ArrayList<>());
                        return created;
                    }
            );
            item.setCurrentZCode(firstNonBlank(summary.getCurrentZCode(), summary.getSkuParent(), item.getCurrentZCode()));
            item.setProductSourceType(firstNonBlank(summary.getProductSourceType(), item.getProductSourceType()));
            item.setPartnerSku(firstNonBlank(summary.getPartnerSku(), item.getPartnerSku()));
            item.setPskuCode(firstNonBlank(summary.getPskuCode(), item.getPskuCode()));
            item.setOfferCode(firstNonBlank(summary.getOfferCode(), item.getOfferCode()));
            item.setTitle(firstNonBlank(summary.getTitle(), item.getTitle()));
            item.setTitleCn(firstNonBlank(summary.getTitleCn(), item.getTitleCn()));
            item.setBrand(firstNonBlank(summary.getBrand(), item.getBrand()));
            item.setImageUrl(firstNonBlank(summary.getImageUrl(), item.getImageUrl()));
            item.setBarcode(firstNonBlank(summary.getBarcode(), item.getBarcode()));
            item.setReferencePrice(firstNonBlank(summary.getReferencePrice(), item.getReferencePrice()));
            item.setOriginalPrice(firstNonBlank(summary.getOriginalPrice(), item.getOriginalPrice()));
            item.setSalePrice(firstNonBlank(summary.getSalePrice(), item.getSalePrice()));
            item.setProductFulltype(firstNonBlank(summary.getProductFulltype(), item.getProductFulltype()));
            item.setGroupRef(normalize(summary.getGroupRef()));
            item.setLiveStatus(firstNonBlank(summary.getLiveStatus(), item.getLiveStatus()));
            item.setStatusCode(firstNonBlank(summary.getStatusCode(), item.getStatusCode()));
            item.setListingStartedAt(firstNonBlank(summary.getListingStartedAt(), item.getListingStartedAt()));
            item.setListingStartedSource(firstNonBlank(summary.getListingStartedSource(), item.getListingStartedSource()));
            item.setIsActive(firstNonNull(summary.getIsActive(), item.getIsActive()));
            item.setMaintenanceEnabled(firstNonNull(summary.getMaintenanceEnabled(), item.getMaintenanceEnabled()));
            item.setSyncStatus(firstNonBlank(summary.getSyncStatus(), item.getSyncStatus()));
            item.setLastSyncedAt(firstNonBlank(summary.getLastSyncedAt(), item.getLastSyncedAt()));
            item.setVariantCount(firstNonNull(summary.getVariantCount(), item.getVariantCount()));
            item.setSiteOfferCount(firstNonNull(summary.getSiteOfferCount(), item.getSiteOfferCount()));
            item.setHistoryMetaReady(firstNonNull(summary.getHistoryMetaReady(), item.getHistoryMetaReady()));
            item.setPendingKeyContentHistoryCount(firstNonNull(
                    summary.getPendingKeyContentHistoryCount(),
                    item.getPendingKeyContentHistoryCount()
            ));
            item.setVisibleKeyContentHistoryCount(firstNonNull(
                    summary.getVisibleKeyContentHistoryCount(),
                    item.getVisibleKeyContentHistoryCount()
            ));
            item.setPendingKeyContentHistoryVisibleAfter(firstNonBlank(
                    summary.getPendingKeyContentHistoryVisibleAfter(),
                    item.getPendingKeyContentHistoryVisibleAfter()
            ));
            if (!summary.getSiteLabels().isEmpty()) {
                item.setSiteLabels(new ArrayList<>(summary.getSiteLabels()));
            }
            if (!summary.getLiveStatuses().isEmpty()) {
                item.setLiveStatuses(new ArrayList<>(summary.getLiveStatuses()));
            } else if (StringUtils.hasText(summary.getLiveStatus())) {
                item.setLiveStatuses(new ArrayList<>(List.of(summary.getLiveStatus())));
            }
            item.setTotalFbnStock(firstNonNull(summary.getTotalFbnStock(), item.getTotalFbnStock()));
            item.setTotalSupermallStock(firstNonNull(summary.getTotalSupermallStock(), item.getTotalSupermallStock()));
            item.setTotalFbpStock(firstNonNull(summary.getTotalFbpStock(), item.getTotalFbpStock()));
            item.setViewsCount(firstNonNull(summary.getViewsCount(), item.getViewsCount()));
            item.setUnitsSold(firstNonNull(summary.getUnitsSold(), item.getUnitsSold()));
            item.setSalesAmount(firstNonBlank(summary.getSalesAmount(), item.getSalesAmount()));
            item.setSalesCurrency(firstNonBlank(summary.getSalesCurrency(), item.getSalesCurrency()));
        }
        view.setProductItems(new ArrayList<>(bySkuParent.values()));
    }

    private StoreContext resolveContext(Long ownerUserId, String storeCode) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法识别店铺初始化范围。");
        }
        requireText(storeCode, "缺少逻辑店铺参考站点，无法识别初始化范围。");

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(ownerUserId);
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法识别初始化范围。");
        }

        StoreSyncStoreRecord referenceStore = resolveOwnerStore(ownerUserId, storeCode);
        if (referenceStore == null) {
            throw new IllegalArgumentException("当前店铺不在选中的老板名下。");
        }

        if (!Boolean.TRUE.equals(referenceStore.getOwnerAuthorized())) {
            throw new IllegalArgumentException("当前店铺还没有完成绑定，暂时不能拉取真实店铺数据。");
        }

        List<StoreSyncStoreRecord> ownerStores = storeSyncMapper.listOwnerStores(ownerUserId);
        List<StoreSyncStoreRecord> localProjectStores = new ArrayList<>();
        String projectKey = projectKey(referenceStore);
        for (StoreSyncStoreRecord ownerStore : ownerStores) {
            if (projectKey.equals(projectKey(ownerStore))) {
                localProjectStores.add(ownerStore);
            }
        }
        if (localProjectStores.isEmpty() && StringUtils.hasText(referenceStore.getProjectCode())) {
            localProjectStores.addAll(storeSyncMapper.listOwnerProjectionProjectSites(
                    ownerUserId,
                    List.of(referenceStore.getProjectCode())
            ));
        }
        if (localProjectStores.isEmpty()) {
            localProjectStores.add(referenceStore);
        }

        return new StoreContext(owner, referenceStore, localProjectStores);
    }

    private StoreSyncStoreRecord resolveOwnerStore(Long ownerUserId, String storeCode) {
        StoreSyncStoreRecord store = storeSyncMapper.selectOwnerStore(ownerUserId, storeCode);
        if (store != null) {
            return store;
        }
        return storeSyncMapper.selectOwnerProjectionStore(ownerUserId, storeCode);
    }

    private List<StoreInitializationStepView> defaultSteps() {
        List<StoreInitializationStepView> steps = new ArrayList<>();
        steps.add(step("structure", "识别店铺结构"));
        steps.add(step("offers", "拉商品清单"));
        steps.add(step("master", "建立商品主档基线"));
        steps.add(step("site-offers", "汇总站点经营面"));
        steps.add(step("result", "生成初始化结果"));
        return steps;
    }

    private StoreInitializationStepView step(String code, String label) {
        StoreInitializationStepView step = new StoreInitializationStepView();
        step.setCode(code);
        step.setLabel(label);
        step.setStatus("pending");
        return step;
    }

    private List<StoreInitializationSiteSummaryView> buildLocalSiteSummaries(List<StoreSyncStoreRecord> localProjectStores) {
        List<StoreInitializationSiteSummaryView> siteSummaries = new ArrayList<>();
        for (StoreSyncStoreRecord localProjectStore : localProjectStores) {
            StoreInitializationSiteSummaryView summary = new StoreInitializationSiteSummaryView();
            summary.setStoreCode(localProjectStore.getStoreCode());
            summary.setSite(localProjectStore.getSite());
            summary.setLiveStatus(Boolean.TRUE.equals(localProjectStore.getOwnerAuthorized()) ? "LOCAL_READY" : "UNBOUND");
            siteSummaries.add(summary);
        }
        return siteSummaries;
    }

    private List<ProjectSiteContext> loadProjectSiteContexts(
            NoonSession session,
            Long ownerUserId,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        Map<String, ProjectSiteContext> siteMap = new LinkedHashMap<>();
        List<StoreSyncStoreRecord> localProjectStores = resolveContext(ownerUserId, store.getStoreCode()).localProjectStores;
        for (StoreSyncStoreRecord localStore : localProjectStores) {
            if (!StringUtils.hasText(localStore.getStoreCode())) {
                continue;
            }
            siteMap.put(
                    localStore.getStoreCode(),
                    new ProjectSiteContext(localStore.getStoreCode(), localStore.getSite(), null)
            );
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("noonStoreCode", "");
        JsonNode root = session.postJson(STORE_LIST_URL, body, true);
        JsonNode noonStoresNode = root.path("noonStores");
        if (noonStoresNode.isArray()) {
            for (JsonNode node : noonStoresNode) {
                String liveStoreCode = text(node, "noonStoreCode");
                if (!StringUtils.hasText(liveStoreCode)) {
                    continue;
                }

                String liveSite = firstNonBlank(text(node, "countryCode"), deriveSiteFromStoreCode(liveStoreCode));
                String statusCode = text(node, "statusCode");
                ProjectSiteContext existing = siteMap.get(liveStoreCode);
                if (existing != null) {
                    existing.setSite(firstNonBlank(existing.getSite(), liveSite));
                    existing.setStatusCode(firstNonBlank(statusCode, existing.getStatusCode()));
                }
            }
        }

        if (!siteMap.containsKey(store.getStoreCode())) {
            siteMap.put(
                    store.getStoreCode(),
                    new ProjectSiteContext(
                            store.getStoreCode(),
                            firstNonBlank(store.getSite(), deriveSiteFromStoreCode(store.getStoreCode())),
                            null
                    )
            );
        }

        List<ProjectSiteContext> orderedSites = new ArrayList<>();
        ProjectSiteContext referenceSite = siteMap.remove(store.getStoreCode());
        if (referenceSite != null) {
            orderedSites.add(referenceSite);
        }
        orderedSites.addAll(siteMap.values());
        return orderedSites;
    }

    private OfferListFetchResult fetchNoonOfferList(
            NoonSession session,
            ProjectSiteContext projectSite,
            List<String> warnings
    ) {
        return fetchOfferList(session, projectSite, OFFER_LIST_NOON_URL, "noon", warnings, true);
    }

    private OfferListFetchResult fetchSupermallOfferList(
            NoonSession session,
            ProjectSiteContext projectSite,
            List<String> warnings
    ) {
        try {
            return fetchOfferList(session, projectSite, OFFER_LIST_SUPERMALL_URL, "supermall", warnings, false);
        } catch (RuntimeException exception) {
            warnings.add("站点 " + projectSite.getStoreCode() + " 的 Supermall 库存读取失败，已继续保留 Noon 库存。");
            return new OfferListFetchResult(0, Collections.emptyList());
        }
    }

    private OfferListFetchResult fetchOfferList(
            NoonSession session,
            ProjectSiteContext projectSite,
            String offerListUrl,
            String offerTab,
            List<String> warnings,
            boolean warnWhenEmpty
    ) {
        List<JsonNode> allHits = new ArrayList<>();
        int total = 0;
        int page = 1;
        int pageCount = 1;

        while (page <= pageCount) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("page", page);
            body.put("per_page", OFFER_PAGE_SIZE);
            body.put("noon_store_code", projectSite.getStoreCode());
            body.put("noonChannelType", offerTab);
            JsonNode root = session.postJson(offerListUrl, body, true);
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("Noon " + offerTab + " 商品索引返回错误：" + root.path("error").asText());
            }
            JsonNode dataNode = root.path("data");
            JsonNode hitsNode = dataNode.path("hits");
            if (hitsNode.isArray()) {
                for (JsonNode hitNode : hitsNode) {
                    allHits.add(hitNode);
                }
            }

            total = dataNode.path("total").asInt(allHits.size());
            if (total <= 0) {
                total = allHits.size();
            }
            pageCount = Math.max((int) Math.ceil(total / (double) OFFER_PAGE_SIZE), 1);
            page++;
        }

        if (warnWhenEmpty && allHits.isEmpty()) {
            warnings.add("站点 " + projectSite.getStoreCode() + " 当前没有拉到 " + offerTab + " 商品索引。");
        }

        return new OfferListFetchResult(total, allHits);
    }

    private JsonNode fetchRetrieveBatch(NoonSession session, List<String> skuParents) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode skuParentsNode = body.putArray("skuParents");
        for (String skuParent : skuParents) {
            skuParentsNode.add(skuParent);
        }
        body.putArray("attributeCodes");
        return session.postJson(ZSKU_RETRIEVE_URL, body, true);
    }

    private void mergeSupermallOffers(
            ProjectSiteContext projectSite,
            List<JsonNode> supermallHits,
            LinkedHashMap<String, ProductIndexEntry> productIndexMap
    ) {
        if (supermallHits == null || supermallHits.isEmpty() || productIndexMap == null) {
            return;
        }
        for (JsonNode hitNode : supermallHits) {
            String skuParent = skuParent(hitNode);
            if (!StringUtils.hasText(skuParent)) {
                continue;
            }
            ProductIndexEntry entry = productIndexMap.computeIfAbsent(skuParent, ProductIndexEntry::new);
            if (!StringUtils.hasText(entry.partnerSku)) {
                entry.partnerSku = text(hitNode, "partner_sku");
            }
            if (!StringUtils.hasText(entry.pskuCode)) {
                entry.pskuCode = NoonProductListFieldSupport.pskuCode(hitNode);
            }
            if (!StringUtils.hasText(entry.offerCode)) {
                entry.offerCode = text(hitNode, "offer_code");
            }
            if (!StringUtils.hasText(entry.catalogSku)) {
                entry.catalogSku = firstNonBlank(
                        text(hitNode, "sku"),
                        text(hitNode, "catalog_sku"),
                        text(hitNode, "zsku_child"),
                        text(hitNode, "child_sku")
                );
            }
            if (!StringUtils.hasText(entry.title)) {
                entry.title = text(hitNode.path("content"), "title");
            }
            if (!StringUtils.hasText(entry.brand)) {
                entry.brand = firstNonBlank(text(hitNode.path("content"), "brand"), text(hitNode, "brand_code"));
            }
            if (!StringUtils.hasText(entry.imageUrl)) {
                entry.imageUrl = resolveImageUrl(text(hitNode.path("content"), "image"));
            }
            if (!StringUtils.hasText(entry.storeCode)) {
                entry.storeCode = projectSite.getStoreCode();
            }
            if (!StringUtils.hasText(entry.site)) {
                entry.site = projectSite.getSite();
            }
            if (StringUtils.hasText(projectSite.getSite())) {
                entry.sites.add(projectSite.getSite());
            }

            ProductSiteOfferEntry siteOfferEntry = entry.siteOffers.computeIfAbsent(
                    normalize(projectSite.getStoreCode()),
                    ignored -> new ProductSiteOfferEntry(projectSite.getStoreCode())
            );
            siteOfferEntry.pskuCode = firstNonBlank(
                    siteOfferEntry.pskuCode,
                    NoonProductListFieldSupport.pskuCode(hitNode)
            );
            siteOfferEntry.offerCode = firstNonBlank(siteOfferEntry.offerCode, text(hitNode, "offer_code"));
            int supermallStock = supermallOfferStock(hitNode);
            siteOfferEntry.supermallStock += supermallStock;
            entry.totalSupermallStock += supermallStock;
        }
    }

    private String skuParent(JsonNode hitNode) {
        return firstNonBlank(
                text(hitNode, "csku_parent"),
                text(hitNode, "zsku_parent"),
                text(hitNode, "sku_parent"),
                text(hitNode, "catalog_sku")
        );
    }

    private void applyFollowSellCatalogContent(NoonSession session, ProductIndexEntry entry) {
        if (entry == null || !needsCatalogContent(entry)) {
            return;
        }
        productNoonCatalogContentService.fetchFollowSellCatalogContent(
                session,
                entry.catalogSku,
                entry.site,
                "store-initialization"
        ).ifPresent(content -> applyCatalogContent(entry, content));
    }

    private boolean needsCatalogContent(ProductIndexEntry entry) {
        return entry != null
                && StringUtils.hasText(entry.catalogSku)
                && (!StringUtils.hasText(entry.title) || !StringUtils.hasText(entry.imageUrl));
    }

    private void applyCatalogContent(ProductIndexEntry entry, CatalogContent content) {
        if (entry == null || content == null) {
            return;
        }
        entry.title = firstNonBlank(entry.title, content.getTitleEn(), content.getTitleAr());
        entry.brand = firstNonBlank(entry.brand, content.getBrand());
        if (!StringUtils.hasText(entry.imageUrl) && !content.getImages().isEmpty()) {
            entry.imageUrl = content.getImages().get(0);
        }
        if (StringUtils.hasText(content.getCatalogSku()) && !StringUtils.hasText(entry.catalogSku)) {
            entry.catalogSku = content.getCatalogSku();
        }
    }

    private List<StoreInitializationProductSampleView> buildSampleProducts(
            LinkedHashMap<String, ProductIndexEntry> productIndexMap
    ) {
        List<StoreInitializationProductSampleView> samples = new ArrayList<>();
        for (ProductIndexEntry entry : productIndexMap.values()) {
            if (samples.size() >= 8) {
                break;
            }
            StoreInitializationProductSampleView sample = new StoreInitializationProductSampleView();
            sample.setSkuParent(entry.skuParent);
            sample.setCurrentZCode(entry.skuParent);
            sample.setPartnerSku(entry.partnerSku);
            sample.setPskuCode(entry.pskuCode);
            sample.setOfferCode(entry.offerCode);
            sample.setStoreCode(entry.storeCode);
            sample.setSite(entry.site);
            sample.setTitle(entry.title);
            sample.setBrand(entry.brand);
            sample.setImageUrl(entry.imageUrl);
            sample.setBarcode(entry.barcode);
            sample.setCurrency(entry.currency);
            sample.setPrice(entry.price);
            sample.setProductFulltype(entry.productFulltype);
            sample.setVariantCount(entry.variantCount);
            sample.setLiveStatus(entry.liveStatus);
            samples.add(sample);
        }
        return samples;
    }

    private List<StoreInitializationProductListItemView> buildProductItems(
            LinkedHashMap<String, ProductIndexEntry> productIndexMap
    ) {
        List<StoreInitializationProductListItemView> items = new ArrayList<>();
        for (ProductIndexEntry entry : productIndexMap.values()) {
            StoreInitializationProductListItemView item = new StoreInitializationProductListItemView();
            item.setSkuParent(entry.skuParent);
            item.setCurrentZCode(entry.skuParent);
            item.setProductSourceType(ProductSourceTypeSupport.resolve(null, entry.catalogSku, entry.skuParent));
            item.setPartnerSku(entry.partnerSku);
            item.setPskuCode(entry.pskuCode);
            item.setOfferCode(entry.offerCode);
            item.setReferenceStoreCode(entry.storeCode);
            item.setTitle(entry.title);
            item.setBrand(entry.brand);
            item.setImageUrl(entry.imageUrl);
            item.setBarcode(entry.barcode);
            item.setCurrency(entry.currency);
            item.setReferencePrice(entry.price);
            item.setOriginalPrice(entry.originalPrice);
            item.setSalePrice(entry.salePrice);
            item.setProductFulltype(entry.productFulltype);
            item.setSkuGroup(entry.skuGroup);
            item.setGroupRef(entry.groupRef);
            item.setGroupRefCanonical(entry.groupRefCanonical);
            item.setLiveStatus(entry.liveStatus);
            item.setStatusCode(entry.statusCode);
            item.setIsActive(resolveLiveStatusActive(entry.liveStatus));
            item.setVariantCount(entry.variantCount);
            item.setSiteOfferCount(entry.siteOfferCount);
            item.setSiteLabels(new ArrayList<>(entry.sites));
            item.setLiveStatuses(new ArrayList<>(entry.liveStatuses));
            item.setIssueTags(sanitizedIssueTags(entry));
            item.setTotalFbnStock(entry.totalFbnStock + entry.totalSupermallStock);
            item.setTotalSupermallStock(entry.totalSupermallStock);
            item.setTotalFbpStock(entry.totalFbpStock);
            item.setViewsCount(entry.viewsCount);
            item.setUnitsSold(entry.unitsSold);
            item.setSalesAmount(entry.salesAmount);
            item.setSalesCurrency(entry.salesCurrency);
            item.setSyncStatus("synced");
            items.add(item);
        }
        return items;
    }

    private LinkedHashMap<String, Integer> normalizeNoonRequestCounts(Map<String, Integer> requestCounts) {
        LinkedHashMap<String, Integer> normalizedCounts = new LinkedHashMap<>();
        int total = 0;
        if (requestCounts != null) {
            Integer recordedTotal = requestCounts.get(NOON_REQUEST_TOTAL_KEY);
            if (recordedTotal != null) {
                total = Math.max(recordedTotal, 0);
            } else {
                for (Map.Entry<String, Integer> entry : requestCounts.entrySet()) {
                    if (!NOON_REQUEST_TOTAL_KEY.equals(entry.getKey()) && entry.getValue() != null) {
                        total += Math.max(entry.getValue(), 0);
                    }
                }
            }
        }
        normalizedCounts.put(NOON_REQUEST_TOTAL_KEY, total);
        for (String requestKey : DEFAULT_NOON_REQUEST_KEYS) {
            int count = requestCounts == null ? 0 : Math.max(requestCounts.getOrDefault(requestKey, 0), 0);
            normalizedCounts.put(requestKey, count);
        }
        if (requestCounts != null) {
            for (Map.Entry<String, Integer> entry : requestCounts.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || normalizedCounts.containsKey(entry.getKey())) {
                    continue;
                }
                normalizedCounts.put(entry.getKey(), entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0));
            }
        }
        return normalizedCounts;
    }

    private <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private String firstItem(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return firstNonBlank(values.get(0));
    }

    private Boolean resolveLiveStatusActive(String liveStatus) {
        String normalized = normalize(liveStatus);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if ("LIVE".equalsIgnoreCase(normalized)
                || "ACTIVE".equalsIgnoreCase(normalized)
                || "TRUE".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "ON".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("FALSE".equalsIgnoreCase(normalized)
                || "0".equals(normalized)
                || normalized.contains("NOT")
                || normalized.contains("OFF")
                || normalized.contains("INACTIVE")) {
            return false;
        }
        return null;
    }

    private String resolveProjectCode(
            NoonSession session,
            String localProjectCode,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        JsonNode root = session.postJson(PROJECT_LIST_URL, body, false);
        JsonNode projectsNode = root.path("projects");
        boolean strictProjectMatchRequired = requiresExactProjectMatch(localProjectCode, store);
        if (!projectsNode.isArray() || projectsNode.size() == 0) {
            if (strictProjectMatchRequired) {
                throw new IllegalStateException(
                        "Noon project.list 未返回任何项目，不能验证目标项目 " + localProjectCode + " 授权。"
                );
            }
            return localProjectCode;
        }

        String localDigits = extractDigits(localProjectCode);
        String projectName = normalize(store.getProjectName());
        for (JsonNode projectNode : projectsNode) {
            String candidateCode = text(projectNode, "projectCode");
            if (StringUtils.hasText(localProjectCode) && localProjectCode.equalsIgnoreCase(candidateCode)) {
                return candidateCode;
            }
        }

        if (StringUtils.hasText(localDigits)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateCode = text(projectNode, "projectCode");
                if (localDigits.equals(extractDigits(candidateCode))) {
                    return candidateCode;
                }
            }
        }

        if (StringUtils.hasText(projectName)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateName = normalize(text(projectNode, "projectName"));
                if (projectName.equalsIgnoreCase(candidateName)) {
                    String candidateCode = text(projectNode, "projectCode");
                    if (strictProjectMatchRequired
                            && StringUtils.hasText(candidateCode)
                            && !candidateCode.equalsIgnoreCase(localProjectCode)) {
                        throw new IllegalStateException(
                                "Noon project.list 找到项目名 "
                                        + projectName
                                        + "，但项目码为 "
                                        + candidateCode
                                        + "，不是目标 "
                                        + localProjectCode
                                        + "；本次不会写入商品正式数据面。"
                        );
                    }
                    return candidateCode;
                }
            }
        }

        if (strictProjectMatchRequired) {
            throw new IllegalStateException(
                    "当前 Noon 账号未授权目标项目 "
                            + localProjectCode
                            + "：project.list 未返回目标项目；本次不会写入商品正式数据面。"
            );
        }

        String fallbackProjectCode = text(projectsNode.get(0), "projectCode");
        if (StringUtils.hasText(fallbackProjectCode)
                && StringUtils.hasText(localProjectCode)
                && !fallbackProjectCode.equalsIgnoreCase(localProjectCode)) {
            warnings.add(
                    "本地 projectCode="
                            + localProjectCode
                            + " 与 Noon 实时 projectCode="
                            + fallbackProjectCode
                            + " 不一致，初始化已按 Noon 实时项目码执行。"
            );
        }
        return StringUtils.hasText(fallbackProjectCode) ? fallbackProjectCode : localProjectCode;
    }

    private ProjectAuthorizationCheck inspectProjectAuthorization(
            NoonSession session,
            String localProjectCode,
            StoreSyncStoreRecord store
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        JsonNode root = session.postJson(PROJECT_LIST_URL, body, false);
        JsonNode projectsNode = root.path("projects");
        ProjectAuthorizationCheck check = new ProjectAuthorizationCheck();
        if (!projectsNode.isArray()) {
            return check;
        }

        check.candidateProjectCount = projectsNode.size();
        String projectName = store == null ? null : normalize(store.getProjectName());
        for (JsonNode projectNode : projectsNode) {
            String candidateCode = text(projectNode, "projectCode");
            String candidateName = text(projectNode, "projectName");
            if (StringUtils.hasText(candidateCode)) {
                check.candidateProjectCodes.add(candidateCode);
            }
            if (StringUtils.hasText(localProjectCode) && localProjectCode.equalsIgnoreCase(candidateCode)) {
                check.authorized = true;
                check.matchedProjectCode = candidateCode;
                check.matchedProjectName = candidateName;
                return check;
            }
        }

        if (StringUtils.hasText(projectName)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateName = text(projectNode, "projectName");
                if (projectName.equalsIgnoreCase(candidateName)) {
                    check.matchedProjectCode = text(projectNode, "projectCode");
                    check.matchedProjectName = candidateName;
                    return check;
                }
            }
        }
        return check;
    }

    private void ensureProjectAuthorization(
            String localProjectCode,
            String resolvedProjectCode,
            StoreSyncStoreRecord store
    ) {
        if (!requiresExactProjectMatch(localProjectCode, store)) {
            return;
        }
        if (!StringUtils.hasText(resolvedProjectCode) || !resolvedProjectCode.equalsIgnoreCase(localProjectCode)) {
            throw new IllegalStateException(
                    "当前 Noon 账号解析到的项目码为 "
                            + firstNonBlank(resolvedProjectCode, "空")
                            + "，不是目标项目 "
                            + localProjectCode
                            + "；本次不会写入商品正式数据面。"
            );
        }
    }

    private boolean requiresExactProjectMatch(String localProjectCode, StoreSyncStoreRecord store) {
        String projectCode = normalize(localProjectCode);
        String projectName = store == null ? null : normalize(store.getProjectName());
        return (StringUtils.hasText(projectCode) && projectCode.toUpperCase(Locale.ROOT).startsWith("PRJ"))
                || "canman".equalsIgnoreCase(projectName);
    }

    private String resolveNoonUser(StoreContext context) {
        StoreSyncStoreRecord referenceStore = context == null ? null : context.referenceStore;
        StoreSyncOwnerContext owner = context == null ? null : context.owner;
        return firstNonBlank(
                referenceStore == null ? null : referenceStore.getNoonPartnerUser(),
                referenceStore == null ? null : referenceStore.getNoonPartnerProjectUser(),
                owner == null ? null : owner.getNoonPartnerUser(),
                owner == null ? null : owner.getNoonPartnerProjectUser()
        );
    }

    private String resolveNoonCookie(StoreContext context) {
        StoreSyncStoreRecord referenceStore = context == null ? null : context.referenceStore;
        StoreSyncOwnerContext owner = context == null ? null : context.owner;
        return firstNonBlank(
                referenceStore == null ? null : referenceStore.getNoonPartnerCookie(),
                owner == null ? null : owner.getNoonPartnerCookie()
        );
    }

    private NoonSession openInitializationNoonSession(
            Long ownerUserId,
            String noonUser,
            String persistedCookie,
            String projectCode,
            String storeCode
    ) {
        return noonSessionGateway.loginWithPersistedCookie(
                ownerUserId,
                noonUser,
                persistedCookie,
                projectCode,
                storeCode
        );
    }

    private Long requestOwnerId(StoreContext context) {
        return context == null || context.owner == null ? null : context.owner.getId();
    }

    private String resolveImageUrl(String rawImagePath) {
        String imagePath = normalize(rawImagePath);
        if (!StringUtils.hasText(imagePath)) {
            return null;
        }
        if (isHttpOrProtocolRelativeUrl(imagePath)) {
            return ProductImageUrlSupport.normalize(imagePath);
        }
        return ProductImageUrlSupport.normalize("https://f.nooncdn.com/" + imagePath.replaceFirst("^/+", ""));
    }

    private boolean isHttpOrProtocolRelativeUrl(String value) {
        return value.startsWith("//")
                || value.regionMatches(true, 0, "http://", 0, "http://".length())
                || value.regionMatches(true, 0, "https://", 0, "https://".length());
    }

    private String resolveFirstImage(JsonNode commonNode) {
        for (int index = 1; index <= 12; index++) {
            String image = text(commonNode, "image_url_" + index);
            if (StringUtils.hasText(image)) {
                return image;
            }
        }
        return null;
    }

    private String deriveSiteFromStoreCode(String storeCode) {
        String normalizedStoreCode = normalize(storeCode);
        if (!StringUtils.hasText(normalizedStoreCode) || !normalizedStoreCode.contains("-")) {
            return null;
        }
        String[] parts = normalizedStoreCode.split("-");
        if (parts.length == 0) {
            return null;
        }
        String suffix = parts[parts.length - 1];
        if (suffix.length() < 2) {
            return suffix.toUpperCase(Locale.ROOT);
        }
        return suffix.substring(suffix.length() - 2).toUpperCase(Locale.ROOT);
    }

    private String projectKey(StoreSyncStoreRecord store) {
        String projectCode = normalize(store.getProjectCode());
        if (StringUtils.hasText(projectCode)) {
            return "project:" + projectCode.toLowerCase(Locale.ROOT);
        }

        String projectName = normalize(store.getProjectName());
        if (StringUtils.hasText(projectName)) {
            return "project-name:" + projectName.toLowerCase(Locale.ROOT);
        }

        return "store:" + normalize(store.getStoreCode());
    }

    private String stateKey(Long ownerUserId, String storeCode) {
        return ownerUserId + ":" + normalize(storeCode);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Boolean booleanValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        String value = valueNode.asText();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return null;
    }

    private Long longValue(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull() || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                continue;
            }
            if (valueNode.isNumber()) {
                return valueNode.asLong();
            }
            String value = valueNode.asText();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                return Long.parseLong(value.replace(",", "").trim());
            } catch (NumberFormatException ignored) {
                // Try the next field.
            }
        }
        return null;
    }

    private List<String> sanitizedIssueTags(ProductIndexEntry productItem) {
        List<String> issues = new ArrayList<>();
        if (productItem == null || productItem.issueTags == null || productItem.issueTags.isEmpty()) {
            return issues;
        }
        for (String issueTag : productItem.issueTags) {
            String normalizedIssue = normalize(issueTag);
            if (!StringUtils.hasText(normalizedIssue) || shouldSuppressContradictedIssue(productItem, normalizedIssue)) {
                continue;
            }
            if (!issues.contains(normalizedIssue)) {
                issues.add(normalizedIssue);
            }
        }
        return issues;
    }

    private boolean shouldSuppressContradictedIssue(ProductIndexEntry productItem, String issueTag) {
        String normalizedIssue = normalize(issueTag);
        if (!StringUtils.hasText(normalizedIssue)) {
            return true;
        }
        if ("no_offer".equalsIgnoreCase(normalizedIssue) && hasOfferSignal(productItem)) {
            return true;
        }
        if ("stock_check".equalsIgnoreCase(normalizedIssue) && hasStockSignal(productItem)) {
            return true;
        }
        return "valid_price".equalsIgnoreCase(normalizedIssue) && hasPositivePriceSignal(productItem);
    }

    private boolean hasOfferSignal(ProductIndexEntry productItem) {
        if (productItem == null) {
            return false;
        }
        if (StringUtils.hasText(productItem.offerCode)
                || StringUtils.hasText(productItem.pskuCode)
                || productItem.siteOfferCount > 0) {
            return true;
        }
        return productItem.siteOffers.values().stream().anyMatch(offer ->
                StringUtils.hasText(offer.offerCode) || StringUtils.hasText(offer.pskuCode)
        );
    }

    private boolean hasStockSignal(ProductIndexEntry productItem) {
        if (productItem == null) {
            return false;
        }
        if (productItem.totalFbnStock + productItem.totalSupermallStock + productItem.totalFbpStock > 0) {
            return true;
        }
        return productItem.siteOffers.values().stream().anyMatch(offer ->
                offer.fbnStock + offer.supermallStock + offer.fbpStock > 0
        );
    }

    private boolean hasPositivePriceSignal(ProductIndexEntry productItem) {
        if (productItem == null) {
            return false;
        }
        if (isPositiveNumber(productItem.finalPrice)
                || isPositiveNumber(productItem.price)
                || isPositiveNumber(productItem.salePrice)
                || isPositiveNumber(productItem.originalPrice)) {
            return true;
        }
        return productItem.siteOffers.values().stream().anyMatch(offer ->
                isPositiveNumber(offer.finalPrice)
                        || isPositiveNumber(offer.salePrice)
                        || isPositiveNumber(offer.originalPrice)
        );
    }

    private boolean isPositiveNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return Double.parseDouble(value.replace(",", "").trim()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private String resolveOfferListFinalPriceSource(String finalPrice, String salePrice, String originalPrice) {
        if (!StringUtils.hasText(finalPrice)) {
            return null;
        }
        if (StringUtils.hasText(salePrice) && !finalPrice.trim().equals(salePrice.trim())) {
            return "promo";
        }
        if (StringUtils.hasText(salePrice)) {
            return "sale";
        }
        if (StringUtils.hasText(originalPrice) && !finalPrice.trim().equals(originalPrice.trim())) {
            return "promo";
        }
        return "base";
    }

    private int explicitSupermallStock(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        for (String fieldName : new String[]{"supermall_stock", "supermall_fbn_stock", "fbn_supermall_stock"}) {
            JsonNode valueNode = node.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                return valueNode.asInt(0);
            }
        }
        return 0;
    }

    private int supermallOfferStock(JsonNode node) {
        int explicitStock = explicitSupermallStock(node);
        if (explicitStock > 0) {
            return explicitStock;
        }
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        for (String fieldName : new String[]{"stock", "fbn_stock", "qty_net", "quantity"}) {
            JsonNode valueNode = node.path(fieldName);
            if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                return valueNode.asInt(0);
            }
        }
        return 0;
    }

    private boolean isRateLimitedMessage(String message) {
        return StringUtils.hasText(message)
                && (message.contains("HTTP 429")
                || message.toLowerCase(Locale.ROOT).contains("too many requests")
                || message.toLowerCase(Locale.ROOT).contains("ip_channel"));
    }

    private String extractDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\D+", "");
    }

    private String nowText() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME_FORMATTER);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME_FORMATTER);
        } catch (Exception exception) {
            return null;
        }
    }

    private static class StoreContext {
        private final StoreSyncOwnerContext owner;
        private final StoreSyncStoreRecord referenceStore;
        private final List<StoreSyncStoreRecord> localProjectStores;

        private StoreContext(
                StoreSyncOwnerContext owner,
                StoreSyncStoreRecord referenceStore,
                List<StoreSyncStoreRecord> localProjectStores
        ) {
            this.owner = owner;
            this.referenceStore = referenceStore;
            this.localProjectStores = localProjectStores;
        }
    }

    private static class OfferListFetchResult {
        private final int total;
        private final List<JsonNode> hits;

        private OfferListFetchResult(int total, List<JsonNode> hits) {
            this.total = total;
            this.hits = hits;
        }
    }

    private static class ProductIndexEntry {
        private final String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String offerCode;
        private String catalogSku;
        private String storeCode;
        private String site;
        private String title;
        private String brand;
        private String imageUrl;
        private List<String> galleryImages = new ArrayList<>();
        private String barcode;
        private final Set<String> barcodes = new LinkedHashSet<>();
        private String currency;
        private String price;
        private String originalPrice;
        private String salePrice;
        private String finalPrice;
        private String finalPriceSource;
        private String productFulltype;
        private String skuGroup;
        private String groupRef;
        private String groupRefCanonical;
        private Integer variantCount;
        private String liveStatus;
        private String statusCode;
        private String deliveryMethod;
        private Boolean isWinningBuybox;
        private Long viewsCount;
        private Long unitsSold;
        private String salesAmount;
        private String salesCurrency;
        private final Set<String> sites = new LinkedHashSet<>();
        private final Set<String> issueTags = new LinkedHashSet<>();
        private final Set<String> liveStatuses = new LinkedHashSet<>();
        private final LinkedHashMap<String, ProductSiteOfferEntry> siteOffers = new LinkedHashMap<>();
        private int totalFbnStock;
        private int totalSupermallStock;
        private int totalFbpStock;
        private int siteOfferCount;

        private ProductIndexEntry(String skuParent) {
            this.skuParent = skuParent;
        }
    }

    private static class ProductSiteOfferEntry {
        private final String storeCode;
        private String pskuCode;
        private String offerCode;
        private String currency;
        private String barcode;
        private String originalPrice;
        private String salePrice;
        private String finalPrice;
        private String finalPriceSource;
        private String activePromotionCode;
        private String activePromotionName;
        private String activePromotionUrl;
        private String liveStatus;
        private String statusCode;
        private String deliveryMethod;
        private Boolean isWinningBuybox;
        private Long viewsCount;
        private Long unitsSold;
        private String salesAmount;
        private String salesCurrency;
        private Boolean isActive;
        private int fbnStock;
        private int supermallStock;
        private int fbpStock;

        private ProductSiteOfferEntry(String storeCode) {
            this.storeCode = storeCode;
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

    private static class ProjectAuthorizationCheck {
        private boolean authorized;
        private int candidateProjectCount;
        private String matchedProjectCode;
        private String matchedProjectName;
        private final List<String> candidateProjectCodes = new ArrayList<>();
    }

    private class InitializationState {
        private final StoreInitializationStatusView view;

        private InitializationState(StoreInitializationStatusView view) {
            this.view = view;
        }

        private synchronized boolean isRunning() {
            return "RUNNING".equals(view.getStatus());
        }

        private synchronized void updateStoreMeta(
                String projectName,
                String projectCode,
                String storeCode,
                int siteCount
        ) {
            view.setProjectName(projectName);
            view.setProjectCode(projectCode);
            view.setStoreCode(storeCode);
            view.setSiteCount(siteCount);
        }

        private synchronized void replaceSiteSummaries(List<StoreInitializationSiteSummaryView> siteSummaries) {
            List<StoreInitializationSiteSummaryView> copied = new ArrayList<>();
            for (StoreInitializationSiteSummaryView siteSummary : siteSummaries) {
                copied.add(copySiteSummary(siteSummary));
            }
            view.setSiteSummaries(copied);
        }

        private synchronized void replaceSampleProducts(List<StoreInitializationProductSampleView> sampleProducts) {
            List<StoreInitializationProductSampleView> copied = new ArrayList<>();
            for (StoreInitializationProductSampleView sampleProduct : sampleProducts) {
                copied.add(copyProductSample(sampleProduct));
            }
            view.setSampleProducts(copied);
            view.setCanEnterProductWorkbench(!copied.isEmpty());
        }

        private synchronized void replaceProductItems(List<StoreInitializationProductListItemView> productItems) {
            List<StoreInitializationProductListItemView> copied = new ArrayList<>();
            for (StoreInitializationProductListItemView productItem : productItems) {
                copied.add(copyProductItem(productItem));
            }
            view.setProductItems(copied);
        }

        private synchronized void setCounts(int uniqueProductCount, int siteOfferCount) {
            view.setUniqueProductCount(uniqueProductCount);
            view.setSiteOfferCount(siteOfferCount);
        }

        private synchronized void setNoonRequestCounts(Map<String, Integer> requestCounts) {
            LinkedHashMap<String, Integer> normalizedCounts = normalizeNoonRequestCounts(requestCounts);
            view.setNoonRequestCounts(normalizedCounts);
            view.setNoonRequestTotalCount(normalizedCounts.getOrDefault(NOON_REQUEST_TOTAL_KEY, 0));
        }

        private synchronized void markRunning(
                String phaseLabel,
                int totalSteps,
                int activeStepIndex,
                int progressPercent,
                String message
        ) {
            view.setStatus("RUNNING");
            view.setPhaseLabel(phaseLabel);
            view.setProgressPercent(progressPercent);
            view.setMessage(message);
            if (!StringUtils.hasText(view.getStartedAt())) {
                view.setStartedAt(nowText());
            }
            updateSteps(totalSteps, activeStepIndex, "running", message);
        }

        private synchronized void completeStep(String message, int progressPercent) {
            int activeStepIndex = resolveActiveStepIndex();
            updateSteps(view.getSteps().size(), activeStepIndex, "completed", message);
            view.setProgressPercent(progressPercent);
            view.setMessage(message);
        }

        private synchronized void markReady(String projectName, String message) {
            for (StoreInitializationStepView step : view.getSteps()) {
                step.setStatus("completed");
            }
            view.setStatus("READY");
            view.setPhaseLabel("初始化完成");
            view.setProgressPercent(100);
            view.setMessage(message);
            view.setLastInitializedAt(nowText());
            if (!StringUtils.hasText(view.getProjectName())) {
                view.setProjectName(projectName);
            }
        }

        private synchronized void markFailed(String message) {
            int activeStepIndex = resolveActiveStepIndex();
            updateSteps(view.getSteps().size(), activeStepIndex, "failed", message);
            view.setStatus("FAILED");
            view.setPhaseLabel("初始化失败");
            view.setMessage(message);
        }

        private synchronized boolean hasProductItems() {
            return view.getProductItems() != null && !view.getProductItems().isEmpty();
        }

        private synchronized List<String> mutableWarnings() {
            return view.getWarnings();
        }

        private synchronized StoreInitializationStatusView snapshot() {
            StoreInitializationStatusView copy = new StoreInitializationStatusView();
            copy.setMode(view.getMode());
            copy.setReady(view.isReady());
            copy.setStatus(view.getStatus());
            copy.setMessage(view.getMessage());
            copy.setOwnerUserId(view.getOwnerUserId());
            copy.setProjectName(view.getProjectName());
            copy.setProjectCode(view.getProjectCode());
            copy.setStoreCode(view.getStoreCode());
            copy.setSiteCount(view.getSiteCount());
            copy.setUniqueProductCount(view.getUniqueProductCount());
            copy.setSiteOfferCount(view.getSiteOfferCount());
            copy.setProgressPercent(view.getProgressPercent());
            copy.setPhaseLabel(view.getPhaseLabel());
            copy.setStartedAt(view.getStartedAt());
            copy.setLastInitializedAt(view.getLastInitializedAt());
            copy.setNoonRequestTotalCount(view.getNoonRequestTotalCount());
            copy.setNoonRequestCounts(new LinkedHashMap<>(view.getNoonRequestCounts()));
            copy.setCanEnterProductWorkbench(view.getCanEnterProductWorkbench());
            copy.setMissingCoreTables(new ArrayList<>(view.getMissingCoreTables()));

            List<StoreInitializationStepView> copiedSteps = new ArrayList<>();
            for (StoreInitializationStepView step : view.getSteps()) {
                StoreInitializationStepView copiedStep = new StoreInitializationStepView();
                copiedStep.setCode(step.getCode());
                copiedStep.setLabel(step.getLabel());
                copiedStep.setStatus(step.getStatus());
                copiedStep.setMessage(step.getMessage());
                copiedSteps.add(copiedStep);
            }
            copy.setSteps(copiedSteps);

            List<StoreInitializationSiteSummaryView> copiedSites = new ArrayList<>();
            for (StoreInitializationSiteSummaryView siteSummary : view.getSiteSummaries()) {
                copiedSites.add(copySiteSummary(siteSummary));
            }
            copy.setSiteSummaries(copiedSites);

            List<StoreInitializationProductSampleView> copiedSamples = new ArrayList<>();
            for (StoreInitializationProductSampleView sample : view.getSampleProducts()) {
                copiedSamples.add(copyProductSample(sample));
            }
            copy.setSampleProducts(copiedSamples);

            List<StoreInitializationProductListItemView> copiedItems = new ArrayList<>();
            for (StoreInitializationProductListItemView item : view.getProductItems()) {
                copiedItems.add(copyProductItem(item));
            }
            copy.setProductItems(copiedItems);
            copy.setWarnings(new ArrayList<>(view.getWarnings()));
            return copy;
        }

        private void updateSteps(int totalSteps, int activeStepIndex, String activeStatus, String message) {
            for (int index = 0; index < totalSteps; index++) {
                StoreInitializationStepView step = view.getSteps().get(index);
                if (index < activeStepIndex - 1 && !"failed".equals(step.getStatus())) {
                    step.setStatus("completed");
                    continue;
                }
                if (index == activeStepIndex - 1) {
                    step.setStatus(activeStatus);
                    step.setMessage(message);
                    continue;
                }
                if (!"completed".equals(step.getStatus())) {
                    step.setStatus("pending");
                }
            }
        }

        private int resolveActiveStepIndex() {
            for (int index = 0; index < view.getSteps().size(); index++) {
                if ("running".equals(view.getSteps().get(index).getStatus())) {
                    return index + 1;
                }
            }
            return view.getSteps().size();
        }

        private StoreInitializationSiteSummaryView copySiteSummary(StoreInitializationSiteSummaryView source) {
            StoreInitializationSiteSummaryView target = new StoreInitializationSiteSummaryView();
            target.setStoreCode(source.getStoreCode());
            target.setSite(source.getSite());
            target.setLiveStatus(source.getLiveStatus());
            target.setProductCount(source.getProductCount());
            return target;
        }

        private StoreInitializationProductSampleView copyProductSample(StoreInitializationProductSampleView source) {
            StoreInitializationProductSampleView target = new StoreInitializationProductSampleView();
            target.setSkuParent(source.getSkuParent());
            target.setCurrentZCode(source.getCurrentZCode());
            target.setProductSourceType(source.getProductSourceType());
            target.setPartnerSku(source.getPartnerSku());
            target.setPskuCode(source.getPskuCode());
            target.setOfferCode(source.getOfferCode());
            target.setStoreCode(source.getStoreCode());
            target.setSite(source.getSite());
            target.setTitle(source.getTitle());
            target.setBrand(source.getBrand());
            target.setImageUrl(source.getImageUrl());
            target.setBarcode(source.getBarcode());
            target.setCurrency(source.getCurrency());
            target.setPrice(source.getPrice());
            target.setProductFulltype(source.getProductFulltype());
            target.setVariantCount(source.getVariantCount());
            target.setLiveStatus(source.getLiveStatus());
            return target;
        }

        private StoreInitializationProductListItemView copyProductItem(StoreInitializationProductListItemView source) {
            StoreInitializationProductListItemView target = new StoreInitializationProductListItemView();
            target.setSkuParent(source.getSkuParent());
            target.setCurrentZCode(source.getCurrentZCode());
            target.setPartnerSku(source.getPartnerSku());
            target.setPskuCode(source.getPskuCode());
            target.setOfferCode(source.getOfferCode());
            target.setReferenceStoreCode(source.getReferenceStoreCode());
            target.setTitle(source.getTitle());
            target.setTitleCn(source.getTitleCn());
            target.setBrand(source.getBrand());
            target.setImageUrl(source.getImageUrl());
            target.setBarcode(source.getBarcode());
            target.setCurrency(source.getCurrency());
            target.setReferencePrice(source.getReferencePrice());
            target.setOriginalPrice(source.getOriginalPrice());
            target.setSalePrice(source.getSalePrice());
            target.setProductFulltype(source.getProductFulltype());
            target.setVariantCount(source.getVariantCount());
            target.setSiteOfferCount(source.getSiteOfferCount());
            target.setGroupRef(source.getGroupRef());
            target.setLiveStatus(source.getLiveStatus());
            target.setStatusCode(source.getStatusCode());
            target.setListingStartedAt(source.getListingStartedAt());
            target.setListingStartedSource(source.getListingStartedSource());
            target.setIsActive(source.getIsActive());
            target.setSyncStatus(source.getSyncStatus());
            target.setLastSyncedAt(source.getLastSyncedAt());
            target.setHistoryMetaReady(source.getHistoryMetaReady());
            target.setPendingKeyContentHistoryCount(source.getPendingKeyContentHistoryCount());
            target.setVisibleKeyContentHistoryCount(source.getVisibleKeyContentHistoryCount());
            target.setPendingKeyContentHistoryVisibleAfter(source.getPendingKeyContentHistoryVisibleAfter());
            target.setSiteLabels(new ArrayList<>(source.getSiteLabels()));
            target.setLiveStatuses(new ArrayList<>(source.getLiveStatuses()));
            target.setIssueTags(new ArrayList<>(source.getIssueTags()));
            target.setTotalFbnStock(source.getTotalFbnStock());
            target.setTotalSupermallStock(source.getTotalSupermallStock());
            target.setTotalFbpStock(source.getTotalFbpStock());
            target.setViewsCount(source.getViewsCount());
            target.setUnitsSold(source.getUnitsSold());
            target.setSalesAmount(source.getSalesAmount());
            target.setSalesCurrency(source.getSalesCurrency());
            return target;
        }
    }

    public static class StoreInitializationCommand {
        private Long ownerUserId;
        private String storeCode;
        private String noonPassword;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getNoonPassword() {
            return noonPassword;
        }

        public void setNoonPassword(String noonPassword) {
            this.noonPassword = noonPassword;
        }
    }

    public static class StoreInitializationStatusView {
        private String mode;
        private boolean ready;
        private String status;
        private String message;
        private Long ownerUserId;
        private String projectName;
        private String projectCode;
        private String storeCode;
        private Integer siteCount;
        private Integer uniqueProductCount;
        private Integer siteOfferCount;
        private Integer progressPercent;
        private String phaseLabel;
        private String startedAt;
        private String lastInitializedAt;
        private Integer noonRequestTotalCount = 0;
        private Map<String, Integer> noonRequestCounts = new LinkedHashMap<>();
        private Boolean canEnterProductWorkbench = false;
        private List<String> missingCoreTables = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private List<StoreInitializationStepView> steps = new ArrayList<>();
        private List<StoreInitializationSiteSummaryView> siteSummaries = new ArrayList<>();
        private List<StoreInitializationProductSampleView> sampleProducts = new ArrayList<>();
        private List<StoreInitializationProductListItemView> productItems = new ArrayList<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(String projectCode) {
            this.projectCode = projectCode;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public Integer getSiteCount() {
            return siteCount;
        }

        public void setSiteCount(Integer siteCount) {
            this.siteCount = siteCount;
        }

        public Integer getUniqueProductCount() {
            return uniqueProductCount;
        }

        public void setUniqueProductCount(Integer uniqueProductCount) {
            this.uniqueProductCount = uniqueProductCount;
        }

        public Integer getSiteOfferCount() {
            return siteOfferCount;
        }

        public void setSiteOfferCount(Integer siteOfferCount) {
            this.siteOfferCount = siteOfferCount;
        }

        public Integer getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(Integer progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getPhaseLabel() {
            return phaseLabel;
        }

        public void setPhaseLabel(String phaseLabel) {
            this.phaseLabel = phaseLabel;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getLastInitializedAt() {
            return lastInitializedAt;
        }

        public void setLastInitializedAt(String lastInitializedAt) {
            this.lastInitializedAt = lastInitializedAt;
        }

        public Integer getNoonRequestTotalCount() {
            return noonRequestTotalCount;
        }

        public void setNoonRequestTotalCount(Integer noonRequestTotalCount) {
            this.noonRequestTotalCount = noonRequestTotalCount;
        }

        public Map<String, Integer> getNoonRequestCounts() {
            return noonRequestCounts;
        }

        public void setNoonRequestCounts(Map<String, Integer> noonRequestCounts) {
            this.noonRequestCounts = noonRequestCounts;
        }

        public Boolean getCanEnterProductWorkbench() {
            return canEnterProductWorkbench;
        }

        public void setCanEnterProductWorkbench(Boolean canEnterProductWorkbench) {
            this.canEnterProductWorkbench = canEnterProductWorkbench;
        }

        public List<String> getMissingCoreTables() {
            return missingCoreTables;
        }

        public void setMissingCoreTables(List<String> missingCoreTables) {
            this.missingCoreTables = missingCoreTables;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }

        public List<StoreInitializationStepView> getSteps() {
            return steps;
        }

        public void setSteps(List<StoreInitializationStepView> steps) {
            this.steps = steps;
        }

        public List<StoreInitializationSiteSummaryView> getSiteSummaries() {
            return siteSummaries;
        }

        public void setSiteSummaries(List<StoreInitializationSiteSummaryView> siteSummaries) {
            this.siteSummaries = siteSummaries;
        }

        public List<StoreInitializationProductSampleView> getSampleProducts() {
            return sampleProducts;
        }

        public void setSampleProducts(List<StoreInitializationProductSampleView> sampleProducts) {
            this.sampleProducts = sampleProducts;
        }

        public List<StoreInitializationProductListItemView> getProductItems() {
            return productItems;
        }

        public void setProductItems(List<StoreInitializationProductListItemView> productItems) {
            this.productItems = productItems;
        }
    }

    public static class StoreInitializationPreflightView {
        private String mode;
        private boolean ready;
        private String status;
        private String message;
        private Long ownerUserId;
        private String projectName;
        private String projectCode;
        private String targetProjectCode;
        private String storeCode;
        private Integer siteCount;
        private Integer noonRequestTotalCount = 0;
        private Map<String, Integer> noonRequestCounts = new LinkedHashMap<>();
        private Integer candidateProjectCount = 0;
        private List<String> candidateProjectCodes = new ArrayList<>();
        private String matchedProjectCode;
        private String matchedProjectName;
        private List<StoreInitializationSiteSummaryView> siteSummaries = new ArrayList<>();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(String projectCode) {
            this.projectCode = projectCode;
        }

        public String getTargetProjectCode() {
            return targetProjectCode;
        }

        public void setTargetProjectCode(String targetProjectCode) {
            this.targetProjectCode = targetProjectCode;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public Integer getSiteCount() {
            return siteCount;
        }

        public void setSiteCount(Integer siteCount) {
            this.siteCount = siteCount;
        }

        public Integer getNoonRequestTotalCount() {
            return noonRequestTotalCount;
        }

        public void setNoonRequestTotalCount(Integer noonRequestTotalCount) {
            this.noonRequestTotalCount = noonRequestTotalCount;
        }

        public Map<String, Integer> getNoonRequestCounts() {
            return noonRequestCounts;
        }

        public void setNoonRequestCounts(Map<String, Integer> noonRequestCounts) {
            this.noonRequestCounts = noonRequestCounts;
        }

        public Integer getCandidateProjectCount() {
            return candidateProjectCount;
        }

        public void setCandidateProjectCount(Integer candidateProjectCount) {
            this.candidateProjectCount = candidateProjectCount;
        }

        public List<String> getCandidateProjectCodes() {
            return candidateProjectCodes;
        }

        public void setCandidateProjectCodes(List<String> candidateProjectCodes) {
            this.candidateProjectCodes = candidateProjectCodes;
        }

        public String getMatchedProjectCode() {
            return matchedProjectCode;
        }

        public void setMatchedProjectCode(String matchedProjectCode) {
            this.matchedProjectCode = matchedProjectCode;
        }

        public String getMatchedProjectName() {
            return matchedProjectName;
        }

        public void setMatchedProjectName(String matchedProjectName) {
            this.matchedProjectName = matchedProjectName;
        }

        public List<StoreInitializationSiteSummaryView> getSiteSummaries() {
            return siteSummaries;
        }

        public void setSiteSummaries(List<StoreInitializationSiteSummaryView> siteSummaries) {
            this.siteSummaries = siteSummaries;
        }
    }

    public static class StoreInitializationStepView {
        private String code;
        private String label;
        private String status;
        private String message;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class StoreInitializationSiteSummaryView {
        private String storeCode;
        private String site;
        private String liveStatus;
        private Integer productCount;

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
        }

        public String getLiveStatus() {
            return liveStatus;
        }

        public void setLiveStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }

        public Integer getProductCount() {
            return productCount;
        }

        public void setProductCount(Integer productCount) {
            this.productCount = productCount;
        }
    }

    public static class StoreInitializationProductSampleView {
        private String skuParent;
        private String currentZCode;
        private String productSourceType;
        private String partnerSku;
        private String pskuCode;
        private String offerCode;
        private String storeCode;
        private String site;
        private String title;
        private String brand;
        private String imageUrl;
        private List<String> galleryImages = new ArrayList<>();
        private String barcode;
        private String currency;
        private String price;
        private String productFulltype;
        private Integer variantCount;
        private String liveStatus;

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getCurrentZCode() {
            return currentZCode;
        }

        public void setCurrentZCode(String currentZCode) {
            this.currentZCode = currentZCode;
        }

        public String getProductSourceType() {
            return productSourceType;
        }

        public void setProductSourceType(String productSourceType) {
            this.productSourceType = productSourceType;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getOfferCode() {
            return offerCode;
        }

        public void setOfferCode(String offerCode) {
            this.offerCode = offerCode;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String site) {
            this.site = site;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public List<String> getGalleryImages() {
            return galleryImages;
        }

        public void setGalleryImages(List<String> galleryImages) {
            this.galleryImages = galleryImages == null ? new ArrayList<>() : galleryImages;
        }

        public String getBarcode() {
            return barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }

        public String getProductFulltype() {
            return productFulltype;
        }

        public void setProductFulltype(String productFulltype) {
            this.productFulltype = productFulltype;
        }

        public Integer getVariantCount() {
            return variantCount;
        }

        public void setVariantCount(Integer variantCount) {
            this.variantCount = variantCount;
        }

        public String getLiveStatus() {
            return liveStatus;
        }

        public void setLiveStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }
    }

    public static class StoreInitializationProductListItemView {
        private String skuParent;
        private String currentZCode;
        private String productSourceType;
        private String partnerSku;
        private String pskuCode;
        private String offerCode;
        private String referenceStoreCode;
        private String title;
        private String titleCn;
        private String brand;
        private String imageUrl;
        private List<String> galleryImages = new ArrayList<>();
        private String barcode;
        private String currency;
        private String referencePrice;
        private String originalPrice;
        private String salePrice;
        private String productFulltype;
        private String skuGroup;
        private String groupRef;
        private String groupRefCanonical;
        private String liveStatus;
        private String statusCode;
        private String listingStartedAt;
        private String listingStartedSource;
        private String operationStageCode;
        private String operationStageUpdatedAt;
        private Long operationStageUpdatedBy;
        private Boolean isActive;
        private Boolean maintenanceEnabled;
        private String syncStatus;
        private String lastSyncedAt;
        private String lastDraftSavedAt;
        private String detailBaselineStatus;
        private String detailBaselineMessage;
        private String detailBaselineSyncedAt;
        private String productVariantSpecStatus;
        private Integer productVariantSpecTotalCount;
        private Integer productVariantSpecReadyCount;
        private Integer productVariantSpecMaintainedCount;
        private Integer variantCount;
        private Integer siteOfferCount;
        private Boolean historyMetaReady;
        private Integer pendingKeyContentHistoryCount;
        private Integer visibleKeyContentHistoryCount;
        private String pendingKeyContentHistoryVisibleAfter;
        private List<String> siteLabels = new ArrayList<>();
        private List<String> liveStatuses = new ArrayList<>();
        private List<String> issueTags = new ArrayList<>();
        private Integer totalFbnStock;
        private Integer totalSupermallStock;
        private Integer totalFbpStock;
        private Long viewsCount;
        private Long unitsSold;
        private String salesAmount;
        private String salesCurrency;
        private Map<String, Object> lastPublishTask;
        private Map<String, Object> listingPublishTask;

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getCurrentZCode() {
            return currentZCode;
        }

        public void setCurrentZCode(String currentZCode) {
            this.currentZCode = currentZCode;
        }

        public String getProductSourceType() {
            return productSourceType;
        }

        public void setProductSourceType(String productSourceType) {
            this.productSourceType = productSourceType;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getOfferCode() {
            return offerCode;
        }

        public void setOfferCode(String offerCode) {
            this.offerCode = offerCode;
        }

        public String getReferenceStoreCode() {
            return referenceStoreCode;
        }

        public void setReferenceStoreCode(String referenceStoreCode) {
            this.referenceStoreCode = referenceStoreCode;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitleCn() {
            return titleCn;
        }

        public void setTitleCn(String titleCn) {
            this.titleCn = titleCn;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public List<String> getGalleryImages() {
            return galleryImages;
        }

        public void setGalleryImages(List<String> galleryImages) {
            this.galleryImages = galleryImages == null ? new ArrayList<>() : galleryImages;
        }

        public String getBarcode() {
            return barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getReferencePrice() {
            return referencePrice;
        }

        public void setReferencePrice(String referencePrice) {
            this.referencePrice = referencePrice;
        }

        public String getOriginalPrice() {
            return originalPrice;
        }

        public void setOriginalPrice(String originalPrice) {
            this.originalPrice = originalPrice;
        }

        public String getSalePrice() {
            return salePrice;
        }

        public void setSalePrice(String salePrice) {
            this.salePrice = salePrice;
        }

        public String getProductFulltype() {
            return productFulltype;
        }

        public void setProductFulltype(String productFulltype) {
            this.productFulltype = productFulltype;
        }

        public String getGroupRef() {
            return groupRef;
        }

        public void setGroupRef(String groupRef) {
            this.groupRef = groupRef;
        }

        public String getSkuGroup() {
            return skuGroup;
        }

        public void setSkuGroup(String skuGroup) {
            this.skuGroup = skuGroup;
        }

        public String getGroupRefCanonical() {
            return groupRefCanonical;
        }

        public void setGroupRefCanonical(String groupRefCanonical) {
            this.groupRefCanonical = groupRefCanonical;
        }

        public String getLiveStatus() {
            return liveStatus;
        }

        public void setLiveStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public String getListingStartedAt() {
            return listingStartedAt;
        }

        public void setListingStartedAt(String listingStartedAt) {
            this.listingStartedAt = listingStartedAt;
        }

        public String getListingStartedSource() {
            return listingStartedSource;
        }

        public void setListingStartedSource(String listingStartedSource) {
            this.listingStartedSource = listingStartedSource;
        }

        public String getOperationStageCode() {
            return operationStageCode;
        }

        public void setOperationStageCode(String operationStageCode) {
            this.operationStageCode = operationStageCode;
        }

        public String getOperationStageUpdatedAt() {
            return operationStageUpdatedAt;
        }

        public void setOperationStageUpdatedAt(String operationStageUpdatedAt) {
            this.operationStageUpdatedAt = operationStageUpdatedAt;
        }

        public Long getOperationStageUpdatedBy() {
            return operationStageUpdatedBy;
        }

        public void setOperationStageUpdatedBy(Long operationStageUpdatedBy) {
            this.operationStageUpdatedBy = operationStageUpdatedBy;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }

        public Boolean getMaintenanceEnabled() {
            return maintenanceEnabled;
        }

        public void setMaintenanceEnabled(Boolean maintenanceEnabled) {
            this.maintenanceEnabled = maintenanceEnabled;
        }

        public String getSyncStatus() {
            return syncStatus;
        }

        public void setSyncStatus(String syncStatus) {
            this.syncStatus = syncStatus;
        }

        public String getLastSyncedAt() {
            return lastSyncedAt;
        }

        public void setLastSyncedAt(String lastSyncedAt) {
            this.lastSyncedAt = lastSyncedAt;
        }

        public String getLastDraftSavedAt() {
            return lastDraftSavedAt;
        }

        public void setLastDraftSavedAt(String lastDraftSavedAt) {
            this.lastDraftSavedAt = lastDraftSavedAt;
        }

        public String getDetailBaselineStatus() {
            return detailBaselineStatus;
        }

        public void setDetailBaselineStatus(String detailBaselineStatus) {
            this.detailBaselineStatus = detailBaselineStatus;
        }

        public String getDetailBaselineMessage() {
            return detailBaselineMessage;
        }

        public void setDetailBaselineMessage(String detailBaselineMessage) {
            this.detailBaselineMessage = detailBaselineMessage;
        }

        public String getDetailBaselineSyncedAt() {
            return detailBaselineSyncedAt;
        }

        public void setDetailBaselineSyncedAt(String detailBaselineSyncedAt) {
            this.detailBaselineSyncedAt = detailBaselineSyncedAt;
        }

        public String getProductVariantSpecStatus() {
            return productVariantSpecStatus;
        }

        public void setProductVariantSpecStatus(String productVariantSpecStatus) {
            this.productVariantSpecStatus = productVariantSpecStatus;
        }

        public Integer getProductVariantSpecTotalCount() {
            return productVariantSpecTotalCount;
        }

        public void setProductVariantSpecTotalCount(Integer productVariantSpecTotalCount) {
            this.productVariantSpecTotalCount = productVariantSpecTotalCount;
        }

        public Integer getProductVariantSpecReadyCount() {
            return productVariantSpecReadyCount;
        }

        public void setProductVariantSpecReadyCount(Integer productVariantSpecReadyCount) {
            this.productVariantSpecReadyCount = productVariantSpecReadyCount;
        }

        public Integer getProductVariantSpecMaintainedCount() {
            return productVariantSpecMaintainedCount;
        }

        public void setProductVariantSpecMaintainedCount(Integer productVariantSpecMaintainedCount) {
            this.productVariantSpecMaintainedCount = productVariantSpecMaintainedCount;
        }

        public Integer getVariantCount() {
            return variantCount;
        }

        public void setVariantCount(Integer variantCount) {
            this.variantCount = variantCount;
        }

        public Integer getSiteOfferCount() {
            return siteOfferCount;
        }

        public void setSiteOfferCount(Integer siteOfferCount) {
            this.siteOfferCount = siteOfferCount;
        }

        public Boolean getHistoryMetaReady() {
            return historyMetaReady;
        }

        public void setHistoryMetaReady(Boolean historyMetaReady) {
            this.historyMetaReady = historyMetaReady;
        }

        public Integer getPendingKeyContentHistoryCount() {
            return pendingKeyContentHistoryCount;
        }

        public void setPendingKeyContentHistoryCount(Integer pendingKeyContentHistoryCount) {
            this.pendingKeyContentHistoryCount = pendingKeyContentHistoryCount;
        }

        public Integer getVisibleKeyContentHistoryCount() {
            return visibleKeyContentHistoryCount;
        }

        public void setVisibleKeyContentHistoryCount(Integer visibleKeyContentHistoryCount) {
            this.visibleKeyContentHistoryCount = visibleKeyContentHistoryCount;
        }

        public String getPendingKeyContentHistoryVisibleAfter() {
            return pendingKeyContentHistoryVisibleAfter;
        }

        public void setPendingKeyContentHistoryVisibleAfter(String pendingKeyContentHistoryVisibleAfter) {
            this.pendingKeyContentHistoryVisibleAfter = pendingKeyContentHistoryVisibleAfter;
        }

        public List<String> getSiteLabels() {
            return siteLabels;
        }

        public void setSiteLabels(List<String> siteLabels) {
            this.siteLabels = siteLabels;
        }

        public List<String> getLiveStatuses() {
            return liveStatuses;
        }

        public void setLiveStatuses(List<String> liveStatuses) {
            this.liveStatuses = liveStatuses;
        }

        public List<String> getIssueTags() {
            return issueTags;
        }

        public void setIssueTags(List<String> issueTags) {
            this.issueTags = issueTags;
        }

        public Integer getTotalFbnStock() {
            return totalFbnStock;
        }

        public void setTotalFbnStock(Integer totalFbnStock) {
            this.totalFbnStock = totalFbnStock;
        }

        public Integer getTotalSupermallStock() {
            return totalSupermallStock;
        }

        public void setTotalSupermallStock(Integer totalSupermallStock) {
            this.totalSupermallStock = totalSupermallStock;
        }

        public Integer getTotalFbpStock() {
            return totalFbpStock;
        }

        public void setTotalFbpStock(Integer totalFbpStock) {
            this.totalFbpStock = totalFbpStock;
        }

        public Long getViewsCount() {
            return viewsCount;
        }

        public void setViewsCount(Long viewsCount) {
            this.viewsCount = viewsCount;
        }

        public Long getUnitsSold() {
            return unitsSold;
        }

        public void setUnitsSold(Long unitsSold) {
            this.unitsSold = unitsSold;
        }

        public String getSalesAmount() {
            return salesAmount;
        }

        public void setSalesAmount(String salesAmount) {
            this.salesAmount = salesAmount;
        }

        public String getSalesCurrency() {
            return salesCurrency;
        }

        public void setSalesCurrency(String salesCurrency) {
            this.salesCurrency = salesCurrency;
        }

        public Map<String, Object> getLastPublishTask() {
            return lastPublishTask;
        }

        public void setLastPublishTask(Map<String, Object> lastPublishTask) {
            this.lastPublishTask = lastPublishTask;
        }

        public Map<String, Object> getListingPublishTask() {
            return listingPublishTask;
        }

        public void setListingPublishTask(Map<String, Object> listingPublishTask) {
            this.listingPublishTask = listingPublishTask;
        }
    }
}
