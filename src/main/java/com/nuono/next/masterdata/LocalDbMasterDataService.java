package com.nuono.next.masterdata;

import com.nuono.next.foundation.FoundationUserDetail;
import com.nuono.next.foundation.FoundationUserScopeSummary;
import com.nuono.next.foundation.FoundationUserStoreLink;
import com.nuono.next.foundation.LocalDbFoundationOverviewService;
import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbMasterDataService {

    private static final LocalDateTime DEFAULT_EXPIRED_TIME = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
    private static final Set<Long> SYSTEM_MENU_IDS = Set.of(
            2L,
            3L,
            5L,
            26L,
            9200L,
            6L,
            24L,
            7L,
            9201L,
            10L,
            25L,
            27L,
            28L,
            9202L,
            9301L,
            9400L,
            9401L
    );
    private static final Set<Long> SYSTEM_PERMISSION_MENU_IDS = Set.of(
            6L,
            24L,
            7L,
            9201L,
            10L,
            25L,
            27L,
            28L,
            9202L,
            9301L,
            9401L
    );

    private final MasterDataMapper masterDataMapper;
    private final LocalDbFoundationOverviewService foundationOverviewService;

    public LocalDbMasterDataService(
            MasterDataMapper masterDataMapper,
            LocalDbFoundationOverviewService foundationOverviewService
    ) {
        this.masterDataMapper = masterDataMapper;
        this.foundationOverviewService = foundationOverviewService;
    }

    public List<MasterDataUserView> listUsers(Long operatorUserId, Integer operatorRoleLevel, String view) {
        List<MasterDataUserView> users = new ArrayList<>(masterDataMapper.listUsers());
        applyScopeSummaries(users, foundationOverviewService.listUserScopeSummaries());
        if (!StringUtils.hasText(view)) {
            return users;
        }
        if ("merchant".equalsIgnoreCase(view)) {
            return filterMerchantUsers(users, operatorRoleLevel);
        }
        if ("team".equalsIgnoreCase(view) || "role".equalsIgnoreCase(view)) {
            List<MasterDataUserView> result = filterTeamUsers(users, operatorUserId, operatorRoleLevel);
            result.removeIf(this::isExternalAccount);
            if (operatorRoleLevel == null || operatorRoleLevel > 0) {
                applyOperatorScopedStoreSummaries(result, operatorUserId);
            }
            return result;
        }
        return users;
    }

    private void applyScopeSummaries(List<MasterDataUserView> users, Map<Long, FoundationUserScopeSummary> scopeSummaries) {
        if (scopeSummaries == null || scopeSummaries.isEmpty()) {
            for (MasterDataUserView user : users) {
                user.setDirectCompanyCount(0);
                user.setDirectCompanies("");
                user.setManagedStoreCount(user.getStoreCount());
                user.setManagedCompanyCount(0);
                user.setDescendantUserCount(0);
            }
            return;
        }
        for (MasterDataUserView user : users) {
            FoundationUserScopeSummary summary = user.getId() != null ? scopeSummaries.get(user.getId()) : null;
            user.setDirectCompanyCount(summary != null ? summary.getDirectCompanyCount() : 0);
            user.setDirectCompanies(summary != null ? summary.getDirectCompanies() : "");
            user.setManagedStoreCount(summary != null ? summary.getManagedStoreCount() : user.getStoreCount());
            user.setManagedCompanyCount(summary != null ? summary.getManagedCompanyCount() : 0);
            user.setDescendantUserCount(summary != null ? summary.getDescendantUserCount() : 0);
        }
    }

    public FoundationUserDetail getUserDetail(Long userId) {
        return foundationOverviewService.buildUserDetail(userId);
    }

    public FoundationUserDetail getUserDetail(Long userId, Long operatorUserId) {
        ensureOperatorCanViewUser(operatorUserId, userId);
        FoundationUserDetail detail = getUserDetail(userId);
        if (operatorUserId == null) {
            return detail;
        }
        MasterDataUserView operator = requireOperator(operatorUserId);
        if (isSystemAdmin(operator)) {
            return detail;
        }
        applyOperatorScopedStoreDetail(detail, operatorUserId);
        return detail;
    }

    public Integer getOperatorRoleLevel(Long operatorUserId) {
        return requireOperator(operatorUserId).getRoleLevel();
    }

    public List<MasterDataRoleView> listRoles() {
        List<MasterDataRoleView> roles = new ArrayList<>(masterDataMapper.listRoles());
        Set<Long> systemPermissionMenuIds = listSystemPermissionMenuIds();
        boolean shouldFilterMenus = !systemPermissionMenuIds.isEmpty();
        for (MasterDataRoleView role : roles) {
            role.setMenuIds(new ArrayList<>());
        }
        for (MasterDataRoleMenuRow row : masterDataMapper.listRoleMenuRows()) {
            if (shouldFilterMenus && !systemPermissionMenuIds.contains(row.getMenuId())) {
                continue;
            }
            roles.stream()
                    .filter(role -> role.getId() != null && role.getId().equals(row.getRoleId()))
                    .findFirst()
                    .ifPresent(role -> {
                        if (!role.getMenuIds().contains(row.getMenuId())) {
                            role.getMenuIds().add(row.getMenuId());
                        }
                    });
        }
        return roles;
    }

    public List<MasterDataMenuView> listMenus() {
        return filterSystemMenus(masterDataMapper.listMenus());
    }

    public List<MasterDataOrgNodeView> listOrgTree(Long operatorUserId, Integer operatorRoleLevel) {
        List<MasterDataOrgUserRow> rows = new ArrayList<>(masterDataMapper.listOrgUsers());
        rows.removeIf(row -> !Integer.valueOf(1).equals(row.getStatus()));
        rows.removeIf(this::isExternalAccount);
        if (operatorUserId != null && operatorRoleLevel != null && operatorRoleLevel > 0) {
            Set<Long> visibleIds = collectVisibleOrgUserIds(rows, operatorUserId);
            visibleIds.add(operatorUserId);
            rows.removeIf(row -> shouldHideOrgRow(row, visibleIds, operatorUserId, operatorRoleLevel));
            applyOperatorScopedOrgSummaries(rows, operatorUserId);
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, MasterDataOrgUserRow> rowMap = new HashMap<>();
        Map<Long, MasterDataOrgNodeView> nodeMap = new HashMap<>();
        for (MasterDataOrgUserRow row : rows) {
            if (row.getId() == null) {
                continue;
            }
            rowMap.put(row.getId(), row);
            MasterDataOrgNodeView node = new MasterDataOrgNodeView();
            node.setId(row.getId());
            node.setAccountNo(row.getAccountNo());
            node.setRealName(row.getRealName());
            node.setRoleName(row.getRoleName());
            node.setRoleLevel(row.getRoleLevel());
            node.setCompanyName(row.getCompanyName());
            node.setStoreSummary(row.getStoreSummary());
            nodeMap.put(row.getId(), node);
        }

        List<MasterDataOrgNodeView> roots = new ArrayList<>();
        for (MasterDataOrgUserRow row : rows) {
            if (row.getId() == null) {
                continue;
            }
            MasterDataOrgNodeView node = nodeMap.get(row.getId());
            Long parentId = resolveOrgParentId(row, rows, rowMap);
            MasterDataOrgNodeView parent = parentId != null ? nodeMap.get(parentId) : null;
            if (parent == null || parentId.equals(row.getId())) {
                roots.add(node);
                continue;
            }
            parent.getChildren().add(node);
        }

        roots.sort(Comparator.comparing(MasterDataOrgNodeView::getRoleLevel, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MasterDataOrgNodeView::getId, Comparator.nullsLast(Long::compareTo)));
        sortOrgNodes(roots);
        return roots;
    }

    private List<MasterDataMenuView> filterSystemMenus(List<MasterDataMenuView> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<MasterDataMenuView> filtered = new ArrayList<>();
        for (MasterDataMenuView menu : menus) {
            if (isSystemMenu(menu)) {
                filtered.add(menu);
            }
        }
        return filtered;
    }

    private Set<Long> listSystemPermissionMenuIds() {
        List<MasterDataMenuView> menus = masterDataMapper.listMenus();
        if (menus == null || menus.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (MasterDataMenuView menu : menus) {
            if (menu.getId() != null && isSystemPermissionMenu(menu)) {
                ids.add(menu.getId());
            }
        }
        return ids;
    }

    private boolean isSystemMenu(MasterDataMenuView menu) {
        if (menu == null) {
            return false;
        }
        if (menu.getId() != null && SYSTEM_MENU_IDS.contains(menu.getId())) {
            return true;
        }
        return isSystemMenuPath(menu) || isSystemMenuName(menu);
    }

    private boolean isSystemPermissionMenu(MasterDataMenuView menu) {
        if (menu == null) {
            return false;
        }
        if (menu.getId() != null && SYSTEM_PERMISSION_MENU_IDS.contains(menu.getId())) {
            return true;
        }
        return isSystemPermissionMenuPath(menu) || isSystemPermissionMenuName(menu);
    }

    private boolean isSystemMenuPath(MasterDataMenuView menu) {
        String path = normalizeMenuPath(menu.getUrlPath());
        return Set.of(
                "/purchase",
                "/product",
                "/user",
                "/system",
                "/logistics",
                "/api/sku/cost",
                "/api/purchase/order",
                "/api/sku/manage",
                "/purchase/logistics-quote",
                "/api/user/manage",
                "/api/user/role",
                "/system/role",
                "/api/system/role",
                "/system/menu",
                "/api/system/menu",
                "/system/file-management"
        ).contains(path);
    }

    private boolean isSystemPermissionMenuPath(MasterDataMenuView menu) {
        String path = normalizeMenuPath(menu.getUrlPath());
        return Set.of(
                "/api/sku/cost",
                "/api/purchase/order",
                "/api/sku/manage",
                "/purchase/logistics-quote",
                "/api/user/manage",
                "/api/user/role",
                "/system/role",
                "/api/system/role",
                "/system/menu",
                "/api/system/menu",
                "/system/file-management"
        ).contains(path);
    }

    private boolean isSystemMenuName(MasterDataMenuView menu) {
        String name = normalizeMenuName(menu.getName());
        return Set.of(
                "采购",
                "商品",
                "用户",
                "系统管理",
                "物流",
                "利润计算",
                "利润计算与上架",
                "采购单",
                "商品管理",
                "货代管理",
                "账号管理",
                "用户管理",
                "角色分配",
                "角色管理",
                "角色维护",
                "菜单维护",
                "文件管理",
                "官方文件管理",
                "官方费用文件管理"
        ).contains(name);
    }

    private boolean isSystemPermissionMenuName(MasterDataMenuView menu) {
        String name = normalizeMenuName(menu.getName());
        return Set.of(
                "利润计算",
                "利润计算与上架",
                "采购单",
                "商品管理",
                "货代管理",
                "账号管理",
                "用户管理",
                "角色分配",
                "角色管理",
                "角色维护",
                "菜单维护",
                "文件管理",
                "官方文件管理",
                "官方费用文件管理"
        ).contains(name);
    }

    private String normalizeMenuPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        return path.trim().toLowerCase();
    }

    private String normalizeMenuName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim();
    }

    private boolean shouldHideOrgRow(
            MasterDataOrgUserRow row,
            Set<Long> visibleIds,
            Long operatorUserId,
            Integer operatorRoleLevel
    ) {
        if (row.getId() == null || !visibleIds.contains(row.getId())) {
            return true;
        }
        if (row.getId().equals(operatorUserId)) {
            return false;
        }
        return row.getRoleLevel() != null && row.getRoleLevel() <= operatorRoleLevel;
    }

    @Transactional
    public Long createUser(MasterDataSaveUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能新增账号。");
        }
        String accountNo = requireText(command.getAccountNo(), "请输入登录账号。");
        if (masterDataMapper.countUsersByAccountNo(accountNo, null) > 0) {
            throw new IllegalArgumentException("登录账号已存在，请更换后再试。");
        }
        MasterDataRoleAssignmentSeed role = requireRoleSeed(command.getRoleId());
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        MasterDataUserView operator = requireOperator(operatorUserId);
        ensureRoleAssignable(operator, role, "无权创建该角色的账号。");
        Long userId = masterDataMapper.nextUserId();
        LocalDateTime expiredTime = command.getExpiredTime() != null ? command.getExpiredTime() : DEFAULT_EXPIRED_TIME;
        List<String> scopedStoreCodes = normalizeScopedStoreCodes(role, command.getStoreCodes(), operatorUserId);
        masterDataMapper.insertUser(
                userId,
                trimToNull(command.getPhone()),
                trimToNull(command.getEmail()),
                accountNo,
                requireText(command.getPassword(), "请设置初始密码。"),
                roleCode(role),
                role.getId(),
                normalizeAccountType(command.getAccountType()),
                trimToNull(command.getRealName()),
                trimToNull(command.getCompanyName() != null ? command.getCompanyName() : operator != null ? operator.getCompanyName() : null),
                normalizeLevel(role.getLevel()),
                1,
                expiredTime,
                operatorUserId
        );
        syncUserMenus(userId, role.getId(), expiredTime, operatorUserId);
        syncUserStores(userId, scopedStoreCodes, operatorUserId);
        return userId;
    }

    @Transactional
    public String updateUser(Long userId, MasterDataSaveUserCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能编辑账号。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能编辑账号。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        ensureOperatorCanManageUser(operatorUserId, userId);
        LocalDateTime expiredTime = command.getExpiredTime() != null
                ? command.getExpiredTime()
                : existing.getExpiredTime() != null ? existing.getExpiredTime() : DEFAULT_EXPIRED_TIME;
        masterDataMapper.updateUserProfile(
                userId,
                trimToNull(command.getRealName()),
                trimToNull(command.getPhone()),
                trimToNull(command.getEmail()),
                normalizeAccountType(command.getAccountType() != null ? command.getAccountType() : existing.getAccountType()),
                trimToNull(command.getCompanyName() != null ? command.getCompanyName() : existing.getCompanyName()),
                expiredTime,
                operatorUserId
        );
        if (StringUtils.hasText(command.getPassword())) {
            masterDataMapper.updateUserPassword(userId, command.getPassword().trim(), operatorUserId);
        }
        if (command.getRoleId() != null && !command.getRoleId().equals(existing.getRoleId())) {
            MasterDataRoleAssignmentSeed role = requireRoleSeed(command.getRoleId());
            ensureRoleAssignable(requireOperator(operatorUserId), role, "无权分配该角色。");
            masterDataMapper.updateUserRole(userId, role.getId(), roleCode(role), normalizeLevel(role.getLevel()), operatorUserId);
            syncUserMenus(userId, role.getId(), expiredTime, operatorUserId);
        } else if (command.getExpiredTime() != null) {
            masterDataMapper.updateUserMenuExpiredTime(userId, expiredTime, operatorUserId);
        }
        return "已更新账号 " + existing.getAccountNo() + "。";
    }

    @Transactional
    public String toggleUserStatus(Long userId, MasterDataToggleUserStatusCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能调整状态。");
        }
        if (command == null || command.getStatus() == null) {
            throw new IllegalArgumentException("缺少目标状态，暂时不能调整账号状态。");
        }
        if (command.getStatus() != 0 && command.getStatus() != 1) {
            throw new IllegalArgumentException("账号状态只支持启用或禁用。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        ensureOperatorCanManageUser(operatorUserId, userId);
        masterDataMapper.updateUserStatus(userId, command.getStatus(), operatorUserId);
        return command.getStatus() == 1
                ? "已启用账号 " + existing.getAccountNo() + "。"
                : "已禁用账号 " + existing.getAccountNo() + "。";
    }

    @Transactional
    public String resetUserPassword(Long userId, MasterDataResetPasswordCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能重置密码。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        Long operatorUserId = defaultOperator(command != null ? command.getOperatorUserId() : null);
        ensureOperatorCanManageUser(operatorUserId, userId);
        String password = command != null && StringUtils.hasText(command.getPassword())
                ? command.getPassword().trim()
                : "123456";
        masterDataMapper.updateUserPassword(userId, password, operatorUserId);
        return "已把账号 " + existing.getAccountNo() + " 的密码重置为 " + password + "。";
    }

    @Transactional
    public String assignStores(Long userId, MasterDataAssignStoresCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能分配店铺。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        Long operatorUserId = defaultOperator(command != null ? command.getOperatorUserId() : null);
        ensureOperatorCanManageUser(operatorUserId, userId);
        MasterDataRoleAssignmentSeed targetRole = existing.getRoleId() != null
                ? requireRoleSeed(existing.getRoleId())
                : null;
        List<String> storeCodes = normalizeScopedStoreCodes(targetRole, command != null ? command.getStoreCodes() : List.of(), operatorUserId);
        syncUserStores(userId, storeCodes, operatorUserId);
        return storeCodes.isEmpty()
                ? "已清空账号 " + existing.getAccountNo() + " 的负责店铺。"
                : "已更新账号 " + existing.getAccountNo() + " 的负责店铺。";
    }

    @Transactional
    public String updateUserQuota(Long userId, MasterDataUserQuotaCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能修改额度。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少额度信息，暂时不能修改额度。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        masterDataMapper.updateUserQuota(
                userId,
                normalizeQuota(command.getListLimit(), existing.getListLimit()),
                normalizeQuota(command.getCollectLimit(), existing.getCollectLimit()),
                normalizeQuota(command.getWhApLimit(), existing.getWhApLimit()),
                normalizeQuota(command.getChatgptTranslateLimit(), existing.getChatgptTranslateLimit()),
                defaultOperator(command.getOperatorUserId())
        );
        return "已更新账号 " + existing.getAccountNo() + " 的额度配置。";
    }

    @Transactional
    public String updateStoreQuota(Long userId, Long projectId, MasterDataUserQuotaCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能修改店铺额度。");
        }
        if (projectId == null) {
            throw new IllegalArgumentException("缺少店铺信息，暂时不能修改店铺额度。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少额度信息，暂时不能修改店铺额度。");
        }
        FoundationUserStoreLink existing = masterDataMapper.selectUserStoreLink(userId, projectId);
        if (existing == null) {
            throw new IllegalArgumentException("目标店铺不在当前账号名下，暂时不能修改额度。");
        }
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        if (!operatorUserId.equals(userId) && !isSystemAdmin(requireOperator(operatorUserId))) {
            ensureOperatorCanManageUser(operatorUserId, userId);
        }
        masterDataMapper.updateProjectQuota(
                projectId,
                normalizeQuota(command.getListLimit(), existing.getListLimit()),
                normalizeQuota(command.getCollectLimit(), existing.getCollectLimit()),
                normalizeQuota(command.getWhApLimit(), existing.getWhApLimit()),
                normalizeQuota(command.getChatgptTranslateLimit(), existing.getChatgptTranslateLimit()),
                operatorUserId
        );
        return "已更新店铺 " + firstNonBlank(existing.getProjectName(), existing.getProjectCode(), existing.getStoreCode()) + " 的额度配置。";
    }

    public List<MasterDataPaymentRecordView> listPayments(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能查看费用记录。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        return masterDataMapper.listMerchantPayments(userId);
    }

    public List<MasterDataPaymentRecordView> listPayments(Long userId, Long operatorUserId) {
        ensureOperatorCanViewUser(operatorUserId, userId);
        return listPayments(userId);
    }

    @Transactional
    public String addPayment(Long userId, MasterDataAddPaymentCommand command) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能添加费用记录。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少费用记录，暂时不能继续保存。");
        }
        MasterDataUserView existing = masterDataMapper.selectUserView(userId);
        if (existing == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        if (command.getAmount() == null || command.getAmount().signum() < 0) {
            throw new IllegalArgumentException("请输入正确的付费金额。");
        }
        if (command.getPaymentDate() == null) {
            throw new IllegalArgumentException("请选择付费日期。");
        }
        Long paymentId = masterDataMapper.nextMerchantPaymentId();
        masterDataMapper.insertMerchantPayment(
                paymentId,
                userId,
                command.getAmount(),
                command.getPaymentDate(),
                trimToNull(command.getRemark()),
                defaultOperator(command.getOperatorUserId())
        );
        return "已为账号 " + existing.getAccountNo() + " 添加费用记录。";
    }

    @Transactional
    public String createRole(MasterDataSaveRoleCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少角色信息，暂时不能新增角色。");
        }
        String name = requireText(command.getName(), "请输入角色名称。");
        String code = requireText(command.getCode(), "请输入角色编码。");
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        ensureSystemAdmin(operatorUserId, "只有系统管理员可以创建角色。");
        Long roleId = masterDataMapper.nextRoleId();
        masterDataMapper.insertRole(
                roleId,
                name,
                code,
                trimToNull(command.getDescription()),
                false,
                normalizeParentId(command.getParentId()),
                normalizeLevel(command.getLevel()),
                operatorUserId
        );
        syncRoleMenus(roleId, normalizeMenuIds(command.getMenuIds()));
        return "已新增角色 " + name + "。";
    }

    @Transactional
    public String updateRole(Long roleId, MasterDataSaveRoleCommand command) {
        if (roleId == null) {
            throw new IllegalArgumentException("缺少角色信息，暂时不能修改角色。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少角色信息，暂时不能修改角色。");
        }
        MasterDataRoleView existing = masterDataMapper.selectRoleView(roleId);
        if (existing == null) {
            throw new IllegalArgumentException("目标角色不存在。");
        }
        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        ensureSystemAdmin(operatorUserId, "只有系统管理员可以修改角色。");
        if (Boolean.TRUE.equals(existing.getSystemRole()) && command.getMenuIds() == null) {
            throw new IllegalArgumentException("系统预设角色仅支持修改菜单。");
        }
        masterDataMapper.updateRole(
                roleId,
                requireText(command.getName(), "请输入角色名称。"),
                trimToNull(command.getDescription()),
                normalizeParentId(command.getParentId()),
                existing.getLevel(),
                operatorUserId
        );
        syncRoleMenus(roleId, normalizeMenuIds(command.getMenuIds()));
        syncRoleUsersMenus(roleId, normalizeMenuIds(command.getMenuIds()), operatorUserId);
        return "已更新角色 " + existing.getName() + "。";
    }

    @Transactional
    public String deleteRole(Long roleId, Long operatorUserId) {
        if (roleId == null) {
            throw new IllegalArgumentException("缺少角色信息，暂时不能删除角色。");
        }
        MasterDataRoleView existing = masterDataMapper.selectRoleView(roleId);
        if (existing == null) {
            throw new IllegalArgumentException("目标角色不存在。");
        }
        Long resolvedOperatorUserId = defaultOperator(operatorUserId);
        ensureSystemAdmin(resolvedOperatorUserId, "只有系统管理员可以删除角色。");
        if (Boolean.TRUE.equals(existing.getSystemRole())) {
            throw new IllegalArgumentException("系统预设角色暂时不能删除。");
        }
        masterDataMapper.softDeleteUserMenusByRoleId(roleId, resolvedOperatorUserId);
        masterDataMapper.softDeleteRoleMenusByRoleId(roleId);
        masterDataMapper.softDeleteRole(roleId, resolvedOperatorUserId);
        return "已删除角色 " + existing.getName() + "。";
    }

    @Transactional
    public String createMenu(MasterDataSaveMenuCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少菜单信息，暂时不能新增菜单。");
        }
        ensureSystemAdmin(defaultOperator(command.getOperatorUserId()), "只有系统管理员可以创建菜单。");
        String name = requireText(command.getName(), "请输入菜单名称。");
        Long menuId = masterDataMapper.nextMenuId();
        masterDataMapper.insertMenu(menuId, name, normalizeParentId(command.getParentId()), trimToNull(command.getUrlPath()));
        return "已新增菜单 " + name + "。";
    }

    @Transactional
    public String updateMenu(Long menuId, MasterDataSaveMenuCommand command) {
        if (menuId == null) {
            throw new IllegalArgumentException("缺少菜单信息，暂时不能修改菜单。");
        }
        if (command == null) {
            throw new IllegalArgumentException("缺少菜单信息，暂时不能修改菜单。");
        }
        MasterDataMenuView existing = masterDataMapper.selectMenuView(menuId);
        if (existing == null) {
            throw new IllegalArgumentException("目标菜单不存在。");
        }
        ensureSystemAdmin(defaultOperator(command.getOperatorUserId()), "只有系统管理员可以修改菜单。");
        masterDataMapper.updateMenu(
                menuId,
                requireText(command.getName(), "请输入菜单名称。"),
                normalizeParentId(command.getParentId()),
                trimToNull(command.getUrlPath())
        );
        return "已更新菜单 " + existing.getName() + "。";
    }

    @Transactional
    public String deleteMenu(Long menuId, Long operatorUserId) {
        if (menuId == null) {
            throw new IllegalArgumentException("缺少菜单信息，暂时不能删除菜单。");
        }
        MasterDataMenuView existing = masterDataMapper.selectMenuView(menuId);
        if (existing == null) {
            throw new IllegalArgumentException("目标菜单不存在。");
        }
        Long resolvedOperatorUserId = defaultOperator(operatorUserId);
        ensureSystemAdmin(resolvedOperatorUserId, "只有系统管理员可以删除菜单。");
        masterDataMapper.softDeleteRoleMenusByMenuId(menuId);
        masterDataMapper.softDeleteUserMenusByMenuId(menuId, resolvedOperatorUserId);
        masterDataMapper.softDeleteMenu(menuId);
        return "已删除菜单 " + existing.getName() + "。";
    }

    @Transactional
    public String assignRole(MasterDataAssignRoleCommand command) {
        if (command == null || command.getUserId() == null) {
            throw new IllegalArgumentException("缺少用户信息，暂时不能分配角色。");
        }
        if (command.getRoleId() == null) {
            throw new IllegalArgumentException("缺少目标角色，暂时不能分配角色。");
        }

        MasterDataRoleAssignmentSeed role = masterDataMapper.selectRoleSeed(command.getRoleId());
        if (role == null) {
            throw new IllegalArgumentException("目标角色不存在。");
        }
        MasterDataRoleAssignmentSeed user = masterDataMapper.selectUserSeed(command.getUserId());
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在。");
        }

        Long operatorUserId = defaultOperator(command.getOperatorUserId());
        if (operatorUserId.equals(command.getUserId())) {
            throw new IllegalArgumentException("不能修改自己的角色。");
        }
        ensureOperatorCanManageUser(operatorUserId, command.getUserId());
        ensureRoleAssignable(requireOperator(operatorUserId), role, "无权分配该角色。");
        LocalDateTime expiredTime = masterDataMapper.selectUserExpiredTime(command.getUserId());
        if (expiredTime == null) {
            expiredTime = DEFAULT_EXPIRED_TIME;
        }
        masterDataMapper.updateUserRole(command.getUserId(), command.getRoleId(), roleCode(role), normalizeLevel(role.getLevel()), operatorUserId);
        syncUserMenus(command.getUserId(), command.getRoleId(), expiredTime, operatorUserId);
        if (isAllStoresRole(role)) {
            syncUserStores(command.getUserId(), normalizeScopedStoreCodes(role, List.of(), operatorUserId), operatorUserId);
        }

        String userLabel = StringUtils.hasText(user.getName()) ? user.getName() : String.valueOf(command.getUserId());
        return "已把用户 " + userLabel + " 调整为角色 " + role.getName() + "，并同步刷新了用户菜单权限。";
    }

    private void syncRoleMenus(Long roleId, List<Long> menuIds) {
        masterDataMapper.softDeleteRoleMenusByRoleId(roleId);
        if (menuIds.isEmpty()) {
            return;
        }
        long nextId = masterDataMapper.nextRoleMenuId();
        for (Long menuId : menuIds) {
            masterDataMapper.insertRoleMenu(nextId++, roleId, menuId);
        }
    }

    private void syncUserMenus(Long userId, Long roleId, LocalDateTime expiredTime, Long operatorUserId) {
        masterDataMapper.softDeleteUserMenus(userId, operatorUserId);
        List<Long> menuIds = masterDataMapper.listRoleMenuIds(roleId);
        if (menuIds.isEmpty()) {
            return;
        }
        long nextId = masterDataMapper.nextUserMenuId();
        for (Long menuId : menuIds) {
            masterDataMapper.insertUserMenu(nextId++, userId, menuId, LocalDateTime.now(), expiredTime, operatorUserId);
        }
    }

    private void syncRoleUsersMenus(Long roleId, List<Long> menuIds, Long operatorUserId) {
        List<MasterDataUserView> roleUsers = masterDataMapper.listUsersByRoleId(roleId);
        if (roleUsers == null || roleUsers.isEmpty()) {
            return;
        }
        masterDataMapper.softDeleteUserMenusByRoleId(roleId, operatorUserId);
        if (menuIds.isEmpty()) {
            return;
        }
        long nextId = masterDataMapper.nextUserMenuId();
        for (MasterDataUserView user : roleUsers) {
            LocalDateTime effectiveTime = user.getEffectiveTime() != null ? user.getEffectiveTime() : LocalDateTime.now();
            LocalDateTime expiredTime = user.getExpiredTime() != null ? user.getExpiredTime() : DEFAULT_EXPIRED_TIME;
            for (Long menuId : menuIds) {
                masterDataMapper.insertUserMenu(nextId++, user.getId(), menuId, effectiveTime, expiredTime, operatorUserId);
            }
        }
    }

    private void syncUserStores(Long userId, List<String> storeCodes, Long operatorUserId) {
        MasterDataUserView operator = requireOperator(operatorUserId);
        boolean systemAdmin = isSystemAdmin(operator);
        if (storeCodes.isEmpty()) {
            softDeleteUserStoresInScope(userId, operatorUserId, systemAdmin);
            return;
        }
        List<MasterDataStoreSeed> seeds = systemAdmin
                ? masterDataMapper.listStoreSeedsByCodes(storeCodes)
                : masterDataMapper.listStoreSeedsByCodesForOperator(storeCodes, operatorUserId);
        Map<String, MasterDataStoreSeed> seedMap = new HashMap<>();
        for (MasterDataStoreSeed seed : seeds) {
            if (!StringUtils.hasText(seed.getStoreCode()) || seedMap.containsKey(seed.getStoreCode())) {
                continue;
            }
            seedMap.put(seed.getStoreCode(), seed);
        }

        Set<String> existingStoreCodes = systemAdmin
                ? Set.of()
                : new HashSet<>(masterDataMapper.listUserStoreCodes(userId));
        List<String> missingStoreCodes = new ArrayList<>();
        for (String storeCode : storeCodes) {
            MasterDataStoreSeed seed = seedMap.get(storeCode);
            if ((seed == null || seed.getId() == null) && !existingStoreCodes.contains(storeCode)) {
                missingStoreCodes.add(storeCode);
                continue;
            }
        }
        if (!missingStoreCodes.isEmpty()) {
            throw new IllegalArgumentException("以下店铺不在当前账号可分配范围内：" + String.join("、", missingStoreCodes));
        }

        softDeleteUserStoresInScope(userId, operatorUserId, systemAdmin);
        long nextAccessId = masterDataMapper.nextProjectAccessId();
        Set<Long> insertedProjectIds = new LinkedHashSet<>();
        for (String storeCode : storeCodes) {
            MasterDataStoreSeed seed = seedMap.get(storeCode);
            if (seed == null || seed.getId() == null) {
                continue;
            }
            if (!insertedProjectIds.add(seed.getId())) {
                continue;
            }
            masterDataMapper.insertUserProjectAccess(
                    nextAccessId++,
                    userId,
                    seed.getId(),
                    operatorUserId
            );
        }
    }

    private void sortOrgNodes(List<MasterDataOrgNodeView> nodes) {
        for (MasterDataOrgNodeView node : nodes) {
            node.getChildren().sort(Comparator
                    .comparing(MasterDataOrgNodeView::getRoleLevel, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MasterDataOrgNodeView::getId, Comparator.nullsLast(Long::compareTo)));
            sortOrgNodes(node.getChildren());
        }
    }

    private Long resolveOrgParentId(
            MasterDataOrgUserRow current,
            List<MasterDataOrgUserRow> rows,
            Map<Long, MasterDataOrgUserRow> rowMap
    ) {
        Integer currentLevel = current.getRoleLevel();
        if (currentLevel == null || currentLevel <= 1) {
            return null;
        }
        for (Long managerId : parseManagerIds(current.getManagerIds())) {
            MasterDataOrgUserRow manager = rowMap.get(managerId);
            if (manager != null
                    && manager.getId() != null
                    && !manager.getId().equals(current.getId())
                    && manager.getRoleLevel() != null
                    && manager.getRoleLevel() < currentLevel) {
                return manager.getId();
            }
        }

        MasterDataOrgUserRow creator = rowMap.get(current.getCreatedBy());
        if (creator != null && creator.getId() != null && !creator.getId().equals(current.getId())) {
            return creator.getId();
        }

        MasterDataOrgUserRow fallback = null;
        for (MasterDataOrgUserRow candidate : rows) {
            if (candidate.getId() == null || candidate.getId().equals(current.getId())) {
                continue;
            }
            Integer candidateLevel = candidate.getRoleLevel();
            if (candidateLevel == null || candidateLevel >= currentLevel) {
                continue;
            }
            if (fallback == null) {
                fallback = candidate;
                continue;
            }
            boolean sameCompany = sameText(candidate.getCompanyName(), current.getCompanyName());
            boolean fallbackSameCompany = sameText(fallback.getCompanyName(), current.getCompanyName());
            if (sameCompany && !fallbackSameCompany) {
                fallback = candidate;
                continue;
            }
            if (sameCompany == fallbackSameCompany && candidateLevel > fallback.getRoleLevel()) {
                fallback = candidate;
            }
        }
        return fallback != null ? fallback.getId() : null;
    }

    private List<MasterDataUserView> filterMerchantUsers(List<MasterDataUserView> users, Integer operatorRoleLevel) {
        if (operatorRoleLevel != null && operatorRoleLevel > 0) {
            return List.of();
        }
        List<MasterDataUserView> result = new ArrayList<>();
        for (MasterDataUserView user : users) {
            if (user.getRoleLevel() != null && user.getRoleLevel() == 1) {
                result.add(user);
            }
        }
        return result;
    }

    private List<MasterDataUserView> filterTeamUsers(
            List<MasterDataUserView> users,
            Long operatorUserId,
            Integer operatorRoleLevel
    ) {
        if (operatorUserId == null || operatorRoleLevel == null) {
            return users;
        }
        if (operatorRoleLevel <= 0) {
            List<MasterDataUserView> result = new ArrayList<>();
            for (MasterDataUserView user : users) {
                if (user.getRoleLevel() != null && user.getRoleLevel() > operatorRoleLevel) {
                    result.add(user);
                }
            }
            return result;
        }
        Set<Long> visibleIds = collectVisibleUserIds(users, operatorUserId);
        List<MasterDataUserView> result = new ArrayList<>();
        for (MasterDataUserView user : users) {
            if (user.getId() == null || !visibleIds.contains(user.getId())) {
                continue;
            }
            if (user.getRoleLevel() != null && user.getRoleLevel() <= operatorRoleLevel) {
                continue;
            }
            result.add(user);
        }
        return result;
    }

    private void applyOperatorScopedStoreSummaries(List<MasterDataUserView> users, Long operatorUserId) {
        if (users.isEmpty() || operatorUserId == null) {
            return;
        }
        Map<Long, List<FoundationUserStoreLink>> linksByUser = listOperatorScopedLinksByUser(operatorUserId);
        for (MasterDataUserView user : users) {
            if (user.getId() == null) {
                continue;
            }
            List<FoundationUserStoreLink> links = linksByUser.getOrDefault(user.getId(), List.of());
            int storeCount = countDistinctStores(links);
            int companyCount = countDistinctCompanies(links).size();
            user.setStoreCount(storeCount);
            user.setAuthorizedStoreCount(countAuthorizedStores(links));
            user.setDirectCompanyCount(companyCount);
            user.setDirectCompanies(joinCompanyLabels(links));
            user.setManagedStoreCount(storeCount);
            user.setManagedCompanyCount(companyCount);
            user.setSites(joinSites(links));
            if (storeCount == 0) {
                user.setBindingStatus("UNBOUND");
            }
        }
    }

    private void applyOperatorScopedOrgSummaries(List<MasterDataOrgUserRow> rows, Long operatorUserId) {
        if (rows.isEmpty() || operatorUserId == null) {
            return;
        }
        Map<Long, List<FoundationUserStoreLink>> linksByUser = listOperatorScopedLinksByUser(operatorUserId);
        for (MasterDataOrgUserRow row : rows) {
            if (row.getId() == null) {
                continue;
            }
            row.setStoreSummary(joinCompanyLabels(linksByUser.getOrDefault(row.getId(), List.of())));
        }
    }

    private void applyOperatorScopedStoreDetail(FoundationUserDetail detail, Long operatorUserId) {
        if (detail == null || detail.getId() == null || operatorUserId == null) {
            return;
        }
        List<FoundationUserStoreLink> links = listOperatorScopedLinksByUser(operatorUserId)
                .getOrDefault(detail.getId(), List.of());
        int storeCount = countDistinctStores(links);
        int authorizedStoreCount = countAuthorizedStores(links);
        int companyCount = countDistinctCompanies(links).size();
        String companyLabels = joinCompanyLabels(links);

        detail.setStoreLinks(links);
        detail.setStoreCount(storeCount);
        detail.setAuthorizedStoreCount(authorizedStoreCount);
        detail.setDirectCompanyCount(companyCount);
        detail.setDirectCompanies(companyLabels);
        detail.setManagedStoreCount(storeCount);
        detail.setManagedAuthorizedStoreCount(authorizedStoreCount);
        detail.setManagedCompanyCount(companyCount);
        detail.setManagedCompanies(companyLabels);
        detail.setSites(joinSites(links));
        detail.setDescendantStoreLinks(List.of());
        detail.setDescendantStoreCount(0);
        detail.setDescendantCompanyCount(0);
        if (storeCount == 0) {
            detail.setBindingStatus("UNBOUND");
        }
    }

    private Map<Long, List<FoundationUserStoreLink>> listOperatorScopedLinksByUser(Long operatorUserId) {
        Map<Long, List<FoundationUserStoreLink>> linksByUser = new HashMap<>();
        if (operatorUserId == null) {
            return linksByUser;
        }
        for (FoundationUserStoreLink link : masterDataMapper.listOperatorScopedStoreLinks(operatorUserId)) {
            if (link.getUserId() == null) {
                continue;
            }
            linksByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(link);
        }
        return linksByUser;
    }

    private int countDistinctStores(List<FoundationUserStoreLink> links) {
        Set<String> keys = new LinkedHashSet<>();
        for (FoundationUserStoreLink link : links) {
            String key = storeScopeKey(link);
            if (StringUtils.hasText(key)) {
                keys.add(key);
            }
        }
        return keys.size();
    }

    private int countAuthorizedStores(List<FoundationUserStoreLink> links) {
        Set<String> keys = new LinkedHashSet<>();
        for (FoundationUserStoreLink link : links) {
            if (!Boolean.TRUE.equals(link.getAuthorized())) {
                continue;
            }
            String key = storeScopeKey(link);
            if (StringUtils.hasText(key)) {
                keys.add(key);
            }
        }
        return keys.size();
    }

    private Map<String, String> countDistinctCompanies(List<FoundationUserStoreLink> links) {
        Map<String, String> companies = new LinkedHashMap<>();
        for (FoundationUserStoreLink link : links) {
            String key = normalizedScopeValue(StringUtils.hasText(link.getOrgCode()) ? link.getOrgCode() : link.getOrgName());
            if (!StringUtils.hasText(key) || companies.containsKey(key)) {
                continue;
            }
            companies.put(key, StringUtils.hasText(link.getOrgName()) ? link.getOrgName().trim() : key);
        }
        return companies;
    }

    private String joinCompanyLabels(List<FoundationUserStoreLink> links) {
        return String.join("、", countDistinctCompanies(links).values());
    }

    private String joinSites(List<FoundationUserStoreLink> links) {
        Set<String> sites = new LinkedHashSet<>();
        for (FoundationUserStoreLink link : links) {
            if (!StringUtils.hasText(link.getSite())) {
                continue;
            }
            for (String site : link.getSite().split(",")) {
                if (StringUtils.hasText(site)) {
                    sites.add(site.trim());
                }
            }
        }
        return String.join(", ", sites);
    }

    private String storeScopeKey(FoundationUserStoreLink link) {
        String projectCode = normalizedScopeValue(link.getProjectCode());
        if (StringUtils.hasText(projectCode)) {
            return projectCode;
        }
        return normalizedScopeValue(link.getStoreCode());
    }

    private String normalizedScopeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private Set<Long> collectVisibleUserIds(List<? extends MasterDataUserView> users, Long operatorUserId) {
        Map<Long, List<Long>> childrenByCreator = new HashMap<>();
        for (MasterDataUserView user : users) {
            if (user.getCreatedBy() == null || user.getId() == null) {
                continue;
            }
            childrenByCreator.computeIfAbsent(user.getCreatedBy(), key -> new ArrayList<>()).add(user.getId());
        }
        Set<Long> visibleIds = collectVisibleDescendantIds(childrenByCreator, operatorUserId);
        visibleIds.addAll(masterDataMapper.listProjectAuthorizedUserIds(operatorUserId));
        return visibleIds;
    }

    private Set<Long> collectVisibleOrgUserIds(List<MasterDataOrgUserRow> rows, Long operatorUserId) {
        Map<Long, List<Long>> childrenByCreator = new HashMap<>();
        for (MasterDataOrgUserRow row : rows) {
            if (row.getCreatedBy() == null || row.getId() == null) {
                continue;
            }
            childrenByCreator.computeIfAbsent(row.getCreatedBy(), key -> new ArrayList<>()).add(row.getId());
        }
        Set<Long> visibleIds = collectVisibleDescendantIds(childrenByCreator, operatorUserId);
        visibleIds.addAll(masterDataMapper.listProjectAuthorizedUserIds(operatorUserId));
        return visibleIds;
    }

    private Set<Long> collectVisibleDescendantIds(Map<Long, List<Long>> childrenByCreator, Long operatorUserId) {
        Set<Long> visibleIds = new LinkedHashSet<>();
        Set<Long> visited = new HashSet<>();
        collectChildren(childrenByCreator, operatorUserId, visibleIds, visited);
        return visibleIds;
    }

    private void collectChildren(
            Map<Long, List<Long>> childrenByCreator,
            Long currentUserId,
            Set<Long> visibleIds,
            Set<Long> visited
    ) {
        if (currentUserId == null || !visited.add(currentUserId)) {
            return;
        }
        for (Long childId : childrenByCreator.getOrDefault(currentUserId, List.of())) {
            if (childId == null || !visibleIds.add(childId)) {
                continue;
            }
            collectChildren(childrenByCreator, childId, visibleIds, visited);
        }
    }

    private MasterDataUserView requireOperator(Long operatorUserId) {
        MasterDataUserView operator = masterDataMapper.selectUserView(operatorUserId);
        if (operator == null) {
            throw new IllegalArgumentException("当前操作账号不存在，暂时不能继续。");
        }
        return operator;
    }

    private void ensureRoleAssignable(
            MasterDataUserView operator,
            MasterDataRoleAssignmentSeed targetRole,
            String errorMessage
    ) {
        if (targetRole == null || targetRole.getLevel() == null) {
            throw new IllegalArgumentException("目标角色缺少层级，暂时不能分配。");
        }
        Integer operatorLevel = operator.getRoleLevel();
        if (operatorLevel == null || targetRole.getLevel() <= operatorLevel) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void ensureOperatorCanManageUser(Long operatorUserId, Long targetUserId) {
        MasterDataUserView operator = requireOperator(operatorUserId);
        MasterDataUserView target = masterDataMapper.selectUserView(targetUserId);
        if (target == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        if (operatorUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("不能操作自己的账号。");
        }
        if (isSystemAdmin(operator)) {
            return;
        }
        Integer operatorLevel = operator.getRoleLevel();
        Integer targetLevel = target.getRoleLevel();
        if (operatorLevel == null || targetLevel == null || targetLevel <= operatorLevel) {
            throw new IllegalArgumentException("无权操作该用户。");
        }
        Set<Long> manageableIds = collectManagedDescendantUserIds(operatorUserId);
        manageableIds.addAll(masterDataMapper.listProjectAuthorizedUserIds(operatorUserId));
        if (!manageableIds.contains(targetUserId)) {
            throw new IllegalArgumentException("无权操作非自己负责范围内的用户。");
        }
    }

    private void ensureOperatorCanViewUser(Long operatorUserId, Long targetUserId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("缺少账号信息，暂时不能查看。");
        }
        MasterDataUserView operator = requireOperator(operatorUserId);
        MasterDataUserView target = masterDataMapper.selectUserView(targetUserId);
        if (target == null) {
            throw new IllegalArgumentException("目标账号不存在。");
        }
        if (operatorUserId.equals(targetUserId) || isSystemAdmin(operator)) {
            return;
        }
        Integer operatorLevel = operator.getRoleLevel();
        Integer targetLevel = target.getRoleLevel();
        if (operatorLevel == null || targetLevel == null || targetLevel <= operatorLevel) {
            throw new IllegalArgumentException("无权查看该用户。");
        }
        Set<Long> visibleIds = collectManagedDescendantUserIds(operatorUserId);
        visibleIds.addAll(masterDataMapper.listProjectAuthorizedUserIds(operatorUserId));
        if (!visibleIds.contains(targetUserId)) {
            throw new IllegalArgumentException("无权查看非自己负责范围内的用户。");
        }
    }

    private Set<Long> collectManagedDescendantUserIds(Long operatorUserId) {
        Map<Long, List<Long>> childrenByCreator = new HashMap<>();
        for (MasterDataUserView user : masterDataMapper.listUsers()) {
            if (user.getCreatedBy() == null || user.getId() == null) {
                continue;
            }
            childrenByCreator.computeIfAbsent(user.getCreatedBy(), key -> new ArrayList<>()).add(user.getId());
        }
        return collectVisibleDescendantIds(childrenByCreator, operatorUserId);
    }

    private boolean isSystemAdmin(MasterDataUserView user) {
        return user != null && user.getRoleLevel() != null && user.getRoleLevel() <= 0;
    }

    private boolean isExternalAccount(MasterDataUserView user) {
        return user != null && "external".equalsIgnoreCase(user.getAccountType());
    }

    private boolean isExternalAccount(MasterDataOrgUserRow user) {
        return user != null && "external".equalsIgnoreCase(user.getAccountType());
    }

    private boolean isActiveUser(MasterDataUserView user) {
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }

    private void ensureSystemAdmin(Long operatorUserId, String errorMessage) {
        MasterDataUserView operator = requireOperator(operatorUserId);
        if (!Long.valueOf(1L).equals(operator.getRoleId()) && !isSystemAdmin(operator)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void softDeleteUserStoresInScope(Long userId, Long operatorUserId, boolean systemAdmin) {
        if (systemAdmin) {
            masterDataMapper.hardDeleteInactiveUserStores(userId);
            masterDataMapper.softDeleteUserStores(userId, operatorUserId);
            return;
        }
        masterDataMapper.hardDeleteInactiveUserStoresForOperator(userId, operatorUserId);
        masterDataMapper.softDeleteUserStoresForOperator(userId, operatorUserId, operatorUserId);
    }

    private List<String> normalizeScopedStoreCodes(
            MasterDataRoleAssignmentSeed role,
            List<String> rawStoreCodes,
            Long operatorUserId
    ) {
        List<String> storeCodes = normalizeStoreCodes(rawStoreCodes);
        if (!storeCodes.isEmpty() || !isAllStoresRole(role) || operatorUserId == null) {
            return storeCodes;
        }
        return normalizeStoreCodes(masterDataMapper.listUserStoreCodes(operatorUserId));
    }

    private boolean isAllStoresRole(MasterDataRoleAssignmentSeed role) {
        if (role == null) {
            return false;
        }
        String roleCode = roleCode(role);
        return "PURCHASE".equalsIgnoreCase(roleCode)
                || "WAREHOUSE".equalsIgnoreCase(roleCode)
                || "采购".equalsIgnoreCase(trimToNull(role.getName()))
                || "仓管".equalsIgnoreCase(trimToNull(role.getName()));
    }

    private Integer normalizeQuota(Integer nextValue, Integer fallbackValue) {
        if (nextValue == null) {
            return fallbackValue != null ? fallbackValue : 0;
        }
        if (nextValue < 0) {
            throw new IllegalArgumentException("额度不能小于 0。");
        }
        return nextValue;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "-";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "-";
    }

    private String requireText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long normalizeParentId(Long value) {
        return value != null ? value : 0L;
    }

    private Integer normalizeLevel(Integer value) {
        return value != null ? value : 3;
    }

    private String normalizeAccountType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "internal";
        }
        return normalized.equalsIgnoreCase("internal") ? "internal" : "external";
    }

    private Long defaultOperator(Long operatorUserId) {
        if (operatorUserId == null) {
            throw new IllegalArgumentException("缺少当前操作账号，暂时不能继续。");
        }
        return operatorUserId;
    }

    private MasterDataRoleAssignmentSeed requireRoleSeed(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("缺少目标角色，暂时不能继续保存账号。");
        }
        MasterDataRoleAssignmentSeed role = masterDataMapper.selectRoleSeed(roleId);
        if (role == null) {
            throw new IllegalArgumentException("目标角色不存在。");
        }
        return role;
    }

    private String roleCode(MasterDataRoleAssignmentSeed role) {
        return StringUtils.hasText(role.getCode()) ? role.getCode().trim() : role.getName();
    }

    private List<Long> normalizeMenuIds(List<Long> rawMenuIds) {
        if (rawMenuIds == null || rawMenuIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long menuId : rawMenuIds) {
            if (menuId != null && menuId > 0) {
                uniqueIds.add(menuId);
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    private List<String> normalizeStoreCodes(List<String> rawStoreCodes) {
        if (rawStoreCodes == null || rawStoreCodes.isEmpty()) {
            return List.of();
        }
        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (String storeCode : rawStoreCodes) {
            if (StringUtils.hasText(storeCode)) {
                uniqueCodes.add(storeCode.trim());
            }
        }
        return new ArrayList<>(uniqueCodes);
    }

    private List<Long> parseManagerIds(String rawManagerIds) {
        if (!StringUtils.hasText(rawManagerIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : rawManagerIds.split(",")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(token.trim()));
            } catch (NumberFormatException ignore) {
                // ignore invalid manager ids in legacy samples
            }
        }
        return ids;
    }

    private boolean sameText(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
