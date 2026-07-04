package com.nuono.next.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.MerchantAuthorization;
import com.nuono.next.noon.NoonSessionGateway.MerchantProject;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbStoreSyncService {

    private static final List<String> SYNCED_RULES = List.of(
            "店铺管理按最新老系统 user_project 项目级店铺读取，user_store 只作为站点明细。",
            "老板可绑定 Noon 商家后台登录邮箱和邮箱授权码、测试连通和绑定新店铺，非老板仅查看列表。",
            "负责人映射按 user_project_access 项目授权聚合，不再按站点行重复展示。",
            "店铺绑定状态写入 user_project，避免把同一项目的多个站点拆成多家店。"
    );

    private final StoreSyncMapper storeSyncMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;
    private final NoonSessionGateway noonSessionGateway;

    public LocalDbStoreSyncService(
            StoreSyncMapper storeSyncMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService,
            NoonSessionGateway noonSessionGateway
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
        this.noonSessionGateway = noonSessionGateway;
    }

    public StoreSyncOverview buildOverview(Long ownerUserId) {
        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();

        StoreSyncOverview overview = new StoreSyncOverview();
        overview.setMode("local-db");
        overview.setReady(inspection.isReady());
        overview.setMissingCoreTables(inspection.getMissingTables());
        overview.setSyncedRules(SYNCED_RULES);

        if (!inspection.isReady()) {
            overview.setMessage("本地库已启用，但最新店铺项目模型还没有补齐，店铺同步链路还不能读取。");
            return overview;
        }

        List<StoreSyncOwnerOption> ownerOptions = storeSyncMapper.listOwnerOptions();
        overview.setOwnerOptions(ownerOptions);

        Long resolvedOwnerId = resolveOwnerId(ownerOptions, ownerUserId);
        overview.setSelectedOwnerId(resolvedOwnerId);

        if (resolvedOwnerId == null) {
            overview.setMessage("当前还没有可用于管理店铺与 Noon 绑定的负责人账号。");
            overview.setSummary(new StoreSyncSummary());
            return overview;
        }

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(resolvedOwnerId);
        if (owner == null) {
            overview.setMessage("选中的老板账号不存在，先重新选择店铺上下文。");
            overview.setSummary(new StoreSyncSummary());
            return overview;
        }

        List<StoreSyncStoreRecord> ownerProjects = storeSyncMapper.listOwnerProjects(resolvedOwnerId);
        Map<String, List<StoreSyncStoreRecord>> sitesByProject = loadSiteMap(ownerProjects, resolvedOwnerId);
        Map<String, List<StoreSyncManagerInfo>> managerMap = loadManagerMap(ownerProjects, resolvedOwnerId);

        List<StoreSyncStoreView> stores = new ArrayList<>();
        int connectedStores = 0;
        int totalSiteStores = 0;
        int connectedSiteStores = 0;
        int managerLinks = 0;
        for (StoreSyncStoreRecord ownerProject : ownerProjects) {
            boolean connectionReady = isConnectionReady(ownerProject);
            List<StoreSyncStoreRecord> siteRows = sitesByProject.getOrDefault(ownerProject.getProjectCode(), List.of());

            StoreSyncStoreView view = new StoreSyncStoreView();
            view.setId(ownerProject.getId());
            view.setProjectName(ownerProject.getProjectName());
            view.setProjectCode(ownerProject.getProjectCode());
            view.setStoreCode(ownerProject.getProjectCode());
            view.setSiteCount(siteRows.size());
            view.setConnectedSiteCount(connectionReady ? siteRows.size() : 0);
            view.setIsAuthorized(Boolean.TRUE.equals(ownerProject.getOwnerAuthorized()));
            view.setConnectionStatus(connectionReady ? "正常" : "未绑定");
            view.setNoonUser(resolveNoonUser(ownerProject));
            view.setNoonPartnerId(resolveNoonPartnerId(ownerProject));

            for (StoreSyncStoreRecord siteRow : siteRows) {
                StoreSyncSiteView siteView = new StoreSyncSiteView();
                siteView.setId(siteRow.getId());
                siteView.setStoreCode(siteRow.getStoreCode());
                siteView.setSite(siteRow.getSite());
                siteView.setIsAuthorized(Boolean.TRUE.equals(ownerProject.getOwnerAuthorized()));
                siteView.setConnectionStatus(connectionReady ? "正常" : "未绑定");
                view.getSiteStores().add(siteView);
            }

            view.setManagers(new ArrayList<>(managerMap.getOrDefault(ownerProject.getProjectCode(), List.of())));
            if (connectionReady) {
                connectedStores++;
                connectedSiteStores += siteRows.size();
            }
            totalSiteStores += siteRows.size();
            managerLinks += view.getManagers().size();
            stores.add(view);
        }

        overview.setStores(stores);
        overview.setSummary(buildSummary(stores.size(), connectedStores, totalSiteStores, connectedSiteStores, managerLinks));
        overview.setMessage(
                "绑定 Noon 商家后台账号后，店铺信息将自动获取。当前已按发布版项目级口径加载 "
                        + resolveOwnerName(owner)
                        + " 的店铺管理视图。"
        );
        return overview;
    }

    @Transactional
    public StoreBindingResult bindStore(StoreBindCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法写入店铺绑定。");
        }
        if (!StringUtils.hasText(command.getStoreCode())) {
            throw new IllegalArgumentException("缺少店铺编码，无法写入店铺绑定。");
        }

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法写入店铺绑定。");
        }

        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(command.getOwnerUserId(), command.getStoreCode());
        if (project == null) {
            throw new IllegalArgumentException("当前店铺不在选中的老板名下。");
        }

        MerchantAuthorization authorization = noonSessionGateway.authorizeConfiguredMerchantEmailLogin(
                command.getOwnerUserId(),
                project.getProjectCode(),
                project.getStoreCode()
        );
        MerchantProject authorizedProject = requireAuthorizedProject(authorization);
        String noonPartnerId = firstNonBlank(derivePartnerId(authorizedProject.getProjectCode()), project.getNoonPartnerId());
        String noonUser = noonSessionGateway.configuredMerchantEmail();
        String mailAuthCode = noonSessionGateway.configuredMerchantMailAuthCode();

        storeSyncMapper.updateProjectEmailBinding(
                project.getId(),
                command.getOwnerUserId(),
                noonUser,
                mailAuthCode,
                noonPartnerId,
                command.getOwnerUserId()
        );
        if (StringUtils.hasText(authorization.getCookie())) {
            storeSyncMapper.updateProjectConnectionSuccess(
                    project.getId(),
                    command.getOwnerUserId(),
                    authorization.getCookie(),
                    command.getOwnerUserId()
            );
        }

        int siteCount = loadSiteMap(List.of(project), command.getOwnerUserId())
                .getOrDefault(project.getProjectCode(), List.of())
                .size();
        return StoreBindingResult.succeeded(
                "店铺 "
                        + resolveProjectLabel(project)
                        + " 已完成 Noon 商家后台绑定，已同步覆盖 "
                        + siteCount
                        + " 个站点。"
        );
    }

    @Transactional
    public StoreBindingResult createStore(StoreCreateCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，无法新增店铺。");
        }
        if (!StringUtils.hasText(command.getProjectName())) {
            throw new IllegalArgumentException("请输入店铺名称。");
        }
        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(command.getOwnerUserId());
        if (owner == null) {
            throw new IllegalArgumentException("老板账号不存在，无法新增店铺。");
        }

        String projectName = command.getProjectName().trim();
        String siteStoreCode = normalize(command.getStoreCode());
        String site = normalize(command.getSite());
        requireText(siteStoreCode, "请输入站点店铺 Code。");
        requireText(site, "请选择站点。");
        boolean authorized = true;

        MerchantAuthorization authorization = noonSessionGateway.authorizeConfiguredMerchantEmailLogin(
                command.getOwnerUserId(),
                normalize(command.getProjectCode()),
                siteStoreCode
        );
        if (authorization.isProjectSelectionRequired()) {
            return StoreBindingResult.projectSelectionRequired(
                    authorization.getProjectList(),
                    "该 Noon 商家后台账号可访问多个 Project，请选择要绑定的店铺。"
            );
        }
        MerchantProject authorizedProject = requireAuthorizedProject(authorization);
        String projectCode = authorizedProject.getProjectCode();
        String noonPartnerId = derivePartnerId(projectCode);
        String noonUser = noonSessionGateway.configuredMerchantEmail();
        String mailAuthCode = noonSessionGateway.configuredMerchantMailAuthCode();
        String orgCode = firstNonBlank(command.getOrgCode(), authorizedProject.getOrgCode());
        String orgName = firstNonBlank(command.getOrgName(), authorizedProject.getOrgName(), owner.getCompanyName());
        if (storeSyncMapper.selectOwnerProject(command.getOwnerUserId(), projectCode) != null
                || (StringUtils.hasText(siteStoreCode)
                && storeSyncMapper.selectOwnerProject(command.getOwnerUserId(), siteStoreCode) != null)) {
            throw new IllegalArgumentException("该老板名下已存在相同店铺。");
        }

        storeSyncMapper.insertOwnerProject(
                command.getOwnerUserId(),
                orgCode,
                orgName,
                projectCode,
                projectName,
                noonUser,
                mailAuthCode,
                noonPartnerId,
                authorized,
                authorized
        );
        if (StringUtils.hasText(authorization.getCookie())) {
            storeSyncMapper.updateProjectSessionCookie(
                    command.getOwnerUserId(),
                    projectCode,
                    authorization.getCookie(),
                    command.getOwnerUserId()
            );
        }
        if (StringUtils.hasText(siteStoreCode)) {
            storeSyncMapper.insertOwnerSiteStore(
                    storeSyncMapper.nextStoreId(),
                    command.getOwnerUserId(),
                    orgCode,
                    orgName,
                    projectCode,
                    projectName,
                    siteStoreCode,
                    site,
                    authorized
            );
        }

        return StoreBindingResult.succeeded("店铺 " + projectName + " 已绑定到当前账号视图。");
    }

    @Transactional
    public StoreConnectionTestResult testConnection(Long ownerUserId, String storeCode) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return StoreConnectionTestResult.failed("缺少老板或店铺上下文，无法测试 Noon 连通。");
        }

        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(ownerUserId, storeCode);
        if (project == null) {
            throw new IllegalArgumentException("店铺不存在或无权访问: " + storeCode);
        }

        String cookie = normalize(project.getNoonPartnerCookie());
        StoreSyncOwnerContext owner = null;
        if (!StringUtils.hasText(cookie)) {
            owner = storeSyncMapper.selectOwnerContext(ownerUserId);
            cookie = owner == null ? null : normalize(owner.getNoonPartnerCookie());
        }

        NoonSessionGateway.RequestCountScope requestCountScope = noonSessionGateway.openRequestCountScope();
        try {
            JsonNode whoami = null;
            String effectiveCookie = cookie;
            if (StringUtils.hasText(cookie)) {
                try {
                    whoami = noonSessionGateway.whoamiWithCookie(
                            cookie,
                            project.getProjectCode(),
                            project.getStoreCode()
                    );
                } catch (Exception ignored) {
                    whoami = null;
                }
            }

            boolean connected = isWhoamiConnected(whoami);
            if (!connected) {
                if (owner == null) {
                    owner = storeSyncMapper.selectOwnerContext(ownerUserId);
                }
                MerchantAuthorization authorization = refreshProjectAuthorization(ownerUserId, project, owner);
                effectiveCookie = authorization.getCookie();
                connected = StringUtils.hasText(effectiveCookie);
            }
            if (connected) {
                storeSyncMapper.updateProjectConnectionSuccess(project.getId(), ownerUserId, effectiveCookie, ownerUserId);
            } else {
                storeSyncMapper.updateProjectConnectionFailure(project.getId(), ownerUserId, ownerUserId);
            }
            StoreConnectionTestResult result = connected
                    ? StoreConnectionTestResult.succeeded("连接正常")
                    : StoreConnectionTestResult.failed("连接失败：Noon Cookie 验证未通过，测试服务器自动登录也未拿到有效会话，请重新绑定账号。");
            result.setNoonRequestCounts(new LinkedHashMap<>(requestCountScope.snapshot()));
            return result;
        } catch (Exception exception) {
            storeSyncMapper.updateProjectConnectionFailure(project.getId(), ownerUserId, ownerUserId);
            StoreConnectionTestResult result = StoreConnectionTestResult.failed(connectionFailureMessage(exception));
            result.setNoonRequestCounts(new LinkedHashMap<>(requestCountScope.snapshot()));
            return result;
        } finally {
            requestCountScope.close();
        }
    }

    private String connectionFailureMessage(Exception exception) {
        String message = exception != null ? exception.getMessage() : null;
        if (StringUtils.hasText(message) && message.contains("缺少 Noon")) {
            return "连接失败：" + message;
        }
        if (StringUtils.hasText(message) && message.contains("Rate limit")) {
            return "连接失败：Noon 当前限制测试服务器登录频率，请稍后重试或重新绑定账号。";
        }
        return "连接失败：测试服务器未能通过 Noon Cookie 验证或自动登录，请重新绑定账号。";
    }

    private boolean isWhoamiConnected(JsonNode whoami) {
        return whoami != null
                && whoami.hasNonNull("email")
                && StringUtils.hasText(whoami.get("email").asText());
    }

    private Long resolveOwnerId(List<StoreSyncOwnerOption> ownerOptions, Long ownerUserId) {
        if (ownerUserId != null) {
            for (StoreSyncOwnerOption ownerOption : ownerOptions) {
                if (ownerUserId.equals(ownerOption.getId())) {
                    return ownerUserId;
                }
            }
        }

        return ownerOptions.isEmpty() ? null : ownerOptions.get(0).getId();
    }

    private Map<String, List<StoreSyncStoreRecord>> loadSiteMap(
            List<StoreSyncStoreRecord> ownerProjects,
            Long ownerUserId
    ) {
        Map<String, List<StoreSyncStoreRecord>> siteMap = new LinkedHashMap<>();
        List<String> projectCodes = ownerProjects.stream()
                .map(StoreSyncStoreRecord::getProjectCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (projectCodes.isEmpty()) {
            return siteMap;
        }

        for (StoreSyncStoreRecord siteRow : storeSyncMapper.listOwnerProjectSites(ownerUserId, projectCodes)) {
            if (!StringUtils.hasText(siteRow.getProjectCode())) {
                continue;
            }
            siteMap.computeIfAbsent(siteRow.getProjectCode(), key -> new ArrayList<>()).add(siteRow);
        }
        List<String> projectionOnlyProjectCodes = projectCodes.stream()
                .filter(projectCode -> !siteMap.containsKey(projectCode))
                .collect(Collectors.toList());
        if (!projectionOnlyProjectCodes.isEmpty()) {
            for (StoreSyncStoreRecord siteRow : storeSyncMapper.listOwnerProjectionProjectSites(
                    ownerUserId,
                    projectionOnlyProjectCodes
            )) {
                if (!StringUtils.hasText(siteRow.getProjectCode())) {
                    continue;
                }
                siteMap.computeIfAbsent(siteRow.getProjectCode(), key -> new ArrayList<>()).add(siteRow);
            }
        }
        return siteMap;
    }

    private Map<String, List<StoreSyncManagerInfo>> loadManagerMap(
            List<StoreSyncStoreRecord> ownerProjects,
            Long ownerUserId
    ) {
        Map<String, List<StoreSyncManagerInfo>> managerMap = new LinkedHashMap<>();
        if (ownerProjects.isEmpty()) {
            return managerMap;
        }

        List<String> projectCodes = ownerProjects.stream()
                .map(StoreSyncStoreRecord::getProjectCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (projectCodes.isEmpty()) {
            return managerMap;
        }

        List<StoreSyncManagerRow> managerRows = storeSyncMapper.listManagersByProjectCodes(projectCodes, ownerUserId);
        Map<String, Map<String, StoreSyncManagerInfo>> dedupMap = new LinkedHashMap<>();
        for (StoreSyncManagerRow managerRow : managerRows) {
            if (!StringUtils.hasText(managerRow.getStoreCode()) || managerRow.getId() == null) {
                continue;
            }
            StoreSyncManagerInfo managerInfo = new StoreSyncManagerInfo();
            managerInfo.setId(managerRow.getId());
            managerInfo.setName(managerRow.getName());
            managerInfo.setRole(managerRow.getRole());
            dedupMap.computeIfAbsent(managerRow.getStoreCode(), key -> new LinkedHashMap<>())
                    .putIfAbsent(managerDisplayKey(managerInfo), managerInfo);
        }

        for (Map.Entry<String, Map<String, StoreSyncManagerInfo>> entry : dedupMap.entrySet()) {
            managerMap.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }

        return managerMap;
    }

    private String managerDisplayKey(StoreSyncManagerInfo managerInfo) {
        return normalize(managerInfo.getName()) + "|" + normalize(managerInfo.getRole());
    }

    private String resolveNoonUser(StoreSyncStoreRecord project) {
        if (StringUtils.hasText(project.getNoonPartnerUser())) {
            return project.getNoonPartnerUser();
        }
        return project.getNoonPartnerProjectUser();
    }

    private String resolveNoonPartnerId(StoreSyncStoreRecord project) {
        return firstNonBlank(project.getNoonPartnerId(), derivePartnerId(project.getProjectCode()), project.getProjectCode());
    }

    private StoreSyncSummary buildSummary(
            int totalStores,
            int connectedStores,
            int totalSiteStores,
            int connectedSiteStores,
            int managerLinks
    ) {
        StoreSyncSummary summary = new StoreSyncSummary();
        summary.setTotalStores(totalStores);
        summary.setConnectedStores(connectedStores);
        summary.setPendingStores(Math.max(totalStores - connectedStores, 0));
        summary.setTotalSiteStores(totalSiteStores);
        summary.setConnectedSiteStores(connectedSiteStores);
        summary.setManagerLinks(managerLinks);
        return summary;
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isConnectionReady(StoreSyncStoreRecord project) {
        boolean hasCredential = StringUtils.hasText(project.getNoonPartnerUser())
                || StringUtils.hasText(project.getNoonPartnerProjectUser());
        boolean bound = project.getBindStatus() != null && project.getBindStatus() == 1;
        return Boolean.TRUE.equals(project.getOwnerAuthorized()) && hasCredential && bound;
    }

    private String resolveProjectLabel(StoreSyncStoreRecord store) {
        if (StringUtils.hasText(store.getProjectName())) {
            return store.getProjectName();
        }
        if (StringUtils.hasText(store.getProjectCode())) {
            return store.getProjectCode();
        }
        return store.getStoreCode();
    }

    private MerchantProject requireAuthorizedProject(MerchantAuthorization authorization) {
        if (authorization == null || !authorization.isSuccess() || authorization.getSelectedProject() == null) {
            throw new IllegalStateException("Noon 商家后台授权未返回可绑定 Project。");
        }
        return authorization.getSelectedProject();
    }

    private String derivePartnerId(String projectCode) {
        String normalized = normalize(projectCode);
        if (normalized != null && normalized.startsWith("PRJ") && normalized.length() > 3) {
            return normalized.substring(3);
        }
        return normalized;
    }

    private MerchantAuthorization refreshProjectAuthorization(
            Long ownerUserId,
            StoreSyncStoreRecord project,
            StoreSyncOwnerContext owner
    ) {
        String noonEmail = firstNonBlank(
                project.getNoonPartnerUser(),
                owner != null ? owner.getNoonPartnerUser() : null
        );
        String mailAuthCode = firstNonBlank(
                project.getNoonPartnerMailAuthCode(),
                owner != null ? owner.getNoonPartnerMailAuthCode() : null
        );
        if (StringUtils.hasText(noonEmail) && StringUtils.hasText(mailAuthCode)) {
            return noonSessionGateway.authorizeMerchantEmailLogin(
                    ownerUserId,
                    noonEmail,
                    mailAuthCode,
                    project.getProjectCode(),
                    project.getStoreCode()
            );
        }
        if (noonSessionGateway.hasConfiguredMerchantEmailLogin()) {
            return noonSessionGateway.authorizeConfiguredMerchantEmailLogin(
                    ownerUserId,
                    project.getProjectCode(),
                    project.getStoreCode()
            );
        }

        NoonSessionGateway.NoonSession session = noonSessionGateway.login(
                ownerUserId,
                firstNonBlank(
                        project.getNoonPartnerProjectUser(),
                        project.getNoonPartnerUser(),
                        owner != null ? owner.getNoonPartnerProjectUser() : null,
                        owner != null ? owner.getNoonPartnerUser() : null
                ),
                firstNonBlank(
                        project.getNoonPartnerPwd(),
                        owner != null ? owner.getNoonPartnerPwd() : null
                ),
                null,
                project.getProjectCode(),
                project.getStoreCode()
        );
        return MerchantAuthorization.authorized(
                new MerchantProject(project.getProjectCode(), project.getProjectName(), null, null),
                session.exportAuthCookieHeader()
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    public static class StoreConnectionTestResult {

        private boolean connected;

        private String message;

        private String checkMode = "NOON_WHOAMI";

        private Map<String, Integer> noonRequestCounts = new LinkedHashMap<>();

        public static StoreConnectionTestResult succeeded(String message) {
            StoreConnectionTestResult result = new StoreConnectionTestResult();
            result.setConnected(true);
            result.setMessage(message);
            return result;
        }

        public static StoreConnectionTestResult failed(String message) {
            StoreConnectionTestResult result = new StoreConnectionTestResult();
            result.setConnected(false);
            result.setMessage(message);
            return result;
        }

        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCheckMode() {
            return checkMode;
        }

        public void setCheckMode(String checkMode) {
            this.checkMode = checkMode;
        }

        public Map<String, Integer> getNoonRequestCounts() {
            return noonRequestCounts;
        }

        public void setNoonRequestCounts(Map<String, Integer> noonRequestCounts) {
            this.noonRequestCounts = noonRequestCounts == null ? new LinkedHashMap<>() : noonRequestCounts;
        }

        public Integer getNoonRequestTotalCount() {
            return noonRequestCounts.getOrDefault("__total__", 0);
        }
    }
}
