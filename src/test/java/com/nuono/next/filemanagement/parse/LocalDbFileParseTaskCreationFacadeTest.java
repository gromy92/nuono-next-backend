package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class LocalDbFileParseTaskCreationFacadeTest {

    @Mock
    private FileManagementParseMapper mapper;
    @Mock
    private FileParseTaskCreationService creationService;
    @Mock
    private FileParseActionPolicy actionPolicy;
    private LocalDbFileManagementParseService facade;

    @BeforeEach
    void setUp() {
        facade = new LocalDbFileManagementParseService(
                mapper,
                new FileParseStorageProperties(),
                mock(FileParseUploadArchiveService.class),
                mock(FileParseTaskCatalogService.class),
                creationService,
                actionPolicy,
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
    void authorizesAndResolvesPlanBeforeDelegatingNewTask() {
        FileParseUserContext user = user(10002L, 2, "OPS_MANAGER", "运营主管");
        FileParseTargetPlanRow plan = plan(4005L);
        FileParseCreateTaskCommand command = command(4005L, null);
        FileParseTaskDetailView expected = new FileParseTaskDetailView();
        when(mapper.selectUserContext(10002L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(false);
        when(mapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(mapper.selectVisibleTargetPlan(4005L, 2, false)).thenReturn(plan);
        when(actionPolicy.availableActions(plan, user)).thenReturn(actions(true));
        when(creationService.create(user, plan, null, command, " key ")).thenReturn(expected);

        FileParseTaskDetailView actual = facade.createTask(session(10002L, 2), command, " key ");

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, actionPolicy, creationService);
        order.verify(mapper).selectUserContext(10002L);
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).countActiveUserMenu(
                10002L,
                LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID
        );
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).selectVisibleTargetPlan(4005L, 2, false);
        order.verify(actionPolicy).availableActions(plan, user);
        order.verify(creationService).create(user, plan, null, command, " key ");
    }

    @Test
    void validatesParentPlanAndStatusBeforeDelegatingUpdate() {
        FileParseUserContext user = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTaskRow parent = task(20001L, 4005L, "published");
        FileParseTargetPlanRow parentVisibility = plan(4005L);
        FileParseTargetPlanRow currentPlan = plan(4005L);
        FileParseCreateTaskCommand command = command(null, 20001L);
        FileParseTaskDetailView expected = new FileParseTaskDetailView();
        when(mapper.selectUserContext(10001L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(true);
        when(mapper.selectTask(20001L)).thenReturn(parent);
        when(mapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(parentVisibility, currentPlan);
        when(actionPolicy.availableActions(currentPlan, user)).thenReturn(actions(true));
        when(creationService.create(user, currentPlan, parent, command, null)).thenReturn(expected);

        FileParseTaskDetailView actual = facade.createTask(session(10001L, 0), command, null);

        assertSame(expected, actual);
        InOrder order = inOrder(mapper, actionPolicy, creationService);
        order.verify(mapper).selectUserContext(10001L);
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).selectTask(20001L);
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).selectVisibleTargetPlan(4005L, 0, true);
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).selectVisibleTargetPlan(4005L, 0, true);
        order.verify(actionPolicy).availableActions(currentPlan, user);
        order.verify(creationService).create(user, currentPlan, parent, command, null);
    }

    @Test
    void rejectsTargetMismatchAfterParentVisibilityCheck() {
        FileParseUserContext user = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTaskRow parent = task(20001L, 4005L, "published");
        FileParseCreateTaskCommand command = command(4006L, 20001L);
        when(mapper.selectUserContext(10001L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(true);
        when(mapper.selectTask(20001L)).thenReturn(parent);
        when(mapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan(4005L));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.createTask(session(10001L, 0), command, null)
        );

        assertEquals("更新源文件必须使用原解析文档的目标输出方案。", error.getMessage());
        verify(mapper, never()).selectVisibleTargetPlan(4006L, 0, true);
        verifyNoInteractions(creationService);
    }

    @Test
    void rejectsRunningParentBeforeFinalPlanLookup() {
        FileParseUserContext user = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseCreateTaskCommand command = command(null, 20001L);
        when(mapper.selectUserContext(10001L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(true);
        when(mapper.selectTask(20001L)).thenReturn(task(20001L, 4005L, "parsing"));
        when(mapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan(4005L));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.createTask(session(10001L, 0), command, null)
        );

        assertEquals("原解析文档仍在解析中，不能更新源文件。", error.getMessage());
        verify(mapper).selectVisibleTargetPlan(4005L, 0, true);
        verifyNoInteractions(creationService);
    }

    @Test
    void authenticatesBeforeRejectingInvalidCommand() {
        FileParseUserContext user = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        when(mapper.selectUserContext(10001L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> facade.createTask(session(10001L, 0), null, null)
        );

        assertEquals("解析文档请求不能为空。", error.getMessage());
        verify(mapper).selectUserContext(10001L);
        verifyNoInteractions(creationService);
    }

    @Test
    void rejectsCreateActionBeforeModuleSideEffects() {
        FileParseUserContext user = user(10004L, 3, "OPS", "运营");
        FileParseTargetPlanRow plan = plan(4005L);
        FileParseCreateTaskCommand command = command(4005L, null);
        when(mapper.selectUserContext(10004L)).thenReturn(user);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(false);
        when(mapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(mapper.selectVisibleTargetPlan(4005L, 3, false)).thenReturn(plan);
        when(actionPolicy.availableActions(plan, user)).thenReturn(actions(false));

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> facade.createTask(session(10004L, 3), command, null)
        );

        verifyNoInteractions(creationService);
    }

    private FileParseCreateTaskCommand command(Long targetPlanId, Long parentTaskId) {
        FileParseTaskInputCommand input = new FileParseTaskInputCommand();
        input.setInputType("manual_text");
        input.setTextContent("source");
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setDocumentTitle("Document");
        command.setTargetPlanId(targetPlanId);
        command.setParentTaskId(parentTaskId);
        command.setInputItems(List.of(input));
        return command;
    }

    private FileParseUserContext user(Long id, Integer roleLevel, String roleCode, String roleName) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(id);
        user.setRoleLevel(roleLevel);
        user.setRoleCode(roleCode);
        user.setRoleName(roleName);
        user.setStatus(1);
        return user;
    }

    private FileParseTargetPlanRow plan(Long id) {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(id);
        return plan;
    }

    private FileParseTaskRow task(Long id, Long targetPlanId, String status) {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setId(id);
        task.setTargetPlanId(targetPlanId);
        task.setStatus(status);
        return task;
    }

    private FileParseAvailableActions actions(boolean canCreate) {
        FileParseAvailableActions actions = new FileParseAvailableActions();
        actions.setCanCreateTask(canCreate);
        return actions;
    }

    private AuthenticatedSession session(Long userId, Integer roleLevel) {
        return new AuthenticatedSession(userId, userId, roleLevel);
    }
}
