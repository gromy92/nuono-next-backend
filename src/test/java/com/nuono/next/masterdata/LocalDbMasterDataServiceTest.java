package com.nuono.next.masterdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.foundation.FoundationUserDetail;
import com.nuono.next.foundation.LocalDbFoundationOverviewService;
import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbMasterDataServiceTest {

    @Mock
    private MasterDataMapper masterDataMapper;

    @Mock
    private LocalDbFoundationOverviewService foundationOverviewService;

    private LocalDbMasterDataService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbMasterDataService(masterDataMapper, foundationOverviewService);
    }

    @Test
    void shouldFilterMerchantUsersForSuperAdminAccountView() {
        MasterDataUserView admin = user(10001L, "admin", 0, 1L);
        MasterDataUserView boss = user(10002L, "boss", 1, 10001L);
        MasterDataUserView manager = user(10003L, "manager", 2, 10002L);
        MasterDataUserView legacyBoss = user(10006L, "legacy-boss", 1, 10001L);
        when(masterDataMapper.listUsers()).thenReturn(List.of(admin, boss, manager, legacyBoss));

        List<MasterDataUserView> result = service.listUsers(10001L, 0, "merchant");

        assertEquals(List.of(10002L, 10006L), result.stream().map(MasterDataUserView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldFilterTeamUsersRecursivelyForBossView() {
        MasterDataUserView admin = user(10001L, "admin", 0, 1L);
        MasterDataUserView boss = user(10002L, "boss", 1, 10001L);
        MasterDataUserView manager = user(10003L, "manager", 2, 10002L);
        MasterDataUserView ops = user(10004L, "ops", 3, 10003L);
        MasterDataUserView otherBoss = user(10006L, "other-boss", 1, 10001L);
        MasterDataUserView externalProjectAccount = user(10007L, "external-project", 3, 10002L);
        externalProjectAccount.setAccountType("external");
        when(masterDataMapper.listUsers()).thenReturn(List.of(admin, boss, manager, ops, otherBoss, externalProjectAccount));

        List<MasterDataUserView> result = service.listUsers(10002L, 1, "team");

        assertEquals(List.of(10003L, 10004L), result.stream().map(MasterDataUserView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldAllowBossToToggleLowerLevelProjectAuthorizedUser() {
        MasterDataToggleUserStatusCommand command = new MasterDataToggleUserStatusCommand();
        command.setOperatorUserId(10002L);
        command.setStatus(0);

        MasterDataUserView admin = user(10001L, "admin", 0, null);
        MasterDataUserView boss = user(10002L, "boss", 1, 10001L);
        MasterDataUserView otherBoss = user(10006L, "other-boss", 1, 10001L);
        MasterDataUserView manager = user(10003L, "manager", 2, 10006L);
        when(masterDataMapper.selectUserView(10002L)).thenReturn(boss);
        when(masterDataMapper.selectUserView(10003L)).thenReturn(manager);
        when(masterDataMapper.listUsers()).thenReturn(List.of(admin, boss, otherBoss, manager));
        when(masterDataMapper.listProjectAuthorizedUserIds(10002L)).thenReturn(List.of(10003L));

        String message = service.toggleUserStatus(10003L, command);

        assertEquals("已禁用账号 manager。", message);
        verify(masterDataMapper).updateUserStatus(10003L, 0, 10002L);
    }

    @Test
    void shouldLimitOrgTreeToOperatorAndLowerLevelDescendants() {
        MasterDataOrgUserRow boss = orgUser(10003L, "boss", "老板", 1, 10001L);
        MasterDataOrgUserRow manager = orgUser(10005L, "manager", "主管", 2, 10003L);
        MasterDataOrgUserRow ops = orgUser(10006L, "ops", "运营", 3, 10005L);
        MasterDataOrgUserRow otherBoss = orgUser(10004L, "other-boss", "老板", 1, 10003L);
        when(masterDataMapper.listOrgUsers()).thenReturn(List.of(boss, manager, ops, otherBoss));

        List<MasterDataOrgNodeView> result = service.listOrgTree(10003L, 1);

        assertEquals(List.of(10003L), result.stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
        assertEquals(List.of(10005L), result.get(0).getChildren().stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
        assertEquals(List.of(10006L), result.get(0).getChildren().get(0).getChildren().stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldHideDisabledUsersFromOrgTree() {
        MasterDataOrgUserRow boss = orgUser(10003L, "boss", "老板", 1, 10001L);
        MasterDataOrgUserRow disabledManager = orgUser(10005L, "manager", "主管", 2, 10003L);
        disabledManager.setStatus(0);
        MasterDataOrgUserRow ops = orgUser(10006L, "ops", "运营", 3, 10003L);
        when(masterDataMapper.listOrgUsers()).thenReturn(List.of(boss, disabledManager, ops));

        List<MasterDataOrgNodeView> result = service.listOrgTree(10003L, 1);

        assertEquals(List.of(10003L), result.stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
        assertEquals(List.of(10006L), result.get(0).getChildren().stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldHideExternalAccountsFromOrgTree() {
        MasterDataOrgUserRow boss = orgUser(10003L, "boss", "老板", 1, 10001L);
        MasterDataOrgUserRow internalManager = orgUser(10005L, "manager", "主管", 2, 10003L);
        MasterDataOrgUserRow externalProjectAccount = orgUser(10007L, "external-project", "仓管", 3, 10003L);
        externalProjectAccount.setAccountType("external");
        when(masterDataMapper.listOrgUsers()).thenReturn(List.of(boss, internalManager, externalProjectAccount));

        List<MasterDataOrgNodeView> result = service.listOrgTree(10003L, 1);

        assertEquals(List.of(10003L), result.stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
        assertEquals(List.of(10005L), result.get(0).getChildren().stream().map(MasterDataOrgNodeView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldAssembleRoleMenuIdsOntoRoleViews() {
        MasterDataRoleView operator = role(4L, "运营");
        MasterDataRoleView supervisor = role(3L, "运营主管");

        MasterDataRoleMenuRow operatorDashboard = roleMenu(4L, 11L);
        MasterDataRoleMenuRow supervisorDashboard = roleMenu(3L, 11L);
        MasterDataRoleMenuRow supervisorRoleAssign = roleMenu(3L, 21L);

        when(masterDataMapper.listRoles()).thenReturn(List.of(operator, supervisor));
        when(masterDataMapper.listRoleMenuRows()).thenReturn(List.of(
                operatorDashboard,
                supervisorDashboard,
                supervisorRoleAssign
        ));

        List<MasterDataRoleView> roles = service.listRoles();

        assertEquals(2, roles.size());
        assertIterableEquals(List.of(11L), roles.get(0).getMenuIds());
        assertIterableEquals(List.of(11L, 21L), roles.get(1).getMenuIds());
    }

    @Test
    void shouldFilterRoleMenusToSupportedSystemMenus() {
        MasterDataRoleView operator = role(4L, "运营");
        when(masterDataMapper.listRoles()).thenReturn(List.of(operator));
        when(masterDataMapper.listMenus()).thenReturn(List.of(
                menu(7L, "商品管理", "/api/sku/manage"),
                menu(13L, "模型测试", "/ai/chat")
        ));
        when(masterDataMapper.listRoleMenuRows()).thenReturn(List.of(
                roleMenu(4L, 7L),
                roleMenu(4L, 13L)
        ));

        List<MasterDataRoleView> roles = service.listRoles();

        assertIterableEquals(List.of(7L), roles.get(0).getMenuIds());
    }

    @Test
    void shouldFilterMenuListToSupportedSystemMenus() {
        when(masterDataMapper.listMenus()).thenReturn(List.of(
                menu(3L, "商品", "/product"),
                menu(7L, "商品管理", "/api/sku/manage"),
                menu(13L, "模型测试", "/ai/chat")
        ));

        List<MasterDataMenuView> menus = service.listMenus();

        assertEquals(List.of(3L, 7L), menus.stream().map(MasterDataMenuView::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldRefreshUserRoleAndUserMenusOnAssignRole() {
        MasterDataAssignRoleCommand command = new MasterDataAssignRoleCommand();
        command.setUserId(10004L);
        command.setRoleId(3L);
        command.setOperatorUserId(10002L);

        MasterDataRoleAssignmentSeed role = seed(3L, "运营主管", 2);
        MasterDataRoleAssignmentSeed user = seed(10004L, "马天龙");
        MasterDataUserView operator = user(10002L, "boss", 1, 10001L);
        MasterDataUserView target = user(10004L, "ops", 3, 10002L);
        LocalDateTime expiredTime = LocalDateTime.of(2028, 6, 30, 23, 59, 59);

        when(masterDataMapper.selectRoleSeed(3L)).thenReturn(role);
        when(masterDataMapper.selectUserSeed(10004L)).thenReturn(user);
        when(masterDataMapper.selectUserView(10002L)).thenReturn(operator);
        when(masterDataMapper.selectUserView(10004L)).thenReturn(target);
        when(masterDataMapper.listUsers()).thenReturn(List.of(operator, target));
        when(masterDataMapper.listRoleMenuIds(3L)).thenReturn(List.of(11L, 21L));
        when(masterDataMapper.selectUserExpiredTime(10004L)).thenReturn(expiredTime);
        when(masterDataMapper.nextUserMenuId()).thenReturn(700L);

        String message = service.assignRole(command);

        assertEquals("已把用户 马天龙 调整为角色 运营主管，并同步刷新了用户菜单权限。", message);

        InOrder inOrder = inOrder(masterDataMapper);
        inOrder.verify(masterDataMapper).selectUserExpiredTime(10004L);
        inOrder.verify(masterDataMapper).updateUserRole(10004L, 3L, "运营主管", 2, 10002L);
        inOrder.verify(masterDataMapper).softDeleteUserMenus(10004L, 10002L);
        inOrder.verify(masterDataMapper).listRoleMenuIds(3L);
        inOrder.verify(masterDataMapper).nextUserMenuId();
        inOrder.verify(masterDataMapper).insertUserMenu(eq(700L), eq(10004L), eq(11L), any(LocalDateTime.class), eq(expiredTime), eq(10002L));
        inOrder.verify(masterDataMapper).insertUserMenu(eq(701L), eq(10004L), eq(21L), any(LocalDateTime.class), eq(expiredTime), eq(10002L));
        verify(masterDataMapper, never()).hardDeleteInactiveUserStoresForOperator(anyLong(), anyLong());
        verify(masterDataMapper, never()).softDeleteUserStoresForOperator(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldUseExplicitOperatorAndDefaultExpiryWhenAssignRoleNeedsFallbacks() {
        MasterDataAssignRoleCommand command = new MasterDataAssignRoleCommand();
        command.setUserId(10004L);
        command.setRoleId(4L);
        command.setOperatorUserId(10003L);

        MasterDataUserView admin = user(10003L, "admin", 0, null);
        MasterDataUserView target = user(10004L, "ops", 3, 10002L);
        when(masterDataMapper.selectRoleSeed(4L)).thenReturn(seed(4L, "运营", 3));
        when(masterDataMapper.selectUserSeed(10004L)).thenReturn(seed(10004L, "马天龙"));
        when(masterDataMapper.selectUserView(10003L)).thenReturn(admin);
        when(masterDataMapper.selectUserView(10004L)).thenReturn(target);
        when(masterDataMapper.listRoleMenuIds(4L)).thenReturn(List.of(15L));
        when(masterDataMapper.selectUserExpiredTime(10004L)).thenReturn(null);
        when(masterDataMapper.nextUserMenuId()).thenReturn(900L);

        service.assignRole(command);

        verify(masterDataMapper).updateUserRole(10004L, 4L, "运营", 3, 10003L);
        verify(masterDataMapper).softDeleteUserMenus(10004L, 10003L);
        verify(masterDataMapper, never()).hardDeleteInactiveUserStores(anyLong());
        verify(masterDataMapper, never()).softDeleteUserStores(anyLong(), anyLong());
        verify(masterDataMapper).insertUserMenu(
                eq(900L),
                eq(10004L),
                eq(15L),
                any(LocalDateTime.class),
                eq(LocalDateTime.of(2099, 12, 31, 23, 59, 59)),
                eq(10003L)
        );
    }

    @Test
    void shouldAssignAllScopedStoresWhenAssignRoleRequiresAllStores() {
        MasterDataAssignRoleCommand command = new MasterDataAssignRoleCommand();
        command.setUserId(10004L);
        command.setRoleId(5L);
        command.setOperatorUserId(10002L);

        MasterDataRoleAssignmentSeed role = seed(5L, "采购", 3);
        MasterDataRoleAssignmentSeed user = seed(10004L, "ops");
        MasterDataUserView operator = user(10002L, "boss", 1, 10001L);
        MasterDataUserView target = user(10004L, "ops", 3, 10002L);

        when(masterDataMapper.selectRoleSeed(5L)).thenReturn(role);
        when(masterDataMapper.selectUserSeed(10004L)).thenReturn(user);
        when(masterDataMapper.selectUserView(10002L)).thenReturn(operator);
        when(masterDataMapper.selectUserView(10004L)).thenReturn(target);
        when(masterDataMapper.listUsers()).thenReturn(List.of(operator, target));
        when(masterDataMapper.listRoleMenuIds(5L)).thenReturn(List.of());
        when(masterDataMapper.selectUserExpiredTime(10004L)).thenReturn(null);
        when(masterDataMapper.listUserStoreCodes(10002L)).thenReturn(List.of("PRJ001", "PRJ002"));
        when(masterDataMapper.listStoreSeedsByCodesForOperator(List.of("PRJ001", "PRJ002"), 10002L)).thenReturn(List.of(
                storeSeed(1L, "PRJ001", "PRJ001", "SA"),
                storeSeed(2L, "PRJ002", "PRJ002", "AE")
        ));
        when(masterDataMapper.listUserStoreCodes(10004L)).thenReturn(List.of());
        when(masterDataMapper.nextProjectAccessId()).thenReturn(30000L);

        service.assignRole(command);

        verify(masterDataMapper).hardDeleteInactiveUserStoresForOperator(10004L, 10002L);
        verify(masterDataMapper).softDeleteUserStoresForOperator(10004L, 10002L, 10002L);
        verify(masterDataMapper).insertUserProjectAccess(30000L, 10004L, 1L, 10002L);
        verify(masterDataMapper).insertUserProjectAccess(30001L, 10004L, 2L, 10002L);
    }

    @Test
    void shouldNotInsertUserMenusWhenTargetRoleHasNoMenus() {
        MasterDataAssignRoleCommand command = new MasterDataAssignRoleCommand();
        command.setUserId(10004L);
        command.setRoleId(8L);
        command.setOperatorUserId(10003L);

        MasterDataUserView admin = user(10003L, "admin", 0, null);
        MasterDataUserView target = user(10004L, "ops", 3, 10002L);
        when(masterDataMapper.selectRoleSeed(8L)).thenReturn(seed(8L, "观察角色", 4));
        when(masterDataMapper.selectUserSeed(10004L)).thenReturn(seed(10004L, "马天龙"));
        when(masterDataMapper.selectUserView(10003L)).thenReturn(admin);
        when(masterDataMapper.selectUserView(10004L)).thenReturn(target);
        when(masterDataMapper.listRoleMenuIds(8L)).thenReturn(List.of());
        when(masterDataMapper.selectUserExpiredTime(10004L)).thenReturn(LocalDateTime.now());

        service.assignRole(command);

        verify(masterDataMapper, never()).nextUserMenuId();
        verify(masterDataMapper, never()).insertUserMenu(anyLong(), anyLong(), anyLong(), any(), any(), anyLong());
    }

    @Test
    void shouldAssignSiteStoreCodesForSystemAdmin() {
        MasterDataAssignStoresCommand command = new MasterDataAssignStoresCommand();
        command.setOperatorUserId(10003L);
        command.setStoreCodes(List.of("STR108065-NAE", "STR108065-NSA"));

        MasterDataUserView admin = user(10003L, "admin", 0, null);
        MasterDataUserView target = user(338L, "luwenhuan", 3, 10002L);

        when(masterDataMapper.selectUserView(10003L)).thenReturn(admin);
        when(masterDataMapper.selectUserView(338L)).thenReturn(target);
        when(masterDataMapper.listStoreSeedsByCodes(List.of("STR108065-NAE", "STR108065-NSA"))).thenReturn(List.of(
                storeSeed(108065L, "PRJ108065", "STR108065-NAE", "AE"),
                storeSeed(108065L, "PRJ108065", "STR108065-NSA", "SA")
        ));
        when(masterDataMapper.nextProjectAccessId()).thenReturn(30000L);

        String message = service.assignStores(338L, command);

        assertEquals("已更新账号 luwenhuan 的负责店铺。", message);
        verify(masterDataMapper).hardDeleteInactiveUserStores(338L);
        verify(masterDataMapper).softDeleteUserStores(338L, 10003L);
        verify(masterDataMapper).insertUserProjectAccess(30000L, 338L, 108065L, 10003L);
        verify(masterDataMapper, never()).listStoreSeedsByCodesForOperator(any(), anyLong());
    }

    @Test
    void shouldRejectMissingOrUnknownRoleAssignmentSeeds() {
        MasterDataAssignRoleCommand empty = new MasterDataAssignRoleCommand();
        assertEquals("缺少用户信息，暂时不能分配角色。", assertThrows(IllegalArgumentException.class, () -> service.assignRole(empty)).getMessage());

        MasterDataAssignRoleCommand missingRole = new MasterDataAssignRoleCommand();
        missingRole.setUserId(10004L);
        assertEquals("缺少目标角色，暂时不能分配角色。", assertThrows(IllegalArgumentException.class, () -> service.assignRole(missingRole)).getMessage());

        MasterDataAssignRoleCommand unknownRole = new MasterDataAssignRoleCommand();
        unknownRole.setUserId(10004L);
        unknownRole.setRoleId(3L);
        when(masterDataMapper.selectRoleSeed(3L)).thenReturn(null);
        assertEquals("目标角色不存在。", assertThrows(IllegalArgumentException.class, () -> service.assignRole(unknownRole)).getMessage());

        MasterDataAssignRoleCommand unknownUser = new MasterDataAssignRoleCommand();
        unknownUser.setUserId(10004L);
        unknownUser.setRoleId(3L);
        when(masterDataMapper.selectRoleSeed(3L)).thenReturn(seed(3L, "运营主管"));
        when(masterDataMapper.selectUserSeed(10004L)).thenReturn(null);
        assertEquals("目标用户不存在。", assertThrows(IllegalArgumentException.class, () -> service.assignRole(unknownUser)).getMessage());
    }

    @Test
    void shouldDelegateUserDetailToFoundationService() {
        FoundationUserDetail detail = new FoundationUserDetail();
        detail.setId(10004L);
        when(foundationOverviewService.buildUserDetail(10004L)).thenReturn(detail);

        FoundationUserDetail result = service.getUserDetail(10004L);

        assertEquals(10004L, result.getId());
        verify(foundationOverviewService).buildUserDetail(10004L);
    }

    @Test
    void shouldUpdateQuotaForMerchantUser() {
        MasterDataUserView existing = user(10002L, "boss", 1, 10001L);
        existing.setListLimit(50);
        existing.setCollectLimit(40);
        existing.setWhApLimit(10);
        existing.setChatgptTranslateLimit(20);
        when(masterDataMapper.selectUserView(10002L)).thenReturn(existing);

        MasterDataUserQuotaCommand command = new MasterDataUserQuotaCommand();
        command.setListLimit(88);
        command.setCollectLimit(66);
        command.setWhApLimit(15);
        command.setChatgptTranslateLimit(33);
        command.setOperatorUserId(10001L);

        assertEquals("已更新账号 boss 的额度配置。", service.updateUserQuota(10002L, command));
        verify(masterDataMapper).updateUserQuota(10002L, 88, 66, 15, 33, 10001L);
    }

    @Test
    void shouldAddMerchantPaymentRecord() {
        MasterDataUserView existing = user(10002L, "boss", 1, 10001L);
        when(masterDataMapper.selectUserView(10002L)).thenReturn(existing);
        when(masterDataMapper.nextMerchantPaymentId()).thenReturn(50003L);

        MasterDataAddPaymentCommand command = new MasterDataAddPaymentCommand();
        command.setAmount(new BigDecimal("888.50"));
        command.setPaymentDate(LocalDate.of(2026, 4, 28));
        command.setRemark("续费补款");
        command.setOperatorUserId(10001L);

        assertEquals("已为账号 boss 添加费用记录。", service.addPayment(10002L, command));
        verify(masterDataMapper).insertMerchantPayment(
                50003L,
                10002L,
                new BigDecimal("888.50"),
                LocalDate.of(2026, 4, 28),
                "续费补款",
                10001L
        );
    }

    @Test
    void shouldCreateUpdateAndDeleteRole() {
        MasterDataSaveRoleCommand create = new MasterDataSaveRoleCommand();
        create.setName("菜单运营");
        create.setCode("MENU_OPS");
        create.setDescription("菜单运营角色");
        create.setParentId(3L);
        create.setLevel(3);
        create.setOperatorUserId(10001L);
        create.setMenuIds(List.of(10L, 25L));

        when(masterDataMapper.nextRoleId()).thenReturn(8L);
        when(masterDataMapper.nextRoleMenuId()).thenReturn(5000L);
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));

        assertEquals("已新增角色 菜单运营。", service.createRole(create));
        verify(masterDataMapper).insertRole(8L, "菜单运营", "MENU_OPS", "菜单运营角色", false, 3L, 3, 10001L);
        verify(masterDataMapper).softDeleteRoleMenusByRoleId(8L);
        verify(masterDataMapper).insertRoleMenu(5000L, 8L, 10L);
        verify(masterDataMapper).insertRoleMenu(5001L, 8L, 25L);

        MasterDataSaveRoleCommand update = new MasterDataSaveRoleCommand();
        update.setName("菜单运营-新");
        update.setDescription("已更新");
        update.setParentId(2L);
        update.setLevel(2);
        update.setMenuIds(List.of(10L));
        update.setOperatorUserId(10001L);

        MasterDataRoleView existing = role(8L, "菜单运营");
        existing.setSystemRole(false);
        existing.setLevel(3);
        when(masterDataMapper.selectRoleView(8L)).thenReturn(existing);
        when(masterDataMapper.nextRoleMenuId()).thenReturn(6000L);
        when(masterDataMapper.listUsersByRoleId(8L)).thenReturn(List.of());

        assertEquals("已更新角色 菜单运营。", service.updateRole(8L, update));
        verify(masterDataMapper).updateRole(8L, "菜单运营-新", "已更新", 2L, 3, 10001L);
        verify(masterDataMapper, times(2)).softDeleteRoleMenusByRoleId(8L);
        verify(masterDataMapper).insertRoleMenu(6000L, 8L, 10L);

        assertEquals("已删除角色 菜单运营。", service.deleteRole(8L, 10001L));
        verify(masterDataMapper).softDeleteUserMenusByRoleId(8L, 10001L);
        verify(masterDataMapper).softDeleteRole(8L, 10001L);
    }

    @Test
    void shouldRejectDeletingSystemRoleOrNonAdminOperator() {
        MasterDataRoleView systemRole = role(1L, "系统管理员");
        systemRole.setSystemRole(true);
        when(masterDataMapper.selectRoleView(1L)).thenReturn(systemRole);
        when(masterDataMapper.selectUserView(10001L)).thenReturn(user(10001L, "admin", 0, null));
        assertEquals("系统预设角色暂时不能删除。", assertThrows(IllegalArgumentException.class, () -> service.deleteRole(1L, 10001L)).getMessage());

        MasterDataRoleView customRole = role(8L, "菜单运营");
        customRole.setSystemRole(false);
        when(masterDataMapper.selectRoleView(8L)).thenReturn(customRole);
        when(masterDataMapper.selectUserView(10004L)).thenReturn(user(10004L, "ops", 3, 10002L));
        assertEquals("只有系统管理员可以删除角色。", assertThrows(IllegalArgumentException.class, () -> service.deleteRole(8L, 10004L)).getMessage());
    }

    @Test
    void shouldCreateUpdateAndDeleteMenu() {
        MasterDataSaveMenuCommand create = new MasterDataSaveMenuCommand();
        create.setName("菜单维护");
        create.setParentId(0L);
        create.setUrlPath("/system/menu");
        create.setOperatorUserId(10003L);
        when(masterDataMapper.nextMenuId()).thenReturn(26L);
        when(masterDataMapper.selectUserView(10003L)).thenReturn(user(10003L, "admin", 0, null));

        assertEquals("已新增菜单 菜单维护。", service.createMenu(create));
        verify(masterDataMapper).insertMenu(26L, "菜单维护", 0L, "/system/menu");

        MasterDataSaveMenuCommand update = new MasterDataSaveMenuCommand();
        update.setName("菜单维护-新");
        update.setParentId(10L);
        update.setUrlPath("/system/menu/manage");
        update.setOperatorUserId(10003L);
        MasterDataMenuView existing = menu(26L, "菜单维护");
        when(masterDataMapper.selectMenuView(26L)).thenReturn(existing);

        assertEquals("已更新菜单 菜单维护。", service.updateMenu(26L, update));
        verify(masterDataMapper).updateMenu(26L, "菜单维护-新", 10L, "/system/menu/manage");

        assertEquals("已删除菜单 菜单维护。", service.deleteMenu(26L, 10003L));
        verify(masterDataMapper).softDeleteRoleMenusByMenuId(26L);
        verify(masterDataMapper).softDeleteUserMenusByMenuId(26L, 10003L);
        verify(masterDataMapper).softDeleteMenu(26L);
    }

    private MasterDataRoleView role(Long id, String name) {
        MasterDataRoleView role = new MasterDataRoleView();
        role.setId(id);
        role.setName(name);
        return role;
    }

    private MasterDataUserView user(Long id, String accountNo, Integer roleLevel, Long createdBy) {
        MasterDataUserView user = new MasterDataUserView();
        user.setId(id);
        user.setAccountNo(accountNo);
        user.setRoleLevel(roleLevel);
        user.setCreatedBy(createdBy);
        user.setStatus(1);
        user.setAccountType("internal");
        return user;
    }

    private MasterDataRoleMenuRow roleMenu(Long roleId, Long menuId) {
        MasterDataRoleMenuRow row = new MasterDataRoleMenuRow();
        row.setRoleId(roleId);
        row.setMenuId(menuId);
        return row;
    }

    private MasterDataRoleAssignmentSeed seed(Long id, String name) {
        return seed(id, name, 3);
    }

    private MasterDataRoleAssignmentSeed seed(Long id, String name, Integer level) {
        MasterDataRoleAssignmentSeed seed = new MasterDataRoleAssignmentSeed();
        seed.setId(id);
        seed.setName(name);
        seed.setLevel(level);
        return seed;
    }

    private MasterDataMenuView menu(Long id, String name) {
        return menu(id, name, null);
    }

    private MasterDataMenuView menu(Long id, String name, String urlPath) {
        MasterDataMenuView menu = new MasterDataMenuView();
        menu.setId(id);
        menu.setName(name);
        menu.setUrlPath(urlPath);
        return menu;
    }

    private MasterDataStoreSeed storeSeed(Long id, String projectCode, String storeCode, String site) {
        MasterDataStoreSeed seed = new MasterDataStoreSeed();
        seed.setId(id);
        seed.setOrgCode("ORG");
        seed.setOrgName("组织");
        seed.setProjectCode(projectCode);
        seed.setProjectName(projectCode);
        seed.setStoreCode(storeCode);
        seed.setSite(site);
        seed.setAuthorized(true);
        return seed;
    }

    private MasterDataOrgUserRow orgUser(Long id, String accountNo, String roleName, Integer roleLevel, Long createdBy) {
        MasterDataOrgUserRow row = new MasterDataOrgUserRow();
        row.setId(id);
        row.setAccountNo(accountNo);
        row.setRealName(accountNo);
        row.setRoleName(roleName);
        row.setRoleLevel(roleLevel);
        row.setCreatedBy(createdBy);
        row.setStatus(1);
        row.setAccountType("internal");
        return row;
    }
}
