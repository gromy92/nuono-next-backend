package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbFileParseTaskCatalogFacadeTest {

    @Mock
    private FileManagementParseMapper mapper;

    @Mock
    private FileParseTaskCatalogService catalogService;

    private LocalDbFileManagementParseService facade;

    @BeforeEach
    void setUp() {
        facade = new LocalDbFileManagementParseService(
                mapper,
                new FileParseStorageProperties(),
                mock(FileParseUploadArchiveService.class),
                catalogService,
                new FileParseActionPolicy(),
                mock(FileParseInputExtractionService.class),
                mock(FileParseStructuredAiService.class),
                mock(FileParseResultDiffService.class),
                mock(FileParseResultPersistenceService.class),
                mock(FileParseItemReviewService.class),
                mock(FileParseQueryViewService.class),
                mock(FileParsePublishService.class),
                mock(FileParseLogisticsChannelActivationService.class)
        );
    }

    @Test
    void authorizesAdminBeforeListingPlans() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        List<FileParseTargetPlanSummary> expected = List.of(new FileParseTargetPlanSummary());
        when(mapper.selectUserContext(10001L)).thenReturn(admin);
        when(catalogService.listTargetPlans(admin)).thenReturn(expected);

        List<FileParseTargetPlanSummary> actual = facade.listTargetPlans(session(10001L, 0));

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, catalogService);
        order.verify(mapper).selectUserContext(10001L);
        order.verify(catalogService).listTargetPlans(admin);
    }

    @Test
    void authorizesMenuAndForwardsUnchangedTaskFilters() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTaskListView expected = new FileParseTaskListView();
        when(mapper.selectUserContext(10002L)).thenReturn(boss);
        when(mapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(catalogService.listTasks(boss, " quote ", 4005L, " done ", 2, 500))
                .thenReturn(expected);

        FileParseTaskListView actual = facade.listTasks(
                session(10002L, 1),
                " quote ",
                4005L,
                " done ",
                2,
                500
        );

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, catalogService);
        order.verify(mapper).selectUserContext(10002L);
        order.verify(mapper).countActiveUserMenu(
                10002L,
                LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID
        );
        order.verify(catalogService).listTasks(boss, " quote ", 4005L, " done ", 2, 500);
    }

    @Test
    void loadsTaskAndVisiblePlanBeforeProjectingDetail() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        FileParseTaskRow task = task(20001L, 4005L);
        FileParseTargetPlanRow plan = plan(4005L);
        FileParseTaskDetailView expected = new FileParseTaskDetailView();
        when(mapper.selectUserContext(10004L)).thenReturn(operator);
        when(mapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(mapper.selectTask(20001L)).thenReturn(task);
        when(mapper.selectVisibleTargetPlan(4005L, 3, false)).thenReturn(plan);
        when(catalogService.getTask(task, plan)).thenReturn(expected);

        FileParseTaskDetailView actual = facade.getTask(session(10004L, 3), 20001L);

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, catalogService);
        order.verify(mapper).selectUserContext(10004L);
        order.verify(mapper).countActiveUserMenu(
                10004L,
                LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID
        );
        order.verify(mapper).selectTask(20001L);
        order.verify(mapper).selectVisibleTargetPlan(4005L, 3, false);
        order.verify(catalogService).getTask(task, plan);
    }

    @Test
    void rejectsMissingMenuBeforeCatalogQuery() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        when(mapper.selectUserContext(10002L)).thenReturn(boss);
        when(mapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(0);

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> facade.listTargetPlans(session(10002L, 1))
        );

        verifyNoInteractions(catalogService);
    }

    @Test
    void rejectsInvisibleTaskPlanBeforeLoadingCatalogInputs() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        when(mapper.selectUserContext(10004L)).thenReturn(operator);
        when(mapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(mapper.selectTask(20001L)).thenReturn(task(20001L, 4005L));
        when(mapper.selectVisibleTargetPlan(4005L, 3, false)).thenReturn(null);

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> facade.getTask(session(10004L, 3), 20001L)
        );

        verifyNoInteractions(catalogService);
    }

    @Test
    void rejectsMissingTaskIdBeforeCatalogProjection() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        when(mapper.selectUserContext(10001L)).thenReturn(admin);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.getTask(session(10001L, 0), null)
        );

        assertEquals("解析文档 ID 不能为空。", error.getMessage());
        verifyNoInteractions(catalogService);
    }

    @Test
    void rejectsUnknownTaskBeforeCatalogProjection() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        when(mapper.selectUserContext(10001L)).thenReturn(admin);
        when(mapper.selectTask(20001L)).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.getTask(session(10001L, 0), 20001L)
        );

        assertEquals("解析文档不存在或已删除。", error.getMessage());
        verifyNoInteractions(catalogService);
    }

    private AuthenticatedSession session(Long userId, Integer roleLevel) {
        return new AuthenticatedSession(userId, userId, roleLevel);
    }

    private FileParseUserContext user(Long id, Integer level, String code, String name) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(id);
        user.setRoleLevel(level);
        user.setRoleCode(code);
        user.setRoleName(name);
        user.setStatus(1);
        return user;
    }

    private FileParseTaskRow task(Long id, Long targetPlanId) {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setId(id);
        task.setTargetPlanId(targetPlanId);
        return task;
    }

    private FileParseTargetPlanRow plan(Long id) {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(id);
        return plan;
    }
}
