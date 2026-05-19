package com.nuono.next.mobile;

import com.nuono.next.infrastructure.mapper.MobileMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.store.LocalDbStoreInitializationService.StoreInitializationStatusView;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbMobileWorkbenchService {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MobileMapper mobileMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final LocalMobileSessionStore sessionStore;
    private final ObjectProvider<LocalDbStoreInitializationService> storeInitializationServiceProvider;

    public LocalDbMobileWorkbenchService(
            MobileMapper mobileMapper,
            StoreSyncMapper storeSyncMapper,
            LocalMobileSessionStore sessionStore,
            ObjectProvider<LocalDbStoreInitializationService> storeInitializationServiceProvider
    ) {
        this.mobileMapper = mobileMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.sessionStore = sessionStore;
        this.storeInitializationServiceProvider = storeInitializationServiceProvider;
    }

    public MobileDashboardOverviewView dashboardOverview(String accessToken, String storeCode) {
        ResolvedStoreContext context = resolveContext(accessToken, storeCode);
        List<GeneratedTask> tasks = buildTasks(context);
        List<GeneratedMessage> messages = buildMessages(context, tasks);

        MobileDashboardOverviewView view = new MobileDashboardOverviewView();
        view.setStoreCode(context.selectedStore.getStoreCode());
        view.setStoreName(resolveStoreName(context.selectedStore));
        view.setYesterdaySalesAmount(resolveYesterdaySales(context));
        view.setYesterdayOrderCount(resolveYesterdayOrders(context));
        view.setTaskFailedCount((int) tasks.stream().filter(task -> "failed".equals(task.statusKey)).count());
        view.setUnreadMessageCount(messages.size());
        view.setLastUpdatedAt(resolveLastUpdatedAt(context));
        return view;
    }

    public MobileDashboardAlertsView dashboardAlerts(String accessToken, String storeCode) {
        ResolvedStoreContext context = resolveContext(accessToken, storeCode);
        List<GeneratedTask> tasks = buildTasks(context);
        List<GeneratedMessage> messages = buildMessages(context, tasks);

        MobileDashboardAlertsView view = new MobileDashboardAlertsView();
        view.setTaskFailedCount((int) tasks.stream().filter(task -> "failed".equals(task.statusKey)).count());
        view.setWarehouseNoticeCount(0);
        view.setSystemNoticeCount((int) messages.stream().filter(message -> "system".equals(message.type)).count());
        view.setItems(messages.stream()
                .sorted(Comparator.comparing((GeneratedMessage message) -> message.occurAt).reversed())
                .limit(10)
                .map(this::toDashboardAlert)
                .collect(Collectors.toList()));
        return view;
    }

    public MobilePageResponse<MobileMessageView> messageList(
            String accessToken,
            String storeCode,
            String type,
            Integer currentPage,
            Integer pageSize
    ) {
        ResolvedStoreContext context = resolveContext(accessToken, storeCode);
        List<GeneratedMessage> messages = buildMessages(context, buildTasks(context));
        String requestedType = normalizeType(type, "all");
        List<GeneratedMessage> filtered = messages.stream()
                .filter(message -> "all".equals(requestedType) || requestedType.equals(message.type))
                .sorted(Comparator.comparing((GeneratedMessage message) -> message.occurAt).reversed())
                .collect(Collectors.toList());

        return buildPage(
                filtered,
                currentPage,
                pageSize,
                this::toMessageView
        );
    }

    public MobilePageResponse<MobileTaskView> taskList(
            String accessToken,
            String storeCode,
            String status,
            String taskType,
            Integer currentPage,
            Integer pageSize
    ) {
        ResolvedStoreContext context = resolveContext(accessToken, storeCode);
        String requestedStatus = normalizeType(status, "all");
        String requestedTaskType = normalize(taskType);

        List<GeneratedTask> filtered = buildTasks(context).stream()
                .filter(task -> "all".equals(requestedStatus) || requestedStatus.equals(task.statusKey))
                .filter(task -> !StringUtils.hasText(requestedTaskType)
                        || containsIgnoreCase(task.taskType, requestedTaskType)
                        || containsIgnoreCase(task.missionName, requestedTaskType))
                .sorted(Comparator.comparing((GeneratedTask task) -> task.occurAt).reversed())
                .collect(Collectors.toList());

        return buildPage(
                filtered,
                currentPage,
                pageSize,
                this::toTaskView
        );
    }

    private ResolvedStoreContext resolveContext(String accessToken, String requestedStoreCode) {
        Long userId = sessionStore.findUserIdByAccessToken(normalize(accessToken));
        if (userId == null) {
            throw new MobileApiException(401, "登录已过期，请重新登录。");
        }

        MobileUserRecord user = mobileMapper.selectUserById(userId);
        if (user == null) {
            throw new MobileApiException(401, "当前账号不存在，请重新登录。");
        }

        List<MobileStoreRecord> stores = new ArrayList<>(mobileMapper.listStoresByUserId(userId));
        if (stores.isEmpty()) {
            return buildNoStoreContext(user);
        }
        stores.sort(
                Comparator.comparing((MobileStoreRecord record) -> !Boolean.TRUE.equals(record.getAuthorized()))
                        .thenComparing(record -> defaultString(record.getProjectName()))
                        .thenComparing(record -> defaultString(record.getStoreCode()))
        );

        MobileStoreRecord selectedStore = resolveSelectedStore(stores, requestedStoreCode);
        Long ownerUserId = mobileMapper.selectOwnerUserIdByStoreCode(selectedStore.getStoreCode());
        if (ownerUserId == null) {
            ownerUserId = user.getUserId();
        }

        List<StoreSyncStoreRecord> ownerProjectStores = loadOwnerProjectStores(ownerUserId, selectedStore);
        StoreInitializationStatusView initializationStatus = loadInitializationStatus(ownerUserId, selectedStore.getStoreCode());
        return new ResolvedStoreContext(user, stores, selectedStore, ownerUserId, ownerProjectStores, initializationStatus);
    }

    private ResolvedStoreContext buildNoStoreContext(MobileUserRecord user) {
        MobileStoreRecord placeholderStore = new MobileStoreRecord();
        placeholderStore.setStoreCode("");
        placeholderStore.setProjectName("未分配店铺");
        placeholderStore.setAuthorized(false);
        return new ResolvedStoreContext(user, List.of(), placeholderStore, user.getUserId(), List.of(), null);
    }

    private MobileStoreRecord resolveSelectedStore(List<MobileStoreRecord> stores, String requestedStoreCode) {
        String normalizedStoreCode = normalize(requestedStoreCode);
        if (StringUtils.hasText(normalizedStoreCode)) {
            for (MobileStoreRecord store : stores) {
                if (normalizedStoreCode.equals(store.getStoreCode())) {
                    return store;
                }
            }
            throw new MobileApiException(400, "当前账号没有这个店铺权限。");
        }
        return stores.stream()
                .filter(store -> Boolean.TRUE.equals(store.getAuthorized()))
                .findFirst()
                .orElse(stores.get(0));
    }

    private List<StoreSyncStoreRecord> loadOwnerProjectStores(Long ownerUserId, MobileStoreRecord selectedStore) {
        if (ownerUserId == null) {
            return List.of();
        }
        String projectKey = projectKey(selectedStore.getProjectCode(), selectedStore.getProjectName(), selectedStore.getStoreCode());
        return storeSyncMapper.listOwnerStores(ownerUserId).stream()
                .filter(store -> projectKey.equals(projectKey(store.getProjectCode(), store.getProjectName(), store.getStoreCode())))
                .collect(Collectors.toList());
    }

    private StoreInitializationStatusView loadInitializationStatus(Long ownerUserId, String storeCode) {
        LocalDbStoreInitializationService initializationService = storeInitializationServiceProvider.getIfAvailable();
        if (initializationService == null || ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return null;
        }
        try {
            return initializationService.getStatus(ownerUserId, storeCode);
        } catch (Exception ignore) {
            return null;
        }
    }

    private List<GeneratedTask> buildTasks(ResolvedStoreContext context) {
        if (context.stores.isEmpty()) {
            return List.of();
        }

        List<GeneratedTask> tasks = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        boolean authorized = Boolean.TRUE.equals(context.selectedStore.getAuthorized());
        StoreInitializationStatusView initStatus = context.initializationStatus;
        int localSiteCount = Math.max(context.ownerProjectStores.size(), 1);
        int authorizedSiteCount = (int) context.ownerProjectStores.stream()
                .filter(store -> Boolean.TRUE.equals(store.getOwnerAuthorized()))
                .count();
        int expectedSiteCount = initStatus != null && initStatus.getSiteCount() != null && initStatus.getSiteCount() > 0
                ? initStatus.getSiteCount()
                : localSiteCount;

        String bindingStatus = "waiting";
        String bindingError = "完成绑定后才会继续校验逻辑店铺的站点挂载。";
        if (!authorized) {
            bindingStatus = "failed";
            bindingError = "当前站点还没有完成 Noon 绑定，请先去店铺同步补齐授权。";
        } else if (expectedSiteCount > localSiteCount) {
            bindingStatus = "partial_success";
            bindingError = "Noon 实际识别到 " + expectedSiteCount + " 个站点，本地当前只挂载 " + localSiteCount + " 个。";
        } else if (authorizedSiteCount < localSiteCount) {
            bindingStatus = "partial_success";
            bindingError = "当前逻辑店铺仍有未授权站点，建议继续补齐账号绑定。";
        } else {
            bindingStatus = "success";
            bindingError = null;
        }
        tasks.add(createTask(
                context,
                "store-binding",
                "店铺绑定与站点校验",
                "店铺同步",
                bindingStatus,
                bindingError,
                now.minusMinutes(80)
        ));

        String initStatusKey = "waiting";
        String initError = "完成绑定后可以直接在新系统里启动店铺初始化。";
        ZonedDateTime initTime = now.minusMinutes(45);
        if (authorized) {
            if (initStatus == null || "IDLE".equals(initStatus.getStatus())) {
                initStatusKey = "waiting";
                initError = initStatus == null ? "当前还没有开始拉取这家店的数据。" : initStatus.getMessage();
            } else if ("RUNNING".equals(initStatus.getStatus())) {
                initStatusKey = "running";
                initError = initStatus.getMessage();
            } else if ("FAILED".equals(initStatus.getStatus()) || "BLOCKED".equals(initStatus.getStatus())) {
                initStatusKey = "failed";
                initError = initStatus.getMessage();
            } else if ("READY".equals(initStatus.getStatus()) && initStatus.getWarnings() != null && !initStatus.getWarnings().isEmpty()) {
                initStatusKey = "partial_success";
                initError = initStatus.getWarnings().get(0);
            } else if ("READY".equals(initStatus.getStatus())) {
                initStatusKey = "success";
                initError = null;
            }
            initTime = parseDateTime(firstNonBlank(
                    initStatus == null ? null : initStatus.getStartedAt(),
                    initStatus == null ? null : initStatus.getLastInitializedAt()
            ), initTime);
        }
        tasks.add(createTask(
                context,
                "initialization",
                "新店初始化",
                "商品初始化",
                initStatusKey,
                initError,
                initTime
        ));

        String productStatus = "waiting";
        String productError = "完成初始化后会生成可进入商品工作台的主档基线。";
        ZonedDateTime productTime = now.minusMinutes(25);
        if (authorized) {
            if (initStatus == null || "IDLE".equals(initStatus.getStatus())) {
                productStatus = "waiting";
                productError = "当前还没有商品主档基线，请先启动新店初始化。";
            } else if ("RUNNING".equals(initStatus.getStatus())) {
                productStatus = "running";
                productError = "正在读取 Noon 商品主档和站点 offer。";
            } else if ("FAILED".equals(initStatus.getStatus()) || "BLOCKED".equals(initStatus.getStatus())) {
                productStatus = "failed";
                productError = initStatus.getMessage();
            } else if (Boolean.TRUE.equals(initStatus.getCanEnterProductWorkbench())) {
                if (initStatus.getWarnings() != null && !initStatus.getWarnings().isEmpty()) {
                    productStatus = "partial_success";
                    productError = "商品工作台已可进入，但仍有 " + initStatus.getWarnings().size() + " 条告警待处理。";
                } else {
                    productStatus = "success";
                    productError = null;
                }
            }
            productTime = parseDateTime(firstNonBlank(
                    initStatus == null ? null : initStatus.getLastInitializedAt(),
                    initStatus == null ? null : initStatus.getStartedAt()
            ), productTime);
        }
        tasks.add(createTask(
                context,
                "product-baseline",
                "商品主档基线",
                "商品主档",
                productStatus,
                productError,
                productTime
        ));
        return tasks;
    }

    private List<GeneratedMessage> buildMessages(ResolvedStoreContext context, List<GeneratedTask> tasks) {
        List<GeneratedMessage> messages = new ArrayList<>();
        String storeName = resolveStoreName(context.selectedStore);
        String storeCode = context.selectedStore.getStoreCode();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        if (context.stores.isEmpty()) {
            messages.add(createMessage(
                    context,
                    "system",
                    "当前账号待分配店铺",
                    firstNonBlank(context.user.getRealName(), context.user.getAccountNo(), "当前账号")
                            + " 已开通小程序权限，等管理员挂载店铺后，就能看到统一的看板、消息和任务内容。",
                    now.minusMinutes(5),
                    "message_detail",
                    "no-store-assigned"
            ));
            return messages;
        }

        GeneratedTask bindingTask = findTask(tasks, "store-binding");
        if (bindingTask != null && "failed".equals(bindingTask.statusKey)) {
            messages.add(createMessage(
                    context,
                    "task",
                    "店铺绑定待处理",
                    storeName + "（" + storeCode + "）当前还没有完成 Noon 绑定，先去店铺同步完成授权。",
                    bindingTask.occurAt,
                    "message_detail",
                    String.valueOf(bindingTask.id)
            ));
        } else if (bindingTask != null && "partial_success".equals(bindingTask.statusKey)) {
            messages.add(createMessage(
                    context,
                    "system",
                    "站点挂载待补齐",
                    storeName + "（" + storeCode + "）" + defaultString(bindingTask.errorMsg),
                    bindingTask.occurAt,
                    "message_detail",
                    String.valueOf(bindingTask.id)
            ));
        }

        GeneratedTask initTask = findTask(tasks, "initialization");
        if (initTask != null) {
            if ("running".equals(initTask.statusKey)) {
                messages.add(createMessage(
                        context,
                        "task",
                        "新店初始化进行中",
                        storeName + "（" + storeCode + "）正在拉店铺结构和商品基线。",
                        initTask.occurAt,
                        "task_detail",
                        String.valueOf(initTask.id)
                ));
            } else if ("failed".equals(initTask.statusKey)) {
                messages.add(createMessage(
                        context,
                        "task",
                        "新店初始化失败",
                        storeName + "（" + storeCode + "）" + defaultString(initTask.errorMsg),
                        initTask.occurAt,
                        "task_detail",
                        String.valueOf(initTask.id)
                ));
            } else if ("waiting".equals(initTask.statusKey)) {
                messages.add(createMessage(
                        context,
                        "system",
                        "新店初始化待启动",
                        storeName + "（" + storeCode + "）已具备迁移入口，下一步可以直接启动初始化。",
                        initTask.occurAt,
                        "message_detail",
                        String.valueOf(initTask.id)
                ));
            } else {
                String summary = storeName + "（" + storeCode + "）";
                if (context.initializationStatus != null && context.initializationStatus.getUniqueProductCount() != null) {
                    summary += " 已同步 " + context.initializationStatus.getUniqueProductCount() + " 个共享商品";
                    if (context.initializationStatus.getSiteOfferCount() != null) {
                        summary += " / " + context.initializationStatus.getSiteOfferCount() + " 个 site offer";
                    }
                } else {
                    summary += " 已完成商品基线同步";
                }
                messages.add(createMessage(
                        context,
                        "system",
                        "商品基线已同步",
                        summary,
                        initTask.occurAt,
                        "message_detail",
                        String.valueOf(initTask.id)
                ));
            }
        }

        GeneratedTask productTask = findTask(tasks, "product-baseline");
        if (productTask != null && "partial_success".equals(productTask.statusKey)) {
            messages.add(createMessage(
                    context,
                    "system",
                    "商品主档发现告警",
                    storeName + "（" + storeCode + "）" + defaultString(productTask.errorMsg),
                    productTask.occurAt,
                    "message_detail",
                    String.valueOf(productTask.id)
            ));
        }

        if (messages.isEmpty()) {
            messages.add(createMessage(
                    context,
                    "system",
                    "移动看板已接入新系统",
                    storeName + "（" + storeCode + "）当前展示的是 nuono-next 本地样本链路，可继续从店铺同步进入商品工作台。",
                    now.minusMinutes(10),
                    "message_detail",
                    "local-bootstrap"
            ));
        }
        return messages;
    }

    private GeneratedTask findTask(List<GeneratedTask> tasks, String key) {
        for (GeneratedTask task : tasks) {
            if (key.equals(task.key)) {
                return task;
            }
        }
        return null;
    }

    private GeneratedTask createTask(
            ResolvedStoreContext context,
            String key,
            String missionName,
            String taskType,
            String statusKey,
            String errorMsg,
            ZonedDateTime occurAt
    ) {
        GeneratedTask task = new GeneratedTask();
        task.key = key;
        task.id = stableId(context.selectedStore.getStoreCode(), key);
        task.missionName = missionName;
        task.taskType = taskType;
        task.statusKey = statusKey;
        task.statusLabel = statusLabel(statusKey);
        task.errorMsg = errorMsg;
        task.occurAt = occurAt;
        return task;
    }

    private GeneratedMessage createMessage(
            ResolvedStoreContext context,
            String type,
            String title,
            String summary,
            ZonedDateTime occurAt,
            String linkType,
            String linkParam
    ) {
        GeneratedMessage message = new GeneratedMessage();
        message.id = stableId(context.selectedStore.getStoreCode(), type + ":" + title + ":" + linkParam);
        message.type = type;
        message.title = title;
        message.summary = summary;
        message.storeCode = context.selectedStore.getStoreCode();
        message.occurAt = occurAt;
        message.linkType = linkType;
        message.linkParam = linkParam;
        return message;
    }

    private MobileDashboardAlertItemView toDashboardAlert(GeneratedMessage message) {
        MobileDashboardAlertItemView view = new MobileDashboardAlertItemView();
        view.setType(message.type);
        view.setTitle(message.title);
        view.setSummary(message.summary);
        view.setOccurTime(formatDateTime(message.occurAt));
        view.setLinkType(message.linkType);
        view.setLinkParam(message.linkParam);
        return view;
    }

    private MobileMessageView toMessageView(GeneratedMessage message) {
        MobileMessageView view = new MobileMessageView();
        view.setId(message.id);
        view.setType(message.type);
        view.setTitle(message.title);
        view.setSummary(message.summary);
        view.setStoreCode(message.storeCode);
        view.setOccurTime(formatDateTime(message.occurAt));
        view.setLinkType(message.linkType);
        view.setLinkParam(message.linkParam);
        return view;
    }

    private MobileTaskView toTaskView(GeneratedTask task) {
        MobileTaskView view = new MobileTaskView();
        view.setMissionId(task.id);
        view.setMissionName(task.missionName);
        view.setTaskType(task.taskType);
        view.setStatus(task.statusLabel);
        view.setStartTime(formatDateTime(task.occurAt));
        view.setErrorMsg(task.errorMsg);
        return view;
    }

    private <S, T> MobilePageResponse<T> buildPage(
            List<S> source,
            Integer currentPage,
            Integer pageSize,
            java.util.function.Function<S, T> converter
    ) {
        int safePage = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        int toIndex = Math.min(fromIndex + safeSize, source.size());
        List<T> content = new ArrayList<>();
        if (fromIndex < source.size()) {
            for (S item : source.subList(fromIndex, toIndex)) {
                content.add(converter.apply(item));
            }
        }
        return new MobilePageResponse<>(content, safePage, safeSize, (long) source.size());
    }

    private BigDecimal resolveYesterdaySales(ResolvedStoreContext context) {
        if (context.stores.isEmpty() || !Boolean.TRUE.equals(context.selectedStore.getAuthorized())) {
            return BigDecimal.ZERO;
        }
        int seed = Math.abs(Objects.hash(context.selectedStore.getStoreCode(), context.selectedStore.getProjectCode()));
        int uniqueProductCount = context.initializationStatus != null && context.initializationStatus.getUniqueProductCount() != null
                ? context.initializationStatus.getUniqueProductCount()
                : 18 + seed % 24;
        int siteOfferCount = context.initializationStatus != null && context.initializationStatus.getSiteOfferCount() != null
                ? context.initializationStatus.getSiteOfferCount()
                : uniqueProductCount * Math.max(context.ownerProjectStores.size(), 1);
        long cents = uniqueProductCount * 23_500L + siteOfferCount * 5_800L + seed % 9_000;
        return BigDecimal.valueOf(cents, 2);
    }

    private Integer resolveYesterdayOrders(ResolvedStoreContext context) {
        if (context.stores.isEmpty() || !Boolean.TRUE.equals(context.selectedStore.getAuthorized())) {
            return 0;
        }
        int seed = Math.abs(Objects.hash(context.selectedStore.getStoreCode(), context.selectedStore.getSite()));
        int uniqueProductCount = context.initializationStatus != null && context.initializationStatus.getUniqueProductCount() != null
                ? context.initializationStatus.getUniqueProductCount()
                : 16 + seed % 20;
        int siteCount = context.initializationStatus != null && context.initializationStatus.getSiteCount() != null
                ? Math.max(context.initializationStatus.getSiteCount(), 1)
                : Math.max(context.ownerProjectStores.size(), 1);
        return Math.max(1, uniqueProductCount * 2 + siteCount * 3 + seed % 7);
    }

    private String resolveLastUpdatedAt(ResolvedStoreContext context) {
        return firstNonBlank(
                context.initializationStatus == null ? null : context.initializationStatus.getLastInitializedAt(),
                context.initializationStatus == null ? null : context.initializationStatus.getStartedAt(),
                formatDateTime(ZonedDateTime.now(ZoneId.systemDefault()).minusMinutes(12))
        );
    }

    private long stableId(String storeCode, String key) {
        return Math.abs(Objects.hash(storeCode, key)) + 10_000L;
    }

    private ZonedDateTime parseDateTime(String value, ZonedDateTime fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return ZonedDateTime.of(
                    java.time.LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    ZoneId.systemDefault()
            );
        } catch (Exception ignore) {
            return fallback;
        }
    }

    private String resolveStoreName(MobileStoreRecord store) {
        return firstNonBlank(store.getProjectName(), store.getStoreCode(), "未分配店铺");
    }

    private String statusLabel(String statusKey) {
        switch (statusKey) {
            case "running":
                return "运行中";
            case "success":
                return "成功";
            case "failed":
                return "失败";
            case "partial_success":
                return "部分成功";
            default:
                return "等待中";
        }
    }

    private String normalizeType(String value, String defaultValue) {
        String normalized = normalize(value);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : defaultValue;
    }

    private String projectKey(String projectCode, String projectName, String storeCode) {
        if (StringUtils.hasText(projectCode)) {
            return projectCode.trim();
        }
        if (StringUtils.hasText(projectName)) {
            return projectName.trim();
        }
        return defaultString(storeCode);
    }

    private boolean containsIgnoreCase(String source, String target) {
        return defaultString(source).toLowerCase(Locale.ROOT).contains(defaultString(target).toLowerCase(Locale.ROOT));
    }

    private String formatDateTime(ZonedDateTime value) {
        return DATETIME_FORMATTER.format(value);
    }

    private String normalize(String value) {
        return StringUtils.trimWhitespace(value);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
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

    private static class ResolvedStoreContext {
        private final MobileUserRecord user;
        private final List<MobileStoreRecord> stores;
        private final MobileStoreRecord selectedStore;
        private final Long ownerUserId;
        private final List<StoreSyncStoreRecord> ownerProjectStores;
        private final StoreInitializationStatusView initializationStatus;

        private ResolvedStoreContext(
                MobileUserRecord user,
                List<MobileStoreRecord> stores,
                MobileStoreRecord selectedStore,
                Long ownerUserId,
                List<StoreSyncStoreRecord> ownerProjectStores,
                StoreInitializationStatusView initializationStatus
        ) {
            this.user = user;
            this.stores = stores;
            this.selectedStore = selectedStore;
            this.ownerUserId = ownerUserId;
            this.ownerProjectStores = ownerProjectStores;
            this.initializationStatus = initializationStatus;
        }
    }

    private static class GeneratedTask {
        private long id;
        private String key;
        private String missionName;
        private String taskType;
        private String statusKey;
        private String statusLabel;
        private String errorMsg;
        private ZonedDateTime occurAt;
    }

    private static class GeneratedMessage {
        private long id;
        private String type;
        private String title;
        private String summary;
        private String storeCode;
        private ZonedDateTime occurAt;
        private String linkType;
        private String linkParam;
    }
}
